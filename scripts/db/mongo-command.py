#!/usr/bin/env python3
"""Client MongoDB minimal (protocole OP_MSG, sans dependance) pour les scripts de test.

Envoie UNE commande a un serveur MongoDB local et affiche la reponse en JSON. Sert a attendre le
demarrage du serveur, initialiser le replica set de test et verifier le contenu de la base apres
les scenarios (aucun client officiel n'est necessaire dans la VM).

Usage : mongo-command.py [--host H] [--port P] [--db BASE] [--allow-failure] '<commande JSON>'
  ex.   mongo-command.py --port 27018 '{"ping": 1}'
        mongo-command.py --port 27018 --db novabank '{"find": "banks", "filter": {}}'
Code de sortie : 0 si la reponse a ok = 1 (ou --allow-failure), 1 sinon, 2 si le serveur est injoignable.
Les entiers JSON sont envoyes en int32 s'ils tiennent, sinon en int64 ; un objet {"$long": n} force int64.
"""
import argparse
import json
import socket
import struct
import sys

OP_MSG = 2013


def encode_document(document):
    body = b"".join(encode_element(str(key), value) for key, value in document.items())
    return struct.pack("<i", len(body) + 5) + body + b"\x00"


def encode_element(name, value):
    key = name.encode("utf-8") + b"\x00"
    if isinstance(value, dict) and set(value) == {"$long"}:
        return b"\x12" + key + struct.pack("<q", int(value["$long"]))
    if isinstance(value, bool):
        return b"\x08" + key + (b"\x01" if value else b"\x00")
    if isinstance(value, int):
        if -2 ** 31 <= value < 2 ** 31:
            return b"\x10" + key + struct.pack("<i", value)
        return b"\x12" + key + struct.pack("<q", value)
    if isinstance(value, float):
        return b"\x01" + key + struct.pack("<d", value)
    if isinstance(value, str):
        data = value.encode("utf-8") + b"\x00"
        return b"\x02" + key + struct.pack("<i", len(data)) + data
    if value is None:
        return b"\x0a" + key
    if isinstance(value, dict):
        return b"\x03" + key + encode_document(value)
    if isinstance(value, list):
        return b"\x04" + key + encode_document({str(i): item for i, item in enumerate(value)})
    raise TypeError("Type non gere : %r" % (value,))


def read_cstring(data, offset):
    end = data.index(b"\x00", offset)
    return data[offset:end].decode("utf-8"), end + 1


def decode_document(data, offset=0, as_list=False):
    length = struct.unpack_from("<i", data, offset)[0]
    end = offset + length - 1
    position = offset + 4
    result = {}
    while position < end:
        kind = data[position]
        name, position = read_cstring(data, position + 1)
        value, position = decode_value(kind, data, position)
        result[name] = value
    return (list(result.values()) if as_list else result), offset + length


def decode_value(kind, data, position):
    if kind == 0x01:
        return struct.unpack_from("<d", data, position)[0], position + 8
    if kind in (0x02, 0x0D, 0x0E):
        length = struct.unpack_from("<i", data, position)[0]
        return data[position + 4:position + 3 + length].decode("utf-8"), position + 4 + length
    if kind == 0x03:
        return decode_document(data, position)
    if kind == 0x04:
        return decode_document(data, position, as_list=True)
    if kind == 0x05:
        length = struct.unpack_from("<i", data, position)[0]
        subtype = data[position + 4]
        raw = data[position + 5:position + 5 + length]
        return {"$binary": raw.hex(), "subtype": subtype}, position + 5 + length
    if kind == 0x06 or kind == 0x0A or kind == 0x7F or kind == 0xFF:
        return None, position
    if kind == 0x07:
        return {"$oid": data[position:position + 12].hex()}, position + 12
    if kind == 0x08:
        return data[position] == 1, position + 1
    if kind == 0x09:
        return {"$date": struct.unpack_from("<q", data, position)[0]}, position + 8
    if kind == 0x0B:
        pattern, position = read_cstring(data, position)
        options, position = read_cstring(data, position)
        return {"$regex": pattern, "options": options}, position
    if kind == 0x10:
        return struct.unpack_from("<i", data, position)[0], position + 4
    if kind == 0x11:
        increment, seconds = struct.unpack_from("<II", data, position)
        return {"$timestamp": [seconds, increment]}, position + 8
    if kind == 0x12:
        return struct.unpack_from("<q", data, position)[0], position + 8
    if kind == 0x13:
        return {"$decimal128": data[position:position + 16].hex()}, position + 16
    raise ValueError("Type BSON non gere : 0x%02x" % kind)


def receive_exactly(connection, size):
    chunks = b""
    while len(chunks) < size:
        chunk = connection.recv(size - len(chunks))
        if not chunk:
            raise ConnectionError("Connexion fermee par le serveur")
        chunks += chunk
    return chunks


def run_command(host, port, command, timeout):
    payload = struct.pack("<I", 0) + b"\x00" + encode_document(command)
    header = struct.pack("<iiii", 16 + len(payload), 1, 0, OP_MSG)
    with socket.create_connection((host, port), timeout=timeout) as connection:
        connection.sendall(header + payload)
        length, _, _, opcode = struct.unpack("<iiii", receive_exactly(connection, 16))
        body = receive_exactly(connection, length - 16)
    if opcode != OP_MSG or body[4] != 0:
        raise ValueError("Reponse inattendue (opcode %d)" % opcode)
    return decode_document(body, 5)[0]


def main():
    parser = argparse.ArgumentParser(description="Commande MongoDB minimale (OP_MSG)")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=27017)
    parser.add_argument("--db", default="admin")
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--allow-failure", action="store_true")
    parser.add_argument("command")
    arguments = parser.parse_args()
    command = json.loads(arguments.command)
    command.setdefault("$db", arguments.db)
    try:
        reply = run_command(arguments.host, arguments.port, command, arguments.timeout)
    except (OSError, ConnectionError, ValueError) as failure:
        print("Serveur MongoDB injoignable (%s:%d) : %s" % (arguments.host, arguments.port, failure), file=sys.stderr)
        return 2
    print(json.dumps(reply, sort_keys=True))
    return 0 if reply.get("ok") == 1 or arguments.allow_failure else 1


if __name__ == "__main__":
    sys.exit(main())
