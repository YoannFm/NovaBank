package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.errorPaths;
import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.warningPaths;
import static dev.novadev.novabank.config.TestConfigs.with;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.PoolSettings;
import dev.novadev.novabank.config.model.Secret;
import dev.novadev.novabank.config.model.StorageSettings;
import dev.novadev.novabank.config.model.StorageType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StorageSectionTest {

    private static Map<String, Object> mysql() {
        Map<String, Object> tree = minimalWith("storage.type", "mysql");
        tree = with(tree, "storage.sql.host", "db.example.net");
        tree = with(tree, "storage.sql.port", "3307");
        tree = with(tree, "storage.sql.database", "nova_bank");
        tree = with(tree, "storage.sql.username", "nova");
        tree = with(tree, "storage.sql.password", "s3cret");
        tree = with(tree, "storage.sql.pool", Map.of("max-size", 20, "min-idle", 5,
                "connection-timeout-ms", 5000L, "max-lifetime-ms", 600000));
        return with(tree, "storage.sql.options", Map.of("useSSL", "true", "cachePrepStmts", true));
    }

    @Test
    void valeurs_valides_lues() {
        StorageSettings storage = loadValid(with(mysql(), "storage.save-interval-seconds", 60)).storage();
        assertThat(storage.type()).isEqualTo(StorageType.MYSQL);
        assertThat(storage.sql().host()).isEqualTo("db.example.net");
        assertThat(storage.sql().port()).isEqualTo(3307);
        assertThat(storage.sql().database()).isEqualTo("nova_bank");
        assertThat(storage.sql().username()).isEqualTo(Secret.of("nova"));
        assertThat(storage.sql().password().reveal()).isEqualTo("s3cret");
        assertThat(storage.sql().pool()).isEqualTo(new PoolSettings(20, 5, 5000, 600000));
        assertThat(storage.sql().options()).containsEntry("useSSL", "true").containsEntry("cachePrepStmts", "true");
        assertThat(storage.saveIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void section_absente_valeurs_par_defaut() {
        StorageSettings storage = loadValid(minimal()).storage();
        assertThat(storage.type()).isEqualTo(StorageType.SQLITE);
        assertThat(storage.sql().host()).isEqualTo("localhost");
        assertThat(storage.sql().port()).isEqualTo(3306);
        assertThat(storage.sql().database()).isEqualTo("novabank");
        assertThat(storage.sql().password().reveal()).isEmpty();
        assertThat(storage.sql().pool()).isEqualTo(new PoolSettings(10, 2, 30_000, 1_800_000));
        assertThat(storage.sql().options()).isEmpty();
        assertThat(storage.sqlite().file()).isEqualTo("data/novabank.db");
        assertThat(storage.file().directory()).isEqualTo("data");
        assertThat(storage.mongodb().uri().reveal()).isEqualTo("mongodb://localhost:27017");
        assertThat(storage.mongodb().database()).isEqualTo("novabank");
        assertThat(storage.mongodb().connectTimeoutMs()).isEqualTo(5_000);
        assertThat(storage.mongodb().serverSelectionTimeoutMs()).isEqualTo(10_000);
        assertThat(storage.mongodb().socketTimeoutMs()).isEqualTo(30_000);
        assertThat(storage.saveIntervalSeconds()).isEqualTo(300);
        assertThat(storage.drivers().directory()).isEqualTo("libs");
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void chaque_type_accepte_sans_tenir_compte_de_la_casse(StorageType type) {
        Map<String, Object> tree = with(mysql(), "storage.type", type.name().toLowerCase(java.util.Locale.ROOT));
        assertThat(loadValid(tree).storage().type()).isEqualTo(type);
    }

    @Test
    void type_inconnu_refuse() {
        assertThat(singleError(minimalWith("storage.type", "postgres"), "storage.type").message())
                .contains("MYSQL").contains("MONGODB");
    }

    @Test
    void port_hors_bornes_refuse() {
        singleError(minimalWith("storage.sql.port", 70000), "storage.sql.port");
        singleError(minimalWith("storage.sql.port", "abc"), "storage.sql.port");
    }

    @Test
    void identifiants_refuses_dans_les_options_jdbc() {
        singleError(with(mysql(), "storage.sql.options", Map.of("password", "x")), "storage.sql.options");
        singleError(with(mysql(), "storage.sql.options", Map.of("User", "root")), "storage.sql.options");
    }

    @Test
    void base_sql_avec_caracteres_interdits_refusee() {
        singleError(minimalWith("storage.sql.database", "nova;drop"), "storage.sql.database");
    }

    @Test
    void min_idle_superieur_a_max_size_refuse() {
        Map<String, Object> tree = with(minimalWith("storage.sql.pool.max-size", 4), "storage.sql.pool.min-idle", 5);
        singleError(tree, "storage.sql.pool.min-idle");
    }

    @Test
    void bornes_hikari_du_pool_appliquees() {
        singleError(minimalWith("storage.sql.pool.connection-timeout-ms", 100), "storage.sql.pool.connection-timeout-ms");
        singleError(minimalWith("storage.sql.pool.max-lifetime-ms", 1000), "storage.sql.pool.max-lifetime-ms");
        singleError(minimalWith("storage.sql.pool.max-size", 0), "storage.sql.pool.max-size");
    }

    @Test
    void utilisateur_vide_refuse_seulement_si_sql_actif() {
        singleError(with(mysql(), "storage.sql.username", ""), "storage.sql.username");
        assertThat(load(minimalWith("storage.sql.username", "")).config()).isPresent();
    }

    @Test
    void mot_de_passe_vide_avertit_si_sql_actif() {
        ConfigLoadResult result = load(with(mysql(), "storage.sql.password", ""));
        assertThat(result.config()).isPresent();
        assertThat(warningPaths(result)).containsExactly("storage.sql.password");
    }

    @Test
    void options_libres_sans_avertissement_et_valeur_non_scalaire_refusee() {
        ConfigLoadResult result = load(minimalWith("storage.sql.options", Map.of("nimporte", "quoi")));
        assertThat(result.report().issues()).isEmpty();
        singleError(minimalWith("storage.sql.options", Map.of("bad", List.of(1))), "storage.sql.options.bad");
        singleError(minimalWith("storage.sql.options", "useSSL=true"), "storage.sql.options");
    }

    @Test
    void uri_mongodb_invalide_refusee_sans_reprendre_la_valeur() {
        String uri = "http://admin:motdepasse@mongo";
        String message = singleError(minimalWith("storage.mongodb.uri", uri), "storage.mongodb.uri").message();
        assertThat(message).doesNotContain("motdepasse");
        assertThat(loadValid(minimalWith("storage.mongodb.uri", "mongodb+srv://u:p@cluster")).storage().mongodb().uri().reveal())
                .startsWith("mongodb+srv://");
    }

    @Test
    void base_mongodb_invalide_refusee() {
        singleError(minimalWith("storage.mongodb.database", "nova bank"), "storage.mongodb.database");
        singleError(minimalWith("storage.mongodb.database", "n".repeat(64)), "storage.mongodb.database");
    }

    @Test
    void delais_mongodb_lus_et_bornes() {
        assertThat(loadValid(minimalWith("storage.mongodb.socket-timeout-ms", 1_500)).storage().mongodb()
                .socketTimeoutMs()).isEqualTo(1_500);
        singleError(minimalWith("storage.mongodb.connect-timeout-ms", 99), "storage.mongodb.connect-timeout-ms");
        singleError(minimalWith("storage.mongodb.server-selection-timeout-ms", 300_001),
                "storage.mongodb.server-selection-timeout-ms");
    }

    @Test
    void chemins_vides_refuses() {
        Map<String, Object> tree = with(minimalWith("storage.sqlite.file", " "), "storage.file.directory", "");
        tree = with(tree, "storage.drivers.directory", "");
        ConfigLoadResult result = load(tree);
        assertThat(errorPaths(result)).containsExactly(
                "storage.sqlite.file", "storage.file.directory", "storage.drivers.directory");
    }

    @Test
    void intervalle_de_sauvegarde_hors_bornes_refuse() {
        singleError(minimalWith("storage.save-interval-seconds", 0), "storage.save-interval-seconds");
    }

    @Test
    void verification_sha256_non_desactivable() {
        ConfigLoadResult disabled = load(minimalWith("storage.drivers.verify-sha256", false));
        assertThat(disabled.config()).isPresent();
        assertThat(warningPaths(disabled)).containsExactly("storage.drivers.verify-sha256");
        assertThat(load(minimalWith("storage.drivers.verify-sha256", true)).report().issues()).isEmpty();
    }
}
