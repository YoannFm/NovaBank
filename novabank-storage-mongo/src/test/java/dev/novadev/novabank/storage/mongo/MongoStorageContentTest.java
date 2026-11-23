package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContentContractTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;

/**
 * Contrat du contenu complet (migration) sur un vrai serveur MongoDB : une base neuve par backend.
 */
@RequiresMongoServer
class MongoStorageContentTest extends StorageContentContractTest {

    private final List<MongoTestServer> databases = new ArrayList<>();

    @Override
    protected StorageBackend newBackend() {
        MongoTestServer database = MongoTestServer.create();
        databases.add(database);
        return database.open();
    }

    @AfterEach
    void dropDatabases() {
        databases.forEach(MongoTestServer::close);
    }
}
