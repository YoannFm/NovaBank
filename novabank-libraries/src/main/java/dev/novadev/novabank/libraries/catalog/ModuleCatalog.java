package dev.novadev.novabank.libraries.catalog;

import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import dev.novadev.novabank.libraries.module.EmbeddedModule;
import java.util.List;
import java.util.Optional;

/**
 * Modules embarques connus (decision D18) et leurs pilotes epingles dans le code, comme
 * {@link DriverCatalog} : la configuration ne peut jamais faire charger un jar arbitraire.
 */
public final class ModuleCatalog {

    /** Version du pilote MongoDB (egale a la propriete {@code mongodb-driver.version} du POM parent). */
    public static final String MONGODB_DRIVER_VERSION = "5.13.0";

    /**
     * Backend MongoDB : module {@code novabank-storage-mongo} et pilote synchrone officiel
     * (Apache 2.0) avec ses dependances d'execution non optionnelles (verifiees dans les POM
     * 5.13.0 : driver-sync -> driver-core + bson ; driver-core -> bson-record-codec en runtime).
     * Sommes SHA-256 calculees le 2026-10-27 sur Maven Central, recoupees avec les .sha1 de
     * Central et avec le miroir Google de Central (Central ne publie pas de .sha256 pour ces
     * artefacts).
     */
    public static final EmbeddedModule MONGODB = new EmbeddedModule("mongodb", "backends/mongodb.jar",
            "dev.novadev.novabank.storage.mongo.MongoBackendFactory",
            List.of(mongo("mongodb-driver-sync", "b1042221132552cdf56f95bcd61d524b745923d7bc3ace1aaa882a7fd51fb94e",
                            179_578L),
                    mongo("mongodb-driver-core", "44f0bc54cba2ef1cbfff8ad5f84e0f1bfccf4886661a93b0e4882ed9c4fdb81d",
                            2_068_639L),
                    mongo("bson", "51f583ee96fda9bc0897e73321c5ae1485102bfd8f95d0d34d7d57919cbc650a", 531_686L),
                    mongo("bson-record-codec", "0f6aca2c3c4b3c63e0c1fea65147c51af02be5d8d940f298abb3dc0906ca1ba0",
                            11_802L)));

    private ModuleCatalog() {
    }

    /**
     * @param type backend de stockage configure
     * @return le module embarque du backend, vide pour les backends integres au plugin
     */
    public static Optional<EmbeddedModule> forStorage(StorageType type) {
        // Switch exhaustif sans branche par defaut : un nouveau backend ne compile pas tant
        // qu'il n'est pas classe ici.
        return switch (type) {
            case MONGODB -> Optional.of(MONGODB);
            case SQLITE, MYSQL, MARIADB, YAML, JSON -> Optional.empty();
        };
    }

    private static LibraryArtifact mongo(String artifactId, String sha256, long size) {
        return new LibraryArtifact("org.mongodb", artifactId, MONGODB_DRIVER_VERSION, sha256, size);
    }
}
