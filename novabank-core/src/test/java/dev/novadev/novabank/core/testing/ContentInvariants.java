package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.storage.StorageContent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coherence journal / banques d'un backend quelconque, lue par son contenu complet : aucune
 * operation a moitie appliquee.
 *
 * <ul>
 *   <li>stock de chaque (banque, item) = depots - retraits reserves + unites rendues ;</li>
 *   <li>version de chaque banque = nombre d'entrees + nombre de retraits soldes (chaque mutation
 *       acceptee incremente la version une fois) ;</li>
 *   <li>valeur cumulee des banques ACCUMULATED = somme des variations + valeurs rendues.</li>
 * </ul>
 */
public final class ContentInvariants {

    private ContentInvariants() {
    }

    /**
     * @param content contenu a verifier, sans ecriture en cours
     * @return les ecarts, vide si tout est coherent
     */
    public static List<String> violations(StorageContent content) {
        Map<String, Long> expectedStock = new HashMap<>();
        Map<BankId, Long> expectedVersion = new HashMap<>();
        Map<BankId, BigDecimal> expectedValue = new HashMap<>();
        content.exportOperations(record -> {
            boolean withdrawal = record.type() == OperationType.WITHDRAW;
            expectedVersion.merge(record.bankId(), withdrawal && record.state() != OperationState.RESERVED ? 2L : 1L,
                    Long::sum);
            BigDecimal value = record.valueDelta();
            if (record.type() == OperationType.DEPOSIT) {
                expectedStock.merge(key(record.bankId(), record.itemKey()), record.quantity(), Long::sum);
            } else if (withdrawal) {
                expectedStock.merge(key(record.bankId(), record.itemKey()),
                        record.withdrawal().returnedQuantity() - record.quantity(), Long::sum);
                value = value.add(record.withdrawal().returnedValue());
            }
            expectedValue.merge(record.bankId(), value, BigDecimal::add);
        });
        List<String> violations = new ArrayList<>();
        Map<String, Long> actualStock = new HashMap<>();
        content.exportBanks(bank -> {
            BigDecimal bankValue = BigDecimal.ZERO;
            for (Map.Entry<ItemKey, StockLine> line : bank.lines().entrySet()) {
                actualStock.put(key(bank.id(), line.getKey()), line.getValue().quantity());
                bankValue = bankValue.add(line.getValue().accumulatedValue());
            }
            check(violations, bank, expectedVersion.getOrDefault(bank.id(), 0L), bankValue,
                    expectedValue.getOrDefault(bank.id(), BigDecimal.ZERO));
        });
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

    private static void check(List<String> violations, Bank bank, long expectedVersion, BigDecimal bankValue,
                              BigDecimal journalValue) {
        if (bank.version() != expectedVersion) {
            violations.add("version " + bank.id() + " : journal " + expectedVersion + ", banque " + bank.version());
        }
        if (bank.valuationMode() == ValuationMode.ACCUMULATED && journalValue.compareTo(bankValue) != 0) {
            violations.add("valeur " + bank.id() + " : journal " + journalValue + ", banque " + bankValue);
        }
    }

    private static String key(BankId bankId, ItemKey item) {
        return bankId + "|" + item;
    }
}
