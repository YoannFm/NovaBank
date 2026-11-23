package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@link ActiveBankStore} JDBC : une ligne par joueur, remplacee a chaque choix (upsert).
 */
final class SqlActiveBankStore implements ActiveBankStore {

    private static final List<String> COLUMNS = List.of("player_id", "entity_type", "entity_id", "updated_at");

    private final SqlTransactions transactions;
    private final Clock clock;
    private final String select;
    private final String upsert;
    private final String selectAll;

    SqlActiveBankStore(SqlTransactions transactions, TableNames tables, SqlDialect dialect, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
        this.select = "SELECT entity_type, entity_id FROM " + tables.activeBanks() + " WHERE player_id = ?";
        this.upsert = dialect.upsert(tables.activeBanks(), COLUMNS, List.of("player_id"));
        this.selectAll = "SELECT player_id, entity_type, entity_id FROM " + tables.activeBanks();
    }

    /**
     * Lit tous les choix (migration).
     *
     * @param sink recepteur de chaque choix
     */
    void exportAll(Consumer<ActiveBankChoice> sink) {
        transactions.read("lecture des banques actives", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(selectAll)) {
                statement.setFetchSize(SqlContent.FETCH_SIZE);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        sink.accept(new ActiveBankChoice(UUID.fromString(rows.getString("player_id")),
                                new EntityRef(rows.getString("entity_type"), rows.getString("entity_id"))));
                    }
                }
            }
            return null;
        });
    }

    /**
     * Enregistre des choix (migration vers une base vide).
     *
     * @param choices choix
     */
    void importAll(List<ActiveBankChoice> choices) {
        transactions.write("import de " + choices.size() + " banque(s) active(s)", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                for (ActiveBankChoice choice : choices) {
                    statement.setString(1, choice.playerId().toString());
                    statement.setString(2, choice.entity().type());
                    statement.setString(3, choice.entity().id());
                    statement.setLong(4, clock.millis());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    @Override
    public Optional<EntityRef> find(UUID playerId) {
        return transactions.read("banque active du joueur " + playerId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(select)) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next()
                            ? Optional.of(new EntityRef(rows.getString("entity_type"), rows.getString("entity_id")))
                            : Optional.<EntityRef>empty();
                }
            }
        });
    }

    @Override
    public void save(UUID playerId, EntityRef entity) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(entity, "entity");
        transactions.write("choix de banque active du joueur " + playerId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, entity.type());
                statement.setString(3, entity.id());
                statement.setLong(4, clock.millis());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
