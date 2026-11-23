#!/usr/bin/env python3
"""Verifications en base apres l'arret du serveur.

Base SQLite : lecture immutable, aucun fichier cree. Base MySQL / MariaDB ("sql:<nom>") : requetes
par le client du serveur de test (variables NOVABANK_IT_DB_CLIENT, _SOCKET, _LIBS de
scripts/db/with-db.sh), en root par le socket local. Base MongoDB ("mongo:<nom>") : agregations par
scripts/db/mongo-command.py (NOVABANK_IT_DB_CLIENT, _PORT). Stockage YAML / JSON ("file:<instantane>") :
lecture de l'instantane novabank.json / novabank.yml et du journal d'ecriture voisin (vide apres un
arret propre).

Usage : bot-db-check.py <fichier.db | sql:<base> | mongo:<base> | file:<instantane>> <etat.json> <phase> <joueur>
Ecrit une ligne RESULT par verification :
  - db-<phase>-reserves : aucun retrait reste RESERVED ;
  - db-<phase>-en-attente (MongoDB) : aucune operation restee en attente dans un document banque ;
    (YAML / JSON) : journal d'ecriture vide apres l'arret (compaction finale faite) ;
  - db-h-total (phase restart) : inventaire + banque + livraisons en attente = total avant l'arret ;
  - db-<phase>-attente : livraisons en attente restantes (information).
"""
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import uuid


STONE = "minecraft:stone"


class SqlQueries:
    """Requetes communes a SQLite et MySQL / MariaDB (meme schema)."""

    def reserved(self):
        return self.scalar("SELECT COUNT(*) FROM novabank_operations WHERE state = 'RESERVED'")

    def pending(self, player_id):
        return self.scalar(
            "SELECT COALESCE(SUM(quantity - delivered_quantity), 0) FROM novabank_pending_deliveries"
            " WHERE player_id = ? AND item_key = ?", (player_id, STONE))

    def bank(self, player_id):
        return self.scalar(
            "SELECT COALESCE(SUM(s.quantity), 0) FROM novabank_stock_lines s JOIN novabank_banks b"
            " ON s.bank_id = b.bank_id WHERE b.owner_type = 'player' AND b.owner_id = ?"
            " AND s.item_key = ?", (player_id, STONE))

    def unresolved(self):
        return None


class SqliteDatabase(SqlQueries):
    def __init__(self, path):
        self.connection = sqlite3.connect("file:" + path + "?immutable=1", uri=True)

    def scalar(self, sql, params=()):
        return self.connection.execute(sql, params).fetchone()[0]

    def close(self):
        self.connection.close()


class ServerDatabase(SqlQueries):
    """Client mysql / mariadb en mode batch ; parametres = UUID ou constantes (echappes)."""

    def __init__(self, name):
        self.command = [os.environ["NOVABANK_IT_DB_CLIENT"], "--no-defaults",
                        "--socket=" + os.environ["NOVABANK_IT_DB_SOCKET"], "-uroot", "-N", "-B", "-D", name]
        self.env = dict(os.environ, LD_LIBRARY_PATH=os.environ["NOVABANK_IT_DB_LIBS"])

    def scalar(self, sql, params=()):
        for value in params:
            sql = sql.replace("?", "'" + str(value).replace("'", "''") + "'", 1)
        output = subprocess.run(self.command + ["-e", sql], env=self.env, check=True, capture_output=True,
                                text=True).stdout.strip()
        return int(output)

    def close(self):
        pass


class MongoDatabase:
    """Agregations par mongo-command.py (collections du module novabank-storage-mongo)."""

    def __init__(self, name):
        self.command = ["python3", "-I", os.environ["NOVABANK_IT_DB_CLIENT"], "--port",
                        os.environ["NOVABANK_IT_DB_PORT"], "--db", name]

    def total(self, collection, pipeline):
        command = {"aggregate": collection, "pipeline": pipeline + [{"$group": {"_id": None, "n": {"$sum": "$n"}}}],
                   "cursor": {}}
        output = subprocess.run(self.command + [json.dumps(command)], check=True, capture_output=True,
                                text=True).stdout
        batch = json.loads(output)["cursor"]["firstBatch"]
        return int(batch[0]["n"]) if batch else 0

    def reserved(self):
        return self.total("operations", [{"$match": {"state": "RESERVED"}}, {"$project": {"n": {"$literal": 1}}}])

    def pending(self, player_id):
        return self.total("pending_deliveries", [{"$match": {"playerId": player_id, "item": STONE}},
                                                 {"$project": {"n": "$remaining"}}])

    def bank(self, player_id):
        return self.total("banks", [{"$match": {"ownerType": "player", "ownerId": player_id}},
                                    {"$unwind": "$lines"}, {"$match": {"lines.item": STONE}},
                                    {"$project": {"n": "$lines.quantity"}}])

    def unresolved(self):
        return self.total("banks", [{"$match": {"pendingOp": {"$exists": True}}}, {"$project": {"n": {"$literal": 1}}}])

    def close(self):
        pass


class FileDatabase:
    """Instantane du stockage fichier (champs : voir TreeMapping de novabank-storage-file)."""

    def __init__(self, path):
        with open(path, encoding="utf-8") as handle:
            if path.endswith(".json"):
                self.tree = json.load(handle)
            else:
                import yaml
                self.tree = yaml.safe_load(handle)
        self.journal = path + ".journal"

    def reserved(self):
        return sum(1 for op in self.tree.get("operations", []) if op["state"] == "RESERVED")

    def pending(self, player_id):
        return sum(int(d["quantity"]) - int(d["delivered"]) for d in self.tree.get("deliveries", [])
                   if d["player"] == player_id and d["item"] == STONE)

    def bank(self, player_id):
        return sum(int(line["quantity"]) for bank in self.tree.get("banks", [])
                   if bank["owner-type"] == "player" and bank["owner-id"] == player_id
                   for line in bank.get("lines", []) if line["item"] == STONE)

    def unresolved(self):
        with open(self.journal, encoding="utf-8") as handle:
            return sum(1 for _ in handle)

    def close(self):
        pass


def offline_uuid(name):
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def main():
    db, state_file, phase, player = sys.argv[1:5]
    player_id = offline_uuid(player)
    if db.startswith("sql:"):
        connection = ServerDatabase(db[4:])
    elif db.startswith("mongo:"):
        connection = MongoDatabase(db[6:])
    elif db.startswith("file:"):
        connection = FileDatabase(db[5:])
    else:
        connection = SqliteDatabase(db)
    reserved = connection.reserved()
    print("RESULT db-%s-reserves %s %d retrait(s) RESERVED" % (phase, "PASS" if reserved == 0 else "FAIL", reserved))
    unresolved = connection.unresolved()
    if unresolved is not None:
        print("RESULT db-%s-en-attente %s %d operation(s) en attente dans les banques"
              % (phase, "PASS" if unresolved == 0 else "FAIL", unresolved))
    pending = connection.pending(player_id)
    bank = connection.bank(player_id)
    print("RESULT db-%s-attente PASS banque stone %d, livraisons stone en attente %d" % (phase, bank, pending))
    if phase == "restart":
        with open(state_file, encoding="utf-8") as handle:
            state = json.load(handle)
        before = state.get("hBefore")
        after = state.get("hAfter")
        if not before or not after:
            print("RESULT db-h-total FAIL etat du bot incomplet : %s" % json.dumps(state))
        else:
            expected = before["inventory"] + before["bank"]
            actual = after["inventory"] + bank + pending
            verdict = "PASS" if actual == expected and after["bank"] == bank else "FAIL"
            print("RESULT db-h-total %s avant %d (bot %d + banque %d) ; apres %d (bot %d + banque %d + attente %d)"
                  % (verdict, expected, before["inventory"], before["bank"], actual, after["inventory"], bank,
                     pending))
    connection.close()


if __name__ == "__main__":
    main()
