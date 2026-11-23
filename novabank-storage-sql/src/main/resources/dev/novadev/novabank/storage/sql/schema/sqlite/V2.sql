-- NovaBank - schema SQLite, version 2.
-- Livraisons : la quantite remise est cumulee, la ligne est conservee une fois tout remis
-- (un rejeu de retrait ne peut pas recreer une livraison deja remise).
-- Banque active choisie par chaque joueur (decision D10).

ALTER TABLE ${prefix}pending_deliveries
    ADD COLUMN delivered_quantity INTEGER NOT NULL DEFAULT 0 CHECK (delivered_quantity >= 0);

CREATE TABLE ${prefix}active_banks (
    player_id   TEXT    NOT NULL PRIMARY KEY,
    entity_type TEXT    NOT NULL,
    entity_id   TEXT    NOT NULL,
    updated_at  INTEGER NOT NULL
);
