package dev.novadev.novabank.bukkit.lifecycle;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ressources ouvertes par le plugin, fermees dans l'ordre inverse de leur enregistrement.
 *
 * <p>Une ressource en echec a la fermeture n'empeche pas la fermeture des suivantes ;
 * l'echec est journalise. La fermeture est idempotente. Utilise depuis le thread qui active
 * ou desactive le plugin (thread principal sous Bukkit ; thread d'arret du serveur sous Folia).
 */
public final class Lifecycle implements AutoCloseable {

    private final Deque<Resource> resources = new ArrayDeque<>();
    private final Logger logger;
    private boolean closed;

    /**
     * @param logger journal des echecs de fermeture
     */
    public Lifecycle(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Enregistre une ressource deja ouverte.
     *
     * @param name     nom affiche en cas d'echec de fermeture
     * @param resource ressource
     * @param <T>      type de la ressource
     * @return la ressource, pour chainer
     * @throws IllegalStateException si le cycle de vie est deja ferme (la ressource est alors fermee)
     */
    public <T extends AutoCloseable> T register(String name, T resource) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resource, "resource");
        if (closed) {
            closeQuietly(new Resource(name, resource));
            throw new IllegalStateException(ConsoleText.tr("Cycle de vie ferme : {0} refuse", name));
        }
        resources.push(new Resource(name, resource));
        return resource;
    }

    /**
     * Ferme toutes les ressources, de la plus recente a la plus ancienne.
     */
    @Override
    public void close() {
        closed = true;
        while (!resources.isEmpty()) {
            closeQuietly(resources.pop());
        }
    }

    private void closeQuietly(Resource resource) {
        try {
            resource.closeable().close();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.warning(ConsoleText.tr("Fermeture de {0} interrompue", resource.name()));
        } catch (Exception | LinkageError failure) {
            logger.log(Level.SEVERE, ConsoleText.tr("Fermeture de {0} impossible : {1}",
                    resource.name(), failure), failure);
        }
    }

    private record Resource(String name, AutoCloseable closeable) {
    }
}
