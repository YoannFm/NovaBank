package dev.novadev.novabank.config.catalog;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.valuation.PriceCatalog;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Catalogue remplacable a chaud (rechargement de la configuration), sans verrou.
 *
 * <p>Chaque appel a {@link #unitPrice(ItemKey)} lit le catalogue courant au moment de
 * l'appel. Un traitement qui consulte plusieurs prix et doit voir une version unique
 * prend d'abord un instantane avec {@link #current()}.
 */
public final class ReloadablePriceCatalog implements PriceCatalog {

    private final AtomicReference<PriceCatalog> current;

    /**
     * Cree le detenteur avec un catalogue initial.
     *
     * @param initial catalogue initial
     */
    public ReloadablePriceCatalog(PriceCatalog initial) {
        this.current = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    @Override
    public Optional<BigDecimal> unitPrice(ItemKey item) {
        return current.get().unitPrice(item);
    }

    /**
     * Instantane du catalogue courant.
     *
     * @return le catalogue en vigueur
     */
    public PriceCatalog current() {
        return current.get();
    }

    /**
     * Remplace le catalogue de facon atomique.
     *
     * @param next nouveau catalogue
     * @return le catalogue remplace
     */
    public PriceCatalog replace(PriceCatalog next) {
        return current.getAndSet(Objects.requireNonNull(next, "next"));
    }
}
