package dev.novadev.novabank.libraries.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.config.model.StorageType;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class DriverCatalogTest {

    @Test
    void associe_chaque_backend_jdbc_a_son_pilote() {
        assertThat(DriverCatalog.forStorage(StorageType.SQLITE)).contains(DriverCatalog.SQLITE);
        assertThat(DriverCatalog.forStorage(StorageType.MYSQL)).contains(DriverCatalog.MARIADB);
        assertThat(DriverCatalog.forStorage(StorageType.MARIADB)).contains(DriverCatalog.MARIADB);
        assertThat(DriverCatalog.forStorage(StorageType.YAML)).isEmpty();
        assertThat(DriverCatalog.forStorage(StorageType.JSON)).isEmpty();
        assertThat(DriverCatalog.forStorage(StorageType.MONGODB)).isEmpty();
    }

    @Test
    void sqlite_epingle_est_la_version_de_test_du_projet() throws Exception {
        Properties pom = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/maven/org.xerial/sqlite-jdbc/pom.properties")) {
            assertThat(in).as("sqlite-jdbc de test sur le classpath").isNotNull();
            pom.load(in);
        }
        assertThat(DriverCatalog.SQLITE.artifacts().get(0).version()).isEqualTo(pom.getProperty("version"));
    }

    @Test
    void decrit_les_classes_et_le_dossier_natif() {
        assertThat(DriverCatalog.SQLITE.driverClassName()).isEqualTo("org.sqlite.JDBC");
        assertThat(DriverCatalog.SQLITE.nativeDirectoryProperty()).contains("org.sqlite.tmpdir");
        assertThat(DriverCatalog.MARIADB.driverClassName()).isEqualTo("org.mariadb.jdbc.Driver");
        assertThat(DriverCatalog.MARIADB.nativeDirectoryProperty()).isEmpty();
        assertThat(DriverCatalog.MARIADB.artifacts().get(0).coordinates())
                .isEqualTo("org.mariadb.jdbc:mariadb-java-client:3.5.11");
    }

    @Test
    void une_description_sans_artefact_est_refusee() {
        assertThatThrownBy(() -> new DriverSpec("x", "x.Driver", List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
