package dev.novadev.novabank.config.section;

import dev.novadev.novabank.config.model.DiagnosticsSettings;
import dev.novadev.novabank.config.tree.ConfigNode;

/**
 * Section {@code diagnostics}.
 */
public final class DiagnosticsSectionParser {

    private DiagnosticsSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section
     */
    public static DiagnosticsSettings parse(ConfigNode root) {
        ConfigNode node = root.section("diagnostics");
        return new DiagnosticsSettings(node.bool("debug", false), node.bool("log-operations", false));
    }
}
