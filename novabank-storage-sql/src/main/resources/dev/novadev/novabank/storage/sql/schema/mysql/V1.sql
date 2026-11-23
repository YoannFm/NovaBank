-- NovaBank - schema MySQL / MariaDB (InnoDB), version 1. Meme modele logique que sqlite/V1.sql.
-- Tables InnoDB, utf8mb4, comparaisons binaires (utf8mb4_bin : sensibles a la casse, comme SQLite).
-- Montants en DECIMAL(38,4) : relecture exacte (BigDecimal a 4 decimales), jamais de flottant.
-- Identifiants (UUID) en CHAR(36) ascii : forme texte canonique, identique a SQLite et lisible
-- en console ; 36 octets au lieu de 16 en BINARY(16), sans incidence au volume d'un serveur.
-- Cles d'items et types d'entites : ascii (motifs [a-z0-9_.:/-]) ; identifiant d'entite : utf8mb4.
-- Horodatages : millisecondes epoch, sauf le journal (secondes + nanosecondes, Instant exact).
--
-- Rejouable : le DDL MySQL valide implicitement, une version interrompue est rejouee en entier
-- au demarrage suivant (CREATE TABLE IF NOT EXISTS, index declares dans la table).

CREATE TABLE IF NOT EXISTS ${prefix}banks (
    bank_id        CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_type     VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id       VARCHAR(191) NOT NULL,
    valuation_mode VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version        BIGINT       NOT NULL CHECK (version >= 0),
    reset_epoch    BIGINT       NOT NULL CHECK (reset_epoch >= 0),
    created_at     BIGINT       NOT NULL,
    updated_at     BIGINT       NOT NULL,
    PRIMARY KEY (bank_id),
    KEY ${prefix}banks_owner (owner_type, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ${prefix}stock_lines (
    bank_id           CHAR(36)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_key          VARCHAR(255)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity          BIGINT        NOT NULL CHECK (quantity >= 0),
    baseline_quantity BIGINT        NOT NULL CHECK (baseline_quantity >= 0),
    accumulated_value DECIMAL(38,4) NOT NULL,
    PRIMARY KEY (bank_id, item_key),
    CONSTRAINT ${prefix}stock_lines_bank FOREIGN KEY (bank_id) REFERENCES ${prefix}banks (bank_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ${prefix}operations (
    operation_id       CHAR(36)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    bank_id            CHAR(36)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_type     VARCHAR(32)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_key           VARCHAR(255)  CHARACTER SET ascii COLLATE ascii_bin,
    quantity           BIGINT        NOT NULL CHECK (quantity >= 0),
    counted_quantity   BIGINT        NOT NULL CHECK (counted_quantity >= 0),
    value_delta        DECIMAL(38,4) NOT NULL,
    state              VARCHAR(32)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at_sec    BIGINT        NOT NULL,
    occurred_at_nano   INT           NOT NULL,
    recipient          CHAR(36)      CHARACTER SET ascii COLLATE ascii_bin,
    reserved_epoch     BIGINT,
    delivered_quantity BIGINT,
    returned_quantity  BIGINT,
    returned_value     DECIMAL(38,4),
    PRIMARY KEY (operation_id),
    KEY ${prefix}operations_bank_time (bank_id, occurred_at_sec, occurred_at_nano),
    KEY ${prefix}operations_state (state, occurred_at_sec, occurred_at_nano),
    CONSTRAINT ${prefix}operations_bank FOREIGN KEY (bank_id) REFERENCES ${prefix}banks (bank_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Pas de cle etrangere vers le journal : la file est un port independant.
CREATE TABLE IF NOT EXISTS ${prefix}pending_deliveries (
    operation_id CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    player_id    CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_key     VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quantity     BIGINT       NOT NULL CHECK (quantity > 0),
    created_at   BIGINT       NOT NULL,
    PRIMARY KEY (operation_id),
    KEY ${prefix}pending_deliveries_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
