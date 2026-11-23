package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.testing.TestBankIds;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SchemaMigrationTest {

    @TempDir
    Path directory;

    @Test
    void une_base_vide_est_amenee_a_la_version_courante() throws SQLException {
        Path file = directory.resolve("empty.db");

        try (SqlStorage storage = SqliteTestSupport.open(file)) {
            assertThat(storage.schemaVersion()).isEqualTo(SchemaMigrator.CURRENT_VERSION);
        }

        try (Connection connection = SqliteTestSupport.rawConnection(file)) {
            assertThat(versions(connection, "novabank_")).containsExactly(1, 2, 3);
            assertThat(tables(connection)).contains("novabank_schema_version", "novabank_banks",
                    "novabank_stock_lines", "novabank_operations", "novabank_pending_deliveries",
                    "novabank_active_banks");
        }
    }

    @Test
    void la_verification_de_sante_relit_la_version_dans_la_base_et_echoue_une_fois_ferme() {
        SqlStorage storage = SqliteTestSupport.open(directory.resolve("health.db"));
        assertThat(storage.checkSchemaVersion()).isEqualTo(SchemaMigrator.CURRENT_VERSION);

        storage.close();

        assertThatThrownBy(storage::checkSchemaVersion).isInstanceOf(StorageException.class);
    }

    @Test
    void relancer_la_migration_est_sans_effet_et_conserve_les_donnees() throws SQLException {
        Path file = directory.resolve("replay.db");
        BankId bankId = TestBankIds.random();
        try (SqlStorage storage = SqliteTestSupport.open(file)) {
            storage.banks().createBank(bankId, new EntityRef("island", "42"), ValuationMode.DERIVED);
        }

        for (int i = 0; i < 2; i++) {
            try (SqlStorage storage = SqliteTestSupport.open(file)) {
                assertThat(storage.schemaVersion()).isEqualTo(SchemaMigrator.CURRENT_VERSION);
                assertThat(storage.banks().findBank(bankId)).isPresent();
            }
        }

        try (Connection connection = SqliteTestSupport.rawConnection(file)) {
            assertThat(versions(connection, "novabank_")).containsExactly(1, 2, 3);
        }
    }

    @Test
    void une_base_d_un_schema_plus_recent_est_refusee_et_le_pool_ferme() throws SQLException {
        Path file = directory.resolve("future.db");
        SqliteTestSupport.open(file).close();
        try (Connection connection = SqliteTestSupport.rawConnection(file);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO novabank_schema_version (version, applied_at) VALUES (99, 0)");
        }
        HikariDataSource pool = SqliteTestSupport.pool(file);

        assertThatThrownBy(() -> SqliteTestSupport.open(pool))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("version 99")
                .hasMessageContaining("plus recent");
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    void deux_prefixes_cohabitent_dans_la_meme_base() throws SQLException {
        Path file = directory.resolve("shared.db");
        BankId bankId = TestBankIds.random();
        try (SqlStorage main = SqliteTestSupport.open(file);
             SqlStorage other = SqlStorage.open(SqliteTestSupport.pool(file), SqliteTestSupport.DIALECT, "shop_",
                     Clock.systemUTC())) {
            main.banks().createBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);

            assertThat(other.banks().findBank(bankId)).isEmpty();
            assertThat(main.banks().findBank(bankId)).isPresent();
        }
        try (Connection connection = SqliteTestSupport.rawConnection(file)) {
            assertThat(versions(connection, "shop_")).containsExactly(1, 2, 3);
            assertThat(tables(connection)).contains("shop_banks", "novabank_banks");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1abc", "Bad_prefix", "nova-bank_", "x; DROP TABLE y", "abcdefghijklmnopqrstuvwxyz1234567"})
    void un_prefixe_invalide_est_refuse_et_le_pool_ferme(String prefix) {
        HikariDataSource pool = SqliteTestSupport.pool(directory.resolve("prefix.db"));

        assertThatThrownBy(() -> SqlStorage.open(pool, SqliteTestSupport.DIALECT, prefix, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prefixe de tables invalide");
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    void un_script_de_migration_absent_est_signale() {
        SqlDialect unknownEngine = (SqlDialect) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {SqlDialect.class}, (proxy, method, args) -> method.getName().equals("name")
                        ? "absent"
                        : method.invoke(SqliteTestSupport.DIALECT, args));

        assertThatThrownBy(() -> SqlStorage.open(SqliteTestSupport.pool(directory.resolve("absent.db")),
                unknownEngine, SqlStorage.DEFAULT_TABLE_PREFIX, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema/absent/V1.sql");
    }

    static List<Integer> versions(Connection connection, String prefix) throws SQLException {
        List<Integer> versions = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM " + prefix + "schema_version ORDER BY version")) {
            while (rows.next()) {
                versions.add(rows.getInt(1));
            }
        }
        return versions;
    }

    static List<String> tables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        return tables;
    }
}
