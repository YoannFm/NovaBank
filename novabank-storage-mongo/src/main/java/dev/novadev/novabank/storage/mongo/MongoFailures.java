package dev.novadev.novabank.storage.mongo;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Traduction des erreurs du pilote en {@link StorageException}, sans jamais exposer l'URI ni le
 * mot de passe (D5).
 */
final class MongoFailures {

    private static final String MASK = "****";
    private static final int DUPLICATE_KEY = 11000;

    private final List<String> secrets;

    /**
     * @param settings reglages dont les secrets (URI complete, mot de passe) sont masques
     */
    MongoFailures(MongoConnectionSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.secrets = settings.password().isEmpty()
                ? List.of(settings.uri().getConnectionString())
                : List.of(settings.uri().getConnectionString(), settings.password());
    }

    /**
     * @param action  action en cours, en francais
     * @param failure erreur du pilote
     * @return l'exception du coeur, cause conservee
     */
    StorageException storage(String action, MongoException failure) {
        return new StorageException(ConsoleText.tr("MongoDB : {0} impossible : {1}",
                action, mask(failure.getMessage())), failure);
    }

    /**
     * Execute un appel au pilote et traduit ses erreurs.
     *
     * @param action action en cours, en francais (message d'erreur)
     * @param work   appel au pilote
     * @param <T>    type du resultat
     * @return le resultat de l'appel
     * @throws StorageException si le pilote signale une erreur (message expurge)
     */
    <T> T call(String action, Supplier<T> work) {
        try {
            return work.get();
        } catch (MongoException failure) {
            throw storage(action, failure);
        }
    }

    /**
     * @param text texte quelconque
     * @return le texte sans les secrets de connexion
     */
    String mask(String text) {
        String masked = String.valueOf(text);
        for (String secret : secrets) {
            masked = masked.replace(secret, MASK);
        }
        return masked;
    }

    /**
     * @param failure erreur du pilote
     * @return {@code true} pour une violation d'index unique (identifiant deja present)
     */
    static boolean isDuplicateKey(MongoException failure) {
        if (failure instanceof MongoWriteException write) {
            return write.getError().getCategory() == ErrorCategory.DUPLICATE_KEY;
        }
        return failure.getCode() == DUPLICATE_KEY;
    }
}
