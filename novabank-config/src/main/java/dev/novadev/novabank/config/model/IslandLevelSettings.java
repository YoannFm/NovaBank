package dev.novadev.novabank.config.model;

import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Valeur de la banque d'une ile ajoutee au niveau de l'ile (sections {@code integrations.superiorskyblock2} et
 * {@code integrations.bentobox}).
 *
 * @param enabled    actif seulement si le plugin vise est installe
 * @param target     bonus alimente (SuperiorSkyblock2 ; toujours LEVEL pour BentoBox)
 * @param multiplier valeur de la banque multipliee par ce coefficient (zero ou plus)
 */
public record IslandLevelSettings(boolean enabled, Target target, BigDecimal multiplier) {

    /** Bonus de SuperiorSkyblock2 qui recoit la valeur de la banque. */
    public enum Target {
        /** Bonus de valeur : le niveau en decoule par la formule de SuperiorSkyblock2. */
        WORTH,
        /** Bonus de niveau : ajoute tel quel au niveau. */
        LEVEL
    }

    /**
     * @throws NullPointerException     si le bonus ou le coefficient est nul
     * @throws IllegalArgumentException si le coefficient est negatif
     */
    public IslandLevelSettings {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(multiplier, "multiplier");
        if (multiplier.signum() < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("multiplier negatif : {0}", multiplier));
        }
    }

    /** @return la configuration par defaut : desactive, bonus de valeur, coefficient 1 */
    public static IslandLevelSettings disabled() {
        return new IslandLevelSettings(false, Target.WORTH, BigDecimal.ONE);
    }
}
