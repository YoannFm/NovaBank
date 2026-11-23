package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.journal.WithdrawalProgress;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lecture et ecriture des entrees du journal d'operations.
 */
final class OperationRows {

    /** Colonnes hors identifiant, dans l'ordre de {@link #bindFields}. */
    private static final List<String> FIELD_COLUMNS = List.of("bank_id", "operation_type", "item_key", "quantity",
            "counted_quantity", "value_delta", "state", "occurred_at_sec", "occurred_at_nano", "recipient",
            "reserved_epoch", "delivered_quantity", "returned_quantity", "returned_value");

    private final SqlDialect dialect;
    private final String selectById;
    private final String selectReserved;
    private final String selectHistory;
    private final String selectAll;
    private final String insert;
    private final String update;

    OperationRows(TableNames tables, SqlDialect dialect) {
        this.dialect = dialect;
        String select = "SELECT operation_id, " + String.join(", ", FIELD_COLUMNS) + " FROM " + tables.operations();
        this.selectById = select + " WHERE operation_id = ?";
        this.selectReserved = select + " WHERE operation_type = '" + OperationType.WITHDRAW.name()
                + "' AND state = '" + OperationState.RESERVED.name() + "'"
                + " ORDER BY occurred_at_sec, occurred_at_nano, operation_id";
        this.selectHistory = select + " WHERE bank_id = ?"
                + " ORDER BY occurred_at_sec DESC, occurred_at_nano DESC, operation_id DESC LIMIT ? OFFSET ?";
        this.selectAll = select;
        List<String> insertColumns = new ArrayList<>();
        insertColumns.add("operation_id");
        insertColumns.addAll(FIELD_COLUMNS);
        this.insert = SqlDialect.insert(tables.operations(), insertColumns);
        this.update = "UPDATE " + tables.operations() + " SET " + String.join(" = ?, ", FIELD_COLUMNS)
                + " = ? WHERE operation_id = ?";
    }

    /**
     * @param connection  connexion
     * @param operationId operation recherchee
     * @return l'entree, ou vide
     * @throws SQLException erreur du pilote
     */
    Optional<OperationRecord> find(Connection connection, OperationId operationId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(selectById)) {
            select.setString(1, operationId.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    /**
     * @param connection connexion
     * @return les retraits reserves, par horodatage puis identifiant
     * @throws SQLException erreur du pilote
     */
    List<OperationRecord> reservedWithdrawals(Connection connection) throws SQLException {
        List<OperationRecord> records = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(selectReserved);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                records.add(read(rows));
            }
        }
        return records;
    }

    /**
     * @param connection connexion
     * @param bankId     banque
     * @param offset     entrees a sauter
     * @param limit      nombre maximal d'entrees
     * @return le journal de la banque, du plus recent au plus ancien
     * @throws SQLException erreur du pilote
     */
    List<OperationRecord> history(Connection connection, BankId bankId, int offset, int limit) throws SQLException {
        List<OperationRecord> records = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(selectHistory)) {
            select.setString(1, bankId.toString());
            select.setInt(2, limit);
            select.setInt(3, offset);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    records.add(read(rows));
                }
            }
        }
        return records;
    }

    /**
     * Lit tout le journal (migration).
     *
     * @param connection connexion
     * @param sink       recepteur de chaque entree
     * @throws SQLException erreur du pilote
     */
    void exportAll(Connection connection, Consumer<OperationRecord> sink) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(selectAll)) {
            select.setFetchSize(SqlContent.FETCH_SIZE);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    sink.accept(read(rows));
                }
            }
        }
    }

    /**
     * Enregistre des entrees telles quelles (migration vers une base vide).
     *
     * @param connection connexion en transaction
     * @param records    entrees
     * @throws SQLException erreur du pilote, dont un identifiant deja present
     */
    void importAll(Connection connection, List<OperationRecord> records) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            for (OperationRecord record : records) {
                statement.setString(1, record.operationId().toString());
                bindFields(statement, 2, record);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Journalise une operation nouvelle.
     *
     * @param connection connexion en transaction
     * @param record     entree
     * @throws WriteConflict si l'identifiant a ete journalise entre-temps
     * @throws SQLException  erreur du pilote
     */
    void insert(Connection connection, OperationRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, record.operationId().toString());
            bindFields(statement, 2, record);
            statement.executeUpdate();
        } catch (SQLException failure) {
            if (dialect.isUniqueViolation(failure)) {
                throw new WriteConflict("Operation " + record.operationId() + " journalisee entre-temps");
            }
            throw failure;
        }
    }

    /**
     * Remplace une entree existante.
     *
     * @param connection connexion en transaction
     * @param record     nouvelle entree
     * @throws SQLException erreur du pilote, ou entree absente
     */
    void update(Connection connection, OperationRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            int next = bindFields(statement, 1, record);
            statement.setString(next, record.operationId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException(ConsoleText.tr("Entree de journal absente : {0}", record.operationId()));
            }
        }
    }

    private int bindFields(PreparedStatement statement, int first, OperationRecord record) throws SQLException {
        int index = first;
        WithdrawalProgress withdrawal = record.withdrawal();
        statement.setString(index++, record.bankId().toString());
        statement.setString(index++, record.type().name());
        setNullableString(statement, index++, record.itemKey() == null ? null : record.itemKey().toString());
        statement.setLong(index++, record.quantity());
        statement.setLong(index++, record.countedQuantity());
        dialect.bindMoney(statement, index++, record.valueDelta());
        statement.setString(index++, record.state().name());
        statement.setLong(index++, record.timestamp().getEpochSecond());
        statement.setInt(index++, record.timestamp().getNano());
        setNullableString(statement, index++, withdrawal == null ? null : withdrawal.recipient().toString());
        setNullableLong(statement, index++, withdrawal == null ? null : withdrawal.reservedEpoch());
        setNullableLong(statement, index++, withdrawal == null ? null : withdrawal.deliveredQuantity());
        setNullableLong(statement, index++, withdrawal == null ? null : withdrawal.returnedQuantity());
        dialect.bindMoney(statement, index++, withdrawal == null ? null : withdrawal.returnedValue());
        return index;
    }

    private OperationRecord read(ResultSet rows) throws SQLException {
        String item = rows.getString("item_key");
        String recipient = rows.getString("recipient");
        WithdrawalProgress withdrawal = recipient == null ? null : new WithdrawalProgress(UUID.fromString(recipient),
                rows.getLong("reserved_epoch"), rows.getLong("delivered_quantity"), rows.getLong("returned_quantity"),
                dialect.readMoney(rows, "returned_value"));
        return new OperationRecord(
                new OperationId(UUID.fromString(rows.getString("operation_id"))),
                OperationType.valueOf(rows.getString("operation_type")),
                new BankId(UUID.fromString(rows.getString("bank_id"))),
                item == null ? null : ItemKey.parse(item),
                rows.getLong("quantity"),
                rows.getLong("counted_quantity"),
                dialect.readMoney(rows, "value_delta"),
                OperationState.valueOf(rows.getString("state")),
                Instant.ofEpochSecond(rows.getLong("occurred_at_sec"), rows.getInt("occurred_at_nano")),
                withdrawal);
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}
