package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.storage.sql.dialect.MariaDbDialect;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import dev.novadev.novabank.storage.sql.pool.PoolOptions;
import dev.novadev.novabank.storage.sql.pool.ServerConnection;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Echec de connexion a un serveur SQL, sans serveur : port ferme. Le message est clair,
 * nomme hote, port et base, et ne contient jamais le mot de passe.
 */
class ServerConnectionFailureTest {

    private static final String PASSWORD = "Tres$ecret-42";

    @Test
    void un_serveur_injoignable_donne_une_erreur_claire_sans_mot_de_passe() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ServerConnection connection = new ServerConnection("127.0.0.1", closedPort, "novabank", "novabank", PASSWORD,
                Map.of());

        assertThatThrownBy(() -> ConnectionPools.server("NovaBank-Test", connection, ServerDatabase.driver(),
                new MariaDbDialect(), new PoolOptions(2, 0, 1_000L, 30_000L)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Connexion impossible au serveur SQL 127.0.0.1:" + closedPort + "/novabank")
                .hasMessageNotContaining(PASSWORD)
                .hasNoCause();
    }
}
