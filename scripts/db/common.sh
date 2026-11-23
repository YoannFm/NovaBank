#!/usr/bin/env bash
# Fonctions communes aux scripts des serveurs de base de donnees de test (sources par les autres
# scripts de scripts/db). macOS : les binaires viennent de Homebrew (brew install redis mariadb
# mysql@8.4 mongodb/brew/mongodb-community), les donnees de test sont dans ~/novabank-db
# (variable NOVABANK_DB_DIR) :
#   data/<produit>-<version>/  datadir (cree au premier demarrage, conserve ensuite)
#   run/                       socket, fichier PID, journal du serveur
# Aucun service Homebrew n'est utilise (brew services) : serveurs prives, ports dedies, 127.0.0.1.
# Dans le projet : aucune suppression (regle de Yo).
#
# Produits : mariadb (MariaDB, LTS la plus recente : 12.3), mysql (MySQL 8.4 LTS), mongodb (MongoDB
# 8.x autonome, sans transaction) et mongodb-rs (meme binaire en replica set mono-noeud "rs0", avec
# transactions ; datadir et port distincts).
# Identifiants de test SQL (base locale, ecoute sur 127.0.0.1 uniquement) : utilisateur novabank,
# mot de passe NOVABANK_DB_PASSWORD (defaut novabank-test), droits limites aux bases novabank*.
# MongoDB de test : sans authentification, ecoute sur 127.0.0.1 uniquement.

# Aucun cache de bytecode Python (__pycache__) dans le dossier du projet.
export PYTHONDONTWRITEBYTECODE=1

DB_DIR="${NOVABANK_DB_DIR:-$HOME/novabank-db}"
BREW_PREFIX="${HOMEBREW_PREFIX:-/opt/homebrew}"
DB_RUN="$DB_DIR/run"
DB_USER="novabank"
DB_PASSWORD="${NOVABANK_DB_PASSWORD:-novabank-test}"
DB_NAME="novabank"
DB_SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_PROJECT_DIR="$(cd "$DB_SCRIPTS_DIR/../.." && pwd)"

# Les scripts suppriment des fichiers dans DB_DIR (archives extraites, installation interrompue) :
# jamais dans le dossier du projet ni dans le dossier qui le contient (dossier connecte de Yo).
case "$DB_DIR/" in
  "$(dirname "$DB_PROJECT_DIR")"/*) echo "NOVABANK_DB_DIR ne doit pas etre dans le dossier NovaBank : $DB_DIR" >&2; exit 2 ;;
esac
mkdir -p "$DB_DIR" "$DB_RUN"

db_vars() { # produit : definit PRODUCT, ENGINE (sql, mongodb ou redis), DB_VERSION, BASE, DATA, SOCKET, PID_FILE,
            # SERVER_LOG, DB_PORT, SERVER_BIN, CLIENT_BIN, DB_LIBS (vide sur macOS), REPLICA_SET
            # (mongodb-rs uniquement, vide sinon)
  PRODUCT="$1"
  ENGINE="sql"
  REPLICA_SET=""
  DB_LIBS=""
  case "$PRODUCT" in
    mariadb)
      DB_PORT="${NOVABANK_MARIADB_PORT:-33061}"
      BASE="$BREW_PREFIX/opt/mariadb"
      SERVER_BIN="$BASE/bin/mariadbd"
      CLIENT_BIN="$BASE/bin/mariadb" ;;
    mysql)
      DB_PORT="${NOVABANK_MYSQL_PORT:-33062}"
      BASE="$BREW_PREFIX/opt/mysql@8.4"
      SERVER_BIN="$BASE/bin/mysqld"
      CLIENT_BIN="$BASE/bin/mysql" ;;
    mongodb|mongodb-rs)
      ENGINE="mongodb"
      BASE="$BREW_PREFIX/opt/mongodb-community"
      SERVER_BIN="$BASE/bin/mongod"
      CLIENT_BIN="$DB_SCRIPTS_DIR/mongo-command.py"
      if [ "$PRODUCT" = "mongodb-rs" ]; then
        DB_PORT="${NOVABANK_MONGODB_RS_PORT:-27019}"
        REPLICA_SET="rs0"
      else
        DB_PORT="${NOVABANK_MONGODB_PORT:-27018}"
      fi ;;
    redis)
      ENGINE="redis"
      DB_PORT="${NOVABANK_REDIS_PORT:-63791}"
      BASE="$BREW_PREFIX/opt/redis"
      SERVER_BIN="$BASE/bin/redis-server"
      CLIENT_BIN="$BASE/bin/redis-cli" ;;
    *) echo "Produit inconnu : $PRODUCT (mariadb, mysql, mongodb, mongodb-rs ou redis)" >&2; return 2 ;;
  esac
  if [ -x "$SERVER_BIN" ]; then
    DB_VERSION="$("$SERVER_BIN" --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
  fi
  DB_VERSION="${DB_VERSION:-absent}"
  DATA="$DB_DIR/data/$PRODUCT-$DB_VERSION"
  SOCKET="$DB_RUN/$PRODUCT.sock"
  PID_FILE="$DB_RUN/$PRODUCT.pid"
  SERVER_LOG="$DB_RUN/$PRODUCT.log"
}

mongo_command() { # [options de mongo-command.py] '<commande JSON>' : commande au serveur MongoDB choisi
  python3 -I "$CLIENT_BIN" --port "$DB_PORT" "$@"
}

client() { # client SQL en root par le socket (produit deja choisi par db_vars)
  "$CLIENT_BIN" --no-defaults --socket="$SOCKET" -uroot "$@"
}
