package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Rejeu des tentatives perdues sur conflit (version changee, conflit d'ecriture de transaction),
 * avec une attente aleatoire croissante, dans un budget de temps.
 *
 * <p>MongoDB n'a pas de verrou de ligne attendu comme {@code SELECT ... FOR UPDATE} : sous forte
 * concurrence sur une meme banque, une operation peut perdre plusieurs fois avant de passer. Le
 * budget des conflits est en temps, pas en nombre d'essais.
 *
 * <p>Une erreur transitoire ({@link TransientFailure}) est rejouee au plus
 * {@value #MAX_TRANSIENT_RETRIES} fois de suite, puis son erreur d'origine remonte : une panne
 * du serveur se signale comme une panne, pas comme un conflit.
 */
final class Contention {

    /** Budget par defaut d'une operation. */
    static final Duration DEFAULT_BUDGET = Duration.ofSeconds(30);

    /** Rejeux consecutifs maximaux d'une erreur transitoire. */
    static final int MAX_TRANSIENT_RETRIES = 2;

    private static final int MAX_PAUSE_MS = 50;

    /**
     * Une tentative : un resultat, ou vide si elle a perdu un conflit et doit etre rejouee.
     *
     * @param <T> type du resultat
     */
    @FunctionalInterface
    interface Attempt<T> {
        Optional<T> run();
    }

    private final Duration budget;

    Contention(Duration budget) {
        this.budget = budget;
    }

    /**
     * @param attempt tentative pure (sans effet quand elle renvoie vide)
     * @param <T>     type du resultat
     * @return le resultat, ou vide si le budget est epuise par des conflits
     * @throws com.mongodb.MongoException l'erreur d'origine d'une erreur transitoire repetee
     * @throws StorageException           si le thread est interrompu
     */
    <T> Optional<T> retry(Attempt<T> attempt) {
        long deadline = System.nanoTime() + budget.toNanos();
        int transientFailures = 0;
        for (int lost = 0; ; lost++) {
            Optional<T> result;
            try {
                result = attempt.run();
                transientFailures = 0;
            } catch (TransientFailure failure) {
                if (++transientFailures > MAX_TRANSIENT_RETRIES || System.nanoTime() - deadline >= 0) {
                    throw failure.getCause();
                }
                result = Optional.empty();
            }
            if (result.isPresent() || System.nanoTime() - deadline >= 0) {
                return result;
            }
            pause(lost);
        }
    }

    private static void pause(int lost) {
        int bound = Math.min(1 << Math.min(lost, 6), MAX_PAUSE_MS);
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(bound + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new StorageException(
                    ConsoleText.tr("MongoDB : attente interrompue entre deux tentatives"), interrupted);
        }
    }
}
