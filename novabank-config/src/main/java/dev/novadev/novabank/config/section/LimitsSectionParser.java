package dev.novadev.novabank.config.section;

import dev.novadev.novabank.config.model.LimitSettings;
import dev.novadev.novabank.config.tree.ConfigNode;

/**
 * Section {@code limits} : garde-fous par operation.
 */
public final class LimitsSectionParser {

    private LimitsSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section
     */
    public static LimitSettings parse(ConfigNode root) {
        ConfigNode node = root.section("limits");
        return new LimitSettings(
                node.longValue("max-quantity-per-operation", 2_304, 1, Long.MAX_VALUE),
                node.longValue("cooldown-ms", 500, 0, Long.MAX_VALUE));
    }
}
