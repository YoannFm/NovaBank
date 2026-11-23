package dev.novadev.novabank.core.journal;

import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Suivi propre a un retrait : destinataire et devenir des unites reservees.
 *
 * @param recipient         joueur destinataire des items
 * @param reservedEpoch     epoch de reset de la banque au moment de la reservation
 * @param deliveredQuantity unites remises au joueur
 * @param returnedQuantity  unites rendues a la banque
 * @param returnedValue     valeur re-creditee a la banque avec ces unites
 */
public record WithdrawalProgress(UUID recipient, long reservedEpoch, long deliveredQuantity, long returnedQuantity,
                                 BigDecimal returnedValue) {

    /**
     * Construit un suivi de retrait.
     *
     * @throws IllegalArgumentException si une quantite ou la valeur est negative
     */
    public WithdrawalProgress {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(returnedValue, "returnedValue");
        if (reservedEpoch < 0 || deliveredQuantity < 0 || returnedQuantity < 0 || returnedValue.signum() < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Suivi de retrait negatif"));
        }
        returnedValue = Money.of(returnedValue);
    }

    /**
     * Suivi d'un retrait tout juste reserve : rien de livre ni de rendu.
     *
     * @param recipient     joueur destinataire
     * @param reservedEpoch epoch de reset de la banque a la reservation
     * @return le suivi initial
     */
    public static WithdrawalProgress reserved(UUID recipient, long reservedEpoch) {
        return new WithdrawalProgress(recipient, reservedEpoch, 0L, 0L, Money.ZERO);
    }
}
