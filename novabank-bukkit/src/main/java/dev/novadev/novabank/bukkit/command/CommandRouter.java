package dev.novadev.novabank.bukkit.command;

import java.util.List;

/**
 * Logique d'une commande, sans dependance serveur : execution et completion.
 */
public interface CommandRouter {

    /**
     * Execute la commande sur le thread de l'emetteur : thread principal sous Bukkit ; sous
     * Folia, region du joueur (ou region globale pour la console).
     *
     * @param actor emetteur
     * @param label nom ou alias saisi
     * @param args  arguments
     */
    void execute(CommandActor actor, String label, String[] args);

    /**
     * @param actor emetteur
     * @param args  arguments en cours de saisie (le dernier est partiel)
     * @return propositions pour le dernier argument
     */
    List<String> complete(CommandActor actor, String[] args);
}
