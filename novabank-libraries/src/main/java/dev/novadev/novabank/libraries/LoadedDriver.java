package dev.novadev.novabank.libraries;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.catalog.DriverSpec;
import dev.novadev.novabank.libraries.classloading.IsolatedClassLoader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Driver;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pilote JDBC charge dans son chargeur isole. L'instance de {@link Driver} se transmet au
 * stockage SQL ({@code DriverDataSource}).
 *
 * <p>A fermer apres le stockage (pool ferme) : la fermeture desinscrit le pilote de
 * {@code DriverManager} (inscription faite par le pilote lui-meme) et ferme le chargeur.
 */
public final class LoadedDriver implements AutoCloseable {

    private final DriverSpec spec;
    private final Driver driver;
    private final IsolatedClassLoader loader;
    private final LibraryLogger logger;
    private final AtomicBoolean closed = new AtomicBoolean();

    LoadedDriver(DriverSpec spec, Driver driver, IsolatedClassLoader loader, LibraryLogger logger) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.driver = Objects.requireNonNull(driver, "driver");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @return le pilote decrit
     */
    public DriverSpec spec() {
        return spec;
    }

    /**
     * @return l'instance du pilote, a passer au stockage SQL
     */
    public Driver driver() {
        return driver;
    }

    /**
     * Desinscrit le pilote de {@code DriverManager} et ferme le chargeur isole. Sans effet
     * apres le premier appel.
     *
     * @throws LibraryException si la desinscription ou la fermeture echoue
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try (IsolatedClassLoader closing = loader) {
            int count = deregister(closing);
            logger.info(ConsoleText.tr("Pilote {0} decharge ({1} inscription(s) DriverManager retiree(s))",
                    spec.id(), count));
        } catch (IOException failure) {
            throw new LibraryException(ConsoleText.tr("Fermeture du chargeur du pilote {0} impossible",
                    spec.id()), failure);
        }
    }

    private int deregister(IsolatedClassLoader closing) {
        try {
            return (Integer) closing.defineCopyOf(DriverManagerCleaner.class)
                    .getMethod("deregisterDriversOf", ClassLoader.class)
                    .invoke(null, closing);
        } catch (InvocationTargetException failure) {
            throw new LibraryException(ConsoleText.tr("Desinscription du pilote {0} impossible",
                    spec.id()), failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new LibraryException(ConsoleText.tr("Desinscription du pilote {0} impossible", spec.id()), failure);
        }
    }
}
