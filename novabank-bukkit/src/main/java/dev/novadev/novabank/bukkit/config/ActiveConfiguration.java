package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfig;
import dev.novadev.novabank.config.catalog.ConfigPriceCatalog;
import dev.novadev.novabank.config.catalog.ReloadablePriceCatalog;
import dev.novadev.novabank.config.validation.ValidationReport;
import dev.novadev.novabank.core.application.TransferRules;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration en vigueur, regles des operations et catalogue de prix remplacables a chaud.
 *
 * <p>Le stockage, les commandes et la politique des items non remis sont mis en place une
 * seule fois au demarrage : leur changement est enregistre mais ne prend effet qu'au
 * redemarrage ({@link ReloadOutcome#restartRequired()}).
 */
public final class ActiveConfiguration {

    private final AtomicReference<Applied> current;
    private final ReloadablePriceCatalog catalog;

    /**
     * @param initial resultat du chargement de demarrage, valide
     * @throws IllegalArgumentException si le resultat contient une erreur
     */
    public ActiveConfiguration(ConfigLoadResult initial) {
        NovaBankConfig config = validConfig(initial);
        this.current = new AtomicReference<>(new Applied(config, initial.report(), TransferRulesFactory.from(config)));
        this.catalog = new ReloadablePriceCatalog(ConfigPriceCatalog.of(config.items()));
    }

    /**
     * @return configuration en vigueur
     */
    public NovaBankConfig config() {
        return current.get().config();
    }

    /**
     * @return rapport de validation de la configuration en vigueur (avertissements)
     */
    public ValidationReport report() {
        return current.get().report();
    }

    /**
     * @return regles en vigueur des operations des joueurs
     */
    public TransferRules rules() {
        return current.get().rules();
    }

    /**
     * @return les classements actifs avec le nombre de lignes affichees : un par type de banque, plus un par
     *         categorie d'items ({@code type#categorie}) si {@code per-category} est actif
     */
    public Map<String, Integer> rankedTypes() {
        Map<String, Integer> ranked = new LinkedHashMap<>();
        List<String> categories = config().items().stream().map(item -> item.category().orElse(null))
                .filter(Objects::nonNull).distinct().toList();
        config().leaderboards().forEach((type, settings) -> {
            if (settings.enabled()) {
                ranked.put(type, settings.size());
                if (settings.perCategory()) {
                    categories.forEach(category -> ranked.put(type + "#" + category, settings.size()));
                }
            }
        });
        return ranked;
    }

    /**
     * Catalogue de prix en vigueur. L'instance est stable : son contenu est remplace a
     * chaque rechargement valide (le {@code BankLedger} lit toujours les prix courants).
     *
     * @return catalogue de prix remplacable a chaud
     */
    public ReloadablePriceCatalog catalog() {
        return catalog;
    }

    /**
     * Applique un nouveau chargement s'il est valide ; sinon rien ne change.
     *
     * @param result resultat d'un nouveau chargement
     * @return le resultat du rechargement
     */
    public ReloadOutcome apply(ConfigLoadResult result) {
        Objects.requireNonNull(result, "result");
        if (result.config().isEmpty()) {
            return new ReloadOutcome(false, result.report(), false, config().items().size());
        }
        NovaBankConfig next = result.config().get();
        catalog.replace(ConfigPriceCatalog.of(next.items()));
        Applied previous = current.getAndSet(new Applied(next, result.report(), TransferRulesFactory.from(next)));
        boolean restartRequired = !previous.config().storage().equals(next.storage())
                || !previous.config().commands().equals(next.commands())
                || previous.config().banks().undeliveredPolicy() != next.banks().undeliveredPolicy();
        return new ReloadOutcome(true, result.report(), restartRequired, next.items().size());
    }

    private static NovaBankConfig validConfig(ConfigLoadResult result) {
        Objects.requireNonNull(result, "result");
        return result.config().orElseThrow(
                () -> new IllegalArgumentException(ConsoleText.tr("Configuration initiale invalide")));
    }

    private record Applied(NovaBankConfig config, ValidationReport report, TransferRules rules) {
    }
}
