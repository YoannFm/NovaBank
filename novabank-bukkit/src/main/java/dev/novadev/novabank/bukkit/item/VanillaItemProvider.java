package dev.novadev.novabank.bukkit.item;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Fournisseur des items vanilla : identification par {@link VanillaStackRule}, creation
 * par {@link Material#matchMaterial(String)}. Ni {@code Material.values()}, ni
 * {@code switch} sur {@code Material} (HANDOFF 2.2).
 */
public final class VanillaItemProvider implements ItemProvider {

    private final ItemFactory factory;

    /**
     * @param factory fabrique d'items du serveur (meta par defaut d'un type)
     */
    public VanillaItemProvider(ItemFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public String namespace() {
        return ItemKey.MINECRAFT;
    }

    @Override
    public Optional<ItemKey> identify(ItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }
        Material type = stack.getType();
        return VanillaStackRule.identify(new VanillaStackRule.StackFacts(type.getKey().toString(), stack.getAmount(),
                type.isAir(), hasDefaultMeta(stack, type)));
    }

    @Override
    public Optional<ItemStack> create(ItemKey key, int amount) {
        return material(key).map(material -> new ItemStack(material, amount));
    }

    @Override
    public int maxStackSize(ItemKey key) {
        return material(key).map(Material::getMaxStackSize).orElse(1);
    }

    private boolean hasDefaultMeta(ItemStack stack, Material type) {
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.equals(factory.getItemMeta(type));
    }

    private static Optional<Material> material(ItemKey key) {
        if (!ItemKey.MINECRAFT.equals(key.namespace())) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(key.toString());
        return material != null && material.isItem() && !material.isAir() ? Optional.of(material) : Optional.empty();
    }
}
