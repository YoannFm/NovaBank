#!/usr/bin/env bash
# Tests REELS des flux joueur de NovaBank avec un bot mineflayer sur un serveur Paper ou Folia
# (cible : voir common.sh).
#
# Une suite = quatre phases, chacune un demarrage du serveur (un appel de commande de la VM
# est limite a ~180 s et tue ses processus d'arriere-plan : une phase tient dans un appel) :
#   main    : base et donnees joueur remises a zero (deplacees dans _to_delete), politique
#             PENDING_DELIVERY ; scenarios a b c d f g j e i, puis h (depot + stop immediat) ;
#   restart : redemarrage apres h, verification du total (bot + banque + livraisons en attente) ;
#   return  : politique RETURN_TO_BANK (config copiee), scenarios e et i, puis h2 (depot +
#             stop 100 ms apres) ; la config PENDING_DELIVERY est remise a la fin ;
#   restart : (second passage) verification du total apres h2.
# Chaque phase : copie du JAR, config de test copiee, demarrage, "Done", bot (scripts/bot,
# node), arret, verifications en base (lecture immutable) et dans le journal (0 trace
# NovaBank), lignes RESULT ajoutees au resume de la suite.
#
# Dependances du bot : installees HORS du projet dans ~/novabank-tools/bot (npm install
# --prefix, package.json copie de scripts/bot) et trouvees par NODE_PATH. Rien n'est
# installe dans le dossier du projet.
#
# Stockage : NOVABANK_STORAGE=yaml|json (sans serveur) ou mariadb|mysql|mongodb|mongodb-rs (voir common.sh), lance par scripts/db/with-db.sh ;
# le stockage est applique a la config de chaque phase (une suite peut changer de stockage apres une
# migration : main en sqlite, migration par run-smoke.sh SMOKE_MIGRATE_TO=json, restart en json) ;
# la base novabank_mc est recreee vide en phase main ; la config.yml du serveur en place avant la
# phase est restauree a la fin de la phase.
#
# Usage : scripts/servers/run-bot-scenarios.sh <cible> <jdk> <main|restart|return|all>
#   ex : run-bot-scenarios.sh 1.21.11 21 main   (puis restart, return, restart) ; folia/1.21.11 21 main
# "all" enchaine les quatre phases (hors VM). Resume : ~/novabank-servers/<projet>-<v>/bot-logs/suite-<id>.summary
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TARGET="${1:?Usage : run-bot-scenarios.sh <cible> <jdk> <phase>}"
JDK="${2:?Usage : run-bot-scenarios.sh <version> <jdk> <phase>}"
PHASE="${3:?Usage : run-bot-scenarios.sh <version> <jdk> <main|restart|return|all>}"
if [ "$PHASE" = "all" ]; then
  for phase in main restart return restart; do "$0" "$TARGET" "$JDK" "$phase" || exit 1; done
  exit 0
fi
case "$PHASE" in main|restart|return|custom|integration|roles|guiedit|itemsedit|hub|prix|params) ;; *) echo "Phase inconnue : $PHASE" >&2; exit 2 ;; esac

parse_target "$TARGET" || exit 2
JAVA="$(java_bin "$JDK")" || exit 1
storage_check || exit 1
BOT_NAME="NovaBot"
BOT_DEPS="$TOOLS_DIR/bot"
START_TIMEOUT="${BOT_START_TIMEOUT:-90}"
BOT_TIMEOUT="${BOT_TIMEOUT:-120}"
STOP_TIMEOUT="${BOT_STOP_TIMEOUT:-40}"

[ -f "$DIR/$SERVER_JAR" ] || "$(dirname "${BASH_SOURCE[0]}")/prepare-server.sh" "$TARGET" "$JDK" || exit 1
PLUGIN_JAR="${NOVABANK_JAR:-$(ls "$PROJECT_DIR"/novabank-plugin/target/NovaBank-*.jar 2>/dev/null | grep -v -- '-obf\.jar$' | head -1)}"
[ -n "$PLUGIN_JAR" ] || { echo "JAR du plugin absent : lancer scripts/build.sh" >&2; exit 1; }

# Dependances du bot (hors projet).
mkdir -p "$BOT_DEPS"
if ! cmp -s "$PROJECT_DIR/scripts/bot/package.json" "$BOT_DEPS/package.json" || [ ! -d "$BOT_DEPS/node_modules/mineflayer" ]; then
  cp -f "$PROJECT_DIR/scripts/bot/package.json" "$BOT_DEPS/package.json"
  (cd "$BOT_DEPS" && npm install --no-audit --no-fund >/dev/null 2>&1) || { echo "npm install en echec" >&2; exit 1; }
fi
MINEFLAYER_VERSION="$(node -p "require('$BOT_DEPS/node_modules/mineflayer/package.json').version")"

LOG_DIR="$DIR/bot-logs"
mkdir -p "$LOG_DIR"
if [ "$PHASE" = "main" ] || [ "$PHASE" = "custom" ] || [ "$PHASE" = "integration" ] || [ "$PHASE" = "roles" ] || [ "$PHASE" = "guiedit" ] || [ "$PHASE" = "itemsedit" ] || [ "$PHASE" = "hub" ] || [ "$PHASE" = "prix" ] || [ "$PHASE" = "params" ]; then
  SUITE="$(date +%Y%m%d-%H%M%S)"
  echo "$SUITE" > "$LOG_DIR/current-suite"
else
  SUITE="$(cat "$LOG_DIR/current-suite" 2>/dev/null)" || { echo "Aucune suite en cours : lancer la phase main" >&2; exit 1; }
fi
SUITE_DIR="$LOG_DIR/suite-$SUITE"
SUMMARY="$LOG_DIR/suite-$SUITE.summary"
mkdir -p "$SUITE_DIR"
RUN="$PHASE-$(date +%H%M%S)"
LOG="$SUITE_DIR/$RUN-server.log"
BOT_OUT="$SUITE_DIR/$RUN-bot.log"
STATE="$SUITE_DIR/state.json"
CONFIG="$DIR/plugins/NovaBank/config.yml"

# Configurations de test : celle du JAR, items de test, politique D13.
write_configs() {
  mkdir -p "$(dirname "$CONFIG")"
  unzip -p "$PLUGIN_JAR" config.yml | python3 -I -c '
import re, sys
text = sys.stdin.read()
items = """items:
  - key: minecraft:stone
    price: "0.5"
    category: pierre
  - key: minecraft:dirt
    price: "0.1"
    category: terre
  - key: minecraft:cobblestone
    price: "0.1"
    category: pierre

"""
import os
if os.environ.get("CUSTOM_KEY"):
    items = items.rstrip("\n") + "\n  - key: " + os.environ["CUSTOM_KEY"] + "\n    price: \"" + os.environ.get("CUSTOM_PRICE", "5") + "\"\n\n"
text = re.sub(r"(?ms)^items:\n.*?(?=^# -{10,})", items, text, count=1)
text = text.replace("per-category: false", "per-category: true")
if os.environ.get("INTEG_TYPE"):
    text = text.replace("  entity-types:\n    - player\n", "  entity-types:\n    - player\n    - " + os.environ["INTEG_TYPE"] + "\n", 1)
if os.environ.get("INTEG_ISLAND_LEVEL") == "1":
    text = text.replace("island-level: false", "island-level: true", 1).replace("apply-to: worth", "apply-to: level", 1)
    text = re.sub(r"(?m)^(    multiplier:) 1$", r"\1 20", text, count=1)
sys.stdout.write(text)
' > "$SUITE_DIR/config-pending.yml"
  storage_configure "$SUITE_DIR/config-pending.yml" || exit 1
  sed 's/^  undelivered-policy: PENDING_DELIVERY$/  undelivered-policy: RETURN_TO_BANK/' "$SUITE_DIR/config-pending.yml" \
    > "$SUITE_DIR/config-return.yml"
  grep -q 'undelivered-policy: RETURN_TO_BANK' "$SUITE_DIR/config-return.yml" || { echo "config RETURN_TO_BANK non generee" >&2; exit 1; }
}

CONFIG_BACKUP="$DIR/config-before-$STORAGE.yml"
[ "$STORAGE" != "sqlite" ] && [ -f "$CONFIG" ] && cp -f "$CONFIG" "$CONFIG_BACKUP"
case "$PHASE" in
  main|custom|integration|roles|guiedit|itemsedit|hub|prix|params)
    write_configs
    storage_reset || exit 1
    for state in "$DIR/plugins/NovaBank/data" "$DIR/world/playerdata"; do
      [ -e "$state" ] && discard "$state"
    done
    cp -f "$SUITE_DIR/config-pending.yml" "$CONFIG" ;;
  restart) cp -f "$SUITE_DIR/config-pending.yml" "$CONFIG"; storage_configure "$CONFIG" || exit 1 ;;
  return)  cp -f "$SUITE_DIR/config-return.yml" "$CONFIG"; storage_configure "$CONFIG" || exit 1 ;;
esac
cp -f "$PLUGIN_JAR" "$DIR/plugins/NovaBank.jar"

FIFO="$DIR/console.fifo"
[ -p "$FIFO" ] || mkfifo "$FIFO"
cd "$DIR" || exit 1
"$JAVA" -Xms512M -Xmx1G -jar "$SERVER_JAR" --nogui < "$FIFO" > "$LOG" 2>&1 &
PID=$!
exec 3> "$FIFO"
alive() { kill -0 "$PID" 2>/dev/null; }
send() { printf '%s\n' "$1" >&3; }

RESULTS=()
deadline=$((SECONDS + START_TIMEOUT))
until grep -qE 'Done \([0-9.,]+s\)!' "$LOG"; do
  if ! alive || [ $SECONDS -ge $deadline ]; then
    RESULTS+=("RESULT $PHASE-demarrage FAIL pas de Done")
    break
  fi
  sleep 1
done

if [ ${#RESULTS[@]} -eq 0 ]; then
  BOT_PORT="$PORT" BOT_MC_VERSION="$VERSION" BOT_NAME="$BOT_NAME" BOT_FIFO="$FIFO" BOT_LOG="$LOG" BOT_STATE="$STATE" \
    NODE_PATH="$BOT_DEPS/node_modules" timeout "$BOT_TIMEOUT" node "$PROJECT_DIR/scripts/bot/scenarios.js" "$PHASE" \
    > "$BOT_OUT" 2>&1
  BOT_EXIT=$?
  mapfile -t BOT_RESULTS < <(grep '^RESULT ' "$BOT_OUT")
  RESULTS+=("${BOT_RESULTS[@]}")
  [ $BOT_EXIT -eq 124 ] && RESULTS+=("RESULT $PHASE-bot FAIL delai du bot depasse")
fi

# Arret : deja demande par les scenarios h (phases main et return), sinon maintenant.
sleep 2
alive && ! grep -q 'Stopping the server' "$LOG" && send "stop"
deadline=$((SECONDS + STOP_TIMEOUT))
while alive && [ $SECONDS -lt $deadline ]; do sleep 1; done
if alive; then
  RESULTS+=("RESULT $PHASE-arret FAIL arret trop long (SIGTERM)")
  kill -TERM "$PID"; sleep 10
fi
wait "$PID"
exec 3>&-

DB="$DIR/plugins/NovaBank/data/novabank.db"
case "$(storage_engine)" in
  sql) DB="sql:$MC_DATABASE" ;;
  mongodb) DB="mongo:$MC_DATABASE" ;;
  file) DB="file:$DIR/plugins/NovaBank/data/novabank.$([ "$STORAGE" = "json" ] && echo json || echo yml)" ;;
esac
if [ "$PHASE" != "custom" ] && [ "$PHASE" != "integration" ] && [ "$PHASE" != "roles" ] && [ "$PHASE" != "guiedit" ] && [ "$PHASE" != "itemsedit" ] && [ "$PHASE" != "hub" ] && [ "$PHASE" != "prix" ] && [ "$PHASE" != "params" ]; then
  mapfile -t DB_RESULTS < <(python3 -I "$PROJECT_DIR/scripts/servers/bot-db-check.py" "$DB" "$STATE" "$PHASE" "$BOT_NAME" 2>&1)
  RESULTS+=("${DB_RESULTS[@]}")
fi
TRACES=$(grep -c 'at dev\.novadev\.novabank' "$LOG")
SEVERE=$(grep -E '(ERROR|SEVERE)\]: \[NovaBank\]' "$LOG" | grep -vc '^$')
RESULTS+=("RESULT $PHASE-journal $([ "$TRACES" -eq 0 ] && [ "$SEVERE" -eq 0 ] && echo PASS || echo FAIL) traces NovaBank $TRACES, erreurs NovaBank $SEVERE")
grep -q 'Arret : ' "$LOG" && RESULTS+=("RESULT $PHASE-drain INFO $(grep -o 'Arret : .*' "$LOG" | tr '\n' ' ')")
[ "$PHASE" = "return" ] && cp -f "$SUITE_DIR/config-pending.yml" "$CONFIG"
[ "$STORAGE" != "sqlite" ] && [ -f "$CONFIG_BACKUP" ] && cp -f "$CONFIG_BACKUP" "$CONFIG"

{
  echo "=== Phase $PHASE : $SERVER_NAME ($(cat "$BUILD_FILE")), JDK $JDK, stockage $STORAGE, mineflayer $MINEFLAYER_VERSION, $(date '+%F %T')"
  printf '%s\n' "${RESULTS[@]}"
} >> "$SUMMARY"
echo "=== Phase $PHASE (suite $SUITE) - journal : $LOG, bot : $BOT_OUT"
printf '%s\n' "${RESULTS[@]}"
if printf '%s\n' "${RESULTS[@]}" | grep -q ' FAIL'; then exit 1; fi
