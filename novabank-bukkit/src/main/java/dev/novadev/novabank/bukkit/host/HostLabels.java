package dev.novadev.novabank.bukkit.host;

import dev.novadev.novabank.bukkit.lifecycle.DataFolders;
import dev.novadev.novabank.config.model.CommandLabel;
import dev.novadev.novabank.config.section.CommandsSectionParser;
import java.io.File;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Nom et alias de la commande de banque d'un hote ({@code commands.hosts.<edition>}), lus directement dans
 * le {@code config.yml} en service ({@link DataFolders}) : le JAR d'une edition ne fait pas forcement tourner le
 * moteur. Valeurs par defaut si le fichier n'existe pas encore (premier demarrage).
 */
public final class HostLabels {

    private HostLabels() {
    }

    /**
     * @param anyPlugin un plugin du serveur (pour trouver {@code plugins/})
     * @param edition   edition de l'hote
     * @return le nom et les alias configures
     */
    public static CommandLabel read(Plugin anyPlugin, String edition) {
        File config = DataFolders.locate(anyPlugin).resolve("config.yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
        String path = "commands.hosts." + edition;
        CommandLabel defaults = CommandsSectionParser.HOST_DEFAULTS.getOrDefault(edition,
                new CommandLabel("bank", List.of()));
        String name = yaml.getString(path + ".name", defaults.name());
        List<String> aliases = yaml.isList(path + ".aliases") ? yaml.getStringList(path + ".aliases")
                : defaults.aliases();
        return new CommandLabel(name, aliases);
    }

    /**
     * Texte de la langue du serveur pour un JAR d'edition qui ne fait pas forcement tourner le moteur : fichier
     * {@code lang/<langue>.yml} du dossier en service, sinon le fichier livre dans ce JAR, sinon {@code fallback}.
     *
     * @param anyPlugin un plugin du serveur (pour trouver {@code plugins/})
     * @param key       cle du texte ({@code bank.clan-description})
     * @param fallback  texte si la cle est introuvable
     * @return le texte, couleurs appliquees
     */
    public static String text(Plugin anyPlugin, String key, String fallback) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', texts(anyPlugin).getString(key, fallback));
    }

    /**
     * @param anyPlugin un plugin du serveur
     * @param key       cle du texte ({@code general.not-ready})
     * @param fallback  texte si la cle est introuvable
     * @return le message de chat : prefixe de NovaBank puis le texte, couleurs appliquees
     */
    public static String chat(Plugin anyPlugin, String key, String fallback) {
        YamlConfiguration texts = texts(anyPlugin);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
                texts.getString("prefix", "") + texts.getString(key, fallback));
    }

    private static YamlConfiguration texts(Plugin anyPlugin) {
        File folder = DataFolders.locate(anyPlugin).toFile();
        String language = YamlConfiguration.loadConfiguration(new File(folder, "config.yml")).getString("language", "fr");
        YamlConfiguration texts = YamlConfiguration.loadConfiguration(new File(folder, "lang/" + language + ".yml"));
        try (java.io.InputStream shipped = HostLabels.class.getResourceAsStream("/lang/" + language + ".yml")) {
            if (shipped != null) {
                texts.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(shipped, java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException unreadable) {
            // le texte de secours de l'appelant s'applique
        }
        return texts;
    }
}
