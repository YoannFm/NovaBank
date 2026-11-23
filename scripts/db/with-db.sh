#!/usr/bin/env bash
# Demarre un serveur de base de test, attend qu'il reponde, execute une commande, puis l'arrete
# (meme si la commande echoue).
#
# Variables exportees pour la commande (lues par les tests JUnit tagues "sql-server" ou
# "mongodb-server" et par les scripts de serveurs Minecraft) :
#   NOVABANK_IT_DB_PRODUCT  mariadb | mysql | mongodb | mongodb-rs | redis
#   NOVABANK_IT_DB_HOST     127.0.0.1
#   NOVABANK_IT_DB_PORT     port du serveur
#   NOVABANK_IT_DB_USER / NOVABANK_IT_DB_PASSWORD  identifiants de test
#   NOVABANK_IT_DB_NAME     base par defaut (novabank)
#   NOVABANK_IT_DB_CTL      chemin de db-server.sh, lance par "bash" (tests de panne : kill puis start)
#   NOVABANK_IT_DB_CLIENT, NOVABANK_IT_DB_SOCKET, NOVABANK_IT_DB_LIBS  client SQL (root par le socket,
#                           avec LD_LIBRARY_PATH=NOVABANK_IT_DB_LIBS), pour les verifications en base
#                           (MongoDB : NOVABANK_IT_DB_CLIENT = mongo-command.py, lance par python3)
#   NOVABANK_IT_DB_URI      MongoDB uniquement : URI de connexion (replicaSet=rs0 pour mongodb-rs)
#
# Exemples (depuis la racine du projet) :
#   scripts/db/with-db.sh mariadb mvn -B -o test -pl novabank-storage-sql -Dgroups=sql-server
#   NOVABANK_STORAGE=mysql scripts/db/with-db.sh mysql scripts/servers/run-smoke.sh 1.21.11 21
#   scripts/db/with-db.sh mongodb-rs mvn -B -o test -pl novabank-storage-mongo -Dgroups=mongodb-server
set -uo pipefail
DB_SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$DB_SCRIPTS/common.sh"
USAGE="Usage : with-db.sh <mariadb|mysql|mongodb|mongodb-rs|redis> <commande...>"
db_vars "${1:?$USAGE}" || exit 2
shift
[ $# -gt 0 ] || { echo "$USAGE" >&2; exit 2; }

bash "$DB_SCRIPTS/db-server.sh" "$PRODUCT" start || exit 1
export NOVABANK_IT_DB_PRODUCT="$PRODUCT" NOVABANK_IT_DB_HOST="127.0.0.1" NOVABANK_IT_DB_PORT="$DB_PORT" \
  NOVABANK_IT_DB_USER="$DB_USER" NOVABANK_IT_DB_PASSWORD="$DB_PASSWORD" NOVABANK_IT_DB_NAME="$DB_NAME" \
  NOVABANK_IT_DB_CTL="$DB_SCRIPTS/db-server.sh" NOVABANK_IT_DB_CLIENT="$CLIENT_BIN" \
  NOVABANK_IT_DB_SOCKET="$SOCKET" NOVABANK_IT_DB_LIBS="$DB_LIBS"
if [ "$ENGINE" = "mongodb" ]; then
  if [ -n "$REPLICA_SET" ]; then
    export NOVABANK_IT_DB_URI="mongodb://127.0.0.1:$DB_PORT/?replicaSet=$REPLICA_SET"
  else
    export NOVABANK_IT_DB_URI="mongodb://127.0.0.1:$DB_PORT/?directConnection=true"
  fi
fi
"$@"
status=$?
bash "$DB_SCRIPTS/db-server.sh" "$PRODUCT" stop
exit $status
