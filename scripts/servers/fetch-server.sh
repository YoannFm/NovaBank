#!/usr/bin/env bash
# Telecharge le dernier build d'un projet PaperMC (API fill v3) dans ~/novabank-servers/cache/,
# verifie le SHA-256 fourni par l'API et affiche sur la sortie standard deux lignes : le chemin
# du jar puis son canal (STABLE, BETA ou ALPHA).
# Canal : build STABLE de plus grand numero ; a defaut (versions Folia anciennes ou en cours),
# BETA puis ALPHA, le canal retenu est affiche. Un jar deja en cache et conforme n'est pas
# retelecharge.
# Usage : scripts/servers/fetch-server.sh <cible>      ex : 1.21.11, paper/1.17.1, folia/26.1.2, spigot/1.21.11
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

parse_target "${1:?Usage : fetch-server.sh <cible>}"
if [ "$SERVER_PROJECT" = "spigot" ]; then
  # Spigot n'est pas telechargeable : compilation par BuildTools (JDK dans SPIGOT_BUILD_JDK, defaut 21 ; 17 pour 1.17 a
  # 1.20.4, 25 pour 26.x). Long (plusieurs minutes), mis en cache dans ~/novabank-servers/cache.
  TARGET="$CACHE_DIR/spigot-$VERSION.jar"
  if [ ! -f "$TARGET" ]; then
    TOOLS="$CACHE_DIR/buildtools"
    mkdir -p "$TOOLS/work-$VERSION"
    [ -f "$TOOLS/BuildTools.jar" ] || curl -fsSL --retry 3 -o "$TOOLS/BuildTools.jar" \
      "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
    JAVA="$(java_bin "${SPIGOT_BUILD_JDK:-21}")" || exit 1
    echo "Compilation de Spigot $VERSION par BuildTools (JDK ${SPIGOT_BUILD_JDK:-21})..." >&2
    (cd "$TOOLS/work-$VERSION" && "$JAVA" -jar "$TOOLS/BuildTools.jar" --rev "$VERSION" --output-dir "$CACHE_DIR" \
      > "$TOOLS/build-$VERSION.log" 2>&1) || { echo "BuildTools en echec, voir $TOOLS/build-$VERSION.log" >&2; exit 1; }
  fi
  [ -f "$TARGET" ] || { echo "BuildTools n'a pas produit $TARGET" >&2; exit 1; }
  printf '%s\n%s\n' "$TARGET" "BUILDTOOLS"
  exit 0
fi
API="https://fill.papermc.io/v3/projects/$SERVER_PROJECT/versions/$VERSION/builds"

read -r BUILD CHANNEL NAME SHA256 URL < <(curl -fsSL --retry 3 "$API" | python3 -I -c '
import json, sys
builds = [b for b in json.load(sys.stdin) if "server:default" in b.get("downloads", {})]
for channel in ("STABLE", "BETA", "ALPHA"):
    candidates = [b for b in builds if b.get("channel") == channel]
    if candidates:
        b = max(candidates, key=lambda b: b["id"])
        d = b["downloads"]["server:default"]
        print(b["id"], channel, d["name"], d["checksums"]["sha256"], d["url"])
        break
else:
    sys.exit("Aucun build telechargeable pour cette version")
')

TARGET="$CACHE_DIR/$NAME"
if [ -f "$TARGET" ] && [ "$(sha256sum "$TARGET" | cut -d' ' -f1)" = "$SHA256" ]; then
  echo "$SERVER_NAME build $BUILD ($CHANNEL) deja en cache (SHA-256 verifie)" >&2
else
  PART="$TARGET.part.$$"
  echo "Telechargement de $SERVER_NAME build $BUILD ($CHANNEL)" >&2
  curl -fsSL --retry 3 -o "$PART" "$URL"
  ACTUAL="$(sha256sum "$PART" | cut -d' ' -f1)"
  if [ "$ACTUAL" != "$SHA256" ]; then
    echo "SHA-256 invalide pour $NAME : attendu $SHA256, obtenu $ACTUAL" >&2
    discard "$PART"
    exit 1
  fi
  mv -f "$PART" "$TARGET"
  echo "$SERVER_NAME build $BUILD ($CHANNEL) telecharge, SHA-256 verifie : $SHA256" >&2
fi
[ "$CHANNEL" = "STABLE" ] || echo "ATTENTION : $SERVER_NAME n'a pas de build STABLE, build $CHANNEL utilise" >&2
printf '%s\n%s\n' "$TARGET" "$CHANNEL"
