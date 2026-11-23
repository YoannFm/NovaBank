package dev.novadev.novabank.config;

import dev.novadev.novabank.config.model.AcceptedItem;
import dev.novadev.novabank.config.model.BankSettings;
import dev.novadev.novabank.config.model.CommandSettings;
import dev.novadev.novabank.config.model.DiagnosticsSettings;
import dev.novadev.novabank.config.model.DiscordSettings;
import dev.novadev.novabank.config.model.IntegrationSettings;
import dev.novadev.novabank.config.model.GuiSettings;
import dev.novadev.novabank.config.model.LeaderboardSettings;
import dev.novadev.novabank.config.model.LimitSettings;
import dev.novadev.novabank.config.model.RedisSettings;
import dev.novadev.novabank.config.model.SecuritySettings;
import dev.novadev.novabank.config.model.ServerSettings;
import dev.novadev.novabank.config.model.StorageSettings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration complete et valide de NovaBank (aucune erreur bloquante).
 *
 * @param server       identite du serveur
 * @param storage      stockage
 * @param redis        synchronisation multi-serveur
 * @param banks        types de banques et regles de valeur
 * @param items        items acceptes et disponibles sur ce serveur
 * @param limits       garde-fous par operation
 * @param leaderboards classement par type d'entite active
 * @param diagnostics  diagnostics
 * @param security     protections anti-duplication
 * @param commands     noms et alias des commandes
 * @param language     langue des messages ({@code lang/<langue>.yml})
 * @param gui          disposition de la GUI de banque
 * @param integrations ce que NovaBank pousse vers d'autres plugins
 * @param discord      salon Discord des operations
 */
public record NovaBankConfig(ServerSettings server, StorageSettings storage, RedisSettings redis, BankSettings banks,
                             List<AcceptedItem> items, LimitSettings limits,
                             Map<String, LeaderboardSettings> leaderboards, DiagnosticsSettings diagnostics,
                             SecuritySettings security, CommandSettings commands, String language,
                             GuiSettings gui, IntegrationSettings integrations, DiscordSettings discord) {

    /**
     * Verifie la presence des sections et fige les collections.
     *
     * @throws NullPointerException si une section est nulle
     */
    public NovaBankConfig {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(banks, "banks");
        items = List.copyOf(items);
        Objects.requireNonNull(limits, "limits");
        leaderboards = Collections.unmodifiableMap(new LinkedHashMap<>(leaderboards));
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(security, "security");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(gui, "gui");
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(discord, "discord");
    }
}
