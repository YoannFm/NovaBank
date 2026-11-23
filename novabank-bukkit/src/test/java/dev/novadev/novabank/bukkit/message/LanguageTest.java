package dev.novadev.novabank.bukkit.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.bukkit.testing.TestLanguages;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LanguageTest {

    private final List<String> warnings = new ArrayList<>();

    @Test
    void cle_absente_du_fichier_de_l_admin_repli_sur_le_jar_et_un_seul_avertissement() {
        Language language = new Language("fr", Map.of("prefix", "[B] ", "a", "admin"),
                Map.of("prefix", "[J] ", "a", "jar", "b", "jar {x}"), warnings::add);

        assertThat(language.text("a")).isEqualTo("admin");
        assertThat(language.text("b", "x", 1)).isEqualTo("jar 1");
        assertThat(language.text("b", "x", 2)).isEqualTo("jar 2");
        assertThat(language.text("inconnue")).isEqualTo("inconnue");
        assertThat(language.chat("a")).isEqualTo("[B] admin");

        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("'b'", "lang/fr.yml"))
                .anySatisfy(warning -> assertThat(warning).contains("inconnue"));
        assertThat(warnings.stream().filter(warning -> warning.contains("'b'"))).hasSize(1);
    }

    @Test
    void placeholders_et_couleurs_convertis_cote_serveur() {
        Language language = new Language("fr", Map.of("k", "&aBonjour {player}&#FF8800 !"), Map.of(), warnings::add);

        assertThat(language.text("k", "player", "Yo"))
                .isEqualTo("\u00a7aBonjour Yo\u00a7x\u00a7f\u00a7f\u00a78\u00a78\u00a70\u00a70 !");
        assertThatThrownBy(() -> language.text("k", "player")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nombres_et_dates_au_format_de_la_langue() {
        Language french = TestLanguages.french();
        Language english = TestLanguages.bundled("en");

        assertThat(french.quantity(1_234_567)).isEqualTo("1 234 567");
        assertThat(french.value(new BigDecimal("1234.5678"))).isEqualTo("1 234,57");
        assertThat(french.value(new BigDecimal("-20.0000"))).isEqualTo("-20");
        assertThat(english.quantity(1_234_567)).isEqualTo("1,234,567");
        assertThat(english.value(new BigDecimal("0.1"))).isEqualTo("0.1");
        assertThat(french.date(Instant.parse("2026-10-27T12:00:00Z"))).matches("\\d{2}/10/2026 \\d{2}:\\d{2}");
    }

    @Test
    void format_de_date_invalide_signale() {
        Language language = new Language("xx", Map.of("locale", "fr-FR", "date-format", "{{"), Map.of(),
                warnings::add);

        assertThat(language.date(Instant.EPOCH)).isNotBlank();
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("Format de date invalide"));
    }

    @Test
    void aplatissement_des_cles_et_listes() {
        Map<String, String> flat = Language.flatten(Map.of("a", Map.of("b", "x", "c", List.of("l1", "l2")),
                "n", 3));

        assertThat(flat).containsEntry("a.b", "x").containsEntry("a.c", "l1\nl2").containsEntry("n", "3");
    }

    @Test
    void fichiers_fournis_fr_et_en_ont_les_memes_cles_et_aucun_tiret_long() {
        Map<String, String> fr = TestLanguages.texts("fr");
        Map<String, String> en = TestLanguages.texts("en");

        assertThat(fr.keySet()).isEqualTo(en.keySet());
        assertThat(fr.values()).noneMatch(text -> text.indexOf('\u2014') >= 0);
        assertThat(en.values()).noneMatch(text -> text.indexOf('\u2014') >= 0);
    }
}
