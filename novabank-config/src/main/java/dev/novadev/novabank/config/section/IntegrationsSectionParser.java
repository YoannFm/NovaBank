package dev.novadev.novabank.config.section;

import dev.novadev.novabank.config.model.IntegrationSettings;
import dev.novadev.novabank.config.model.IslandLevelSettings;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Section {@code integrations}.
 */
public final class IntegrationsSectionParser {

    private IntegrationsSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section (tout est desactive si elle est absente)
     */
    public static IntegrationSettings parse(ConfigNode root) {
        ConfigNode integrations = root.section("integrations");
        ConfigNode skyblock = integrations.section("superiorskyblock2");
        IslandLevelSettings superiorSkyblock2 = new IslandLevelSettings(skyblock.bool("island-level", false),
                skyblock.enumValue("apply-to", IslandLevelSettings.Target.class, IslandLevelSettings.Target.WORTH),
                multiplier(skyblock));
        // BentoBox (addon Level) n'a qu'un niveau, sans bonus de valeur : pas de apply-to.
        ConfigNode bentobox = integrations.section("bentobox");
        IslandLevelSettings bentoBox = new IslandLevelSettings(bentobox.bool("island-level", false),
                IslandLevelSettings.Target.LEVEL, multiplier(bentobox));
        return new IntegrationSettings(superiorSkyblock2, bentoBox);
    }

    private static BigDecimal multiplier(ConfigNode node) {
        Optional<String> text = node.optionalText("multiplier");
        if (text.isEmpty()) {
            return BigDecimal.ONE;
        }
        try {
            BigDecimal parsed = new BigDecimal(text.get().trim());
            if (parsed.signum() >= 0) {
                return parsed;
            }
            node.error("multiplier", ConsoleText.tr("zero ou plus attendu, recu : {0}", text.get()));
        } catch (NumberFormatException invalid) {
            node.error("multiplier", ConsoleText.tr("nombre attendu, recu : {0}", text.get()));
        }
        return BigDecimal.ONE;
    }
}
