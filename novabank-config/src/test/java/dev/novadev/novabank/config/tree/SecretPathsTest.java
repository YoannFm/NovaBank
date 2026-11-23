package dev.novadev.novabank.config.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.Secret;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretPathsTest {

    @ParameterizedTest
    @ValueSource(strings = {"storage.sql.password", "storage.sql.username", "storage.mongodb.uri", "redis.password",
            "storage.mongodb.uri.x", "redis.password.a.b"})
    void chemins_secrets_et_sous_chemins(String path) {
        assertThat(SecretPaths.isSecret(path)).isTrue();
        assertThat(SecretPaths.touchesSecret(path)).isTrue();
        assertThat(SecretPaths.mask(path, "valeur")).isEqualTo(Secret.MASK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"storage", "storage.sql", "redis", "storage.mongodb"})
    void sections_contenant_un_secret(String path) {
        assertThat(SecretPaths.isSecret(path)).isFalse();
        assertThat(SecretPaths.touchesSecret(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"storage.sql.port", "storage.sql.passwords", "redis.password2", "redis.host", "items"})
    void chemins_ordinaires(String path) {
        assertThat(SecretPaths.isSecret(path)).isFalse();
        assertThat(SecretPaths.touchesSecret(path)).isFalse();
        assertThat(SecretPaths.mask(path, 3)).isEqualTo(3);
    }

    @Test
    void arbre_masque_en_profondeur_sans_toucher_au_reste() {
        Map<String, Object> tree = new java.util.LinkedHashMap<>();
        tree.put("storage", Map.of("mongodb", Map.of("uri", "abc", "database", "nb")));
        tree.put("redis", Map.of("password", Map.of("nested", "x")));
        tree.put("items", List.of(Map.of("key", "minecraft:stone")));
        tree.put("vide", null);
        Map<String, Object> masked = SecretPaths.maskTree(tree);
        assertThat(masked.get("storage")).isEqualTo(Map.of("mongodb", Map.of("uri", Secret.MASK, "database", "nb")));
        assertThat(masked.get("redis")).isEqualTo(Map.of("password", Secret.MASK));
        assertThat(masked.get("items")).isEqualTo(List.of(Map.of("key", "minecraft:stone")));
        assertThat(masked).containsEntry("vide", null);
        assertThat(tree.get("storage")).isEqualTo(Map.of("mongodb", Map.of("uri", "abc", "database", "nb")));
    }

    @Test
    void secret_compare_par_valeur_et_toujours_masque() {
        assertThat(Secret.of("a")).isEqualTo(Secret.of("a")).hasSameHashCodeAs(Secret.of("a"))
                .isNotEqualTo(Secret.of("b")).isNotEqualTo("a").hasToString(Secret.MASK);
        assertThat(Secret.of(" ").isBlank()).isTrue();
    }
}
