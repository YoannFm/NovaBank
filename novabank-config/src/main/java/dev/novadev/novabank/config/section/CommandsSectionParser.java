package dev.novadev.novabank.config.section;

import dev.novadev.novabank.config.model.CommandLabel;
import dev.novadev.novabank.config.model.CommandSettings;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.config.tree.ConfigTrees;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Section {@code commands} : nom et alias de la commande des joueurs et de la commande
 * d'administration. Tous les noms et alias doivent etre distincts.
 */
public final class CommandsSectionParser {

    /** Nom de commande : minuscules, chiffres, '_' ou '-'. */
    static final Pattern LABEL = Pattern.compile("[a-z0-9_-]+");

    /**
     * Hotes pris en charge (edition) et commande de banque par defaut, dans l'ordre du fichier. IridiumSkyblock et
     * SimpleClans ont deja leur {@code bank} (banque d'argent) : la notre s'y appelle {@code coffre}.
     */
    public static final Map<String, CommandLabel> HOST_DEFAULTS = hostDefaults();

    /** Hotes dont la commande de banque est une sous-commande de la leur ({@code /is bank}, {@code /town bank}). */
    public static final Set<String> SUBCOMMAND_HOSTS = Set.of("superiorskyblock2", "bentobox", "iridiumskyblock", "towny",
            "simpleclans");

    private static Map<String, CommandLabel> hostDefaults() {
        CommandLabel bank = new CommandLabel("bank", List.of("banque"));
        CommandLabel chest = new CommandLabel("coffre", List.of("items"));
        Map<String, CommandLabel> defaults = new LinkedHashMap<>();
        defaults.put("superiorskyblock2", bank);
        defaults.put("bentobox", bank);
        defaults.put("iridiumskyblock", chest);
        defaults.put("towny", bank);
        defaults.put("simpleclans", chest);
        defaults.put("lands", new CommandLabel("landbank", List.of()));
        defaults.put("griefprevention", new CommandLabel("claimbank", List.of()));
        defaults.put("worldguard", new CommandLabel("regionbank", List.of()));
        return java.util.Collections.unmodifiableMap(defaults);
    }

    private CommandsSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section
     */
    public static CommandSettings parse(ConfigNode root) {
        ConfigNode node = root.section("commands");
        Set<String> taken = new HashSet<>();
        CommandLabel player = label(node.section("player"), "bank", List.of("banque"), taken);
        CommandLabel admin = label(node.section("admin"), "novabank", List.of(), taken);
        ConfigNode hostsNode = node.section("hosts");
        Map<String, CommandLabel> hosts = new LinkedHashMap<>();
        HOST_DEFAULTS.forEach((edition, defaults) -> {
            // Sous-commande : espace de noms propre a l'hote ; commande propre : partage celui des commandes NovaBank.
            Set<String> names = SUBCOMMAND_HOSTS.contains(edition) ? new HashSet<>() : taken;
            hosts.put(edition, label(hostsNode.section(edition), defaults.name(), defaults.aliases(), names));
        });
        return new CommandSettings(player, admin, hosts);
    }

    private static CommandLabel label(ConfigNode node, String defaultName, List<String> defaultAliases,
                                      Set<String> taken) {
        String name = node.matching("name", defaultName, LABEL, ConfigPatterns.IDENTIFIER_HINT);
        if (!taken.add(name)) {
            node.error("name", ConsoleText.tr("nom de commande '{0}' deja utilise par une autre commande NovaBank",
                    name));
        }
        List<String> raw = node.stringList("aliases", defaultAliases);
        List<String> aliases = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String alias = raw.get(i).toLowerCase(Locale.ROOT);
            String element = ConfigTrees.element("aliases", i);
            if (!LABEL.matcher(alias).matches()) {
                node.error(element, ConsoleText.tr("alias invalide '{0}' (attendu : {1})",
                        raw.get(i), ConfigPatterns.IDENTIFIER_HINT));
            } else if (!taken.add(alias)) {
                node.error(element, ConsoleText.tr("alias '{0}' deja utilise par une commande NovaBank", alias));
            } else {
                aliases.add(alias);
            }
        }
        return new CommandLabel(name, aliases);
    }
}
