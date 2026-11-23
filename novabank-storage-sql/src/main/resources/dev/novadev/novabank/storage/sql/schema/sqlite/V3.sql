-- NovaBank - schema SQLite, version 3.
-- Flux de changements des banques (synchronisation multi-serveur) : lecture par date de mise a jour.

CREATE INDEX ${prefix}banks_updated ON ${prefix}banks (updated_at);
