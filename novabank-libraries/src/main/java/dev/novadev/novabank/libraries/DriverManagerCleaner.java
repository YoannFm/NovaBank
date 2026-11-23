package dev.novadev.novabank.libraries;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

/**
 * Usage interne : desinscrit de {@link DriverManager} les pilotes charges par un chargeur
 * isole.
 *
 * <p>Les pilotes s'inscrivent eux-memes dans {@code DriverManager} lors de l'initialisation
 * de leur classe. NovaBank ne s'en sert pas, mais cette inscription retient le chargeur
 * isole (fuite memoire a chaque rechargement du plugin). {@code DriverManager} n'accepte
 * de desinscrire un pilote que si l'appelant voit sa classe : cette classe n'agit donc que
 * copiee dans le chargeur isole ({@code IsolatedClassLoader#defineCopyOf}). Elle ne doit
 * referencer que des classes du JDK, et n'utilise pas de lambda pour rester une seule
 * classe.
 */
public final class DriverManagerCleaner {

    private DriverManagerCleaner() {
    }

    /**
     * @param loader chargeur dont les pilotes sont a desinscrire
     * @return le nombre de pilotes desinscrits
     * @throws SQLException si {@code DriverManager} refuse une desinscription
     */
    public static int deregisterDriversOf(ClassLoader loader) throws SQLException {
        int count = 0;
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == loader) {
                DriverManager.deregisterDriver(driver);
                count++;
            }
        }
        return count;
    }
}
