package dev.novadev.novabank.bukkit.command;

/**
 * Actions d'administration sur la banque personnelle d'un joueur.
 */
public interface BankAdminActions {

    /**
     * @param actor  administrateur
     * @param label  commande saisie
     * @param player nom ou UUID du joueur
     */
    void bank(CommandActor actor, String label, String player);

    /**
     * @param actor     administrateur
     * @param label     commande saisie
     * @param player    nom ou UUID du joueur
     * @param confirmed {@code true} pour la seconde commande ({@code confirm})
     */
    void reset(CommandActor actor, String label, String player, boolean confirmed);

    /**
     * @param actor  administrateur
     * @param label  commande saisie
     * @param player nom ou UUID du joueur
     * @param page   page, a partir de 1
     */
    void history(CommandActor actor, String label, String player, int page);
}
