#!/usr/bin/env bash
# Smoke test reel de NovaBank sur un serveur Paper ou Folia (cible : voir common.sh) :
#   1. copie le JAR du plugin (novabank-plugin/target/NovaBank-<version>.jar) dans plugins/ ;
#   2. demarre le serveur en arriere-plan avec le JDK demande et -Xmx1G ;
#   3. attend "Done", envoie en console : novabank diagnostic, novabank bank <joueur inconnu>,
#      novabank reload, stop ;
#   4. attend la fin du processus et ecrit un resume (lignes NovaBank, exceptions, "Done").
#
# Console : l'entree standard du serveur est un FIFO (console.fifo, cree une fois dans le
# dossier du serveur et reutilise). Le script ouvre le FIFO en ecriture sur le descripteur 3
# pendant toute la vie du serveur : chaque commande est une ligne ecrite sur ce descripteur,
# le serveur ne voit jamais de fin de fichier avant "stop", et rien n'est rejoue d'un run a
# l'autre (contrairement a "tail -f" d'un fichier de commandes). Aucune dependance a screen/tmux.
#
# Config : si plugins/NovaBank/config.yml est absent, le plugin cree lui-meme le fichier par
# defaut au demarrage (aucune preparation par le script).
#
# Stockage : NOVABANK_STORAGE=yaml|json (sans serveur) ou mariadb|mysql|mongodb|mongodb-rs (voir common.sh) ; pour ces derniers, le script doit alors
# etre lance par scripts/db/with-db.sh. La config.yml du serveur est sauvegardee, remplacee par celle
# du JAR configuree pour le serveur de base (base novabank_mc recreee vide), puis restauree a la fin.
# SMOKE_FRESH_DRIVERS=1 : pilotes MariaDB / MongoDB deja telecharges et modules extraits deplaces dans
# _to_delete (premier telechargement, premiere extraction).
#
# SMOKE_BEFORE_RELOAD : commande shell executee juste avant "novabank reload" (par exemple
# modifier config.yml pour verifier un rechargement refuse ou un changement de stockage).
# SMOKE_MIGRATE_TO=<type> : avant le rechargement, migration reelle du stockage actif vers ce type
# par la console (novabank migrate <type> puis confirm) ; echec du smoke si la migration echoue.
# Le type de la config n'est pas change (a faire avant le prochain demarrage).
#
# Usage : scripts/servers/run-smoke.sh <cible> <jdk>     ex : run-smoke.sh 1.21.11 21, run-smoke.sh folia/26.1.2 25
# Delais par defaut (variables SMOKE_*_TIMEOUT) : un run complet tient en moins de 3 minutes une
# fois le serveur prepare avec son JDK (prepare-server.sh <cible> <jdk> : correctif Paperclip fait).
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TARGET="${1:?Usage : run-smoke.sh <cible> <jdk>}"
JDK="${2:?Usage : run-smoke.sh <cible> <jdk>}"
parse_target "$TARGET" || exit 2
JAVA="$(java_bin "$JDK")" || exit 1
storage_check || exit 1
START_TIMEOUT="${SMOKE_START_TIMEOUT:-110}"
COMMAND_TIMEOUT="${SMOKE_COMMAND_TIMEOUT:-20}"
STOP_TIMEOUT="${SMOKE_STOP_TIMEOUT:-30}"

if [ ! -f "$DIR/$SERVER_JAR" ]; then
  "$(dirname "${BASH_SOURCE[0]}")/prepare-server.sh" "$TARGET" || exit 1
fi
PLUGIN_JAR="${NOVABANK_JAR:-$(ls "$PROJECT_DIR"/novabank-plugin/target/NovaBank-*.jar 2>/dev/null | grep -v -- '-obf\.jar$' | head -1)}"
if [ -z "$PLUGIN_JAR" ]; then
  echo "JAR du plugin absent : lancer mvn -B verify a la racine du projet" >&2
  exit 1
fi
cp -f "$PLUGIN_JAR" "$DIR/plugins/NovaBank.jar"

CONFIG="$DIR/plugins/NovaBank/config.yml"
CONFIG_BACKUP="$DIR/config-before-$STORAGE.yml"
if [ "$STORAGE" != "sqlite" ]; then
  [ -f "$CONFIG" ] && cp -f "$CONFIG" "$CONFIG_BACKUP"
  mkdir -p "$(dirname "$CONFIG")"
  unzip -p "$PLUGIN_JAR" config.yml > "$CONFIG"
  storage_configure "$CONFIG" || exit 1
  storage_reset || exit 1
  echo "config.yml de test : stockage $STORAGE"
fi
if [ "${SMOKE_FRESH_DRIVERS:-0}" = "1" ]; then
  for jar in "$DIR"/plugins/NovaBank/libs/mariadb-java-client-*.jar "$DIR"/plugins/NovaBank/libs/mongodb-driver-*.jar \
      "$DIR"/plugins/NovaBank/libs/bson-*.jar "$DIR"/plugins/NovaBank/libs/backends/*.jar; do
    [ -f "$jar" ] && discard "$jar"
  done
fi

RUN_ID="$(date +%Y%m%d-%H%M%S)"
LOG_DIR="$DIR/smoke-logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/run-$RUN_ID.log"
SUMMARY="$LOG_DIR/run-$RUN_ID.summary"
FIFO="$DIR/console.fifo"
[ -p "$FIFO" ] || mkfifo "$FIFO"

cd "$DIR" || exit 1
"$JAVA" -Xms512M -Xmx1G -jar "$SERVER_JAR" --nogui < "$FIFO" > "$LOG" 2>&1 &
PID=$!
exec 3> "$FIFO"
echo "Serveur $SERVER_NAME demarre (PID $PID, JDK $JDK), journal : $LOG"

alive() { kill -0 "$PID" 2>/dev/null; }

wait_for() { # motif delai : 0 trouve, 1 delai depasse, 2 serveur arrete
  local pattern="$1" deadline=$((SECONDS + $2))
  while [ $SECONDS -lt $deadline ]; do
    grep -qE -- "$pattern" "$LOG" && return 0
    alive || return 2
    sleep 1
  done
  return 1
}

send() {
  echo "[smoke] > $1"
  printf '%s\n' "$1" >&3
}

RESULT="OK"
if wait_for 'Done \([0-9.,]+s\)!' "$START_TIMEOUT"; then
  sleep 2
  send "novabank diagnostic"
  wait_for 'Threads IO' "$COMMAND_TIMEOUT" || RESULT="ECHEC (pas de reponse au diagnostic)"
  send "novabank bank JoueurInconnuSmoke"
  wait_for 'Joueur inconnu : JoueurInconnuSmoke|Unknown player: JoueurInconnuSmoke' "$COMMAND_TIMEOUT" \
    || RESULT="ECHEC (pas de reponse a novabank bank)"
  if [ -n "${SMOKE_MIGRATE_TO:-}" ]; then
    send "novabank migrate $SMOKE_MIGRATE_TO"
    wait_for 'migrate .* confirm' "$COMMAND_TIMEOUT" || RESULT="ECHEC (pas de demande de confirmation de migration)"
    send "novabank migrate $SMOKE_MIGRATE_TO confirm"
    if wait_for 'terminee et verifiee|Migration en echec' "${SMOKE_MIGRATE_TIMEOUT:-90}"; then
      grep -q 'Migration en echec' "$LOG" && RESULT="ECHEC (migration en echec)"
    else
      RESULT="ECHEC (migration sans resultat dans le delai)"
    fi
  fi
  if [ -n "${SMOKE_BEFORE_RELOAD:-}" ]; then
    echo "[smoke] avant rechargement : $SMOKE_BEFORE_RELOAD"
    bash -c "$SMOKE_BEFORE_RELOAD"
  fi
  send "novabank reload"
  wait_for 'Configuration rechargee|Rechargement refuse' "$COMMAND_TIMEOUT" \
    || RESULT="ECHEC (pas de reponse au rechargement)"
  send "stop"
else
  RESULT="ECHEC (pas de Done dans le delai ou serveur arrete)"
  alive && send "stop"
fi

deadline=$((SECONDS + STOP_TIMEOUT))
while alive && [ $SECONDS -lt $deadline ]; do sleep 1; done
if alive; then
  RESULT="ECHEC (arret trop long, processus termine par SIGTERM)"
  kill -TERM "$PID"
  sleep 10
fi
wait "$PID"
EXIT_CODE=$?
exec 3>&-

DB="$DIR/plugins/NovaBank/data/novabank.db"
{
  echo "=== Smoke NovaBank : $SERVER_NAME ($(cat "$BUILD_FILE" 2>/dev/null)), JDK $JDK, run $RUN_ID"
  echo "Resultat du script : $RESULT ; code de sortie Java : $EXIT_CODE"
  echo "JAR plugin : $(basename "$PLUGIN_JAR") sha256 $(sha256sum "$PLUGIN_JAR" | cut -c1-16)..."
  echo "--- Demarrage"
  grep -E 'Done \(' "$LOG" | tail -1
  echo "--- Lignes NovaBank"
  grep -nE 'NovaBank|novabank' "$LOG" | grep -vE '\[smoke\]'
  echo "--- Exceptions / erreurs (toutes sources)"
  grep -nE 'Exception|Error|SEVERE|ERROR' "$LOG" || echo "(aucune)"
  echo "--- Traces de pile NovaBank : $(grep -c 'at dev\.novadev\.novabank' "$LOG")"
  echo "--- Pilotes dans plugins/NovaBank/libs"
  ls -la "$DIR/plugins/NovaBank/libs" 2>/dev/null | grep -v '^total' || echo "(dossier absent)"
  if [ -d "$DIR/plugins/NovaBank/libs/backends" ]; then
    echo "--- Modules extraits (libs/backends)"
    (cd "$DIR/plugins/NovaBank/libs/backends" && sha256sum ./*.jar)
  fi
  echo "--- Fichiers de langue"
  ls "$DIR/plugins/NovaBank/lang" 2>/dev/null || echo "(dossier absent)"
  if [ "$(storage_engine)" = "file" ]; then
    echo "--- Stockage $STORAGE (plugins/NovaBank/data)"
    ls -la "$DIR/plugins/NovaBank/data" 2>/dev/null | grep -v '^total' || echo "(dossier absent)"
    for file in "$DIR"/plugins/NovaBank/data/novabank.json "$DIR"/plugins/NovaBank/data/novabank.yml; do
      [ -f "$file" ] && echo "$(basename "$file") : $(head -c 200 "$file" | tr '\n' ' ')"
    done
  elif [ "$(storage_engine)" = "mongodb" ]; then
    echo "--- Base $STORAGE ($MC_DATABASE)"
    storage_mongo '{"listCollections": 1, "nameOnly": true}' | python3 -I -c 'import json, sys; print("collections :", " ".join(sorted(c["name"] for c in json.load(sys.stdin)["cursor"]["firstBatch"])))'
    storage_mongo '{"find": "schema", "filter": {}}' | python3 -I -c 'import json, sys; print("schema :", [d.get("version") for d in json.load(sys.stdin)["cursor"]["firstBatch"]])'
    storage_mongo '{"listIndexes": "operations"}' | python3 -I -c 'import json, sys; print("index operations :", " ".join(sorted(i["name"] for i in json.load(sys.stdin)["cursor"]["firstBatch"])))'
  elif [ "$STORAGE" != "sqlite" ]; then
    echo "--- Base $STORAGE ($MC_DATABASE)"
    echo "tables : $(storage_sql -D "$MC_DATABASE" -e 'SHOW TABLES' | tr '\n' ' ')"
    echo "schema_version : $(storage_sql -D "$MC_DATABASE" -e 'SELECT version FROM novabank_schema_version ORDER BY version' | tr '\n' ' ')"
    echo "moteur / interclassement : $(storage_sql -e "SELECT DISTINCT CONCAT(ENGINE, ' ', TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$MC_DATABASE'" | tr '\n' ' ')"
  fi
  echo "--- Base SQLite"
  if [ "$STORAGE" != "sqlite" ]; then
    echo "(non utilisee : stockage $STORAGE)"
  elif [ -f "$DB" ]; then
    python3 -I - "$DB" <<'PY'
import sqlite3, sys
# immutable : lecture seule sans creer de fichiers -wal/-shm apres l'arret du serveur.
connection = sqlite3.connect("file:" + sys.argv[1] + "?immutable=1", uri=True)
tables = [row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
print("tables :", ", ".join(tables))
print("schema_version :", [row[0] for row in connection.execute("SELECT version FROM novabank_schema_version ORDER BY version")])
print("colonnes pending_deliveries :", [row[1] for row in connection.execute("PRAGMA table_info(novabank_pending_deliveries)")])
connection.close()
PY
  else
    echo "(base absente : $DB)"
  fi
} > "$SUMMARY"
if [ "$STORAGE" != "sqlite" ] && [ -f "$CONFIG_BACKUP" ]; then
  cp -f "$CONFIG_BACKUP" "$CONFIG"
  echo "config.yml d'origine restauree" >> "$SUMMARY"
fi
cat "$SUMMARY"
