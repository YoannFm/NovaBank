package dev.novadev.novabank.bukkit.command;

import java.util.Optional;
import java.util.UUID;

/**
 * Emetteur d'une commande, vu par la logique de commande (testable sans serveur).
 */
public interface CommandActor {

    /**
     * @param permission permission Bukkit
     * @return {@code true} si l'emetteur la possede
     */
    boolean hasPermission(String permission);

    /**
     * @return {@code true} pour la console du serveur (ce qu'elle recoit est deja journalise)
     */
    boolean isConsole();

    /**
     * Envoie un message ; appele depuis le thread de la commande, celui du joueur ou le
     * contexte global (envoi de message sur pour plusieurs threads sous Paper et Folia).
     *
     * @param message texte
     */
    void send(String message);

    /**
     * Envoie une ligne cliquable qui PRE-REMPLIT la barre de saisie (sans l'executer). Sans chat interactif (console),
     * c'est un simple message.
     *
     * @param message texte
     * @param command saisie proposee
     * @param hover   texte de survol
     */
    default void sendSuggestion(String message, String command, String hover) {
        send(message);
    }

    /**
     * Envoie une ligne dont le clic ouvre un lien. Sans chat interactif (console), c'est un simple message.
     *
     * @param message texte
     * @param url     lien
     * @param hover   texte de survol
     */
    default void sendLink(String message, String url, String hover) {
        send(message);
    }

    /**
     * @return l'UUID du joueur emetteur, vide pour la console ou un bloc de commande
     */
    Optional<UUID> playerId();

    /**
     * @return nom de l'emetteur, pour le journal
     */
    String name();
}
