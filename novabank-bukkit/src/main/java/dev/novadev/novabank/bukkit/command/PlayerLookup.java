package dev.novadev.novabank.bukkit.command;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Recherche d'un joueur par son nom (ou son UUID), connecte ou non.
 */
public interface PlayerLookup {

    /**
     * @param name nom ou UUID saisi
     * @param done joueur trouve, ou vide ; appele dans le contexte global
     */
    void find(String name, Consumer<Optional<KnownPlayer>> done);

    /**
     * @param id   UUID du joueur
     * @param name nom connu
     */
    record KnownPlayer(UUID id, String name) {

        /**
         * @throws NullPointerException si un champ est nul
         */
        public KnownPlayer {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }
}
