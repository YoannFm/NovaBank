package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.config.model.StorageType;

/**
 * Migration du stockage declenchee par la commande d'administration.
 */
public interface MigrationActions {

    /**
     * @param actor     administrateur
     * @param label     commande saisie
     * @param target    type de stockage vise
     * @param argument  type tel que saisi (repris dans les messages)
     * @param confirmed {@code true} pour la seconde commande ({@code confirm})
     */
    void migrate(CommandActor actor, String label, StorageType target, String argument, boolean confirmed);
}
