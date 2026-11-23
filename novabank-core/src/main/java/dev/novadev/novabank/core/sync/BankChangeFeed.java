package dev.novadev.novabank.core.sync;

import java.time.Instant;
import java.util.List;

/**
 * Lecture des banques modifiees dans le stockage partage. C'est le filet de securite de la
 * synchronisation : la version de la banque est incrementee dans la meme transaction que
 * l'ecriture du stock et du journal, donc un changement enregistre est toujours retrouve ici,
 * meme si Redis etait coupe ou si le serveur auteur est tombe avant de notifier.
 */
public interface BankChangeFeed {

    /**
     * Liste les banques modifiees depuis une date.
     *
     * @param since borne inferieure INCLUSE de la date d'enregistrement
     * @param limit nombre maximal de banques, strictement positif
     * @return les changements par date croissante puis identifiant de banque croissant
     * @throws dev.novadev.novabank.core.repository.StorageException si le stockage est injoignable
     */
    List<BankChange> changesSince(Instant since, int limit);
}
