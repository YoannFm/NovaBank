package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.testing.ConcurrentRunner;
import dev.novadev.novabank.core.testing.TestBankIds;
import dev.novadev.novabank.storage.sql.dialect.MariaDbDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Migrations de schema sur un vrai serveur MySQL / MariaDB : base vide, base V1 avec donnees,
 * schema du futur, reprise d'une version interrompue (DDL non transactionnel) et migrateurs
 * concurrents (verrou {@code GET_LOCK}).
 */
@RequiresSqlServer
class ServerSchemaMigrationTest {

    private static final String PREFIX = "novabank_";
    private static final List<String> TABLES = List.of("schema_version", "banks", "stock_lines", "operations",
            "pending_deliveries", "active_banks");

    private ServerDatabase database;

    @BeforeEach
    void createDatabase() throws SQLException {
        database = ServerDatabase.create();
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        database.close();
    }

    @Test
    void une_base_vide_est_amenee_en_v2_avec_des_tables_innodb_utf8mb4_et_des_montants_decimaux()
            throws SQLException {
        try (SqlStorage storage = database.open(PREFIX)) {
            assertThat(storage.schemaVersion()).isEqualTo(SchemaMigrator.CURRENT_VERSION);
        }

        try (Connection connection = database.rawConnection()) {
            assertThat(versions(connection)).containsExactly(1, 2, 3);
            for (String table : TABLES) {
                assertThat(tableOptions(connection, PREFIX + table)).as(table)
                        .isEqualTo("InnoDB utf8mb4_bin");
            }
            assertThat(columnType(connection, "stock_lines", "accumulated_value")).isEqualTo("decimal(38,4)");
            assertThat(columnType(connection, "operations", "value_delta")).isEqualTo("decimal(38,4)");
            assertThat(columnType(connection, "banks", "bank_id")).isEqualTo("char(36)");
            assertThat(columnType(connection, "stock_lines", "quantity")).startsWith("bigint");
        }
    }

    @Test
    void une_base_v1_avec_donnees_est_migree_en_v2_sans_perte() throws SQLException {
        UUID player = UUID.randomUUID();
        OperationId delivery = OperationId.random();
        BankId bankId = BankId.forOwner(EntityRef.player(player));
        try (Connection connection = database.rawConnection()) {
            SchemaV2MigrationTest.createV1Database(connection, ServerDatabase.DIALECT, bankId, player, delivery);
        }

        try (SqlStorage storage = database.open(PREFIX)) {
            assertThat(storage.schemaVersion()).isEqualTo(3);
            assertThat(storage.banks().findBank(bankId)).hasValueSatisfying(bank ->
                    assertThat(bank.line(ItemKey.minecraft("stone")).quantity()).isEqualTo(64));
            assertThat(storage.deliveries().pendingFor(player))
                    .containsExactly(new PendingDelivery(delivery, player, ItemKey.minecraft("stone"), 12));
            assertThat(storage.deliveries().markDelivered(delivery, 12)).isTrue();
            storage.activeBanks().save(player, new EntityRef("island", "42"));
            assertThat(storage.activeBanks().find(player)).contains(new EntityRef("island", "42"));
        }
        try (Connection connection = database.rawConnection()) {
            assertThat(versions(connection)).containsExactly(1, 2, 3);
        }
    }

    @Test
    void une_base_d_un_schema_plus_recent_est_refusee_et_le_pool_ferme() throws SQLException {
        database.open(PREFIX).close();
        try (Connection connection = database.rawConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO novabank_schema_version (version, applied_at) VALUES (99, 0)");
        }
        HikariDataSource pool = database.pool();

        assertThatThrownBy(() -> database.open(pool, PREFIX))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("version 99")
                .hasMessageContaining("plus recent");
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    void une_v1_interrompue_au_milieu_est_reprise_au_demarrage_suivant() throws SQLException {
        List<String> v1 = SchemaScript.statements(ServerDatabase.DIALECT.name(), 1, PREFIX);
        try (Connection connection = database.rawConnection(); Statement statement = connection.createStatement()) {
            statement.execute(SchemaMigrator.versionTableDdl(PREFIX + "schema_version", ServerDatabase.DIALECT));
            statement.execute(v1.get(0));
            statement.execute(v1.get(1));
        }

        try (SqlStorage storage = database.open(PREFIX)) {
            assertThat(storage.schemaVersion()).isEqualTo(3);
            BankId bankId = TestBankIds.random();
            storage.banks().createBank(bankId, EntityRef.player(UUID.randomUUID()),
                    ValuationMode.DERIVED);
            assertThat(storage.banks().findBank(bankId)).isPresent();
        }
        try (Connection connection = database.rawConnection()) {
            assertThat(versions(connection)).containsExactly(1, 2, 3);
        }
    }

    @Test
    void une_v2_interrompue_apres_l_ajout_de_colonne_est_reprise_sans_erreur() throws SQLException {
        try (Connection connection = database.rawConnection(); Statement statement = connection.createStatement()) {
            statement.execute(SchemaMigrator.versionTableDdl(PREFIX + "schema_version", ServerDatabase.DIALECT));
            for (String sql : SchemaScript.statements(ServerDatabase.DIALECT.name(), 1, PREFIX)) {
                statement.execute(sql);
            }
            statement.executeUpdate("INSERT INTO novabank_schema_version (version, applied_at) VALUES (1, 0)");
            // Premiere instruction de V2 (ALTER TABLE ... ADD COLUMN), puis "arret brutal".
            statement.execute(SchemaScript.statements(ServerDatabase.DIALECT.name(), 2, PREFIX).get(0));
        }

        try (SqlStorage storage = database.open(PREFIX)) {
            assertThat(storage.schemaVersion()).isEqualTo(3);
            UUID player = UUID.randomUUID();
            storage.activeBanks().save(player, EntityRef.player(player));
            assertThat(storage.activeBanks().find(player)).contains(EntityRef.player(player));
        }
    }

    @RepeatedTest(3)
    void quatre_serveurs_qui_demarrent_ensemble_ne_migrent_qu_une_fois() throws Exception {
        int servers = 4;
        String prefix = "m" + UUID.randomUUID().toString().substring(0, 8) + "_";
        CyclicBarrier start = new CyclicBarrier(servers);
        List<Integer> versions = Collections.synchronizedList(new ArrayList<>());

        ConcurrentRunner.run(servers, server -> {
            HikariDataSource pool = database.pool();
            start.await();
            try (SqlStorage storage = database.open(pool, prefix)) {
                versions.add(storage.schemaVersion());
            }
        });

        assertThat(versions).hasSize(servers).allMatch(version -> version == SchemaMigrator.CURRENT_VERSION);
        try (Connection connection = database.rawConnection()) {
            assertThat(versions(connection, prefix)).containsExactly(1, 2, 3);
        }
    }

    @Test
    void la_migration_attend_le_verrou_tenu_par_un_autre_serveur() throws Exception {
        try (Connection other = database.rawConnection()) {
            assertThat(lock(other, PREFIX + "migration")).isEqualTo(1);
            CompletableFuture<Integer> opening = CompletableFuture.supplyAsync(() -> {
                try (SqlStorage storage = database.open(PREFIX)) {
                    return storage.schemaVersion();
                }
            });

            TimeUnit.MILLISECONDS.sleep(1_500);
            assertThat(opening).isNotDone();
            try (Connection check = database.rawConnection()) {
                assertThat(tableExists(check, PREFIX + "banks")).isFalse();
            }

            release(other, PREFIX + "migration");
            assertThat(opening.get(30, TimeUnit.SECONDS)).isEqualTo(SchemaMigrator.CURRENT_VERSION);
        }
    }

    @Test
    void un_verrou_de_migration_jamais_libere_donne_une_erreur_claire() throws Exception {
        MariaDbDialect impatient = new MariaDbDialect(MariaDbDialect.DEFAULT_LOCK_WAIT_TIMEOUT, Duration.ofSeconds(1));
        try (Connection other = database.rawConnection()) {
            lock(other, PREFIX + "migration");
            HikariDataSource pool = database.pool(impatient, ServerDatabase.POOL);

            assertThatThrownBy(() -> database.open(pool, impatient, PREFIX))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Verrou de migration")
                    .hasMessageContaining("non obtenu en 1 s");
            assertThat(pool.isClosed()).isTrue();
        }
    }

    private static int lock(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static void release(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, name);
            statement.executeQuery().close();
        }
    }

    private static List<Integer> versions(Connection connection) throws SQLException {
        return versions(connection, PREFIX);
    }

    private static List<Integer> versions(Connection connection, String prefix) throws SQLException {
        return SchemaMigrationTest.versions(connection, prefix);
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) == 1;
            }
        }
    }

    private static String tableOptions(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT ENGINE, TABLE_COLLATION"
                + " FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) + " " + rows.getString(2) : "absente";
            }
        }
    }

    private static String columnType(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COLUMN_TYPE FROM"
                + " information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            statement.setString(1, PREFIX + table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1).toLowerCase(Locale.ROOT) : "absente";
            }
        }
    }
}
