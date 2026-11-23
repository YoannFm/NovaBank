#!/usr/bin/env bash
# Test d'integration reelle d'un plugin tiers (E10) : copie les jars de ../Plugins dans plugins/ du serveur de test, lance
# la phase bot `integration` (le bot cree une entite, choisit son type de banque, depose), puis range les jars dans
# ~/novabank-servers/parked (rien n'est supprime).
# Usage : scripts/servers/run-integration.sh <type> <regex-nom> <op 0|1> "<cmd1|cmd2>" <jar>...
# Variables : VER (1.21.11), JDK (21), INTEG_WAIT (ms entre commandes), INTEG_CLICK (cases de menu a cliquer, ex 11|motif),
#             INTEG_PHASE=roles (+ INTEG_INVITE, INTEG_ACCEPT : second joueur NovaBot2),
#             INTEG_HOLD (item a tenir en main), INTEG_PRE (commandes console, {name} = joueur).
# Exemple : scripts/servers/run-integration.sh town . 0 "/town new MaVille" Towny-0.103.2.0.jar
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
source ~/novabank-tools/env.sh
VER="${VER:-1.21.11}"
PLUGINS="$ROOT/../Plugins"
DIR="${NOVABANK_SERVERS_DIR:-$HOME/novabank-servers}"
TYPE="${1:?type de banque}"; NAME="${2:?regex du nom}"; OP="${3:?0|1}"; CMDS="${4:?commandes}"; shift 4
for jar in "$@"; do cp -f "$PLUGINS/$jar" "$DIR/paper-$VER/plugins/"; done
INTEG_TYPE="$TYPE" INTEG_NAME="$NAME" INTEG_OP="$OP" INTEG_CMDS="$CMDS" BOT_START_TIMEOUT="${BOT_START_TIMEOUT:-300}" \
  bash "$HERE/run-bot-scenarios.sh" "$VER" "${JDK:-21}" "${INTEG_PHASE:-integration}"
status=$?
mkdir -p "$DIR/parked"
for jar in "$@"; do mv -f "$DIR/paper-$VER/plugins/$(basename "$jar")" "$DIR/parked/"; done
exit $status
