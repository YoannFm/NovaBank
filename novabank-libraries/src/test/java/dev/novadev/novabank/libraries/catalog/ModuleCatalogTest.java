package dev.novadev.novabank.libraries.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ModuleCatalogTest {

    @Test
    void seul_mongodb_est_un_module_embarque() {
        assertThat(ModuleCatalog.forStorage(StorageType.MONGODB)).contains(ModuleCatalog.MONGODB);
        for (StorageType type : new StorageType[] {StorageType.SQLITE, StorageType.MYSQL, StorageType.MARIADB,
                StorageType.YAML, StorageType.JSON}) {
            assertThat(ModuleCatalog.forStorage(type)).as(type.name()).isEmpty();
        }
    }

    @Test
    void mongodb_epingle_le_pilote_synchrone_et_ses_dependances_d_execution() {
        assertThat(ModuleCatalog.MONGODB.resource()).isEqualTo("backends/mongodb.jar");
        assertThat(ModuleCatalog.MONGODB.entryClassName()).isEqualTo("dev.novadev.novabank.storage.mongo.MongoBackendFactory");
        assertThat(ModuleCatalog.MONGODB.libraries()).extracting(LibraryArtifact::coordinates).containsExactly(
                "org.mongodb:mongodb-driver-sync:5.13.0", "org.mongodb:mongodb-driver-core:5.13.0",
                "org.mongodb:bson:5.13.0", "org.mongodb:bson-record-codec:5.13.0");
    }

    @Test
    void la_version_epinglee_est_celle_du_pom() throws Exception {
        Properties versions = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/novabank-versions.properties")) {
            assertThat(in).isNotNull();
            versions.load(in);
        }
        assertThat(ModuleCatalog.MONGODB_DRIVER_VERSION).isEqualTo(versions.getProperty("mongodb-driver.version"));
    }
}
