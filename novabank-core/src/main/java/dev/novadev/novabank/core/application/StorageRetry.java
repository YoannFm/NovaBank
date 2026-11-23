package dev.novadev.novabank.core.application;

import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Nouvelles tentatives d'un appel au stockage, sur l'executeur d'entrees/sorties.
 *
 * <p>N'est sur que pour des appels idempotents : les operations de la banque le sont par
 * leur {@link dev.novadev.novabank.api.OperationId}, ce qui permet aussi de lever le doute
 * apres une erreur survenue pendant la validation (le rejeu repond
 * {@code DUPLICATE_OPERATION} si la premiere tentative avait abouti).
 *
 * @param attempts nombre total de tentatives, 1 minimum
 * @param backoff  attente avant la n-ieme nouvelle tentative : n x {@code backoff}
 */
public record StorageRetry(int attempts, Duration backoff) {

    /** Trois tentatives, 200 ms puis 400 ms d'attente. */
    public static final StorageRetry DEFAULT = new StorageRetry(3, Duration.ofMillis(200));

    private static final System.Logger LOGGER = System.getLogger("NovaBank");

    /**
     * @throws IllegalArgumentException si le nombre de tentatives est nul ou l'attente negative
     */
    public StorageRetry {
        Objects.requireNonNull(backoff, "backoff");
        if (attempts < 1 || backoff.isNegative()) {
            throw new IllegalArgumentException(ConsoleText.tr("Tentatives invalides : {0} / {1}", attempts, backoff));
        }
    }

    /**
     * Execute l'appel, avec de nouvelles tentatives sur toute exception.
     *
     * @param description description de l'appel pour le journal
     * @param call        appel idempotent
     * @param <T>         type du resultat
     * @return le resultat, ou vide si toutes les tentatives ont echoue (chaque echec est journalise)
     */
    public <T> Optional<T> call(String description, Supplier<T> call) {
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return Optional.of(call.get());
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                        ConsoleText.tr("Stockage en echec ({0}), tentative {1}/{2} : {3}",
                                description, attempt, attempts, failure.getMessage()));
                if (attempt < attempts && !pause(attempt)) {
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private boolean pause(int attempt) {
        try {
            Thread.sleep(backoff.toMillis() * attempt);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
