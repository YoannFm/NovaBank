package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.config.NovaBankConfig;
import dev.novadev.novabank.config.model.AcceptedItem;
import dev.novadev.novabank.core.application.TransferRules;
import java.time.Duration;

/**
 * Traduit la configuration validee en regles de la couche application.
 */
public final class TransferRulesFactory {

    private TransferRulesFactory() {
    }

    /**
     * @param config configuration validee
     * @return les regles des operations des joueurs
     */
    public static TransferRules from(NovaBankConfig config) {
        return new TransferRules(config.banks().entityTypes(), config.banks().defaultPriority(),
                config.banks().roles(), config.banks().valuationMode(),
                config.items().stream().map(AcceptedItem::key).toList(), config.limits().maxQuantityPerOperation(),
                Duration.ofMillis(config.limits().cooldownMs()), config.security().savePlayerData());
    }
}
