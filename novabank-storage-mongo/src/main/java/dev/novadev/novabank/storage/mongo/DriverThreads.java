package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.core.text.ConsoleText;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Arret du seul thread du pilote qui survit a {@code MongoClient.close()} : l'elagueur
 * ({@code BufferPoolPruner}) du pool de tampons partage {@code PowerOfTwoBufferPool.DEFAULT},
 * cree une fois par chargeur de classes et jamais arrete par le pilote. Sans cet arret, il
 * retiendrait le chargeur du module apres la fermeture du plugin.
 *
 * <p>API interne du pilote (methode {@code disablePruning}, non publique) appelee par reflexion :
 * possible car la version du pilote est epinglee (catalogue) et testee ; si elle change de forme,
 * l'echec est journalise et la fermeture continue (le chargeur de modules signale alors le thread
 * restant).
 */
final class DriverThreads {

    private static final String POOL_CLASS = "com.mongodb.internal.connection.PowerOfTwoBufferPool";

    private DriverThreads() {
    }

    /**
     * @param logger journal en cas d'echec
     */
    static void stopBufferPoolPruner(Logger logger) {
        try {
            Class<?> pool = Class.forName(POOL_CLASS, false, DriverThreads.class.getClassLoader());
            Field defaultPool = pool.getField("DEFAULT");
            Method disablePruning = pool.getDeclaredMethod("disablePruning");
            disablePruning.setAccessible(true);
            disablePruning.invoke(defaultPool.get(null));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logger.log(Level.WARNING,
                    ConsoleText.tr("MongoDB : arret de l'elagueur de tampons du pilote impossible"), failure);
        }
    }
}
