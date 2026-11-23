package dev.novadev.novabank.config.catalog;

import dev.novadev.novabank.api.item.ItemKey;

/**
 * Port fourni par la plateforme : items reellement disponibles sur ce serveur.
 *
 * <p>Permet d'ignorer, avec un avertissement, un item vanilla qui n'existe pas dans la
 * version du serveur (cuivre avant 1.17, cerisier avant 1.20...) ou un item custom dont
 * le plugin fournisseur (ItemsAdder, Nexo, Oraxen...) n'est pas installe.
 */
public interface ItemAvailability {

    /**
     * Indique si un fournisseur d'items custom est installe et actif.
     * Jamais appele pour le namespace {@link ItemKey#MINECRAFT}.
     *
     * @param namespace namespace du fournisseur, par exemple {@code itemsadder}
     * @return {@code true} si le fournisseur est present
     */
    boolean providerPresent(String namespace);

    /**
     * Indique si un item existe sur ce serveur (version vanilla ou fournisseur present).
     *
     * @param key cle de l'item
     * @return {@code true} si l'item peut etre depose et recree
     */
    boolean exists(ItemKey key);
}
