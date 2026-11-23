#!/usr/bin/env bash
# Validation reelle de la synchronisation multi-serveur (E7e) : DEUX serveurs Paper sur la MEME
# base MariaDB et le MEME Redis. Un joueur au meme nom sur chacun partage la meme banque ; les
# depots et retraits sont concus pour etre concurrents. Phases :
#   1. Redis actif : depots/retraits simultanes sur A et B ; stock final exact ; evenements recus via Redis ;
#   2. Redis COUPE (kill -9) : meme charge ; stock final exact ; evenements rattrapes via la base ;
#   3. Redis relance : le bus se reconnecte, les evenements repassent par Redis.
# Aucune simulation : serveurs, base, Redis et clients Minecraft reels.
#
# Usage : source ~/novabank-tools/env.sh ; NOVABANK_STORAGE=mariadb scripts/db/with-db.sh mariadb \
#           scripts/servers/run-sync-check.sh [cible=1.21.11] [jdk=21]
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/common.sh"
export NOVABANK_STORAGE="${NOVABANK_STORAGE:-mariadb}"
TARGET="${1:-1.21.11}"
JDK="${2:-21}"
parse_target "$TARGET" || exit 2
JAVA="$(java_bin "$JDK")" || exit 1
[ "$STORAGE" = "mariadb" ] || [ "$STORAGE" = "mysql" ] || { echo "Stockage partage requis (mariadb ou mysql)" >&2; exit 1; }
storage_check || exit 1
PLUGIN_JAR="${NOVABANK_JAR:-$(ls "$PROJECT_DIR"/novabank-plugin/target/NovaBank-*.jar 2>/dev/null | grep -v -- '-obf\.jar$' | head -1)}"
[ -n "$PLUGIN_JAR" ] || { echo "JAR du plugin absent : scripts/build.sh" >&2; exit 1; }
BOT_DEPS="$TOOLS_DIR/bot"
[ -d "$BOT_DEPS/node_modules/mineflayer" ] || { echo "Bot absent : npm install dans $BOT_DEPS" >&2; exit 1; }

REDIS_PORT="${NOVABANK_REDIS_PORT:-63791}"
START_TIMEOUT="${SYNC_START_TIMEOUT:-420}"
DB_CTL="$PROJECT_DIR/scripts/db/db-server.sh"
RUN_DIR="$SERVERS_DIR/sync-check-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN_DIR"
RESULTS=()
pass() { RESULTS+=("RESULT $1 PASS $2"); echo "PASS $1 : $2"; }
fail() { RESULTS+=("RESULT $1 FAIL $2"); echo "FAIL $1 : $2"; }

# --- Serveur B : copie des fichiers du serveur prepare (monde et plugins non copies).
[ -f "$DIR/$SERVER_JAR" ] || "$HERE/prepare-server.sh" "$TARGET" "$JDK" || exit 1
declare -A SRV_DIR=( [a]="$DIR" [b]="$DIR-b" ) SRV_PORT=( [a]="$PORT" [b]="$((PORT + 100))" )
if [ ! -f "${SRV_DIR[b]}/$SERVER_JAR" ]; then
  mkdir -p "${SRV_DIR[b]}/plugins"
  for entry in "$SERVER_JAR" eula.txt cache libraries versions; do
    [ -e "$DIR/$entry" ] && cp -R "$DIR/$entry" "${SRV_DIR[b]}/"
  done
fi
sed "s/^server-port=.*/server-port=${SRV_PORT[b]}/" "$DIR/server.properties" > "${SRV_DIR[b]}/server.properties"
sed "s/^server-port=.*/server-port=${SRV_PORT[a]}/" "$DIR/server.properties" > "$DIR/server.properties.tmp" && mv -f "$DIR/server.properties.tmp" "$DIR/server.properties"

# --- Configurations : stockage partage + Redis, items de test, reconciliation rapide.
configure() { # a|b
  local name="$1" config="${SRV_DIR[$1]}/plugins/NovaBank/config.yml"
  mkdir -p "$(dirname "$config")"
  unzip -p "$PLUGIN_JAR" config.yml | python3 -I -c '
import re, sys
text = sys.stdin.read()
items = """items:
  - key: minecraft:stone
    price: "0.5"

"""
sys.stdout.write(re.sub(r"(?ms)^items:\n.*?(?=^# -{10,})", items, text, count=1))
' > "$config"
  storage_configure "$config" || exit 1
  python3 -I - "$config" "$REDIS_PORT" "$name" <<'PY'
import re, sys
path, port, name = sys.argv[1:4]
text = open(path, encoding="utf-8").read()
start, end = text.index("\nredis:\n"), text.index("\n# ----", text.index("\nredis:\n") + 1)
block = text[start:end]
for key, value in {"enabled": "true", "host": "127.0.0.1", "port": port, "server-name": "srv-" + name,
                   "poll-interval-seconds": "1", "overlap-seconds": "5"}.items():
    block, n = re.subn(r"(?m)^  %s: .*$" % key, "  %s: %s" % (key, value), block, count=1)
    if n != 1:
        sys.exit("redis.%s non applique" % key)
open(path, "w", encoding="utf-8").write(text[:start] + block + text[end:])
PY
  [ $? -eq 0 ] || exit 1
  cp -f "$PLUGIN_JAR" "${SRV_DIR[$name]}/plugins/NovaBank.jar"
}

# --- Cycle de vie des serveurs (console par FIFO, comme run-smoke.sh).
declare -A PID LOG
start_server() { # a|b
  local name="$1" dir="${SRV_DIR[$1]}"
  LOG[$name]="$RUN_DIR/server-$name.log"
  [ -p "$dir/console.fifo" ] || mkfifo "$dir/console.fifo"
  ( cd "$dir" && exec "$JAVA" -Xms256M -Xmx768M -jar "$SERVER_JAR" --nogui < "$dir/console.fifo" > "${LOG[$name]}" 2>&1 ) &
  PID[$name]=$!
  if [ "$name" = a ]; then exec 3> "$dir/console.fifo"; else exec 4> "$dir/console.fifo"; fi
}
console() { # a|b commande
  if [ "$1" = a ]; then printf '%s\n' "$2" >&3; else printf '%s\n' "$2" >&4; fi
}
wait_log() { # a|b motif timeout
  local deadline=$((SECONDS + $3))
  until grep -qE "$2" "${LOG[$1]}"; do
    kill -0 "${PID[$1]}" 2>/dev/null || return 1
    [ $SECONDS -lt $deadline ] || return 1
    sleep 1
  done
}
diag() { # a|b : une ligne "Synchronisation : ..." fraiche dans le journal du serveur
  local before; before="$(grep -c 'Synchronisation :' "${LOG[$1]}")"
  console "$1" "novabank diagnostic"
  local deadline=$((SECONDS + 30))
  until [ "$(grep -c 'Synchronisation :' "${LOG[$1]}")" -gt "$before" ]; do
    [ $SECONDS -lt $deadline ] || { echo "(diagnostic absent)"; return 1; }
    sleep 1
  done
  grep 'Synchronisation :' "${LOG[$1]}" | tail -1 | sed 's/^.*Synchronisation : //'
}
field() { # ligne motif : premier nombre qui suit le motif
  printf '%s' "$1" | grep -oE "$2 [0-9]+" | head -1 | grep -oE '[0-9]+$'
}
stock() { storage_sql -e "SELECT COALESCE(SUM(quantity),0) FROM $MC_DATABASE.novabank_stock_lines WHERE item_key='minecraft:stone'"; }

run_bots() { # depots_a retraits_a depots_b retraits_b
  local status=0 pids=()
  for name in a b; do
    local deposits withdraws fifo="${SRV_DIR[$name]}/console.fifo"
    if [ $name = a ]; then deposits=$1; withdraws=$2; else deposits=$3; withdraws=$4; fi
    ( SYNC_DEPOSITS=$deposits SYNC_WITHDRAWS=$withdraws BOT_PORT="${SRV_PORT[$name]}" BOT_MC_VERSION="$VERSION" \
        BOT_NAME=SyncBot BOT_FIFO="$fifo" BOT_LOG="${LOG[$name]}" BOT_STATE="$RUN_DIR/state-$name.json" \
        NODE_PATH="$BOT_DEPS/node_modules" timeout 400 node "$PROJECT_DIR/scripts/bot/sync.js" \
        > "$RUN_DIR/bot-$name-last.log" 2>&1; echo $? > "$RUN_DIR/bot-$name-last.status" ) &
    pids+=($!)
  done
  wait "${pids[@]}"
  for name in a b; do
    [ "$(cat "$RUN_DIR/bot-$name-last.status")" = 0 ] || { status=1; grep RESULT "$RUN_DIR/bot-$name-last.log" >&2; }
  done
  return $status
}

cleanup() {
  local name pid
  for name in a b; do
    pid="${PID[$name]:-}"
    { [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; } || continue
    # "stop" n'est accepte qu'apres "Done" ; avant, arret par SIGTERM (hook d'arret du serveur).
    if grep -qE 'Done \([0-9.,]+s\)!' "${LOG[$name]}" 2>/dev/null; then console "$name" stop; else kill -TERM "$pid"; fi
  done
  for name in a b; do
    pid="${PID[$name]:-}"
    [ -n "$pid" ] || continue
    for _ in $(seq 1 90); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill -0 "$pid" 2>/dev/null && kill -TERM "$pid"
  done
  bash "$DB_CTL" redis stop > /dev/null 2>&1
}
trap cleanup EXIT

# ======================= Deroulement
storage_reset || exit 1
bash "$DB_CTL" redis start || exit 1
for name in a b; do
  for dir in "${SRV_DIR[$name]}/plugins/NovaBank/data" "${SRV_DIR[$name]}/world/playerdata"; do
    [ -e "$dir" ] && discard "$dir"
  done
  configure "$name"
done
start_server a
start_server b
wait_log a 'Done \([0-9.,]+s\)!' $START_TIMEOUT && wait_log b 'Done \([0-9.,]+s\)!' $START_TIMEOUT \
  && pass demarrage-a-b "deux serveurs demarres (ports ${SRV_PORT[a]} et ${SRV_PORT[b]})" || { fail demarrage-a-b "pas de Done"; tail -n 20 "${LOG[a]}" "${LOG[b]}"; exit 1; }
sleep 3
da="$(diag a)"; db_="$(diag b)"
echo "A : $da"; echo "B : $db_"
case "$da$db_" in *"Redis OK"*"Redis OK"*) pass diagnostic-bus "les deux serveurs voient Redis" ;; *) fail diagnostic-bus "bus non connecte" ;; esac

# --- Phase 1 : Redis actif, charge concurrente (A : 40 depots ; B : 40 depots puis 10 retraits => 70).
run_bots 40 0 40 10 || fail phase1-bots "un bot a echoue"
sleep 3
total="$(stock)"
[ "$total" = 70 ] && pass phase1-stock "stock final 70 apres 80 depots et 10 retraits concurrents" || fail phase1-stock "stock = $total (attendu 70)"
SYNC_TOP_EXPECT=35 run_bots 0 0 0 0 && pass phase1-classement "/bank top identique sur A et B (35 = 70 x 0.5)" || fail phase1-classement "classement incoherent entre A et B"
da="$(diag a)"; db_="$(diag b)"
echo "A : $da"; echo "B : $db_"
[ "$(field "$da" 'recus via Redis')" -gt 0 ] && [ "$(field "$db_" 'recus via Redis')" -gt 0 ] \
  && pass phase1-redis "chaque serveur a recu des evenements de l'autre via Redis" || fail phase1-redis "aucun evenement via Redis"
before_a="$(field "$da" 'via la base')"; before_b="$(field "$db_" 'via la base')"

# --- Phase 2 : Redis tue (kill -9) pendant la charge (A : 20 depots ; B : 20 depots => 110).
bash "$DB_CTL" redis kill
sleep 2
run_bots 20 0 20 0 || fail phase2-bots "un bot a echoue"
sleep 3
total="$(stock)"
[ "$total" = 110 ] && pass phase2-stock "stock final 110 avec Redis coupe (la base fait foi)" || fail phase2-stock "stock = $total (attendu 110)"
SYNC_TOP_EXPECT=55 run_bots 0 0 0 0 && pass phase2-classement "/bank top identique sur A et B avec Redis coupe (55)" || fail phase2-classement "classement incoherent avec Redis coupe"
da="$(diag a)"; db_="$(diag b)"
echo "A : $da"; echo "B : $db_"
case "$da$db_" in *COUPE*COUPE*) pass phase2-diagnostic "bus signale COUPE sur les deux serveurs" ;; *) fail phase2-diagnostic "bus non signale coupe" ;; esac
after_a="$(field "$da" 'via la base')"; after_b="$(field "$db_" 'via la base')"
[ "$after_a" -gt "$before_a" ] && [ "$after_b" -gt "$before_b" ] \
  && pass phase2-rattrapage "evenements rattrapes par la lecture de la base (A $before_a -> $after_a, B $before_b -> $after_b)" \
  || fail phase2-rattrapage "pas de rattrapage (A $before_a -> $after_a, B $before_b -> $after_b)"

# --- Deploiement de la configuration, Redis coupe : A applique et sauvegarde localement, B ne recoit rien.
cfg() { echo "${SRV_DIR[$1]}/plugins/NovaBank/config.yml"; }
console a "novabank config set limits.cooldown-ms 888"
sleep 4
grep -q 'cooldown-ms: 888' "$(cfg a)" && ls "${SRV_DIR[a]}"/plugins/NovaBank/backup/*-edit.yml > /dev/null 2>&1 \
  && pass phase2-config-locale "A applique la config et sauvegarde son fichier (Redis coupe)" \
  || fail phase2-config-locale "A n'a pas ecrit ou sauvegarde sa config"
grep -q 'cooldown-ms: 888' "$(cfg b)" && fail phase2-config-isolee "B a recu la config alors que Redis est coupe" \
  || pass phase2-config-isolee "B inchange : la diffusion est impossible Redis coupe (pas de rattrapage, limite connue)"

# --- Phase 3 : Redis relance, le bus se reconnecte tout seul (A : 5 depots => 115).
bash "$DB_CTL" redis start
sleep 15
da="$(diag a)"; db_="$(diag b)"
case "$da$db_" in *COUPE*) fail phase3-reconnexion "bus encore coupe: A=$da B=$db_" ;; *) pass phase3-reconnexion "bus reconnecte sans redemarrage des serveurs" ;; esac
bus_before_b="$(field "$db_" 'recus via Redis')"
run_bots 5 0 0 0 || fail phase3-bots "un bot a echoue"
sleep 3
db_="$(diag b)"
total="$(stock)"
[ "$total" = 115 ] && pass phase3-stock "stock final 115" || fail phase3-stock "stock = $total (attendu 115)"
[ "$(field "$db_" 'recus via Redis')" -gt "$bus_before_b" ] \
  && pass phase3-redis "B recoit de nouveau les evenements de A via Redis" || fail phase3-redis "B ne recoit rien via Redis"

# --- Deploiement de la configuration, Redis actif : A change, B sauvegarde puis applique.
b_backups_before="$(ls "${SRV_DIR[b]}"/plugins/NovaBank/backup 2>/dev/null | wc -l)"
console a "novabank config set limits.cooldown-ms 777"
deadline=$((SECONDS + 20)); until grep -q 'cooldown-ms: 777' "$(cfg b)"; do [ $SECONDS -lt $deadline ] || break; sleep 1; done
grep -q 'cooldown-ms: 777' "$(cfg b)" && pass phase3-config-deployee "B a recu limits.cooldown-ms = 777 par Redis" \
  || fail phase3-config-deployee "B n'a pas recu la configuration"
ls "${SRV_DIR[b]}"/plugins/NovaBank/backup/*-remote.yml > /dev/null 2>&1 \
  && [ "$(ls "${SRV_DIR[b]}"/plugins/NovaBank/backup | wc -l)" -gt "$b_backups_before" ] \
  && pass phase3-config-sauvegarde-b "B a sauvegarde son fichier avant d'appliquer" \
  || fail phase3-config-sauvegarde-b "pas de sauvegarde -remote chez B"
grep -q "Configuration recue d'un autre serveur" "${LOG[b]}" && pass phase3-config-journal-b "B journalise la reception" \
  || fail phase3-config-journal-b "pas de trace de reception chez B"
grep -q 'server-name: srv-b' "$(cfg b)" && grep -q 'server-name: srv-a' "$(cfg a)" \
  && pass phase3-config-sections-locales "redis.server-name et le reste de redis/storage/server restent propres a chaque serveur" \
  || fail phase3-config-sections-locales "section locale modifiee par la diffusion"

# --- Rattrapage : B a une valeur differente (serveur reste a l'ecart), "config push" sur A la lui renvoie.
python3 - "$(cfg b)" <<'PY'
import sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read().replace("cooldown-ms: 777", "cooldown-ms: 555")
open(path, "w", encoding="utf-8").write(text)
PY
console a "novabank config push"
deadline=$((SECONDS + 20)); until grep -q 'cooldown-ms: 777' "$(cfg b)"; do [ $SECONDS -lt $deadline ] || break; sleep 1; done
grep -q 'cooldown-ms: 777' "$(cfg b)" && pass phase3-config-push "config push rattrape B (555 -> 777)" || fail phase3-config-push "B n'a pas ete rattrape"

# --- Coherence journal / stock et absence d'erreurs.
journal="$(storage_sql -e "SELECT COALESCE(SUM(counted_quantity),0) FROM $MC_DATABASE.novabank_operations WHERE operation_type='DEPOSIT'")"
withdrawn="$(storage_sql -e "SELECT COALESCE(SUM(quantity),0) FROM $MC_DATABASE.novabank_operations WHERE operation_type='WITHDRAW'")"
[ "$((journal - withdrawn))" = "$(stock)" ] && pass journal-stock "journal (depots $journal - retraits $withdrawn) = stock $(stock)" \
  || fail journal-stock "journal $journal - $withdrawn != stock $(stock)"
errors="$(grep -hE 'Exception|SEVERE|\[ERROR\]' "${LOG[a]}" "${LOG[b]}" | grep -v 'Synchronisation Redis' | grep -v 'Synchronisation : ' | head -5)"
[ -z "$errors" ] && pass aucune-erreur "aucune exception ni SEVERE hors messages Redis attendus" || fail aucune-erreur "$errors"

# --- Phase 4 (SYNC_ORPHAN=1) : retrait orphelin. A est fige (SIGSTOP) entre la reservation d'un retrait et sa
# confirmation, comme un serveur plante. B ne doit PAS solder ce retrait recent (il pourrait etre en cours), meme en
# redemarrant ; une fois le delai de grace depasse (2 min), B le solde. A est ensuite tue (kill -9), jamais relance.
if [ "${SYNC_ORPHAN:-0}" = 1 ]; then
  reserved() { storage_sql -e "SELECT COUNT(*) FROM $MC_DATABASE.novabank_operations WHERE operation_type='WITHDRAW' AND state='RESERVED'"; }
  ( SYNC_DEPOSITS=0 SYNC_WITHDRAWS=60 BOT_PORT="${SRV_PORT[a]}" BOT_MC_VERSION="$VERSION" BOT_NAME=SyncBot \
      BOT_FIFO="${SRV_DIR[a]}/console.fifo" BOT_LOG="${LOG[a]}" BOT_STATE="$RUN_DIR/state-a.json" \
      NODE_PATH="$BOT_DEPS/node_modules" timeout 300 node "$PROJECT_DIR/scripts/bot/sync.js" > "$RUN_DIR/bot-a-orphan.log" 2>&1 ) &
  orphan_bot=$!
  sleep 12
  caught=0
  for attempt in $(seq 1 80); do
    sleep "0.$((RANDOM % 9 + 1))"
    kill -STOP "${PID[a]}"
    if [ "$(reserved)" -gt 0 ]; then caught=1; break; fi
    kill -CONT "${PID[a]}"
  done
  if [ $caught = 1 ]; then
    t0=$SECONDS
    op="$(storage_sql -e "SELECT operation_id FROM $MC_DATABASE.novabank_operations WHERE state='RESERVED' LIMIT 1")"
    pass phase4-orphelin "A fige avec un retrait RESERVED ($op), essai $attempt"
    # Redemarrage de B pendant que le retrait est recent : l'ancien code le soldait des le demarrage.
    console b stop
    for _ in $(seq 1 90); do kill -0 "${PID[b]}" 2>/dev/null || break; sleep 1; done
    start_server b
    if wait_log b 'Done \([0-9.,]+s\)!' $START_TIMEOUT; then
      sleep 5
      age=$((SECONDS - t0))
      if [ $age -ge 110 ]; then fail phase4-recent "B a mis trop longtemps a redemarrer ($age s), test non concluant"
      elif [ "$(reserved)" -gt 0 ]; then pass phase4-recent "B redemarre (retrait age de $age s) : retrait recent NON solde"
      else fail phase4-recent "B a solde un retrait de $age s (il pouvait etre en cours sur A)"; fi
    else fail phase4-recent "B n'a pas redemarre"; fi
    deadline=$((t0 + 240))
    until [ "$(reserved)" = 0 ] || [ $SECONDS -ge $deadline ]; do sleep 5; done
    age=$((SECONDS - t0))
    if [ "$(reserved)" = 0 ] && grep -q 'Reconciliation : 1 retrait' "${LOG[b]}"; then
      pass phase4-solde "B a solde l'orphelin apres ~$age s (grace 120 s, verification chaque minute)"
    else fail phase4-solde "orphelin non solde apres $age s"; fi
    state="$(storage_sql -e "SELECT state, delivered_quantity FROM $MC_DATABASE.novabank_operations WHERE operation_id='$op'")"
    delivery="$(storage_sql -e "SELECT quantity FROM $MC_DATABASE.novabank_pending_deliveries WHERE operation_id='$op'")"
    [ "$delivery" = 1 ] && pass phase4-livraison "retrait solde ($state), 1 unite en livraison differee pour le joueur" \
      || fail phase4-livraison "livraison differee absente (etat : $state, livraison : '$delivery')"
  else
    kill -CONT "${PID[a]}"
    fail phase4-orphelin "aucun retrait RESERVED capture en 80 essais"
  fi
  kill -KILL "${PID[a]}" 2>/dev/null
  kill "$orphan_bot" 2>/dev/null
  wait "$orphan_bot" 2>/dev/null
fi

echo "--- Resume"; printf '%s\n' "${RESULTS[@]}" | tee "$RUN_DIR/summary.txt"
echo "Journaux : $RUN_DIR"
printf '%s\n' "${RESULTS[@]}" | grep -q ' FAIL ' && exit 1
exit 0
