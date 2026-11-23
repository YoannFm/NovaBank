package dev.novadev.novabank.core.sync;

/**
 * Fabrique d'un bus de synchronisation.
 */
public interface SyncBusFactory {

    /**
     * Ouvre le bus. Une indisponibilite du serveur Redis n'est pas une erreur : le bus se
     * reconnecte en arriere-plan et {@link SyncBus#status()} la signale.
     *
     * @return le bus, a fermer par l'appelant
     */
    SyncBus open();
}
