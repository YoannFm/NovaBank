package dev.novadev.novabank.bukkit.item;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.inventory.ItemStack;

/**
 * Registre des fournisseurs d'items, par namespace. L'identification interroge les
 * fournisseurs custom avant le fournisseur vanilla (un item custom est aussi un materiau
 * vanilla, mais porteur de meta : le vanilla le refuse de toute facon).
 */
public final class ItemProviders {

    private final Map<String, ItemProvider> byNamespace = new ConcurrentHashMap<>();
    private final List<ItemProvider> identificationOrder = new CopyOnWriteArrayList<>();

    /**
     * @param provider fournisseur
     * @throws IllegalStateException si le namespace est deja gere
     */
    public void register(ItemProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (byNamespace.putIfAbsent(provider.namespace(), provider) != null) {
            throw new IllegalStateException(ConsoleText.tr("Namespace d'items deja gere : {0}", provider.namespace()));
        }
        if (ItemKey.MINECRAFT.equals(provider.namespace())) {
            identificationOrder.add(provider);
        } else {
            identificationOrder.add(0, provider);
        }
    }

    /**
     * @param stack pile
     * @return la cle de l'item identifie, ou vide
     */
    public Optional<ItemKey> identify(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return Optional.empty();
        }
        for (ItemProvider provider : identificationOrder) {
            Optional<ItemKey> key = provider.identify(stack);
            if (key.isPresent()) {
                return key;
            }
        }
        return Optional.empty();
    }

    /**
     * @param key cle
     * @return le fournisseur de son namespace, ou vide (plugin absent)
     */
    public Optional<ItemProvider> provider(ItemKey key) {
        return Optional.ofNullable(byNamespace.get(key.namespace()));
    }
}
