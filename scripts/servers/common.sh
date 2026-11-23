#!/usr/bin/env bash
# Fonctions communes aux scripts de serveurs de test NovaBank (sources par les autres scripts).
# Regle projet : aucune suppression ; ce qui doit disparaitre est deplace dans _to_delete/.
#
# Cible d'un serveur : "<version>" (Paper) ou "<projet>/<version>" ; projets de l'API fill
# de PaperMC pris en charge : paper, folia ; spigot est COMPILE par BuildTools (fetch-server.sh). Exemples : 1.21.11, paper/1.17.1, folia/26.1.2.

# Aucun cache de bytecode Python (__pycache__) dans le dossier du projet.
export PYTHONDONTWRITEBYTECODE=1

SERVERS_DIR="${NOVABANK_SERVERS_DIR:-$HOME/novabank-servers}"
CACHE_DIR="$SERVERS_DIR/cache"
TRASH_DIR="$SERVERS_DIR/_to_delete"
TOOLS_DIR="${NOVABANK_TOOLS_DIR:-$HOME/novabank-tools}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

mkdir -p "$SERVERS_DIR" "$CACHE_DIR" "$TRASH_DIR"

discard() { # chemin : deplace dans _to_delete/ avec un horodatage
  mv -f "$1" "$TRASH_DIR/$(basename "$1").$(date +%Y%m%d-%H%M%S)"
}

parse_target() { # cible : definit SERVER_PROJECT, VERSION, SERVER_NAME (affichage), DIR, SERVER_JAR, BUILD_FILE, PORT
  case "$1" in
    */*) SERVER_PROJECT="${1%%/*}"; VERSION="${1#*/}" ;;
    *)   SERVER_PROJECT="paper"; VERSION="$1" ;;
  esac
  case "$SERVER_PROJECT" in
    paper) SERVER_NAME="Paper $VERSION" ;;
    folia) SERVER_NAME="Folia $VERSION" ;;
    spigot) SERVER_NAME="Spigot $VERSION" ;;
    *) echo "Projet de serveur inconnu : $SERVER_PROJECT (paper, folia ou spigot)" >&2; return 2 ;;
  esac
  if [ -z "$VERSION" ]; then
    echo "Version absente dans la cible : $1" >&2
    return 2
  fi
  DIR="$SERVERS_DIR/$SERVER_PROJECT-$VERSION"
  SERVER_JAR="$SERVER_PROJECT.jar"
  BUILD_FILE="$DIR/$SERVER_PROJECT-build.txt"
  # Port dedie et stable par cible (25500-25599) ; Paper garde le port calcule sur la version seule.
  local key="$VERSION"
  [ "$SERVER_PROJECT" = "paper" ] || key="$SERVER_PROJECT/$VERSION"
  PORT=$((25500 + $(printf '%s' "$key" | cksum | cut -d' ' -f1) % 100))
}

java_bin() { # majeure du JDK (17, 21, 25) : JAVA<n>_HOME defini par ~/novabank-tools/env.sh
  local var="JAVA$1_HOME" home java
  [ -f "$TOOLS_DIR/env.sh" ] && source "$TOOLS_DIR/env.sh"
  [ "$1" = "25" ] && JAVA25_HOME="${JAVA25_HOME:-$JAVA_HOME}"
  home="${!var:-}"
  java="$home/bin/java"
  if [ -z "$home" ] || [ ! -x "$java" ]; then
    echo "JDK $1 introuvable (variable $var de $TOOLS_DIR/env.sh)" >&2
    return 1
  fi
  echo "$java"
}

# Stockage du plugin pendant les tests de serveur : NOVABANK_STORAGE = sqlite (defaut), yaml, json
# (fichiers dans plugins/NovaBank/data), mariadb, mysql, mongodb (MongoDB autonome) ou mongodb-rs
# (replica set). Pour mariadb, mysql et mongodb*, le serveur de base doit tourner pendant tout le run :
#   NOVABANK_STORAGE=mariadb scripts/db/with-db.sh mariadb scripts/servers/run-smoke.sh 1.21.11 21
#   NOVABANK_STORAGE=mongodb-rs scripts/db/with-db.sh mongodb-rs scripts/servers/run-smoke.sh 1.21.11 21
# (variables NOVABANK_IT_DB_* exportees par with-db.sh). Base du plugin : novabank_mc.
STORAGE="${NOVABANK_STORAGE:-sqlite}"
MC_DATABASE="novabank_mc"

storage_engine() { # sqlite, file, sql ou mongodb
  case "$STORAGE" in
    sqlite) echo sqlite ;;
    yaml|json) echo file ;;
    mariadb|mysql) echo sql ;;
    mongodb|mongodb-rs) echo mongodb ;;
  esac
}

storage_check() { # verifie que le serveur de base attendu est demarre
  case "$STORAGE" in
    sqlite|yaml|json) return 0 ;;
    mariadb|mysql|mongodb|mongodb-rs)
      if [ "${NOVABANK_IT_DB_PRODUCT:-}" != "$STORAGE" ] || [ -z "${NOVABANK_IT_DB_PORT:-}" ]; then
        echo "NOVABANK_STORAGE=$STORAGE : lancer ce script via scripts/db/with-db.sh $STORAGE" >&2
        return 1
      fi ;;
    *) echo "NOVABANK_STORAGE inconnu : $STORAGE (sqlite, yaml, json, mariadb, mysql, mongodb ou mongodb-rs)" >&2; return 2 ;;
  esac
}

storage_sql() { # requete SQL (root, base novabank_mc) ; sortie tabulee sans en-tete
  LD_LIBRARY_PATH="$NOVABANK_IT_DB_LIBS" "$NOVABANK_IT_DB_CLIENT" --no-defaults --socket="$NOVABANK_IT_DB_SOCKET" \
    -uroot -N -B "$@"
}

storage_mongo() { # '<commande JSON>' sur la base novabank_mc ; reponse JSON
  python3 -I "$NOVABANK_IT_DB_CLIENT" --port "$NOVABANK_IT_DB_PORT" --db "$MC_DATABASE" "$@"
}

storage_reset() { # base du plugin vide (serveur de base uniquement)
  case "$(storage_engine)" in
    sql) storage_sql -e "DROP DATABASE IF EXISTS $MC_DATABASE; CREATE DATABASE $MC_DATABASE" ;;
    mongodb) storage_mongo '{"dropDatabase": 1}' > /dev/null ;;
  esac
}

storage_configure() { # config.yml : stockage choisi (aucun effet pour sqlite)
  [ "$STORAGE" = "sqlite" ] && return 0
  if [ "$(storage_engine)" = "file" ]; then
    local backend; backend="$(printf '%s' "$STORAGE" | tr '[:lower:]' '[:upper:]')"
    sed -i "0,/^  type: SQLITE\$/s//  type: $backend/" "$1"
    grep -q "^  type: $backend\$" "$1" || { echo "config.yml : type $backend non applique" >&2; return 1; }
    return 0
  fi
  python3 -I - "$1" "$STORAGE" "$NOVABANK_IT_DB_PORT" "$MC_DATABASE" "$NOVABANK_IT_DB_USER" "$NOVABANK_IT_DB_PASSWORD" \
    "${NOVABANK_IT_DB_URI:-}" <<'PY2'
import re, sys
path, storage, port, database, user, password, uri = sys.argv[1:8]
text = open(path, encoding="utf-8").read()
backend = "MONGODB" if storage.startswith("mongodb") else storage.upper()
text, count = re.subn(r"(?m)^  type: [A-Z]+$", "  type: " + backend, text, count=1)
if backend == "MONGODB":
    start, end = text.index("\n  mongodb:\n"), text.index("\n  # Intervalle de sauvegarde")
    values = {"uri": '"%s"' % uri, "database": database}
    expected = 3
else:
    start, end = text.index("\n  sql:\n"), text.index("\n  sqlite:\n")
    values = {"host": "127.0.0.1", "port": port, "database": database, "username": user, "password": '"%s"' % password}
    expected = 7
block = text[start:end]
for key, value in values.items():
    block, found = re.subn(r"(?m)^    %s: .*$" % key, "    %s: %s" % (key, value), block, count=1)
    count += found
if backend != "MONGODB":
    options = '\n      sslMode: trust' if storage == "mysql" else " {}"
    block, found = re.subn(r"(?m)^    options: .*$", "    options:" + options, block, count=1)
    count += found
if count != expected:
    sys.exit("config.yml : section storage non reconnue")
open(path, "w", encoding="utf-8").write(text[:start] + block + text[end:])
PY2
}
