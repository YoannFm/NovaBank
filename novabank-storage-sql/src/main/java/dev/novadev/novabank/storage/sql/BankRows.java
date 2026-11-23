package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.repository.RepositorySupport;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lecture et ecriture des banques et de leurs lignes de stock.
 */
final class BankRows {

    private static final List<String> BANK_COLUMNS = List.of("bank_id", "owner_type", "owner_id", "valuation_mode",
            "version", "reset_epoch", "created_at", "updated_at");
    private static final List<String> LINE_COLUMNS = List.of("bank_id", "item_key", "quantity", "baseline_quantity",
            "accumulated_value");
    private static final List<String> LINE_KEY = List.of("bank_id", "item_key");

    private final SqlDialect dialect;
    private final Clock clock;
    private final String selectBank;
    private final String selectAll;
    private final String insertBank;
    private final String importBank;
    private final String importLine;
    private final String updateVersion;
    private final String upsertLine;
    private final String deleteLine;
    private final String selectChanges;

    BankRows(TableNames tables, SqlDialect dialect, Clock clock) {
        this.dialect = dialect;
        this.clock = clock;
        this.selectBank = "SELECT b.owner_type, b.owner_id, b.valuation_mode, b.version, b.reset_epoch,"
                + " l.item_key, l.quantity, l.baseline_quantity, l.accumulated_value"
                + " FROM " + tables.banks() + " b LEFT JOIN " + tables.stockLines() + " l ON l.bank_id = b.bank_id"
                + " WHERE b.bank_id = ?";
        this.selectAll = "SELECT b.bank_id, b.owner_type, b.owner_id, b.valuation_mode, b.version, b.reset_epoch,"
                + " l.item_key, l.quantity, l.baseline_quantity, l.accumulated_value"
                + " FROM " + tables.banks() + " b LEFT JOIN " + tables.stockLines() + " l ON l.bank_id = b.bank_id"
                + " ORDER BY b.bank_id";
        this.insertBank = dialect.insertIgnoringDuplicate(tables.banks(), BANK_COLUMNS);
        this.importBank = SqlDialect.insert(tables.banks(), BANK_COLUMNS);
        this.importLine = SqlDialect.insert(tables.stockLines(), LINE_COLUMNS);
        this.updateVersion = "UPDATE " + tables.banks() + " SET version = ?, reset_epoch = ?, updated_at = ?"
                + " WHERE bank_id = ? AND version = ?";
        this.upsertLine = dialect.upsert(tables.stockLines(), LINE_COLUMNS, LINE_KEY);
        this.deleteLine = "DELETE FROM " + tables.stockLines() + " WHERE bank_id = ? AND item_key = ?";
        this.selectChanges = "SELECT bank_id, version, reset_epoch, updated_at FROM " + tables.banks()
                + " WHERE updated_at >= ? ORDER BY updated_at, bank_id LIMIT ?";
    }

    /**
     * Lit une banque et ses lignes en une seule instruction (un seul instantane).
     *
     * @param connection connexion
     * @param bankId     banque
     * @param lockRows   verrouiller les lignes lues (lecture dans une transaction d'ecriture)
     * @return la banque, ou vide
     * @throws SQLException erreur du pilote
     */
    Optional<Bank> find(Connection connection, BankId bankId, boolean lockRows) throws SQLException {
        String sql = lockRows ? selectBank + dialect.lockRowsSuffix() : selectBank;
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, bankId.toString());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                Bank header = header(rows, bankId);
                Map<ItemKey, StockLine> lines = new HashMap<>();
                do {
                    addLine(rows, lines);
                } while (rows.next());
                return Optional.of(withLines(header, lines));
            }
        }
    }

    /**
     * Lit les banques modifiees depuis une date (flux de changements).
     *
     * @param connection connexion
     * @param since      borne inferieure incluse
     * @param limit      nombre maximal de banques
     * @return les changements par date puis identifiant croissants
     * @throws SQLException erreur du pilote
     */
    List<BankChange> changesSince(Connection connection, Instant since, int limit) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(selectChanges)) {
            select.setLong(1, since.toEpochMilli());
            select.setInt(2, limit);
            try (ResultSet rows = select.executeQuery()) {
                List<BankChange> changes = new ArrayList<>();
                while (rows.next()) {
                    changes.add(new BankChange(new BankId(UUID.fromString(rows.getString(1))), rows.getLong(2),
                            rows.getLong(3), Instant.ofEpochMilli(rows.getLong(4))));
                }
                return changes;
            }
        }
    }

    /**
     * Lit toutes les banques avec leurs lignes (migration), une banque a la fois.
     *
     * @param connection connexion
     * @param sink       recepteur de chaque banque
     * @throws SQLException erreur du pilote
     */
    void exportAll(Connection connection, Consumer<Bank> sink) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(selectAll)) {
            select.setFetchSize(SqlContent.FETCH_SIZE);
            try (ResultSet rows = select.executeQuery()) {
                Bank header = null;
                Map<ItemKey, StockLine> lines = new HashMap<>();
                while (rows.next()) {
                    BankId bankId = new BankId(UUID.fromString(rows.getString("bank_id")));
                    if (header == null || !header.id().equals(bankId)) {
                        if (header != null) {
                            sink.accept(withLines(header, lines));
                            lines.clear();
                        }
                        header = header(rows, bankId);
                    }
                    addLine(rows, lines);
                }
                if (header != null) {
                    sink.accept(withLines(header, lines));
                }
            }
        }
    }

    /**
     * Enregistre des banques telles quelles (migration vers une base vide).
     *
     * @param connection connexion en transaction
     * @param banks      banques, version, epoch et lignes compris
     * @throws SQLException erreur du pilote, dont un identifiant deja present
     */
    void importAll(Connection connection, List<Bank> banks) throws SQLException {
        long now = clock.millis();
        try (PreparedStatement bankInsert = connection.prepareStatement(importBank);
             PreparedStatement lineInsert = connection.prepareStatement(importLine)) {
            boolean anyLine = false;
            for (Bank bank : banks) {
                bankInsert.setString(1, bank.id().toString());
                bankInsert.setString(2, bank.owner().type());
                bankInsert.setString(3, bank.owner().id());
                bankInsert.setString(4, bank.valuationMode().name());
                bankInsert.setLong(5, bank.version());
                bankInsert.setLong(6, bank.resetEpoch());
                bankInsert.setLong(7, now);
                bankInsert.setLong(8, now);
                bankInsert.addBatch();
                for (Map.Entry<ItemKey, StockLine> entry : bank.lines().entrySet()) {
                    bindLine(lineInsert, bank.id().toString(), entry.getKey(), entry.getValue());
                    lineInsert.addBatch();
                    anyLine = true;
                }
            }
            bankInsert.executeBatch();
            if (anyLine) {
                lineInsert.executeBatch();
            }
        }
    }

    private Bank header(ResultSet rows, BankId bankId) throws SQLException {
        return new Bank(bankId, new EntityRef(rows.getString("owner_type"), rows.getString("owner_id")),
                ValuationMode.valueOf(rows.getString("valuation_mode")), rows.getLong("version"),
                rows.getLong("reset_epoch"), Map.of());
    }

    private void addLine(ResultSet rows, Map<ItemKey, StockLine> lines) throws SQLException {
        String item = rows.getString("item_key");
        if (item != null) {
            lines.put(ItemKey.parse(item), new StockLine(rows.getLong("quantity"), rows.getLong("baseline_quantity"),
                    dialect.readMoney(rows, "accumulated_value")));
        }
    }

    private static Bank withLines(Bank header, Map<ItemKey, StockLine> lines) {
        return new Bank(header.id(), header.owner(), header.valuationMode(), header.version(), header.resetEpoch(),
                lines);
    }

    private void bindLine(PreparedStatement statement, String bankId, ItemKey item, StockLine line)
            throws SQLException {
        statement.setString(1, bankId);
        statement.setString(2, item.toString());
        statement.setLong(3, line.quantity());
        statement.setLong(4, line.baselineQuantity());
        dialect.bindMoney(statement, 5, line.accumulatedValue());
    }

    /**
     * Insere une banque neuve, sans effet si l'identifiant existe deja.
     *
     * @param connection connexion en transaction
     * @param bank       banque neuve
     * @throws SQLException erreur du pilote
     */
    void insertIfAbsent(Connection connection, Bank bank) throws SQLException {
        long now = clock.millis();
        try (PreparedStatement insert = connection.prepareStatement(insertBank)) {
            insert.setString(1, bank.id().toString());
            insert.setString(2, bank.owner().type());
            insert.setString(3, bank.owner().id());
            insert.setString(4, bank.valuationMode().name());
            insert.setLong(5, bank.version());
            insert.setLong(6, bank.resetEpoch());
            insert.setLong(7, now);
            insert.setLong(8, now);
            insert.executeUpdate();
        }
    }

    /**
     * Enregistre l'etat produit par une mutation : version lue + 1 (verrou optimiste),
     * puis seules les lignes de stock modifiees ou disparues.
     *
     * @param connection connexion en transaction
     * @param current    etat lu dans cette transaction
     * @param updated    etat produit par la mutation
     * @return l'etat enregistre
     * @throws WriteConflict si la version a change depuis la lecture
     * @throws SQLException  erreur du pilote
     */
    Bank saveSuccessor(Connection connection, Bank current, Bank updated) throws SQLException {
        Bank saved = RepositorySupport.successor(current, updated);
        String bankId = saved.id().toString();
        try (PreparedStatement update = connection.prepareStatement(updateVersion)) {
            update.setLong(1, saved.version());
            update.setLong(2, saved.resetEpoch());
            update.setLong(3, clock.millis());
            update.setString(4, bankId);
            update.setLong(5, current.version());
            if (update.executeUpdate() != 1) {
                throw new WriteConflict("Version de la banque " + bankId + " modifiee depuis la lecture");
            }
        }
        writeChangedLines(connection, bankId, current.lines(), saved.lines());
        return saved;
    }

    private void writeChangedLines(Connection connection, String bankId, Map<ItemKey, StockLine> before,
                                   Map<ItemKey, StockLine> after) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(upsertLine);
             PreparedStatement delete = connection.prepareStatement(deleteLine)) {
            boolean upserts = false;
            for (Map.Entry<ItemKey, StockLine> entry : after.entrySet()) {
                StockLine line = entry.getValue();
                if (!line.equals(before.get(entry.getKey()))) {
                    bindLine(upsert, bankId, entry.getKey(), line);
                    upsert.addBatch();
                    upserts = true;
                }
            }
            boolean deletes = false;
            for (ItemKey item : before.keySet()) {
                if (!after.containsKey(item)) {
                    delete.setString(1, bankId);
                    delete.setString(2, item.toString());
                    delete.addBatch();
                    deletes = true;
                }
            }
            if (upserts) {
                upsert.executeBatch();
            }
            if (deletes) {
                delete.executeBatch();
            }
        }
    }
}
