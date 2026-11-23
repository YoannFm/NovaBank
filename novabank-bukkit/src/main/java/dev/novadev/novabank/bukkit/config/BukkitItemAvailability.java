package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.catalog.ItemAvailability;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Disponibilite des items sur ce serveur : items vanilla selon la version, items custom
 * selon la presence du plugin fournisseur.
 *
 * <p>E4b1 : un namespace custom est disponible des que son plugin est active. La
 * verification item par item via l'API de chaque fournisseur arrive avec les
 * fournisseurs d'items (E9b).
 */
public final class BukkitItemAvailability implements ItemAvailability {

    /** Namespace de cle d'item -> nom du plugin fournisseur. */
    private static final Map<String, String> PROVIDER_PLUGINS = Map.of(
            "itemsadder", "ItemsAdder",
            "nexo", "Nexo",
            "oraxen", "Oraxen",
            "craftengine", "CraftEngine",
            "mmoitems", "MMOItems");

    private final VanillaItems vanilla;
    private final PluginPresence plugins;

    /**
     * @param vanilla items vanilla du serveur
     * @param plugins plugins actives
     */
    public BukkitItemAvailability(VanillaItems vanilla, PluginPresence plugins) {
        this.vanilla = Objects.requireNonNull(vanilla, "vanilla");
        this.plugins = Objects.requireNonNull(plugins, "plugins");
    }

    @Override
    public boolean providerPresent(String namespace) {
        return providerPlugin(namespace).map(plugins::isEnabled).orElse(false);
    }

    @Override
    public boolean exists(ItemKey key) {
        Objects.requireNonNull(key, "key");
        if (ItemKey.MINECRAFT.equals(key.namespace())) {
            return vanilla.isItem(key.toString());
        }
        return providerPresent(key.namespace());
    }

    /**
     * @param namespace namespace de cle d'item
     * @return le plugin fournisseur connu, vide sinon
     */
    public static Optional<String> providerPlugin(String namespace) {
        return Optional.ofNullable(PROVIDER_PLUGINS.get(namespace.toLowerCase(Locale.ROOT)));
    }
}
