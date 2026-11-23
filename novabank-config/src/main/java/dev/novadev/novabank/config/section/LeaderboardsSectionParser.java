package dev.novadev.novabank.config.section;

import dev.novadev.novabank.config.model.LeaderboardSettings;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Section {@code leaderboards} : un classement par type d'entite active. Un type
 * active sans entree prend les valeurs par defaut ; une entree pour un type non active
 * est ignoree avec un avertissement.
 */
public final class LeaderboardsSectionParser {

    private LeaderboardsSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root        racine de la configuration
     * @param entityTypes types d'entite actives ({@code banks.entity-types})
     * @return un classement par type active, dans l'ordre des types, non modifiable
     */
    public static Map<String, LeaderboardSettings> parse(ConfigNode root, List<String> entityTypes) {
        ConfigNode node = root.section("leaderboards");
        node.keys().stream()
                .filter(type -> !entityTypes.contains(type))
                .forEach(type -> node.ignore(type,
                        ConsoleText.tr("type d'entite non active dans banks.entity-types : classement ignore")));
        Map<String, LeaderboardSettings> leaderboards = new LinkedHashMap<>();
        for (String type : entityTypes) {
            ConfigNode section = node.section(type);
            leaderboards.put(type, new LeaderboardSettings(
                    section.bool("enabled", true),
                    section.intValue("size", 10, 1, 1_000),
                    section.intValue("refresh-seconds", 60, 5, 86_400),
                    section.bool("per-category", false)));
        }
        return Collections.unmodifiableMap(leaderboards);
    }
}
