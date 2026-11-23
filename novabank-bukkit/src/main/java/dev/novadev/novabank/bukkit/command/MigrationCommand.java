package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.bukkit.storage.StorageMigrator;
import dev.novadev.novabank.config.NovaBankConfig;
import dev.novadev.novabank.config.model.StorageType;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code migrate <type> [confirm]} : confirmation en deux temps (comme le reset), puis migration
 * par {@link StorageMigrator} ; le resultat est envoye a l'administrateur (la progression est dans
 * la console).
 */
public final class MigrationCommand implements MigrationActions {

    private final StorageMigrator migrator;
    private final Supplier<NovaBankConfig> config;
    private final PendingConfirmations<StorageType> confirmations;
    private final Supplier<Language> language;

    /**
     * @param migrator      migration
     * @param config        configuration en vigueur (sections de la cible)
     * @param confirmations demandes de migration en attente
     * @param language      langue en vigueur
     */
    public MigrationCommand(StorageMigrator migrator, Supplier<NovaBankConfig> config,
                            PendingConfirmations<StorageType> confirmations, Supplier<Language> language) {
        this.migrator = Objects.requireNonNull(migrator, "migrator");
        this.config = Objects.requireNonNull(config, "config");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.language = Objects.requireNonNull(language, "language");
    }

    @Override
    public void migrate(CommandActor actor, String label, StorageType target, String argument, boolean confirmed) {
        Language text = language.get();
        NovaBankConfig current = config.get();
        Optional<StorageMigrator.Refusal> refusal = migrator.refusal(target, current.storage(),
                current.redis().enabled());
        if (refusal.isPresent()) {
            actor.send(text.chat("admin.migrate.refused." + refusal.get().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                    "type", target));
            return;
        }
        if (!confirmed) {
            confirmations.request(actor.name(), target);
            actor.send(text.chat("admin.migrate.confirm", "source", migrator.sourceType(), "type", target, "label",
                    label, "argument", argument, "seconds", PendingConfirmations.WINDOW.toSeconds()));
            return;
        }
        if (!confirmations.confirm(actor.name(), target)) {
            actor.send(text.chat("admin.migrate.no-request", "type", target, "label", label, "argument", argument));
            return;
        }
        boolean started = migrator.start(target, current.storage(), outcome -> {
            Language now = language.get();
            actor.send(outcome.success()
                    ? now.chat("admin.migrate.done", "summary", outcome.detail(), "type", target)
                    : now.chat("admin.migrate.failed", "reason", outcome.detail(), "source", migrator.sourceType()));
        });
        actor.send(started ? text.chat("admin.migrate.started", "source", migrator.sourceType(), "type", target)
                : text.chat("admin.migrate.busy"));
    }
}
