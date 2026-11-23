package dev.novadev.novabank.config.section;

import dev.novadev.novabank.config.model.SecuritySettings;
import dev.novadev.novabank.config.tree.ConfigNode;

/**
 * Section {@code security}.
 */
public final class SecuritySectionParser {

    /** Attente par defaut de la fin des operations en cours a l'arret. */
    static final long DEFAULT_DRAIN_TIMEOUT_MS = 10_000L;
    /** Au-dela, l'arret du serveur serait bloque trop longtemps (thread qui desactive le plugin). */
    static final long MAX_DRAIN_TIMEOUT_MS = 60_000L;

    private SecuritySectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section
     */
    public static SecuritySettings parse(ConfigNode root) {
        ConfigNode node = root.section("security");
        return new SecuritySettings(node.bool("save-player-data", true),
                node.longValue("shutdown-drain-timeout-ms", DEFAULT_DRAIN_TIMEOUT_MS, 0L, MAX_DRAIN_TIMEOUT_MS));
    }
}
