-- NovaBank - schema SQLite, version 1.
-- Montants en TEXT canonique (BigDecimal a 4 decimales) : jamais de REAL.
-- Identifiants (UUID) en TEXT. Horodatages : millisecondes epoch, sauf le journal
-- (secondes + nanosecondes, relecture exacte de l'Instant).

CREATE TABLE ${prefix}banks (
    bank_id        TEXT    NOT NULL PRIMARY KEY,
    owner_type     TEXT    NOT NULL,
    owner_id       TEXT    NOT NULL,
    valuation_mode TEXT    NOT NULL,
    version        INTEGER NOT NULL CHECK (version >= 0),
    reset_epoch    INTEGER NOT NULL CHECK (reset_epoch >= 0),
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL
);

CREATE INDEX ${prefix}banks_owner ON ${prefix}banks (owner_type, owner_id);

CREATE TABLE ${prefix}stock_lines (
    bank_id           TEXT    NOT NULL REFERENCES ${prefix}banks (bank_id),
    item_key          TEXT    NOT NULL,
    quantity          INTEGER NOT NULL CHECK (quantity >= 0),
    baseline_quantity INTEGER NOT NULL CHECK (baseline_quantity >= 0),
    accumulated_value TEXT    NOT NULL,
    PRIMARY KEY (bank_id, item_key)
);

CREATE TABLE ${prefix}operations (
    operation_id       TEXT    NOT NULL PRIMARY KEY,
    bank_id            TEXT    NOT NULL REFERENCES ${prefix}banks (bank_id),
    operation_type     TEXT    NOT NULL,
    item_key           TEXT,
    quantity           INTEGER NOT NULL CHECK (quantity >= 0),
    counted_quantity   INTEGER NOT NULL CHECK (counted_quantity >= 0),
    value_delta        TEXT    NOT NULL,
    state              TEXT    NOT NULL,
    occurred_at_sec    INTEGER NOT NULL,
    occurred_at_nano   INTEGER NOT NULL,
    recipient          TEXT,
    reserved_epoch     INTEGER,
    delivered_quantity INTEGER,
    returned_quantity  INTEGER,
    returned_value     TEXT
);

CREATE INDEX ${prefix}operations_bank_time ON ${prefix}operations (bank_id, occurred_at_sec, occurred_at_nano);

CREATE INDEX ${prefix}operations_state ON ${prefix}operations (state, occurred_at_sec, occurred_at_nano);

-- Pas de cle etrangere vers le journal : la file est un port independant.
CREATE TABLE ${prefix}pending_deliveries (
    operation_id TEXT    NOT NULL PRIMARY KEY,
    player_id    TEXT    NOT NULL,
    item_key     TEXT    NOT NULL,
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    created_at   INTEGER NOT NULL
);

CREATE INDEX ${prefix}pending_deliveries_player ON ${prefix}pending_deliveries (player_id);
