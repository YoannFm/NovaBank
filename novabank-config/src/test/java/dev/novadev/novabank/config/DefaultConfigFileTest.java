package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.load;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.AcceptedItem;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Le config.yml livre doit etre valide et refleter exactement les valeurs par defaut du code.
 */
class DefaultConfigFileTest {

    private static String text() throws IOException {
        try (InputStream in = DefaultConfigFileTest.class.getResourceAsStream("/config.yml")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> parsed() throws IOException {
        Map<String, Object> tree = new Yaml().load(text());
        return tree;
    }

    @Test
    void fichier_brut_charge_sans_erreur_ni_avertissement() throws IOException {
        ConfigLoadResult result = load(parsed());
        assertThat(result.report().issues()).isEmpty();
        assertThat(result.config()).isPresent();
        assertThat(result.migrated()).isFalse();
    }

    @Test
    void valeurs_du_fichier_identiques_aux_valeurs_par_defaut_du_code() throws IOException {
        NovaBankConfig fromFile = TestConfigs.loadValid(parsed());
        NovaBankConfig fromDefaults = TestConfigs.loadValid(Map.of());
        assertThat(fromFile.server()).isEqualTo(fromDefaults.server());
        assertThat(fromFile.storage()).isEqualTo(fromDefaults.storage());
        assertThat(fromFile.redis()).isEqualTo(fromDefaults.redis());
        assertThat(fromFile.banks()).isEqualTo(fromDefaults.banks());
        assertThat(fromFile.limits()).isEqualTo(fromDefaults.limits());
        assertThat(fromFile.leaderboards()).isEqualTo(fromDefaults.leaderboards());
        assertThat(fromFile.diagnostics()).isEqualTo(fromDefaults.diagnostics());
        assertThat(fromFile.security()).isEqualTo(fromDefaults.security());
        assertThat(fromFile.commands()).isEqualTo(fromDefaults.commands());
        assertThat(fromFile.language()).isEqualTo(fromDefaults.language());
        assertThat(fromFile.gui()).isEqualTo(fromDefaults.gui());
    }

    @Test
    void items_d_exemple_avec_prix_exacts_et_exemple_custom_commente() throws IOException {
        NovaBankConfig config = TestConfigs.loadValid(parsed());
        assertThat(config.items()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(config.items()).extracting(AcceptedItem::key).allMatch(key -> key.namespace().equals("minecraft"));
        assertThat(config.items()).extracting(AcceptedItem::price).contains(new BigDecimal("0.1"), new BigDecimal("1.5"));
        assertThat(text()).contains("# - key: itemsadder:pack/ruby_block").doesNotContain(String.valueOf((char) 0x2014));
    }
}
