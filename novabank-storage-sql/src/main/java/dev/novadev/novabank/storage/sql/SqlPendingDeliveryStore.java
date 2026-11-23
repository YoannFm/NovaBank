package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@link PendingDeliveryStore} JDBC, idempotent par operation d'origine (cle primaire).
 *
 * <p>La quantite remise est cumulee dans {@code delivered_quantity} par une mise a jour
 * conditionnelle (jamais au-dela du du) ; la ligne d'une livraison entierement remise est
 * conservee pour que l'ajout reste idempotent.
 */
final class SqlPendingDeliveryStore implements PendingDeliveryStore {

    private static final List<String> COLUMNS = List.of("operation_id", "player_id", "item_key", "quantity",
            "created_at");

    private final SqlTransactions transactions;
    private final Clock clock;
    private final String insert;
    private final String selectById;
    private final String selectByPlayer;
    private final String deliver;
    private final String selectAll;
    private final String importRow;

    SqlPendingDeliveryStore(SqlTransactions transactions, TableNames tables, SqlDialect dialect, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
        this.insert = dialect.insertIgnoringDuplicate(tables.pendingDeliveries(), COLUMNS);
        String select = "SELECT operation_id, player_id, item_key, quantity - delivered_quantity AS remaining FROM "
                + tables.pendingDeliveries();
        this.selectById = select + " WHERE operation_id = ? AND quantity > delivered_quantity";
        this.selectByPlayer = select + " WHERE player_id = ? AND quantity > delivered_quantity"
                + " ORDER BY created_at, operation_id";
        this.deliver = "UPDATE " + tables.pendingDeliveries() + " SET delivered_quantity = delivered_quantity + ?"
                + " WHERE operation_id = ? AND quantity - delivered_quantity >= ?";
        this.selectAll = "SELECT operation_id, player_id, item_key, quantity, delivered_quantity, created_at FROM "
                + tables.pendingDeliveries() + " ORDER BY created_at, operation_id";
        List<String> importColumns = new ArrayList<>(COLUMNS);
        importColumns.add("delivered_quantity");
        this.importRow = SqlDialect.insert(tables.pendingDeliveries(), importColumns);
    }

    /**
     * Lit toutes les livraisons, remises ou non, par ordre d'enregistrement (migration).
     *
     * @param sink recepteur de chaque livraison
     */
    void exportAll(Consumer<StoredDelivery> sink) {
        transactions.read("lecture des livraisons", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(selectAll)) {
                statement.setFetchSize(SqlContent.FETCH_SIZE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        sink.accept(new StoredDelivery(new PendingDelivery(
                                new OperationId(UUID.fromString(rows.getString("operation_id"))),
                                UUID.fromString(rows.getString("player_id")), ItemKey.parse(rows.getString("item_key")),
                                rows.getLong("quantity")), rows.getLong("delivered_quantity"),
                                Instant.ofEpochMilli(rows.getLong("created_at"))));
                    }
                }
            }
            return null;
        });
    }

    /**
     * Enregistre des livraisons telles quelles (migration vers une base vide).
     *
     * @param deliveries livraisons, date d'enregistrement et quantite remise comprises
     */
    void importAll(List<StoredDelivery> deliveries) {
        transactions.write("import de " + deliveries.size() + " livraison(s)", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(importRow)) {
                for (StoredDelivery stored : deliveries) {
                    PendingDelivery delivery = stored.delivery();
                    statement.setString(1, delivery.operationId().toString());
                    statement.setString(2, delivery.playerId().toString());
                    statement.setString(3, delivery.itemKey().toString());
                    statement.setLong(4, delivery.quantity());
                    statement.setLong(5, stored.createdAt().toEpochMilli());
                    statement.setLong(6, stored.deliveredQuantity());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    @Override
    public void add(PendingDelivery delivery) {
        transactions.write("ajout de la livraison " + delivery.operationId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, delivery.operationId().toString());
                statement.setString(2, delivery.playerId().toString());
                statement.setString(3, delivery.itemKey().toString());
                statement.setLong(4, delivery.quantity());
                statement.setLong(5, clock.millis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<PendingDelivery> find(OperationId operationId) {
        return transactions.read("lecture de la livraison " + operationId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(selectById)) {
                statement.setString(1, operationId.toString());
                List<PendingDelivery> found = readAll(statement);
                return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
            }
        });
    }

    @Override
    public List<PendingDelivery> pendingFor(UUID playerId) {
        return transactions.read("livraisons du joueur " + playerId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(selectByPlayer)) {
                statement.setString(1, playerId.toString());
                return readAll(statement);
            }
        });
    }

    @Override
    public boolean markDelivered(OperationId operationId, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Quantite remise non positive : {0}", quantity));
        }
        return transactions.write("remise de la livraison " + operationId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(deliver)) {
                statement.setLong(1, quantity);
                statement.setString(2, operationId.toString());
                statement.setLong(3, quantity);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private static List<PendingDelivery> readAll(PreparedStatement statement) throws SQLException {
        List<PendingDelivery> deliveries = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                deliveries.add(new PendingDelivery(new OperationId(UUID.fromString(rows.getString("operation_id"))),
                        UUID.fromString(rows.getString("player_id")), ItemKey.parse(rows.getString("item_key")),
                        rows.getLong("remaining")));
            }
        }
        return deliveries;
    }
}
