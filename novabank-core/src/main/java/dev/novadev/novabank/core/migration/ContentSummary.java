package dev.novadev.novabank.core.migration;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.WithdrawalProgress;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resume verifiable du contenu d'un backend : nombres d'elements, somme de controle de chaque
 * banque (proprietaire, mode, version, epoch, stocks et valeurs de chaque ligne) et sommes de
 * controle globales du journal, des livraisons et des banques actives.
 *
 * <p>Chaque element est reduit a un texte canonique (champs prefixes par leur longueur) puis a
 * son SHA-256 ; les sommes globales additionnent ces empreintes modulo 2^256, donc ne dependent
 * pas de l'ordre de lecture propre a chaque backend.
 */
public final class ContentSummary {

    private static final BigInteger MODULUS = BigInteger.ONE.shiftLeft(256);
    private static final int MAX_LISTED_BANKS = 5;

    private final Map<BankId, String> banks = new HashMap<>();
    private long stockLines;
    private long operations;
    private long deliveries;
    private long activeBanks;
    private BigInteger operationsDigest = BigInteger.ZERO;
    private BigInteger deliveriesDigest = BigInteger.ZERO;
    private BigInteger activeBanksDigest = BigInteger.ZERO;

    /**
     * Lit tout le contenu d'un backend.
     *
     * @param content contenu a resumer
     * @return son resume
     */
    public static ContentSummary of(StorageContent content) {
        ContentSummary summary = new ContentSummary();
        content.exportBanks(summary::addBank);
        content.exportOperations(summary::addOperation);
        content.exportDeliveries(summary::addDelivery);
        content.exportActiveBanks(summary::addActiveBank);
        return summary;
    }

    /**
     * @param bank banque lue
     * @throws IllegalStateException si la banque a deja ete comptee
     */
    public void addBank(Bank bank) {
        if (banks.put(bank.id(), digest(canonical(bank))) != null) {
            throw new IllegalStateException(ConsoleText.tr("Banque lue deux fois : {0}", bank.id()));
        }
        stockLines += bank.lines().size();
    }

    /**
     * @param record entree de journal lue
     */
    public void addOperation(OperationRecord record) {
        operations++;
        operationsDigest = operationsDigest.add(new BigInteger(1, sha256(canonical(record)))).mod(MODULUS);
    }

    /**
     * @param delivery livraison lue
     */
    public void addDelivery(StoredDelivery delivery) {
        deliveries++;
        deliveriesDigest = deliveriesDigest.add(new BigInteger(1, sha256(canonical(delivery)))).mod(MODULUS);
    }

    /**
     * @param choice banque active lue
     */
    public void addActiveBank(ActiveBankChoice choice) {
        activeBanks++;
        activeBanksDigest = activeBanksDigest.add(new BigInteger(1, sha256(new Canonical()
                .add(choice.playerId()).add(choice.entity().type()).add(choice.entity().id()).text())))
                .mod(MODULUS);
    }

    /**
     * @return nombre de banques
     */
    public long banks() {
        return banks.size();
    }

    /**
     * @return nombre de lignes de stock, toutes banques
     */
    public long stockLines() {
        return stockLines;
    }

    /**
     * @return nombre d'entrees du journal
     */
    public long operations() {
        return operations;
    }

    /**
     * @return nombre de livraisons (remises ou non)
     */
    public long deliveries() {
        return deliveries;
    }

    /**
     * @return nombre de banques actives
     */
    public long activeBanks() {
        return activeBanks;
    }

    /**
     * Compare deux resumes.
     *
     * @param copy resume de la copie
     * @return les ecarts lisibles, vide si la copie est identique
     */
    public List<String> differences(ContentSummary copy) {
        return differences(copy, "a la source", "dans la cible");
    }

    /**
     * Compare deux resumes, avec le nom de chacun dans les ecarts de comptes.
     *
     * @param copy       autre resume
     * @param thisLabel  designation de ce resume (ex. "a la source")
     * @param otherLabel designation de l'autre (ex. "dans la cible")
     * @return les ecarts lisibles, vide si les contenus sont identiques
     */
    public List<String> differences(ContentSummary copy, String thisLabel, String otherLabel) {
        Objects.requireNonNull(copy, "copy");
        List<String> differences = new ArrayList<>();
        String labels = " " + thisLabel + ", %d " + otherLabel;
        compare(differences, "banques", banks(), copy.banks(), labels);
        compare(differences, "lignes de stock", stockLines, copy.stockLines, labels);
        compare(differences, "operations", operations, copy.operations, labels);
        compare(differences, "livraisons", deliveries, copy.deliveries, labels);
        compare(differences, "banques actives", activeBanks, copy.activeBanks, labels);
        Set<BankId> ids = new HashSet<>(banks.keySet());
        ids.addAll(copy.banks.keySet());
        List<BankId> different = ids.stream()
                .filter(id -> !Objects.equals(banks.get(id), copy.banks.get(id)))
                .sorted(Comparator.comparing(BankId::toString))
                .toList();
        if (!different.isEmpty()) {
            differences.add(different.size() + " banque(s) differente(s) (stocks, valeurs, version ou epoch), dont "
                    + different.subList(0, Math.min(MAX_LISTED_BANKS, different.size())));
        }
        if (!operationsDigest.equals(copy.operationsDigest)) {
            differences.add("somme de controle du journal d'operations differente");
        }
        if (!deliveriesDigest.equals(copy.deliveriesDigest)) {
            differences.add("somme de controle des livraisons differente");
        }
        if (!activeBanksDigest.equals(copy.activeBanksDigest)) {
            differences.add("somme de controle des banques actives differente");
        }
        return differences;
    }

    private static void compare(List<String> differences, String label, long first, long second, String labels) {
        if (first != second) {
            differences.add(label + " : " + first + String.format(labels, second));
        }
    }

    private static String canonical(Bank bank) {
        Canonical text = new Canonical().add(bank.id()).add(bank.owner().type()).add(bank.owner().id())
                .add(bank.valuationMode()).add(bank.version()).add(bank.resetEpoch());
        List<Map.Entry<ItemKey, StockLine>> lines = new ArrayList<>(bank.lines().entrySet());
        lines.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        for (Map.Entry<ItemKey, StockLine> line : lines) {
            text.add(line.getKey()).add(line.getValue().quantity()).add(line.getValue().baselineQuantity())
                    .add(line.getValue().accumulatedValue().toPlainString());
        }
        return text.text();
    }

    private static String canonical(OperationRecord record) {
        Canonical text = new Canonical().add(record.operationId()).add(record.type()).add(record.bankId())
                .add(record.itemKey()).add(record.quantity()).add(record.countedQuantity())
                .add(record.valueDelta().toPlainString()).add(record.state()).add(record.timestamp());
        WithdrawalProgress withdrawal = record.withdrawal();
        if (withdrawal != null) {
            text.add(withdrawal.recipient()).add(withdrawal.reservedEpoch()).add(withdrawal.deliveredQuantity())
                    .add(withdrawal.returnedQuantity()).add(withdrawal.returnedValue().toPlainString());
        }
        return text.text();
    }

    private static String canonical(StoredDelivery delivery) {
        return new Canonical().add(delivery.delivery().operationId()).add(delivery.delivery().playerId())
                .add(delivery.delivery().itemKey()).add(delivery.delivery().quantity())
                .add(delivery.deliveredQuantity()).add(delivery.createdAt().toEpochMilli()).text();
    }

    private static String digest(String canonical) {
        return new BigInteger(1, sha256(canonical)).toString(16);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(ConsoleText.tr("SHA-256 absent du JDK"), impossible);
        }
    }

    /** Texte canonique : chaque champ prefixe par sa longueur, {@code -} pour une valeur absente. */
    private static final class Canonical {

        private final StringBuilder text = new StringBuilder();

        Canonical add(Object value) {
            String field = value == null ? "-" : value.toString();
            text.append(field.length()).append(':').append(field).append('|');
            return this;
        }

        String text() {
            return text.toString();
        }
    }
}
