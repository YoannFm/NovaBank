#!/usr/bin/env bash
# Matrice de compatibilite NovaBank (E9) : demarre un serveur reel par cible et par stockage, enchaine le smoke test et,
# quand mineflayer sait s'y connecter, la suite du bot. ECRIT LE 2026-10-28, NON EXECUTE (tests reportes a la version
# finale, decision de Yo). Resume : ~/novabank-servers/matrix-<date>.txt (une ligne RESULT par cible et par etape).
#
# Cibles (JDK) : Paper 1.17.1 (17), 1.18.2 (17), 1.19.4 (17), 1.20.4 (21), 1.20.6 (21), 1.21.11 (21), 26.1.2 (25),
# 26.3 (25) ; Folia 1.19.4 (17), 1.21.11 (21), 26.1.2 (25), 26.3 (25) ; Spigot 1.17.1, 1.21.11, 26.3 (compile par
# BuildTools, long, avec le JDK de la cible).
# Versions mineflayer 4.39 : jusqu'a 1.21.11 et 26.1 (PAS 26.3) -> smoke seul pour 26.3.
#
# Usage : source ~/novabank-tools/env.sh ; scripts/servers/run-matrix.sh [filtre]
#   filtre : sous-chaine de la cible (ex. folia, 1.21, 26.) ; sans filtre : toute la matrice.
# Prerequis : mvn -B install (JAR), npm install dans ~/novabank-tools/bot, Homebrew (MariaDB pour la 2e passe).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
FILTER="${1:-}"
OUT="${NOVABANK_SERVERS_DIR:-$HOME/novabank-servers}/matrix-$(date +%Y%m%d-%H%M%S).txt"
mkdir -p "$(dirname "$OUT")"

# cible:jdk:bot (bot = oui|non)
TARGETS=(
  "1.17.1:17:oui" "1.18.2:17:oui" "1.19.4:17:oui" "1.20.4:21:oui" "1.20.6:21:oui" "1.21.11:21:oui"
  "26.1.2:25:oui" "26.3:25:non"
  "folia/1.19.4:17:oui" "folia/1.21.11:21:oui" "folia/26.1.2:25:oui" "folia/26.3:25:non"
  "spigot/1.17.1:17:oui" "spigot/1.21.11:21:oui" "spigot/26.3:25:non"
)

record() { echo "$1" | tee -a "$OUT"; }

for entry in "${TARGETS[@]}"; do
  IFS=: read -r target jdk bot <<< "$entry"
  [ -z "$FILTER" ] || [[ "$target" == *"$FILTER"* ]] || continue
  record "=== $target (JDK $jdk)"
  export SPIGOT_BUILD_JDK="$jdk"
  if ! bash "$HERE/prepare-server.sh" "$target" "$jdk" > /dev/null 2>&1; then
    record "RESULT $target-preparation FAIL telechargement ou correctif impossible"
    continue
  fi
  # 1. smoke SQLite
  smoke="$(bash "$HERE/run-smoke.sh" "$target" "$jdk" 2>&1)"
  if grep -q "Resultat du script : OK" <<< "$smoke"; then
    record "RESULT $target-smoke-sqlite PASS"
  else
    record "RESULT $target-smoke-sqlite FAIL $(grep -m1 -E 'Resultat du script|Exception|SEVERE' <<< "$smoke")"
  fi
  # 2. smoke MariaDB
  smoke="$(NOVABANK_STORAGE=mariadb bash "$ROOT/scripts/db/with-db.sh" mariadb bash "$HERE/run-smoke.sh" "$target" "$jdk" 2>&1)"
  if grep -q "Resultat du script : OK" <<< "$smoke"; then
    record "RESULT $target-smoke-mariadb PASS"
  else
    record "RESULT $target-smoke-mariadb FAIL $(grep -m1 -E 'Resultat du script|Exception|SEVERE' <<< "$smoke")"
  fi
  # 3. suite du bot (SQLite) : main, restart, return, restart
  if [ "$bot" = oui ]; then
    for phase in main restart return restart; do
      run="$(bash "$HERE/run-bot-scenarios.sh" "$target" "$jdk" "$phase" 2>&1)"
      fails="$(grep -c 'RESULT .* FAIL' <<< "$run")"
      passes="$(grep -c 'RESULT .* PASS' <<< "$run")"
      if [ "$fails" = 0 ] && [ "$passes" -gt 0 ]; then
        record "RESULT $target-bot-$phase PASS $passes scenario(s)"
      else
        record "RESULT $target-bot-$phase FAIL $fails echec(s), $passes reussite(s)"
      fi
    done
  else
    record "RESULT $target-bot SKIP mineflayer ne gere pas cette version"
  fi
done
record "--- Resume ecrit dans $OUT"
grep -c ' FAIL ' "$OUT" | xargs -I{} echo "{} echec(s)"
! grep -q ' FAIL ' "$OUT"
