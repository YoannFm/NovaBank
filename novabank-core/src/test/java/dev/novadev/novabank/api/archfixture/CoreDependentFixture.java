package dev.novadev.novabank.api.archfixture;

import dev.novadev.novabank.core.money.Money;
import java.math.BigDecimal;

/**
 * Classe volontairement fautive : un type du package API qui depend du coeur.
 * Sert uniquement a prouver que {@code ApiArchitectureTest} detecte la violation.
 */
public final class CoreDependentFixture {

    private CoreDependentFixture() {
    }

    /**
     * @return une valeur calculee par le coeur
     */
    public static BigDecimal leak() {
        return Money.ZERO;
    }
}
