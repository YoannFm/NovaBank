package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.errorPaths;
import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.warningPaths;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.catalog.ConfigPriceCatalog;
import dev.novadev.novabank.config.model.AcceptedItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemsSectionTest {

    private static Map<String, Object> items(Object... entries) {
        return minimalWith("items", List.of(entries));
    }

    private static Map<String, Object> item(String key, Object price) {
        return Map.of("key", key, "price", price);
    }

    @Test
    void valeurs_valides_lues_dans_l_ordre() {
        NovaBankConfig config = loadValid(items(
                Map.of("key", "minecraft:diamond_block", "price", "90", "category", "minerais",
                        "display-name", "Bloc de diamant"),
                item("itemsadder:pack/ruby_block", 120)));
        assertThat(config.items()).containsExactly(
                new AcceptedItem(ItemKey.minecraft("diamond_block"), new BigDecimal("90"), Optional.of("minerais"),
                        Optional.of("Bloc de diamant")),
                new AcceptedItem(ItemKey.parse("itemsadder:pack/ruby_block"), new BigDecimal("120"), Optional.empty(),
                        Optional.empty()));
    }

    @Test
    void prix_texte_haute_precision_conserve_exactement() {
        String price = "0.123456789012345678901234567890";
        AcceptedItem item = loadValid(items(item("minecraft:stone", price))).items().get(0);
        assertThat(item.price().toPlainString()).isEqualTo(price);
        assertThat(ConfigPriceCatalog.of(List.of(item)).unitPrice(item.key())).contains(new BigDecimal(price));
    }

    @Test
    void prix_numeriques_acceptes() {
        List<AcceptedItem> accepted = loadValid(items(item("minecraft:stone", 5), item("minecraft:dirt", 1.5),
                item("minecraft:sand", 0))).items();
        assertThat(accepted).extracting(AcceptedItem::price)
                .containsExactly(new BigDecimal("5"), new BigDecimal("1.5"), new BigDecimal("0"));
    }

    @Test
    void prix_negatif_refuse() {
        assertThat(singleError(items(item("minecraft:stone", "-0.01")), "items[0].price").message()).contains("-0.01");
    }

    @Test
    void prix_non_numerique_ou_absent_refuse() {
        singleError(items(item("minecraft:stone", "abc")), "items[0].price");
        singleError(items(Map.of("key", "minecraft:stone")), "items[0].price");
        singleError(items(item("minecraft:stone", Double.NaN)), "items[0].price");
    }

    @Test
    void cle_absente_ou_invalide_refusee() {
        singleError(items(Map.of("price", "1")), "items[0].key");
        singleError(items(item("stone", "1")), "items[0].key");
        singleError(items(item("Minecraft:Stone", "1")), "items[0].key");
    }

    @Test
    void doublon_de_cle_refuse() {
        assertThat(singleError(items(item("minecraft:stone", "1"), item("minecraft:dirt", "1"),
                item("minecraft:stone", "2")), "items[2].key").message()).contains("minecraft:stone");
    }

    @Test
    void erreurs_de_plusieurs_items_accumulees() {
        ConfigLoadResult result = load(items(Map.of("key", "x", "price", "y", "category", "Pas Bon"),
                item("minecraft:stone", "-1")));
        assertThat(errorPaths(result)).containsExactly("items[0].key", "items[0].price", "items[0].category",
                "items[1].price");
    }

    @Test
    void element_qui_n_est_pas_une_section_refuse() {
        singleError(items("minecraft:stone"), "items[0]");
    }

    @Test
    void item_vanilla_inconnu_de_la_version_ignore_avec_avertissement() {
        FakeItemAvailability server116 = new FakeItemAvailability(Set.of(), Set.of(ItemKey.minecraft("copper_block")));
        ConfigLoadResult result = new NovaBankConfigLoader(server116).load(items(item("minecraft:copper_block", "3"),
                item("minecraft:stone", "1")));
        assertThat(result.config()).isPresent();
        assertThat(result.report().warnings()).singleElement().satisfies(issue -> {
            assertThat(issue.path()).isEqualTo("items[0].key");
            assertThat(issue.message()).contains("version du serveur");
        });
        ConfigPriceCatalog catalog = ConfigPriceCatalog.of(result.config().orElseThrow().items());
        assertThat(catalog.items()).containsExactly(ItemKey.minecraft("stone"));
        assertThat(catalog.unitPrice(ItemKey.minecraft("copper_block"))).isEmpty();
    }

    @Test
    void fournisseur_custom_absent_ignore_avec_avertissement() {
        FakeItemAvailability noProvider = new FakeItemAvailability(Set.of(), Set.of());
        ConfigLoadResult result = new NovaBankConfigLoader(noProvider).load(items(item("nexo:ruby", "3"),
                item("minecraft:stone", "1")));
        assertThat(result.config().orElseThrow().items()).extracting(AcceptedItem::key)
                .containsExactly(ItemKey.minecraft("stone"));
        assertThat(result.report().warnings()).singleElement()
                .satisfies(issue -> assertThat(issue.message()).contains("fournisseur").contains("nexo"));
    }

    @Test
    void item_custom_inconnu_du_fournisseur_ignore_avec_avertissement() {
        FakeItemAvailability availability = new FakeItemAvailability(Set.of("oraxen"), Set.of(ItemKey.parse("oraxen:gone")));
        ConfigLoadResult result = new NovaBankConfigLoader(availability).load(items(item("oraxen:gone", "3"),
                item("oraxen:here", "1")));
        assertThat(result.config().orElseThrow().items()).extracting(AcceptedItem::key)
                .containsExactly(ItemKey.parse("oraxen:here"));
        assertThat(warningPaths(result)).containsExactly("items[0].key");
    }

    @Test
    void aucun_item_accepte_avertit() {
        ConfigLoadResult result = load(minimalWith("items", null));
        assertThat(result.config().orElseThrow().items()).isEmpty();
        assertThat(warningPaths(result)).containsExactly("items");
        assertThat(load(minimal()).report().issues()).isEmpty();
    }
}
