package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.testing.TestBankIds;
import dev.novadev.novabank.storage.sql.dialect.SqliteDialect;
import dev.novadev.novabank.storage.sql.pool.DriverDataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDialectTest {

    @TempDir
    Path directory;

    @Test
    void chaque_connexion_du_pool_recoit_les_reglages_de_session() throws SQLException {
        try (HikariDataSource pool = SqliteTestSupport.pool(directory.resolve("pragmas.db"));
             Connection connection = pool.getConnection()) {
            assertThat(JournalInvariants.single(connection, "PRAGMA journal_mode")).isEqualTo("wal");
            assertThat(JournalInvariants.single(connection, "PRAGMA synchronous")).isEqualTo("2");
            assertThat(JournalInvariants.single(connection, "PRAGMA foreign_keys")).isEqualTo("1");
            assertThat(JournalInvariants.single(connection, "PRAGMA busy_timeout")).isEqualTo("10000");
        }
    }

    @Test
    void une_transaction_prend_le_verrou_d_ecriture_des_son_ouverture() throws SQLException {
        Path file = directory.resolve("immediate.db");
        DriverDataSource source = SqliteTestSupport.unpooled(file, new SqliteDialect(Duration.ofMillis(100)));
        try (Connection setup = source.getConnection(); Statement statement = setup.createStatement()) {
            statement.execute("CREATE TABLE t (x INTEGER)");
        }
        try (Connection holder = source.getConnection(); Connection other = source.getConnection();
             Statement write = other.createStatement()) {
            holder.setAutoCommit(false);

            assertThatThrownBy(() -> write.executeUpdate("INSERT INTO t VALUES (1)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("SQLITE_BUSY");

            holder.rollback();
            holder.setAutoCommit(true);
            assertThat(write.executeUpdate("INSERT INTO t VALUES (2)")).isEqualTo(1);
        }
    }

    @Test
    void seules_les_violations_d_unicite_sont_reconnues() throws SQLException {
        SqliteDialect dialect = SqliteTestSupport.DIALECT;
        try (Connection connection = SqliteTestSupport.rawConnection(directory.resolve("errors.db"));
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE p (id INTEGER PRIMARY KEY, u TEXT UNIQUE, c INTEGER CHECK (c > 0),"
                    + " f INTEGER REFERENCES p (id))");
            statement.executeUpdate("INSERT INTO p VALUES (1, 'a', 1, NULL)");

            assertThat(dialect.isUniqueViolation(failure(statement, "INSERT INTO p VALUES (1, 'b', 1, NULL)"))).isTrue();
            assertThat(dialect.isUniqueViolation(failure(statement, "INSERT INTO p VALUES (2, 'a', 1, NULL)"))).isTrue();
            assertThat(dialect.isUniqueViolation(failure(statement, "INSERT INTO p VALUES (3, 'c', 0, NULL)"))).isFalse();
            assertThat(dialect.isUniqueViolation(failure(statement, "INSERT INTO p VALUES (4, 'd', 1, 99)"))).isFalse();
            assertThat(dialect.isUniqueViolation(failure(statement, "INSERT INTO absent VALUES (1)"))).isFalse();
        }
    }

    @Test
    void les_montants_sont_stockes_en_texte_exact() throws SQLException {
        Path file = directory.resolve("money.db");
        BankId bankId = TestBankIds.random();
        ItemKey gold = ItemKey.minecraft("gold_block");
        OperationId id = OperationId.random();
        try (SqlStorage storage = SqliteTestSupport.open(file)) {
            storage.banks().createBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);
            storage.banks().apply(id, bankId, bank -> MutationDecision.accept(
                    bank.withLine(gold, new StockLine(1L, 0L, new BigDecimal("123456789012345.6789"))),
                    OperationRecord.deposit(id, bankId, gold, 1L, 1L, new BigDecimal("0.0001"), Instant.EPOCH)));
        }
        try (Connection connection = SqliteTestSupport.rawConnection(file);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT typeof(l.accumulated_value), l.accumulated_value,"
                     + " typeof(o.value_delta), o.value_delta FROM novabank_stock_lines l, novabank_operations o")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("text");
            assertThat(rows.getString(2)).isEqualTo("123456789012345.6789");
            assertThat(rows.getString(3)).isEqualTo("text");
            assertThat(rows.getString(4)).isEqualTo("0.0001");
        }
    }

    @Test
    void les_instructions_generees_ciblent_la_cle() {
        SqliteDialect dialect = SqliteTestSupport.DIALECT;

        assertThat(dialect.upsert("t", List.of("k", "a", "b"), List.of("k"))).isEqualTo(
                "INSERT INTO t (k, a, b) VALUES (?, ?, ?) ON CONFLICT (k) DO UPDATE SET a = excluded.a, b = excluded.b");
        assertThat(dialect.insertIgnoringDuplicate("t", List.of("k", "a")))
                .isEqualTo("INSERT INTO t (k, a) VALUES (?, ?) ON CONFLICT DO NOTHING");
        assertThat(dialect.lockRowsSuffix()).isEmpty();
        assertThatThrownBy(() -> new SqliteDialect(Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void la_source_sans_pool_signale_proprement_ses_limites() throws SQLException {
        DriverDataSource source = SqliteTestSupport.unpooled(directory.resolve("source.db"), SqliteTestSupport.DIALECT);
        DriverDataSource wrongUrl = new DriverDataSource(SqliteTestSupport.driver(), "jdbc:other:x", new Properties(),
                List.of());
        DriverDataSource badInit = new DriverDataSource(SqliteTestSupport.driver(),
                SqliteTestSupport.DIALECT.jdbcUrl(directory.resolve("init.db")), new Properties(), List.of("NOT SQL"));

        source.setLoginTimeout(7);
        source.setLogWriter(null);
        assertThat(source.getLoginTimeout()).isEqualTo(7);
        assertThat(source.getLogWriter()).isNull();
        assertThat(source.isWrapperFor(DriverDataSource.class)).isTrue();
        assertThat(source.unwrap(DriverDataSource.class)).isSameAs(source);
        assertThatThrownBy(() -> source.unwrap(String.class)).isInstanceOf(SQLException.class);
        assertThatThrownBy(source::getParentLogger).isInstanceOf(SQLFeatureNotSupportedException.class);
        assertThatThrownBy(() -> source.getConnection("u", "p")).isInstanceOf(SQLFeatureNotSupportedException.class);
        assertThatThrownBy(wrongUrl::getConnection).isInstanceOf(SQLException.class).hasMessageContaining("jdbc:other:x");
        assertThatThrownBy(badInit::getConnection).isInstanceOf(SQLException.class);
    }

    private static SQLException failure(Statement statement, String sql) {
        try {
            statement.executeUpdate(sql);
        } catch (SQLException failure) {
            return failure;
        }
        throw new AssertionError("Erreur attendue : " + sql);
    }
}
