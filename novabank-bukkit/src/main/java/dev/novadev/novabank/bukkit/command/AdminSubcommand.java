package dev.novadev.novabank.bukkit.command;

import java.util.List;

/**
 * Sous-commande d'administration branchee sur {@link AdminCommandRouter} ({@code gui}, {@code items}...), avec sa
 * permission propre.
 */
public interface AdminSubcommand {

    /** @return permission exigee */
    String permission();

    /**
     * @param actor emetteur (permission deja verifiee)
     * @param label etiquette de la commande
     * @param args  arguments apres le nom de la sous-commande
     */
    void execute(CommandActor actor, String label, String[] args);

    /**
     * @param args arguments apres le nom de la sous-commande (le dernier est en cours de saisie)
     * @return propositions de completion
     */
    List<String> complete(String[] args);
}
