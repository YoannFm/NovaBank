#!/usr/bin/env bash
# Pilote un serveur de base de test installe par install-<produit>.sh.
#
#   start   demarre le serveur en arriere-plan (127.0.0.1:<port>, socket et PID dans ~/novabank-db/run),
#           attend qu'il reponde, puis cree au premier demarrage l'utilisateur de test et la base
#           novabank (utf8mb4_bin) ; MongoDB : cree le datadir, initialise le replica set rs0
#           (mongodb-rs) au premier demarrage et attend qu'un primaire accepte les ecritures ;
#   stop    arret propre (SIGTERM) et attente de la fin du processus ;
#   kill    arret brutal (SIGKILL), pour les tests de panne : aucune ecriture n'est terminee
#           proprement, InnoDB (ou WiredTiger) fait sa recuperation au demarrage suivant ;
#   status  affiche "running <pid>" ou "stopped".
#
# macOS : binaires Homebrew (voir common.sh). Au premier demarrage le datadir SQL est initialise
# ici (mariadb-install-db / mysqld --initialize-insecure), root sans mot de passe par le socket.
#
# Usage : scripts/db/db-server.sh <mariadb|mysql|mongodb|mongodb-rs|redis> <start|stop|kill|status>
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
db_vars "${1:?Usage : db-server.sh <mariadb|mysql|mongodb|mongodb-rs|redis> <start|stop|kill|status>}" || exit 2
ACTION="${2:?Usage : db-server.sh <produit> <start|stop|kill|status>}"
START_TIMEOUT="${NOVABANK_DB_START_TIMEOUT:-90}"
STOP_TIMEOUT="${NOVABANK_DB_STOP_TIMEOUT:-60}"

server_pid() {
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null)" || return 1
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && echo "$pid"
}

start_server() {
  if server_pid > /dev/null; then
    echo "$PRODUCT deja demarre (PID $(server_pid))"
    return 0
  fi
  case "$ENGINE" in
    sql) start_sql ;;
    mongodb) start_mongodb ;;
    redis) start_redis ;;
  esac
}

wait_ready() { # pid commande... : attend que la commande reussisse tant que le serveur lance est vivant
  local launched="$1" deadline=$((SECONDS + START_TIMEOUT))
  shift
  until "$@" > /dev/null 2>&1; do
    if [ $SECONDS -ge $deadline ] || ! kill -0 "$launched" 2>/dev/null; then
      echo "$PRODUCT ne repond pas (arrete ou delai de ${START_TIMEOUT} s depasse) ; fin du journal :" >&2
      tail -20 "$SERVER_LOG" >&2
      return 1
    fi
    sleep 0.5
  done
}

start_mongodb() {
  [ -x "$SERVER_BIN" ] || { echo "$PRODUCT non installe : brew install (voir common.sh)" >&2; return 1; }
  mkdir -p "$DATA"
  local options=(--dbpath "$DATA" --port "$DB_PORT" --bind_ip 127.0.0.1 --unixSocketPrefix "$DB_RUN"
    --pidfilepath "$PID_FILE" --logpath "$SERVER_LOG" --logappend --wiredTigerCacheSizeGB 0.25
    --setParameter diagnosticDataCollectionEnabled=false)
  [ -z "$REPLICA_SET" ] || options+=(--replSet "$REPLICA_SET")
  nohup "$SERVER_BIN" "${options[@]}" > /dev/null 2>&1 &
  local launched=$!
  wait_ready "$launched" mongo_command '{"ping": 1}' || return 1
  if [ -n "$REPLICA_SET" ]; then
    # Premier demarrage : replica set mono-noeud. Ensuite la configuration est relue du datadir.
    if ! mongo_command '{"replSetGetStatus": 1}' > /dev/null 2>&1; then
      mongo_command "{\"replSetInitiate\": {\"_id\": \"$REPLICA_SET\", \"members\": [{\"_id\": 0, \"host\": \"127.0.0.1:$DB_PORT\"}]}}" \
        > /dev/null || { echo "Initialisation du replica set $REPLICA_SET impossible" >&2; return 1; }
    fi
    wait_ready "$launched" mongo_writable_primary || return 1
  fi
  echo "$PRODUCT $DB_VERSION demarre (PID $(server_pid), 127.0.0.1:$DB_PORT${REPLICA_SET:+, replica set $REPLICA_SET})"
}

mongo_writable_primary() {
  mongo_command '{"hello": 1}' | grep -q '"isWritablePrimary": true'
}

start_redis() {
  [ -x "$SERVER_BIN" ] || { echo "redis non installe : brew install redis" >&2; return 1; }
  mkdir -p "$DATA"
  # Sans persistance disque : Redis ne porte aucune donnee de reference (decision D1).
  "$SERVER_BIN" --port "$DB_PORT" --bind 127.0.0.1 --protected-mode yes --save "" --appendonly no \
    --dir "$DATA" --pidfile "$PID_FILE" --logfile "$SERVER_LOG" --daemonize yes > /dev/null 2>&1
  local deadline=$((SECONDS + START_TIMEOUT))
  until "$CLIENT_BIN" -p "$DB_PORT" ping 2>/dev/null | grep -q PONG; do
    [ $SECONDS -lt $deadline ] || { echo "redis ne repond pas ; fin du journal :" >&2; tail -20 "$SERVER_LOG" >&2; return 1; }
    sleep 0.3
  done
  echo "redis $DB_VERSION demarre (PID $(server_pid), 127.0.0.1:$DB_PORT)"
}

init_sql_datadir() {
  mkdir -p "$DATA"
  case "$PRODUCT" in
    mariadb) "$BASE/bin/mariadb-install-db" --no-defaults --basedir="$BASE" --datadir="$DATA" \
               --auth-root-authentication-method=normal --skip-test-db > "$DB_RUN/$PRODUCT-init.log" 2>&1 ;;
    mysql)   "$SERVER_BIN" --no-defaults --initialize-insecure --basedir="$BASE" --datadir="$DATA" \
               --log-error="$DB_RUN/$PRODUCT-init.log" > /dev/null 2>&1 ;;
  esac || { echo "Initialisation du datadir $PRODUCT impossible, voir $DB_RUN/$PRODUCT-init.log" >&2; return 1; }
}

start_sql() {
  [ -x "$SERVER_BIN" ] || { echo "$PRODUCT non installe : brew install" >&2; return 1; }
  [ -d "$DATA/mysql" ] || init_sql_datadir || return 1
  local options=(--no-defaults --basedir="$BASE" --datadir="$DATA" --port="$DB_PORT" --bind-address=127.0.0.1
    --socket="$SOCKET" --pid-file="$PID_FILE" --log-error="$SERVER_LOG" --skip-name-resolve
    --innodb-buffer-pool-size=128M --innodb-flush-log-at-trx-commit=1 --max-connections=200)
  case "$PRODUCT" in
    mariadb) options+=(--skip-log-bin) ;;
    mysql)   options+=(--disable-log-bin --mysqlx=OFF --performance-schema=OFF --secure-file-priv=NULL) ;;
  esac
  nohup "$SERVER_BIN" "${options[@]}" > /dev/null 2>&1 &
  wait_ready $! client -N -e 'SELECT 1' || return 1
  if [ ! -f "$DATA/novabank-ready" ]; then
    client <<SQL || return 1
CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`novabank%\`.* TO '$DB_USER'@'%';
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
SQL
    touch "$DATA/novabank-ready"
  fi
  echo "$PRODUCT $DB_VERSION demarre (PID $(server_pid), 127.0.0.1:$DB_PORT)"
}

wait_exit() { # pid
  local deadline=$((SECONDS + STOP_TIMEOUT))
  while kill -0 "$1" 2>/dev/null; do
    [ $SECONDS -lt $deadline ] || { echo "$PRODUCT ne s'arrete pas (PID $1)" >&2; return 1; }
    sleep 0.5
  done
}

case "$ACTION" in
  start) start_server ;;
  stop)
    pid="$(server_pid)" || { echo "$PRODUCT deja arrete"; exit 0; }
    kill -TERM "$pid" && wait_exit "$pid" && echo "$PRODUCT arrete" ;;
  kill)
    pid="$(server_pid)" || { echo "$PRODUCT deja arrete"; exit 0; }
    kill -KILL "$pid" && wait_exit "$pid" && echo "$PRODUCT tue (SIGKILL, PID $pid)" ;;
  status)
    if pid="$(server_pid)"; then echo "running $pid"; else echo "stopped"; fi ;;
  *) echo "Action inconnue : $ACTION" >&2; exit 2 ;;
esac
