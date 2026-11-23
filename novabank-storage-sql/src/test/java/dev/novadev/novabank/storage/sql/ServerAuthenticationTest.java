package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import dev.novadev.novabank.storage.sql.pool.ServerConnection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Authentification reelle : un mauvais mot de passe donne une erreur claire et masquee ; sous
 * MySQL 8.4 sans SSL ni recuperation de cle publique, le message conseille la configuration.
 */
@RequiresSqlServer
class ServerAuthenticationTest {

    private static final String WRONG_PASSWORD = "Mauvais-Mot2Passe";

    private ServerDatabase database;

    @BeforeEach
    void create() throws SQLException {
        database = ServerDatabase.create();
    }

    @AfterEach
    void drop() throws SQLException {
        database.close();
    }

    @Test
    void un_mauvais_mot_de_passe_est_refuse_sans_apparaitre_dans_le_message() {
        ServerConnection valid = database.connection(ServerDatabase.productOptions());
        ServerConnection wrong = new ServerConnection(valid.host(), valid.port(), valid.database(), valid.user(),
                WRONG_PASSWORD, valid.options());

        assertThatThrownBy(() -> ConnectionPools.server("NovaBank-IT", wrong, ServerDatabase.driver(),
                ServerDatabase.DIALECT, ServerDatabase.POOL))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(valid.describe())
                .hasMessageContaining("Access denied")
                .hasMessageNotContaining(WRONG_PASSWORD);
    }

    @Test
    @EnabledIf("isMySql")
    void mysql_sans_ssl_ni_cle_publique_recoit_un_conseil_de_configuration() throws Exception {
        // La cle de cache de caching_sha2_password est videe : authentification complete exigee.
        ServerDatabase.control("stop");
        ServerDatabase.control("start");

        assertThatThrownBy(() -> ConnectionPools.server("NovaBank-IT", database.connection(Map.of()),
                ServerDatabase.driver(), ServerDatabase.DIALECT, ServerDatabase.POOL))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("RSA public key")
                .hasMessageContaining("sslMode");
        try (var pool = ConnectionPools.server("NovaBank-IT", database.connection(Map.of("allowPublicKeyRetrieval",
                "true")), ServerDatabase.driver(), ServerDatabase.DIALECT, ServerDatabase.POOL)) {
            assertThat(pool.isRunning()).isTrue();
        }
    }

    static boolean isMySql() {
        return "mysql".equals(ServerDatabase.product()) && ServerDatabase.controllable();
    }
}
