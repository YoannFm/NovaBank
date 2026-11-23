package dev.novadev.novabank.libraries.module;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.LibraryLogger;
import dev.novadev.novabank.libraries.classloading.IsolatedClassLoader;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Module embarque charge : instance de sa classe d'entree et chargeur isole qui la porte.
 *
 * <p>A fermer APRES les ressources creees par l'entree (backend ferme) : la fermeture attend la fin
 * des threads crees sous {@link #withContextClassLoader} (ils heritent du chargeur du module comme
 * chargeur de contexte, par exemple les threads de surveillance d'un pilote, qui chargent encore
 * des classes en s'arretant), puis ferme le chargeur et ses jars.
 *
 * @param <T> type de l'entree (interface du coeur, par exemple une fabrique de backend)
 */
public final class LoadedModule<T> implements AutoCloseable {

    private final EmbeddedModule module;
    private final T entry;
    private final IsolatedClassLoader loader;
    private final LibraryLogger logger;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Duration threadsTimeout;

    LoadedModule(EmbeddedModule module, T entry, IsolatedClassLoader loader, LibraryLogger logger,
                 Duration threadsTimeout) {
        this.module = Objects.requireNonNull(module, "module");
        this.entry = Objects.requireNonNull(entry, "entry");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.threadsTimeout = Objects.requireNonNull(threadsTimeout, "threadsTimeout");
    }

    /**
     * @return la description du module
     */
    public EmbeddedModule module() {
        return module;
    }

    /**
     * @return l'instance de la classe d'entree
     */
    public T entry() {
        return entry;
    }

    /**
     * @return le chargeur isole du module (diagnostic, tests)
     */
    public ClassLoader classLoader() {
        return loader;
    }

    /**
     * Execute une action avec le chargeur du module comme chargeur de contexte du thread : les
     * recherches de services faites par le pilote (et les threads qu'il cree pendant l'action, qui
     * heritent du chargeur de contexte) voient le module et non le plugin.
     *
     * @param action action a executer
     * @param <R>    type du resultat
     * @return le resultat de l'action
     */
    public <R> R withContextClassLoader(Supplier<R> action) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /**
     * Attend la fin des threads du module, puis ferme le chargeur isole. Sans effet apres le
     * premier appel. Un thread encore actif apres le delai est signale (il pourra echouer en
     * chargeant une classe) mais n'empeche pas la fermeture.
     *
     * @throws LibraryException si la fermeture echoue
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<String> remaining = awaitModuleThreads();
        if (!remaining.isEmpty()) {
            logger.warning(ConsoleText.tr("Module {0} : thread(s) encore actif(s) a la fermeture : {1}",
                    module.id(), remaining));
        }
        try {
            loader.close();
            logger.info(ConsoleText.tr("Module {0} decharge", module.id()));
        } catch (IOException failure) {
            throw new LibraryException(ConsoleText.tr("Fermeture du chargeur du module {0} impossible",
                    module.id()), failure);
        }
    }

    private List<String> awaitModuleThreads() {
        long deadline = System.nanoTime() + threadsTimeout.toNanos();
        List<String> remaining = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == Thread.currentThread() || thread.getContextClassLoader() != loader) {
                continue;
            }
            try {
                long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (left > 0) {
                    thread.join(left);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                remaining.add(thread.getName());
            }
        }
        return remaining;
    }
}
