/**
 * Stockage SQL des banques, du journal et des livraisons en attente (JDBC + HikariCP).
 *
 * <p>Le code de ce package n'emet que du SQL portable ; tout ce qui depend du moteur
 * passe par {@link dev.novadev.novabank.storage.sql.dialect.SqlDialect}.
 */
package dev.novadev.novabank.storage.sql;
