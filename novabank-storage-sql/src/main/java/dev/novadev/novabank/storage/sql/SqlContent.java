package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StoredDelivery;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link StorageContent} SQL : chaque lecture est une seule instruction en autocommit, lue en
 * flux ({@link #FETCH_SIZE} lignes a la fois) ; chaque lot importe est une transaction.
 */
final class SqlContent implements StorageContent {

    /** Lignes lues a la fois par les lectures completes. */
    static final int FETCH_SIZE = 500;

    private final SqlTransactions transactions;
    private final TableNames tables;
    private final BankRows banks;
    private final OperationRows operations;
    private final SqlPendingDeliveryStore deliveries;
    private final SqlActiveBankStore activeBanks;

    SqlContent(SqlTransactions transactions, TableNames tables, BankRows banks, OperationRows operations,
               SqlPendingDeliveryStore deliveries, SqlActiveBankStore activeBanks) {
        this.transactions = transactions;
        this.tables = tables;
        this.banks = banks;
        this.operations = operations;
        this.deliveries = deliveries;
        this.activeBanks = activeBanks;
    }

    @Override
    public boolean isEmpty() {
        return transactions.read("verification d'une base vide", connection -> {
            for (String table : List.of(tables.banks(), tables.operations(), tables.pendingDeliveries(),
                    tables.activeBanks())) {
                try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM " + table + " LIMIT 1");
                     ResultSet rows = select.executeQuery()) {
                    if (rows.next()) {
                        return false;
                    }
                }
            }
            return true;
        });
    }

    @Override
    public void exportBanks(Consumer<Bank> sink) {
        transactions.read("lecture des banques", connection -> {
            banks.exportAll(connection, sink);
            return null;
        });
    }

    @Override
    public void exportOperations(Consumer<OperationRecord> sink) {
        transactions.read("lecture du journal", connection -> {
            operations.exportAll(connection, sink);
            return null;
        });
    }

    @Override
    public void exportDeliveries(Consumer<StoredDelivery> sink) {
        deliveries.exportAll(sink);
    }

    @Override
    public void exportActiveBanks(Consumer<ActiveBankChoice> sink) {
        activeBanks.exportAll(sink);
    }

    @Override
    public void importBanks(List<Bank> imported) {
        transactions.write("import de " + imported.size() + " banque(s)", connection -> {
            banks.importAll(connection, imported);
            return null;
        });
    }

    @Override
    public void importOperations(List<OperationRecord> records) {
        transactions.write("import de " + records.size() + " operation(s)", connection -> {
            operations.importAll(connection, records);
            return null;
        });
    }

    @Override
    public void importDeliveries(List<StoredDelivery> imported) {
        deliveries.importAll(imported);
    }

    @Override
    public void importActiveBanks(List<ActiveBankChoice> choices) {
        activeBanks.importAll(choices);
    }
}
