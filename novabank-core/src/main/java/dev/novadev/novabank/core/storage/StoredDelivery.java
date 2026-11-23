package dev.novadev.novabank.core.storage;

import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Instant;
import java.util.Objects;

/**
 * Etat complet d'une livraison, pour la copie entre backends : quantite due a l'origine,
 * quantite deja remise et date d'enregistrement (ordre de remise). Une livraison entierement
 * remise reste copiee (elle empeche un nouvel ajout pour la meme operation).
 *
 * @param delivery          livraison d'origine, {@code quantity} = quantite due a l'enregistrement
 * @param deliveredQuantity unites deja remises, entre 0 et la quantite due
 * @param createdAt         date d'enregistrement, ramenee a la milliseconde (precision commune
 *                          a tous les backends)
 */
public record StoredDelivery(PendingDelivery delivery, long deliveredQuantity, Instant createdAt) {

    /**
     * @throws IllegalArgumentException si la quantite remise est hors de [0, quantite due]
     */
    public StoredDelivery {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(createdAt, "createdAt");
        if (deliveredQuantity < 0 || deliveredQuantity > delivery.quantity()) {
            throw new IllegalArgumentException(ConsoleText.tr("Quantite remise hors bornes : {0} / {1}",
                    deliveredQuantity, delivery.quantity()));
        }
        createdAt = Instant.ofEpochMilli(createdAt.toEpochMilli());
    }

    /**
     * @return la quantite restant due
     */
    public long remainingQuantity() {
        return delivery.quantity() - deliveredQuantity;
    }
}
