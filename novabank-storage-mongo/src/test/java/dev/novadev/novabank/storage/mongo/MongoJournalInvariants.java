package dev.novadev.novabank.storage.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;

/**
 * Verifie, directement dans la base, que l'etat des banques est exactement celui que decrit le
 * journal (memes regles que les invariants SQL) et qu'aucune operation n'est restee en attente :
 *
 * <ul>
 *   <li>aucun document banque ne porte {@code pendingOp} ;</li>
 *   <li>stock de chaque (banque, item) = depots - retraits reserves + unites rendues ;</li>
 *   <li>version de chaque banque = nombre d'entrees + nombre de retraits soldes ;</li>
 *   <li>valeur cumulee des banques ACCUMULATED = somme des variations + valeurs rendues.</li>
 * </ul>
 */
final class MongoJournalInvariants {

    private MongoJournalInvariants() {
    }

    static List<String> violations(String databaseName) {
        try (MongoClient client = MongoTestServer.rawClient()) {
            return violations(client.getDatabase(databaseName));
        }
    }

    private static List<String> violations(MongoDatabase database) {
        List<String> violations = new ArrayList<>();
        Map<String, Long> expectedStock = new HashMap<>();
        Map<String, Long> expectedVersion = new HashMap<>();
        Map<String, BigDecimal> expectedValue = new HashMap<>();
        for (Document operation : database.getCollection("operations").find()) {
            String bank = operation.getString("bankId");
            String type = operation.getString("type");
            long quantity = operation.getLong("quantity");
            boolean settled = "WITHDRAW".equals(type) && !"RESERVED".equals(operation.getString("state"));
            expectedVersion.merge(bank, settled ? 2L : 1L, Long::sum);
            BigDecimal value = new BigDecimal(operation.getString("valueDelta"));
            if ("DEPOSIT".equals(type)) {
                expectedStock.merge(bank + "|" + operation.getString("item"), quantity, Long::sum);
            } else if ("WITHDRAW".equals(type)) {
                expectedStock.merge(bank + "|" + operation.getString("item"),
                        operation.getLong("returned") - quantity, Long::sum);
                value = value.add(new BigDecimal(operation.getString("returnedValue")));
            }
            expectedValue.merge(bank, value, BigDecimal::add);
        }
        Map<String, Long> actualStock = new HashMap<>();
        for (Document bank : database.getCollection("banks").find()) {
            String id = bank.getString("_id");
            if (bank.containsKey("pendingOp")) {
                violations.add("operation en attente sur la banque " + id);
            }
            BigDecimal bankValue = BigDecimal.ZERO;
            for (Document line : bank.getList("lines", Document.class)) {
                actualStock.put(id + "|" + line.getString("item"), line.getLong("quantity"));
                bankValue = bankValue.add(new BigDecimal(line.getString("value")));
            }
            long expected = expectedVersion.getOrDefault(id, 0L);
            if (bank.getLong("version") != expected) {
                violations.add("version " + id + " : journal " + expected + ", banque " + bank.getLong("version"));
            }
            BigDecimal journalValue = expectedValue.getOrDefault(id, BigDecimal.ZERO);
            if ("ACCUMULATED".equals(bank.getString("mode")) && journalValue.compareTo(bankValue) != 0) {
                violations.add("valeur " + id + " : journal " + journalValue + ", banque " + bankValue);
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
        return violations;
    }
}
