package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.with;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.RedisSettings;
import dev.novadev.novabank.config.model.Secret;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RedisSectionTest {

    private static Map<String, Object> redisOn(String storageType) {
        return with(minimalWith("storage.type", storageType), "redis.enabled", true);
    }

    @Test
    void valeurs_valides_lues() {
        Map<String, Object> tree = with(redisOn("MARIADB"), "redis", Map.ofEntries(Map.entry("enabled", "true"),
                Map.entry("host", "redis.local"), Map.entry("port", 6380), Map.entry("password", "pw"),
                Map.entry("database", 2), Map.entry("ssl", true), Map.entry("channel-prefix", "reseau:a"),
                Map.entry("connect-timeout-ms", 500), Map.entry("socket-timeout-ms", 1500),
                Map.entry("server-name", "lobby-1"), Map.entry("poll-interval-seconds", 2),
                Map.entry("overlap-seconds", 10)));
        tree = with(tree, "storage.sql.password", "x");
        RedisSettings redis = loadValid(tree).redis();
        assertThat(redis).isEqualTo(new RedisSettings(true, "redis.local", 6380, Secret.of("pw"), 2, true,
                "reseau:a", 500, 1500, "lobby-1", 2, 10, true));
    }

    @Test
    void section_absente_valeurs_par_defaut() {
        assertThat(loadValid(minimal()).redis()).isEqualTo(new RedisSettings(false, "localhost", 6379, Secret.of(""),
                0, false, "novabank", 2000, 2000, "server", 5, 30, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"YAML", "JSON", "SQLITE"})
    void redis_avec_stockage_mono_serveur_refuse(String storageType) {
        String message = singleError(redisOn(storageType), "redis.enabled").message();
        assertThat(message).contains(storageType).contains("mono-serveur");
    }

    @Test
    void redis_avec_mongodb_accepte() {
        assertThat(loadValid(redisOn("MONGODB")).redis().enabled()).isTrue();
    }

    @Test
    void valeurs_invalides_refusees_au_bon_chemin() {
        singleError(minimalWith("redis.channel-prefix", "Nova Bank"), "redis.channel-prefix");
        singleError(minimalWith("redis.database", -1), "redis.database");
        singleError(minimalWith("redis.port", 0), "redis.port");
        singleError(minimalWith("redis.connect-timeout-ms", 50), "redis.connect-timeout-ms");
        singleError(minimalWith("redis.ssl", "oui"), "redis.ssl");
        singleError(minimalWith("redis.server-name", "a b"), "redis.server-name");
        singleError(minimalWith("redis.poll-interval-seconds", 0), "redis.poll-interval-seconds");
        singleError(minimalWith("redis.overlap-seconds", 1), "redis.overlap-seconds");
    }
}
