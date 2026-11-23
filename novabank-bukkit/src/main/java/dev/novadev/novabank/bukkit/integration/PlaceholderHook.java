package dev.novadev.novabank.bukkit.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Enregistre l'expansion {@code %novabank_...%} de PlaceholderAPI. Cette classe est la seule a toucher aux classes de
 * PlaceholderAPI : elle n'est chargee que si le plugin est installe.
 */
public final class PlaceholderHook implements AutoCloseable {

    private final PlaceholderExpansion expansion;

    private PlaceholderHook(PlaceholderExpansion expansion) {
        this.expansion = expansion;
    }

    /**
     * @param author  auteurs de NovaBank
     * @param version version de NovaBank
     * @param values  textes des placeholders
     * @return l'enregistrement, a fermer a l'arret
     */
    public static PlaceholderHook register(String author, String version, PlaceholderValues values) {
        PlaceholderExpansion expansion = new PlaceholderExpansion() {
            @Override
            public String getIdentifier() {
                return "novabank";
            }

            @Override
            public String getAuthor() {
                return author;
            }

            @Override
            public String getVersion() {
                return version;
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public String onRequest(OfflinePlayer player, String params) {
                return values.resolve(player == null ? null : player.getUniqueId(), params);
            }
        };
        expansion.register();
        return new PlaceholderHook(expansion);
    }

    @Override
    public void close() {
        expansion.unregister();
    }
}
