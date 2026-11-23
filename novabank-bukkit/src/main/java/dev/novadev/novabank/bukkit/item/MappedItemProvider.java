package dev.novadev.novabank.bukkit.item;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;

/**
 * Fournisseur d'un plugin d'items custom, defini par quatre fonctions : identifiant du plugin d'une
 * pile, pile neuve d'un identifiant, et conversion des identifiants entre le plugin et la cle
 * NovaBank (D12). Aucune dependance a l'API du plugin : les liaisons sont faites par
 * {@link CustomItemPlugins}. Toute erreur du plugin est traitee comme "item inconnu" : un plugin
 * defaillant ne peut ni compter ni creer d'item.
 */
public final class MappedItemProvider implements ItemProvider {

    private final String namespace;
    private final Function<ItemStack, String> idOf;
    private final Function<String, ItemStack> build;
    private final Function<String, String> toPluginId;
    private final Function<String, String> toNovaId;

    /**
     * @param namespace  namespace des cles gerees
     * @param idOf       identifiant du plugin d'une pile, {@code null} si elle n'en est pas une
     * @param build      pile neuve d'un identifiant du plugin, {@code null} s'il n'existe pas
     * @param toPluginId identifiant NovaBank (sans namespace) vers identifiant du plugin
     * @param toNovaId   identifiant du plugin vers identifiant NovaBank (sans namespace)
     */
    public MappedItemProvider(String namespace, Function<ItemStack, String> idOf, Function<String, ItemStack> build,
                              Function<String, String> toPluginId, Function<String, String> toNovaId) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.idOf = Objects.requireNonNull(idOf, "idOf");
        this.build = Objects.requireNonNull(build, "build");
        this.toPluginId = Objects.requireNonNull(toPluginId, "toPluginId");
        this.toNovaId = Objects.requireNonNull(toNovaId, "toNovaId");
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public Optional<ItemKey> identify(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return Optional.empty();
        }
        try {
            String pluginId = idOf.apply(stack);
            return pluginId == null || pluginId.isBlank() ? Optional.empty()
                    : Optional.of(new ItemKey(namespace, toNovaId.apply(pluginId)));
        } catch (RuntimeException unknown) {
            // Plugin defaillant ou identifiant inexprimable en cle NovaBank : item non compte.
            return Optional.empty();
        }
    }

    @Override
    public Optional<ItemStack> create(ItemKey key, int amount) {
        if (!namespace.equals(key.namespace()) || amount <= 0) {
            return Optional.empty();
        }
        try {
            ItemStack stack = build.apply(toPluginId.apply(key.id()));
            if (stack == null || stack.getType().isAir()) {
                return Optional.empty();
            }
            ItemStack copy = stack.clone();
            copy.setAmount(Math.min(amount, Math.max(1, copy.getMaxStackSize())));
            return Optional.of(copy);
        } catch (RuntimeException unknown) {
            return Optional.empty();
        }
    }

    @Override
    public int maxStackSize(ItemKey key) {
        return create(key, 1).map(ItemStack::getMaxStackSize).orElse(1);
    }
}
