package dev.novadev.novabank.core.sync;

import java.util.function.Consumer;

/**
 * Bus de notification rapide entre serveurs (Redis). Il n'est jamais la source de verite : un
 * message perdu est rattrape par la lecture du {@link BankChangeFeed}. Une implementation
 * gere elle-meme la reconnexion et ne leve jamais d'exception depuis ses methodes.
 */
public interface SyncBus extends AutoCloseable {

    /**
     * Diffuse un evenement, sans file d'attente.
     *
     * @param event evenement
     * @return {@code true} si le bus l'a accepte, {@code false} s'il est coupe
     */
    boolean publish(SyncEvent event);

    /**
     * Enregistre le receveur des evenements des autres serveurs et des siens (l'appelant filtre).
     * Il est appele depuis un thread du bus, jamais depuis le thread principal du serveur.
     *
     * @param listener receveur, appele sequentiellement
     */
    void subscribe(Consumer<SyncEvent> listener);

    /**
     * Diffuse une commande de controle a tous les serveurs (par exemple {@code reload}).
     *
     * @param origin  serveur emetteur
     * @param command commande, {@code [a-z-]{1,32}}
     * @return {@code true} si le bus l'a acceptee ; {@code false} s'il est coupe ou sans canal de controle
     */
    default boolean publishControl(String origin, String command) {
        return false;
    }

    /**
     * Enregistre le receveur des commandes de controle (les siennes comprises : l'appelant filtre).
     *
     * @param listener receveur (serveur emetteur, commande), appele depuis un thread du bus
     */
    default void subscribeControl(java.util.function.BiConsumer<String, String> listener) {
    }

    /**
     * @return l'etat de la liaison
     */
    SyncBusStatus status();

    /**
     * Ferme la liaison et libere ses threads.
     */
    @Override
    void close();
}
