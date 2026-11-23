package dev.novadev.novabank.bukkit.host;

import org.bukkit.plugin.Plugin;

/**
 * Integration d'un hote compilee contre son API (sous-classe de sa commande, permissions de son menu), livree dans le
 * JAR de l'edition sous forme de plugin (NovaBank-IridiumSkyblock...) et declaree dans
 * {@code META-INF/services/dev.novadev.novabank.bukkit.host.EditionExtension}. Demarree par {@link NovaBankLauncher}
 * apres le moteur ; elle passe par {@link RemoteBridges}, que le moteur tourne dans ce JAR ou dans un autre.
 */
public interface EditionExtension {

    /**
     * Chargement du plugin de l'edition, avant l'activation des plugins : ce que l'hote exige tot (Lands : flags de
     * role enregistres avant la lecture des terres).
     *
     * @param plugin plugin de l'edition
     * @throws Exception si l'API de l'hote n'est pas celle attendue (signale, jamais fatal)
     */
    default void load(Plugin plugin) throws Exception {
    }

    /**
     * @param plugin plugin de l'edition (son hote est active : dependance du plugin.yml)
     * @return le retrait, a l'arret
     * @throws Exception si l'API de l'hote n'est pas celle attendue (signale, jamais fatal)
     */
    AutoCloseable start(Plugin plugin) throws Exception;
}
