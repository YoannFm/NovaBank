package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.config.ReloadOutcome;
import dev.novadev.novabank.bukkit.config.ValidationLog;
import dev.novadev.novabank.bukkit.diagnostic.DiagnosticReport;
import dev.novadev.novabank.bukkit.diagnostic.DiagnosticSnapshot;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.bukkit.message.ValidationLines;
import dev.novadev.novabank.config.validation.ValidationIssue;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.ChatColor;

/**
 * Actions d'administration : le diagnostic interroge la base sur l'executeur IO puis
 * repond depuis le contexte global ; le rechargement (configuration et langue) s'execute
 * sur le thread de la commande (resolution des items par l'API du serveur). Un
 * rechargement lance par un joueur est aussi journalise en console (celui lance depuis la
 * console l'est deja).
 */
public final class ScheduledAdminActions implements AdminActions {

    private final TaskScheduler scheduler;
    private final Supplier<DiagnosticSnapshot> diagnostics;
    private final Supplier<ReloadOutcome> reloader;
    private final Supplier<Language> language;
    private final Logger logger;

    /**
     * @param scheduler   planificateur
     * @param diagnostics collecte du diagnostic (bloquante)
     * @param reloader    rechargement de la configuration et de la langue
     * @param language    langue en vigueur
     * @param logger      journal
     */
    public ScheduledAdminActions(TaskScheduler scheduler, Supplier<DiagnosticSnapshot> diagnostics,
                                 Supplier<ReloadOutcome> reloader, Supplier<Language> language, Logger logger) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.reloader = Objects.requireNonNull(reloader, "reloader");
        this.language = Objects.requireNonNull(language, "language");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void diagnostic(CommandActor actor) {
        actor.send(language.get().chat("admin.diagnostic.running"));
        try {
            scheduler.runAsync(() -> {
                List<String> lines;
                try {
                    lines = DiagnosticReport.lines(diagnostics.get(), language.get());
                } catch (RuntimeException failure) {
                    lines = List.of(language.get().chat("admin.diagnostic.failed", "cause",
                            String.valueOf(failure.getMessage())));
                }
                List<String> response = lines;
                scheduler.runGlobal(() -> response.forEach(actor::send));
            });
        } catch (RejectedExecutionException saturated) {
            actor.send(language.get().chat("admin.diagnostic.failed", "cause",
                    language.get().text("admin.diagnostic.saturated")));
        }
    }

    @Override
    public void reload(CommandActor actor) {
        actor.send(language.get().chat("admin.reload.running"));
        ReloadOutcome outcome = reloader.get();
        Language text = language.get();
        for (ValidationIssue issue : outcome.report().issues()) {
            actor.send(ValidationLines.of(issue));
        }
        List<String> summary = new ArrayList<>();
        if (!outcome.applied()) {
            summary.add(text.chat("admin.reload.refused", "errors", outcome.report().errors().size()));
        } else {
            summary.add(text.chat("admin.reload.applied", "items", outcome.acceptedItems(), "warnings",
                    outcome.report().warnings().size()));
            if (outcome.restartRequired()) {
                summary.add(text.chat("admin.reload.restart-required"));
            }
        }
        summary.forEach(actor::send);
        if (!actor.isConsole()) {
            ValidationLog.log(logger, outcome.report());
            summary.forEach(line -> logger.info(ChatColor.stripColor(line)));
        }
    }
}
