package dev.novadev.novabank.config.section;

import static dev.novadev.novabank.config.section.ConfigPatterns.IDENTIFIER;
import static dev.novadev.novabank.config.section.ConfigPatterns.IDENTIFIER_HINT;

import dev.novadev.novabank.config.model.ServerSettings;
import dev.novadev.novabank.config.tree.ConfigNode;

/**
 * Section {@code server}.
 */
public final class ServerSectionParser {

    private ServerSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section
     */
    public static ServerSettings parse(ConfigNode root) {
        ConfigNode node = root.section("server");
        return new ServerSettings(
                node.matching("id", "main", IDENTIFIER, IDENTIFIER_HINT),
                node.matching("group", "default", IDENTIFIER, IDENTIFIER_HINT));
    }
}
