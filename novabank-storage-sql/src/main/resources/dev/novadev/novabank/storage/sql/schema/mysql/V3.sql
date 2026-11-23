-- NovaBank - schema MySQL / MariaDB, version 3. Meme modele logique que sqlite/V3.sql.
-- Rejouable : "index deja cree" (1061) est reconnu par le dialecte et l'instruction consideree comme appliquee.

ALTER TABLE ${prefix}banks ADD KEY ${prefix}banks_updated (updated_at);
