package dev.novadev.novabank.core.ranking;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ligne d'un classement.
 *
 * @param rank   rang a partir de 1 (deux banques de meme valeur ont des rangs consecutifs, departagees par entite)
 * @param bankId banque
 * @param owner  entite proprietaire
 * @param value  valeur de la banque
 */
public record RankedEntry(int rank, BankId bankId, EntityRef owner, BigDecimal value) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public RankedEntry {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(value, "value");
    }
}
