package dev.novadev.novabank.config.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.config.validation.ValidationIssue;
import dev.novadev.novabank.config.validation.ValidationReport;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConfigNodeTest {

    private final ParseContext context = new ParseContext();

    private ConfigNode node(Map<?, ?> values) {
        return ConfigNode.root(values, context);
    }

    private ValidationReport report() {
        return context.report().build();
    }

    @Test
    void decimaux_lus_depuis_tous_les_types_numeriques() {
        ConfigNode node = node(Map.of("i", 3, "l", 4L, "s", (short) 5, "b", (byte) 6, "bi", BigInteger.TEN,
                "bd", new BigDecimal("1.25"), "f", 1.1f, "d", 2.5, "t", " 7.75 "));
        assertThat(node.requiredDecimal("i")).contains(new BigDecimal("3"));
        assertThat(node.requiredDecimal("l")).contains(new BigDecimal("4"));
        assertThat(node.requiredDecimal("s")).contains(new BigDecimal("5"));
        assertThat(node.requiredDecimal("b")).contains(new BigDecimal("6"));
        assertThat(node.requiredDecimal("bi")).contains(BigDecimal.TEN);
        assertThat(node.requiredDecimal("bd")).contains(new BigDecimal("1.25"));
        assertThat(node.requiredDecimal("f")).contains(new BigDecimal("1.1"));
        assertThat(node.requiredDecimal("d")).contains(new BigDecimal("2.5"));
        assertThat(node.requiredDecimal("t")).contains(new BigDecimal("7.75"));
        assertThat(report().issues()).isEmpty();
    }

    @Test
    void decimaux_invalides_signales() {
        ConfigNode node = node(Map.of("inf", Double.POSITIVE_INFINITY, "bool", true, "unit", TimeUnit.SECONDS));
        assertThat(node.requiredDecimal("inf")).isEmpty();
        assertThat(node.requiredDecimal("bool")).isEmpty();
        assertThat(node.requiredDecimal("unit")).isEmpty();
        assertThat(node.requiredDecimal("absent")).isEmpty();
        assertThat(report().errors()).extracting(ValidationIssue::path).containsExactly("inf", "bool", "unit", "absent");
    }

    @Test
    void entiers_hors_bornes_et_hors_long_signales() {
        ConfigNode node = node(Map.of("big", "99999999999999999999", "neg", -5, "frac", "2.5"));
        assertThat(node.longValue("big", 1, 0, Long.MAX_VALUE)).isEqualTo(1);
        assertThat(node.intValue("neg", 2, Integer.MIN_VALUE, -10)).isEqualTo(2);
        assertThat(node.intValue("frac", 3, 0, 10)).isEqualTo(3);
        assertThat(report().errors()).extracting(ValidationIssue::message).containsExactly(
                "nombre entier attendu, recu : '99999999999999999999'",
                "doit etre inferieur ou egal a -10 (recu : -5)",
                "nombre entier attendu, recu : '2.5'");
    }

    @Test
    void textes_depuis_scalaires_et_types_non_scalaires_refuses() {
        ConfigNode node = node(Map.of("n", 12, "b", false, "l", List.of(1), "m", Map.of("a", 1)));
        assertThat(node.string("n", "x")).isEqualTo("12");
        assertThat(node.string("b", "x")).isEqualTo("false");
        assertThat(node.string("l", "x")).isEqualTo("x");
        assertThat(node.optionalText("m")).isEmpty();
        assertThat(report().errors()).extracting(ValidationIssue::message)
                .containsExactly("texte attendu, recu : une liste", "texte attendu, recu : une section");
    }

    @Test
    void texte_obligatoire_et_texte_optionnel() {
        ConfigNode node = node(Map.of("vide", "  ", "ok", "a"));
        assertThat(node.requiredString("absent")).isEmpty();
        assertThat(node.requiredString("ok")).contains("a");
        assertThat(node.optionalText("vide")).isEmpty();
        assertThat(report().errors()).extracting(ValidationIssue::path).containsExactly("absent");
    }

    @Test
    void enumeration_insensible_a_la_casse_et_aux_tirets() {
        ConfigNode node = node(Map.of("a", "seconds", "b", "MILLI-SECONDS", "c", "MILLISECONDS", "d", "jours"));
        assertThat(node.enumValue("a", TimeUnit.class, TimeUnit.DAYS)).isEqualTo(TimeUnit.SECONDS);
        assertThat(node.enumValue("b", TimeUnit.class, TimeUnit.DAYS)).isEqualTo(TimeUnit.DAYS);
        assertThat(node.enumValue("c", TimeUnit.class, TimeUnit.DAYS)).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(node.enumValue("d", TimeUnit.class, TimeUnit.HOURS)).isEqualTo(TimeUnit.HOURS);
        assertThat(node.enumValue("absent", TimeUnit.class, TimeUnit.HOURS)).isEqualTo(TimeUnit.HOURS);
        assertThat(report().errors()).extracting(ValidationIssue::path).containsExactly("b", "d");
    }

    @Test
    void section_mise_en_cache_et_chemins_imbriques() {
        ConfigNode root = node(Map.of("a", Map.of("b", Map.of("c", 1))));
        ConfigNode b = root.section("a").section("b");
        assertThat(root.section("a")).isSameAs(root.section("a"));
        assertThat(b.path()).isEqualTo("a.b");
        assertThat(b.pathOf("c")).isEqualTo("a.b.c");
        assertThat(b.intValue("c", 0, 0, 5)).isEqualTo(1);
        context.reportUnreadKeys();
        assertThat(report().issues()).isEmpty();
    }

    @Test
    void liste_de_sections_avec_element_invalide() {
        List<ConfigNode> nodes = node(Map.of("l", List.of(Map.of("k", 1), "x"))).sectionList("l");
        assertThat(nodes).extracting(ConfigNode::path).containsExactly("l[0]");
        assertThat(report().errors()).extracting(ValidationIssue::path).containsExactly("l[1]");
        assertThat(node(Map.of("l", "x")).sectionList("l")).isEmpty();
    }

    @Test
    void lecture_d_un_secret_non_declare_refusee() {
        ConfigNode node = node(Map.of("password", "x"));
        assertThatThrownBy(() -> node.secret("password", "")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SecretPaths");
        assertThat(node.section("redis").secret("password", "").reveal()).isEmpty();
    }

    @Test
    void chemins_reconnus_lus_libres_ou_ignores() {
        ConfigNode root = node(Map.of("opts", Map.of("x", "1"), "ign", Map.of("y", 2), "lu", 1, "inconnu", 2));
        root.stringMap("opts");
        root.ignore("ign", "raison");
        root.intValue("lu", 0, 0, 5);
        root.bool("absent", false);
        assertThat(context.isRecognized("opts.x")).isTrue();
        assertThat(context.isRecognized("opts.x.y")).isTrue();
        assertThat(context.isRecognized("ign")).isTrue();
        assertThat(context.isRecognized("ign.y")).isFalse();
        assertThat(context.isRecognized("absent")).isTrue();
        assertThat(context.isRecognized("inconnu")).isFalse();
        context.reportUnreadKeys();
        assertThat(report().warnings()).extracting(ValidationIssue::path).containsExactly("ign", "inconnu");
    }

    @Test
    void valeurs_nulles_traitees_comme_absentes() {
        Map<String, Object> values = new java.util.HashMap<>();
        values.put("k", null);
        ConfigNode node = node(values);
        assertThat(node.has("k")).isFalse();
        assertThat(node.bool("k", true)).isTrue();
        assertThat(node.stringList("k", List.of("d"))).containsExactly("d");
        assertThat(node.optionalMatching("k", java.util.regex.Pattern.compile("x"), "x")).isEqualTo(Optional.empty());
        assertThat(report().issues()).isEmpty();
    }
}
