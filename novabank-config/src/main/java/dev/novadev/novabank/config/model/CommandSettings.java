package dev.novadev.novabank.config.model;

import java.util.Map;
import java.util.Objects;

/**
 * Section {@code commands} : noms et alias des commandes, appliques au demarrage.
 *
 * @param player commande des joueurs
 * @param admin  commande d'administration
 * @param hosts  commande de banque de chaque plugin hote, par edition ({@code superiorskyblock2} : sous-commande de
 *               {@code /is} ; {@code lands} : commande propre)
 */
public record CommandSettings(CommandLabel player, CommandLabel admin, Map<String, CommandLabel> hosts) {

    /**
     * Verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public CommandSettings {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(admin, "admin");
        hosts = Map.copyOf(hosts);
    }
}
