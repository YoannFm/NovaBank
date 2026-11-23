package dev.novadev.novabank.api.ranking;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ligne d'un classement.
 *
 * @param rank  rang a partir de 1
 * @param bank  banque
 * @param owner entite proprietaire
 * @param value valeur de la banque
 */
public record LeaderboardLine(int rank, BankId bank, EntityRef owner, BigDecimal value) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public LeaderboardLine {
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(value, "value");
    }
}
