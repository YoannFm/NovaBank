package dev.novadev.novabank.config.model;

import java.util.List;
import java.util.Objects;

/**
 * Nom et alias d'une commande.
 *
 * @param name    nom principal ({@code [a-z0-9_-]+})
 * @param aliases alias, sans doublon
 */
public record CommandLabel(String name, List<String> aliases) {

    /**
     * Fige les alias.
     *
     * @throws NullPointerException si un champ est nul
     */
    public CommandLabel {
        Objects.requireNonNull(name, "name");
        aliases = List.copyOf(aliases);
    }
}
