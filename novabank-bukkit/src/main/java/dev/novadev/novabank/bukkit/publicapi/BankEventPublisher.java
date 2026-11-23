package dev.novadev.novabank.bukkit.publicapi;

import dev.novadev.novabank.api.event.BankOperationEvent;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.LocalChangeSink;
import dev.novadev.novabank.core.application.OperationActors;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginManager;

/**
 * Publie un {@link BankOperationEvent} (asynchrone) pour chaque operation enregistree par ce serveur. Une
 * exception d'un ecouteur est journalisee et n'affecte ni l'operation ni les autres ecouteurs.
 */
public final class BankEventPublisher implements LocalChangeSink {

    private final PluginManager manager;
    private final Logger logger;
    private final OperationActors actors;

    /**
     * @param manager gestionnaire de plugins du serveur
     * @param logger  journal du plugin
     * @param actors  joueur a l'origine des operations lancees sur ce serveur
     */
    public BankEventPublisher(PluginManager manager, Logger logger, OperationActors actors) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.actors = Objects.requireNonNull(actors, "actors");
    }

    @Override
    public void localChange(BankChange change) {
        // Les evenements sont publies par applied(), qui porte le detail de l'operation.
    }

    @Override
    public void applied(Bank bank, OperationRecord record) {
        try {
            publish(new BankOperationEvent(record.operationId(), bank.id(), bank.owner(), record.type(),
                    record.state().name(), record.itemKey(), record.quantity(), record.valueDelta(), bank.version(),
                    actors.actor(record.operationId()).orElse(null)));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, ConsoleText.tr("Evenement BankOperationEvent : un ecouteur a echoue"), failure);
        }
    }

    @Override
    public void bankCreated(Bank bank) {
        try {
            publish(new dev.novadev.novabank.api.event.BankCreatedEvent(bank.id(), bank.owner()));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, ConsoleText.tr("Evenement BankCreatedEvent : un ecouteur a echoue"), failure);
        }
    }

    /**
     * Pendant l'arret, Spigot considere tout thread comme principal et refuse un evenement asynchrone
     * ({@link IllegalStateException}) : l'operation est deja enregistree, l'evenement est simplement omis.
     */
    private void publish(org.bukkit.event.Event event) {
        try {
            manager.callEvent(event);
        } catch (IllegalStateException stopping) {
            logger.log(Level.FINE, ConsoleText.tr("Evenement {0} omis (arret du serveur)",
                    event.getEventName()), stopping);
        }
    }

    @Override
    public void noteAnnounceFailure(RuntimeException failure) {
        logger.log(Level.WARNING, ConsoleText.tr("Evenement BankOperationEvent : publication en echec"), failure);
    }
}
