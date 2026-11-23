package dev.novadev.novabank.libraries.catalog;

import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.util.List;
import java.util.Optional;

/**
 * Pilotes JDBC connus, epingles dans le code.
 *
 * <p>Le catalogue est volontairement absent de la configuration : modifier un fichier du
 * serveur ne doit jamais permettre de faire charger un jar arbitraire. Changer de version
 * d'un pilote = nouvelle version de NovaBank, avec nouvelle somme SHA-256.
 *
 * <p>Aucune dependance transitive n'est listee : les deux pilotes n'ont que des
 * dependances optionnelles (slf4j, waffle, SDK AWS), absentes a l'execution.
 */
public final class DriverCatalog {

    /**
     * SQLite JDBC (xerial), Apache 2.0, bibliotheques natives incluses. Meme version que
     * le pilote de test du projet. Bibliotheque native extraite dans le dossier designe par
     * {@code org.sqlite.tmpdir}.
     */
    public static final DriverSpec SQLITE = new DriverSpec("sqlite", "org.sqlite.JDBC",
            List.of(new LibraryArtifact("org.xerial", "sqlite-jdbc", "3.53.4.0",
                    "bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb", 12_002_370L)),
            Optional.of("org.sqlite.tmpdir"));

    /**
     * MariaDB Connector/J 3.x (LGPL 2.1), utilise pour MariaDB et MySQL.
     */
    public static final DriverSpec MARIADB = new DriverSpec("mariadb", "org.mariadb.jdbc.Driver",
            List.of(new LibraryArtifact("org.mariadb.jdbc", "mariadb-java-client", "3.5.11",
                    "42604178b9e57ec08ee0035079961516f086e455d0141f0b22c6eff29fb2fff4", 793_387L)),
            Optional.empty());

    private DriverCatalog() {
    }

    /**
     * @param type backend de stockage configure
     * @return le pilote JDBC du backend, vide pour les backends sans JDBC
     */
    public static Optional<DriverSpec> forStorage(StorageType type) {
        // Switch exhaustif sans branche par defaut : un nouveau backend ne compile pas tant
        // qu'il n'est pas classe ici.
        return switch (type) {
            case SQLITE -> Optional.of(SQLITE);
            case MYSQL, MARIADB -> Optional.of(MARIADB);
            case YAML, JSON, MONGODB -> Optional.empty();
        };
    }
}
