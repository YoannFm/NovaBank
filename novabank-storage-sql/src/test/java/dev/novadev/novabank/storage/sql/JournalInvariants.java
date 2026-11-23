package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifie, directement en SQL, que l'etat des banques est exactement celui que decrit le
 * journal : aucune operation a moitie appliquee.
 *
 * <ul>
 *   <li>SQLite : {@code PRAGMA integrity_check} = ok ;</li>
 *   <li>stock de chaque (banque, item) = depots - retraits reserves + unites rendues ;</li>
 *   <li>version de chaque banque = nombre d'entrees + nombre de retraits soldes (chaque
 *       mutation acceptee incremente la version une fois) ;</li>
 *   <li>valeur cumulee des banques ACCUMULATED = somme des variations + valeurs rendues.</li>
 * </ul>
 */
final class JournalInvariants {

    /** Source d'une connexion directe pour les verifications finales. */
    @FunctionalInterface
    interface ConnectionSource {
        Connection open() throws SQLException;
    }

    private JournalInvariants() {
    }

    /**
     * Coherence du journal et des banques de toute la base, a verifier sans ecriture en cours.
     *
     * @param raw    connexion directe
     * @param prefix prefixe des tables
     */
    static void assertConsistent(ConnectionSource raw, String prefix) throws SQLException {
        try (Connection connection = raw.open()) {
            assertThat(violations(connection, prefix)).isEmpty();
        }
    }

    /**
     * @return la liste des ecarts, vide si tout est coherent
     */
    static List<String> violations(Connection connection, String prefix) throws SQLException {
        List<String> violations = new ArrayList<>();
        if ("SQLite".equals(connection.getMetaData().getDatabaseProductName())) {
            String integrity = single(connection, "PRAGMA integrity_check");
            if (!"ok".equals(integrity)) {
                violations.add("integrity_check : " + integrity);
            }
        }
        Map<String, Long> expectedStock = new HashMap<>();
        Map<String, Long> expectedVersion = new HashMap<>();
        Map<String, BigDecimal> expectedValue = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT bank_id, operation_type, item_key, quantity, state,"
                     + " value_delta, returned_quantity, returned_value FROM " + prefix + "operations")) {
            while (rows.next()) {
                String bank = rows.getString("bank_id");
                String type = rows.getString("operation_type");
                long quantity = rows.getLong("quantity");
                long versions = "WITHDRAW".equals(type) && !"RESERVED".equals(rows.getString("state")) ? 2L : 1L;
                expectedVersion.merge(bank, versions, Long::sum);
                BigDecimal value = new BigDecimal(rows.getString("value_delta"));
                if ("DEPOSIT".equals(type)) {
                    expectedStock.merge(bank + "|" + rows.getString("item_key"), quantity, Long::sum);
                } else if ("WITHDRAW".equals(type)) {
                    expectedStock.merge(bank + "|" + rows.getString("item_key"),
                            rows.getLong("returned_quantity") - quantity, Long::sum);
                    value = value.add(new BigDecimal(rows.getString("returned_value")));
                }
                expectedValue.merge(bank, value, BigDecimal::add);
            }
        }
        Map<String, Long> actualStock = new HashMap<>();
        Map<String, BigDecimal> actualValue = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT bank_id, item_key, quantity, accumulated_value FROM "
                     + prefix + "stock_lines")) {
            while (rows.next()) {
                actualStock.put(rows.getString("bank_id") + "|" + rows.getString("item_key"), rows.getLong("quantity"));
                actualValue.merge(rows.getString("bank_id"), new BigDecimal(rows.getString("accumulated_value")),
                        BigDecimal::add);
            }
        }
        Set<String> lines = new HashSet<>(expectedStock.keySet());
        lines.addAll(actualStock.keySet());
        for (String line : lines) {
            long expected = expectedStock.getOrDefault(line, 0L);
            long actual = actualStock.getOrDefault(line, 0L);
            if (expected != actual) {
                violations.add("stock " + line + " : journal " + expected + ", banque " + actual);
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT bank_id, valuation_mode, version FROM " + prefix
                     + "banks")) {
            while (rows.next()) {
                String bank = rows.getString("bank_id");
                long expected = expectedVersion.getOrDefault(bank, 0L);
                if (rows.getLong("version") != expected) {
                    violations.add("version " + bank + " : journal " + expected + ", banque " + rows.getLong("version"));
                }
                BigDecimal journalValue = expectedValue.getOrDefault(bank, BigDecimal.ZERO);
                BigDecimal bankValue = actualValue.getOrDefault(bank, BigDecimal.ZERO);
                if ("ACCUMULATED".equals(rows.getString("valuation_mode")) && journalValue.compareTo(bankValue) != 0) {
                    violations.add("valeur " + bank + " : journal " + journalValue + ", banque " + bankValue);
                }
            }
        }
        return violations;
    }

    static String single(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
