package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.storage.sql.dialect.MariaDbDialect;
import dev.novadev.novabank.storage.sql.pool.ServerConnection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Dialecte MySQL / MariaDB sans serveur : SQL emis, URL, reglages et classement des erreurs.
 * Le comportement reel est verifie par les tests {@link RequiresSqlServer}.
 */
class MariaDbDialectTest {

    private final MariaDbDialect dialect = new MariaDbDialect();

    @Test
    void l_url_ne_contient_que_hote_port_et_base() {
        assertThat(dialect.jdbcUrl("db.example.org", 3306, "novabank")).isEqualTo("jdbc:mariadb://db.example.org:3306/novabank");
        assertThat(dialect.jdbcUrl("127.0.0.1", 33061, "nb_1")).isEqualTo("jdbc:mariadb://127.0.0.1:33061/nb_1");
        assertThat(dialect.jdbcUrl("::1", 3306, "novabank")).isEqualTo("jdbc:mariadb://[::1]:3306/novabank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "host/evil", "host?user=root", "a@b", "h,h2", "host:3306", "-host", "h&x=1"})
    void un_hote_qui_modifierait_l_url_est_refuse(String host) {
        assertThatThrownBy(() -> dialect.jdbcUrl(host, 3306, "novabank"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hote SQL invalide");
    }

    @Test
    void un_port_ou_une_base_invalides_sont_refuses() {
        assertThatThrownBy(() -> dialect.jdbcUrl("localhost", 0, "novabank")).hasMessageContaining("Port SQL invalide");
        assertThatThrownBy(() -> dialect.jdbcUrl("localhost", 3306, "nova?bank"))
                .hasMessageContaining("Nom de base SQL invalide");
    }

    @Test
    void les_reglages_de_session_fixent_isolation_mode_strict_et_attente_de_verrou() {
        assertThat(dialect.name()).isEqualTo("mysql");
        assertThat(dialect.singleWriter()).isFalse();
        assertThat(dialect.lockRowsSuffix()).isEqualTo(" FOR UPDATE");
        assertThat(dialect.tableOptions()).contains("ENGINE=InnoDB").contains("utf8mb4").contains("utf8mb4_bin");
        assertThat(dialect.connectionProperties()).containsEntry("useAffectedRows", "false");
        assertThat(dialect.connectionInitStatements()).containsExactly(
                "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED",
                "SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'",
                "SET SESSION innodb_lock_wait_timeout = 10");
    }

    @Test
    void les_insertions_utilisent_on_duplicate_key_update() {
        assertThat(dialect.insertIgnoringDuplicate("t", List.of("a", "b")))
                .isEqualTo("INSERT INTO t (a, b) VALUES (?, ?) ON DUPLICATE KEY UPDATE a = a");
        assertThat(dialect.upsert("t", List.of("k", "a", "b"), List.of("k")))
                .isEqualTo("INSERT INTO t (k, a, b) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE a = VALUES(a), b = VALUES(b)");
    }

    @Test
    void les_erreurs_sont_classees_par_leur_code_mysql() {
        assertThat(dialect.isUniqueViolation(error(1062))).isTrue();
        assertThat(dialect.isUniqueViolation(error(1586))).isTrue();
        assertThat(dialect.isUniqueViolation(error(1213))).isFalse();
        assertThat(dialect.isRetryable(error(1213))).isTrue();
        assertThat(dialect.isRetryable(error(1205))).isTrue();
        assertThat(dialect.isRetryable(error(1062))).isFalse();
        assertThat(dialect.isAlreadyApplied(error(1060))).isTrue();
        assertThat(dialect.isAlreadyApplied(error(1061))).isTrue();
        assertThat(dialect.isAlreadyApplied(error(1050))).isTrue();
        assertThat(dialect.isAlreadyApplied(error(1146))).isFalse();
    }

    @Test
    void un_echec_rsa_de_mysql_8_recoit_un_conseil_de_configuration() {
        assertThat(dialect.connectionFailureHint("RSA public key is not available client side"))
                .hasValueSatisfying(hint -> assertThat(hint).contains("sslMode").contains("allowPublicKeyRetrieval"));
        assertThat(dialect.connectionFailureHint("Connection refused")).isEmpty();
        assertThat(dialect.connectionFailureHint(null)).isEmpty();
    }

    @Test
    void des_attentes_inferieures_a_une_seconde_sont_refusees() {
        assertThatThrownBy(() -> new MariaDbDialect(Duration.ofMillis(500), Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MariaDbDialect(Duration.ofSeconds(5), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void le_mot_de_passe_n_apparait_ni_dans_la_description_ni_dans_les_messages_masques() {
        ServerConnection connection = new ServerConnection("db", 3306, "novabank", "nova", "S3cret!pass",
                Map.of("sslMode", "verify-full"));

        assertThat(connection.toString()).doesNotContain("S3cret!pass").contains("nova@db:3306/novabank");
        assertThat(connection.describe()).isEqualTo("db:3306/novabank");
        assertThat(connection.mask("Access denied (S3cret!pass)")).isEqualTo("Access denied (****)");
        assertThat(new ServerConnection("db", 3306, "n", "u", "", Map.of()).mask("texte")).isEqualTo("texte");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "password", "PASSWORD"})
    void les_identifiants_sont_refuses_dans_les_options(String key) {
        assertThatThrownBy(() -> new ServerConnection("db", 3306, "n", "u", "p", Map.of(key, "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage.sql.username");
    }

    private static SQLException error(int code) {
        return new SQLException("erreur " + code, "HY000", code);
    }
}
