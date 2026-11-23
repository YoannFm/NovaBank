package dev.novadev.novabank.api;

import dev.novadev.novabank.api.entity.EntityRef;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant unique et stable d'une banque.
 *
 * <p>Un identifiant ne change jamais pendant la vie d'une banque, quel que soit
 * le backend de stockage utilise. La banque d'une entite a un identifiant
 * <em>deterministe</em> ({@link #forOwner(EntityRef)}) : deux serveurs qui creent en meme
 * temps la banque de la meme entite designent la meme banque, et la creation reste
 * idempotente.
 *
 * @param value UUID de la banque, non nul
 */
public record BankId(UUID value) {

    /**
     * Construit un identifiant de banque.
     *
     * @throws NullPointerException si {@code value} est nul
     */
    public BankId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Identifiant de la banque d'une entite : UUID de type 3 (base sur un nom) de
     * {@code "novabank:" + type + ":" + id}. Toujours le meme pour la meme entite.
     *
     * @param owner entite proprietaire
     * @return l'identifiant de sa banque
     */
    public static BankId forOwner(EntityRef owner) {
        Objects.requireNonNull(owner, "owner");
        String name = "novabank:" + owner.type() + ':' + owner.id();
        return new BankId(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
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
