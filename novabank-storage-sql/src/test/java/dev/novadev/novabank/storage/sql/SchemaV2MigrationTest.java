package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration V1 -> V2 d'une base V1 existante, contenant deja des donnees : les donnees
 * sont conservees, les livraisons gardent leur quantite due, la table des banques actives
 * est utilisable.
 */
class SchemaV2MigrationTest {

    @TempDir
    Path directory;

    @Test
    void une_base_v1_avec_donnees_est_migree_jusqu_a_la_version_courante_sans_perte() throws Exception {
        Path file = directory.resolve("v1.db");
        UUID player = UUID.randomUUID();
        OperationId delivery = OperationId.random();
        BankId bankId = BankId.forOwner(EntityRef.player(player));
        try (Connection connection = SqliteTestSupport.rawConnection(file)) {
            createV1Database(connection, SqliteTestSupport.DIALECT, bankId, player, delivery);
        }

        try (SqlStorage storage = SqliteTestSupport.open(file)) {
            assertThat(storage.schemaVersion()).isEqualTo(3);
            assertThat(storage.banks().findBank(bankId)).hasValueSatisfying(bank ->
                    assertThat(bank.line(ItemKey.minecraft("stone")).quantity()).isEqualTo(64));
            assertThat(storage.deliveries().pendingFor(player))
                    .containsExactly(new PendingDelivery(delivery, player, ItemKey.minecraft("stone"), 12));

            assertThat(storage.deliveries().markDelivered(delivery, 12)).isTrue();
            assertThat(storage.deliveries().pendingFor(player)).isEmpty();
            storage.activeBanks().save(player, new EntityRef("island", "42"));
            assertThat(storage.activeBanks().find(player)).contains(new EntityRef("island", "42"));
        }
        try (Connection connection = SqliteTestSupport.rawConnection(file)) {
            assertThat(SchemaMigrationTest.versions(connection, "novabank_")).containsExactly(1, 2, 3);
            assertThat(SchemaMigrationTest.tables(connection)).contains("novabank_active_banks");
        }
    }

    /**
     * Cree une base au schema V1 (prefixe {@code novabank_}) contenant une banque, une ligne de
     * stock de 64 pierres et une livraison en attente de 12 pierres.
     */
    static void createV1Database(Connection connection, SqlDialect dialect, BankId bankId, UUID player,
                                 OperationId delivery) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SchemaMigrator.versionTableDdl("novabank_schema_version", dialect));
            for (String sql : SchemaScript.statements(dialect.name(), 1, "novabank_")) {
                statement.execute(sql);
            }
            statement.executeUpdate("INSERT INTO novabank_schema_version (version, applied_at) VALUES (1, 0)");
            try (PreparedStatement bank = connection.prepareStatement("INSERT INTO novabank_banks VALUES"
                    + " (?, 'player', ?, 'DERIVED', 1, 0, 0, 0)")) {
                bank.setString(1, bankId.toString());
                bank.setString(2, player.toString());
                bank.executeUpdate();
            }
            try (PreparedStatement line = connection.prepareStatement("INSERT INTO novabank_stock_lines VALUES"
                    + " (?, 'minecraft:stone', 64, 0, '0.0000')")) {
                line.setString(1, bankId.toString());
                line.executeUpdate();
            }
            try (PreparedStatement pending = connection.prepareStatement("INSERT INTO novabank_pending_deliveries"
                    + " VALUES (?, ?, 'minecraft:stone', 12, 0)")) {
                pending.setString(1, delivery.toString());
                pending.setString(2, player.toString());
                pending.executeUpdate();
            }
        }
    }
}
