package dev.novadev.novabank.core.sync;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Instant;
import java.util.Objects;

/**
 * Une banque a change : elle est maintenant a cette version. Les changements successifs d'une
 * meme banque se fondent en un seul (seule la derniere version compte pour invalider un cache).
 *
 * @param bankId     banque modifiee
 * @param version    version enregistree dans le stockage, positive ou nulle
 * @param resetEpoch nombre de reinitialisations subies, positif ou nul
 * @param updatedAt  date d'enregistrement par le stockage
 */
public record BankChange(BankId bankId, long version, long resetEpoch, Instant updatedAt) {

    /**
     * @throws NullPointerException     si l'identifiant ou la date est nul
     * @throws IllegalArgumentException si la version ou l'epoch est negative
     */
    public BankChange {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0 || resetEpoch < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Version ou epoch negative : {0} / {1}",
                    version, resetEpoch));
        }
    }
}
