-- NovaBank - schema MySQL / MariaDB, version 2. Meme modele logique que sqlite/V2.sql.
-- Livraisons : la quantite remise est cumulee, la ligne est conservee une fois tout remis
-- (un rejeu de retrait ne peut pas recreer une livraison deja remise).
-- Banque active choisie par chaque joueur (decision D10).
--
-- Rejouable : si la version a ete interrompue apres l'ajout de la colonne, l'erreur "colonne
-- deja presente" (1060) est reconnue par le dialecte et l'instruction consideree comme appliquee
-- (MySQL n'accepte pas ADD COLUMN IF NOT EXISTS).

ALTER TABLE ${prefix}pending_deliveries
    ADD COLUMN delivered_quantity BIGINT NOT NULL DEFAULT 0 CHECK (delivered_quantity >= 0);

CREATE TABLE IF NOT EXISTS ${prefix}active_banks (
    player_id   CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entity_type VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entity_id   VARCHAR(191) NOT NULL,
    updated_at  BIGINT       NOT NULL,
    PRIMARY KEY (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
