#!/usr/bin/env bash
# Prepare (ou remet a jour) le dossier d'un serveur de test : ~/novabank-servers/<projet>-<version>/
# jar verifie (fetch-server.sh), eula, server.properties minimal (hors ligne, monde plat,
# distances 4, pas de structures, difficulte paisible, port dedie, RCON et query desactives).
# Difficulte paisible : malgre spawn-monsters=false, des slimes ont tue le bot de test sur le monde
# plat (constate sous Folia 1.21.11) ; un monde existant garde sa difficulte, le bot passe donc aussi
# "difficulty peaceful" en console. Le monde et les plugins existants sont conserves d'un run a l'autre.
# Avec un JDK, applique aussi le correctif Paperclip (telechargement du serveur Mojang et des
# bibliotheques, sans demarrer) : le premier smoke test n'a plus que le demarrage a faire.
# Spigot : compile par BuildTools avec le JDK de SPIGOT_BUILD_JDK (voir fetch-server.sh) ; pas de correctif Paperclip.
# Usage : scripts/servers/prepare-server.sh <cible> [jdk]      ex : 1.21.11 21, folia/26.1.2 25
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

parse_target "${1:?Usage : prepare-server.sh <cible> [jdk]}"
JDK="${2:-}"
mapfile -t FETCHED < <("$(dirname "${BASH_SOURCE[0]}")/fetch-server.sh" "$1")
JAR="${FETCHED[0]:-}"
CHANNEL="${FETCHED[1]:-}"
[ -f "$JAR" ] || { echo "Telechargement de $SERVER_NAME en echec" >&2; exit 1; }

mkdir -p "$DIR/plugins"
cp -f "$JAR" "$DIR/$SERVER_JAR"
echo "$(basename "$JAR") ($CHANNEL)" > "$BUILD_FILE"
echo "eula=true" > "$DIR/eula.txt"
cat > "$DIR/server.properties" <<PROPS
# Genere par scripts/servers/prepare-server.sh (reecrit a chaque preparation)
server-port=$PORT
online-mode=false
level-name=world
level-type=flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structures":{"structures":{}}}
generate-structures=false
view-distance=4
simulation-distance=4
spawn-protection=0
difficulty=peaceful
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
max-players=2
enable-rcon=false
enable-query=false
enable-command-block=false
sync-chunk-writes=false
motd=NovaBank test $SERVER_NAME
PROPS
echo "Serveur pret : $DIR (port $PORT, $(cat "$BUILD_FILE"))"
if [ -n "$JDK" ] && [ "$SERVER_PROJECT" != "spigot" ]; then
  JAVA="$(java_bin "$JDK")"
  echo "Correctif Paperclip (JDK $JDK)..."
  (cd "$DIR" && "$JAVA" -Dpaperclip.patchonly=true -jar "$SERVER_JAR" > "$DIR/paperclip-patch.log" 2>&1)
  echo "Correctif Paperclip termine : $(tail -1 "$DIR/paperclip-patch.log")"
fi
