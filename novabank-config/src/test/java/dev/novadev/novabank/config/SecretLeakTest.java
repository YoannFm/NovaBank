package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.with;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.Secret;
import dev.novadev.novabank.config.tree.SecretPaths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Aucune valeur secrete ne doit apparaitre dans le modele affiche ni dans les messages.
 */
class SecretLeakTest {

    private static final Map<String, String> SECRETS = Map.of(
            "storage.sql.username", "secret-user-1",
            "storage.sql.password", "secret-pass-2",
            "storage.mongodb.uri", "mongodb://secret-mongo-3@host",
            "redis.password", "secret-redis-4",
            "discord.webhook-url", "https://discord.com/api/webhooks/1/secret-hook-5",
            "discord.bot-token", "secret-token-6");

    @Test
    void la_liste_de_test_couvre_tous_les_chemins_secrets() {
        assertThat(SECRETS.keySet()).isEqualTo(SecretPaths.ALL);
    }

    @Test
    void le_modele_n_affiche_jamais_les_secrets() {
        Map<String, Object> tree = minimal();
        for (Map.Entry<String, String> secret : SECRETS.entrySet()) {
            tree = with(tree, secret.getKey(), secret.getValue());
        }
        NovaBankConfig config = TestConfigs.loadValid(tree);
        String shown = config.toString();
        SECRETS.values().forEach(value -> assertThat(shown).doesNotContain(value));
        assertThat(shown).contains(Secret.MASK);
        assertThat(config.redis().password().reveal()).isEqualTo("secret-redis-4");
    }

    @Test
    void les_messages_d_erreur_ne_reprennent_jamais_un_secret() {
        Map<String, Object> tree = minimal();
        for (String path : SecretPaths.ALL) {
            tree = with(tree, path, List.of("secret-dans-une-liste"));
        }
        ConfigLoadResult result = load(tree);
        assertThat(result.report().errors()).extracting(issue -> issue.path()).containsAll(SecretPaths.ALL);
        assertThat(result.report().issues()).allSatisfy(issue -> assertThat(issue.message())
                .doesNotContain("secret-dans-une-liste"));
        assertThat(result.report().errors()).filteredOn(issue -> issue.message().startsWith("texte attendu"))
                .hasSize(SecretPaths.ALL.size())
                .allSatisfy(issue -> assertThat(issue.message()).endsWith(Secret.MASK));
    }

    @Test
    void l_arbre_masque_cache_les_secrets_pour_l_affichage() {
        Map<String, Object> tree = minimal();
        for (Map.Entry<String, String> secret : SECRETS.entrySet()) {
            tree = with(tree, secret.getKey(), secret.getValue());
        }
        String shown = SecretPaths.maskTree(load(tree).tree()).toString();
        SECRETS.values().forEach(value -> assertThat(shown).doesNotContain(value));
        assertThat(shown).contains("minecraft:stone");
    }
}
