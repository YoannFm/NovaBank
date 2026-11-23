package dev.novadev.novabank.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant unique d'une operation bancaire (depot, retrait, reset).
 *
 * <p>Il est cree par l'appelant <em>avant</em> la premiere tentative et reutilise
 * a chaque nouvelle tentative de la meme operation : NovaBank garantit qu'une
 * operation portant un identifiant deja enregistre ne produit jamais un second
 * effet (idempotence, protection anti-duplication).
 *
 * @param value UUID de l'operation, non nul
 */
public record OperationId(UUID value) {

    /**
     * Construit un identifiant d'operation.
     *
     * @throws NullPointerException si {@code value} est nul
     */
    public OperationId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Cree un nouvel identifiant aleatoire, a utiliser pour une nouvelle operation.
     *
     * @return un identifiant d'operation inedit
     */
    public static OperationId random() {
        return new OperationId(UUID.randomUUID());
    }

    /**
     * Forme textuelle : le UUID canonique.
     *
     * @return le UUID au format standard
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
