package dev.novadev.novabank.bukkit.command;

/**
 * Actions d'administration declenchees par la commande.
 */
public interface AdminActions {

    /**
     * Collecte le diagnostic (requete en base sur l'executeur IO) puis l'envoie.
     *
     * @param actor destinataire
     */
    void diagnostic(CommandActor actor);

    /**
     * Recharge la configuration et envoie le rapport.
     *
     * @param actor destinataire
     */
    void reload(CommandActor actor);
}
