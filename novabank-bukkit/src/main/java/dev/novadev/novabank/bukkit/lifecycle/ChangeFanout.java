package dev.novadev.novabank.bukkit.lifecycle;

import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.LocalChangeSink;
import dev.novadev.novabank.core.sync.RemoteChangeListener;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Relaye chaque changement de banque, local ou distant, aux consommateurs enregistres apres coup (la GUI est
 * assemblee apres le stockage et la synchronisation). Un consommateur qui echoue n'empeche pas les autres.
 */
final class ChangeFanout implements LocalChangeSink, RemoteChangeListener {

    private final List<Consumer<BankChange>> consumers = new CopyOnWriteArrayList<>();
    private final Logger logger;

    ChangeFanout(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void add(Consumer<BankChange> consumer) {
        consumers.add(Objects.requireNonNull(consumer, "consumer"));
    }

    @Override
    public void localChange(BankChange change) {
        dispatch(change);
    }

    @Override
    public void onRemoteChange(BankChange change) {
        dispatch(change);
    }

    @Override
    public void noteAnnounceFailure(RuntimeException failure) {
        logger.log(Level.WARNING, ConsoleText.tr("Relais des changements de banque en echec"), failure);
    }

    private void dispatch(BankChange change) {
        for (Consumer<BankChange> consumer : consumers) {
            try {
                consumer.accept(change);
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING,
                        ConsoleText.tr("Relais des changements de banque : un consommateur a echoue"), failure);
            }
        }
    }
}
