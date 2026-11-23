package dev.novadev.novabank.bukkit.storage;

import dev.novadev.novabank.bukkit.lifecycle.Lifecycle;
import dev.novadev.novabank.bukkit.lifecycle.StartupException;
import dev.novadev.novabank.config.model.StorageSettings;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.migration.MigrationException;
import dev.novadev.novabank.core.migration.MigrationReport;
import dev.novadev.novabank.core.migration.StorageMigration;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.FreezableBackend;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Migration du stockage actif vers un autre backend, depuis le serveur (CDC 7.2). La copie et la
 * verification sont faites par {@link StorageMigration} (coeur) ; cette classe ne fait que
 * l'orchestration :
 *
 * <ol>
 *   <li>maintenance : nouveaux flux joueurs refuses ({@link FlowDrain#pause()}), attente de la fin
 *       des flux en cours, puis gel des ecritures restantes ({@link FreezableBackend}) ;</li>
 *   <li>sur l'executeur IO : ouverture de la cible (sections de configuration du type vise),
 *       copie verifiee, fermeture de la cible ;</li>
 *   <li>succes : la maintenance est maintenue jusqu'au redemarrage (la source ne doit plus
 *       changer) ; echec : rien n'a ete ecrit cote source, la maintenance est levee.</li>
 * </ol>
 */
public final class StorageMigrator {

    /** Ouverture d'un backend cible, ses ressources enregistrees dans un cycle de vie dedie. */
    @FunctionalInterface
    public interface TargetOpener {

        /**
         * @param settings  section stockage, type = cible
         * @param resources cycle de vie de la cible (ferme apres la migration)
         * @return la cible ouverte
         * @throws StartupException si la cible ne peut pas etre ouverte
         */
        ActiveStorage open(StorageSettings settings, Lifecycle resources);
    }

    /** Motif d'un refus avant toute action. */
    public enum Refusal {
        /** La cible est le stockage actif. */
        SAME_TYPE,
        /** Cible MySQL / MariaDB sans utilisateur configure. */
        SQL_USERNAME_MISSING,
        /** Cible mono-serveur alors que Redis est active. */
        REDIS_ENABLED
    }

    /**
     * Issue d'une migration lancee.
     *
     * @param success {@code true} si la copie est faite et verifiee
     * @param detail  bilan (succes) ou cause (echec), en francais
     */
    public record Outcome(boolean success, String detail) {
    }

    private enum State {
        IDLE,
        RUNNING,
        DONE
    }

    private final ActiveStorage source;
    private final FreezableBackend writes;
    private final FlowDrain drain;
    private final TargetOpener opener;
    private final TaskScheduler scheduler;
    private final Supplier<Duration> waitTimeout;
    private final Clock clock;
    private final Logger logger;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    /**
     * @param source      stockage actif (lu seulement)
     * @param writes      barriere des ecritures du stockage actif
     * @param drain       suivi des flux joueurs
     * @param opener      ouverture de la cible
     * @param scheduler   executeur IO et contexte global
     * @param waitTimeout attente maximale des operations en cours
     * @param clock       horloge
     * @param logger      journal du serveur (progression)
     */
    public StorageMigrator(ActiveStorage source, FreezableBackend writes, FlowDrain drain, TargetOpener opener,
                           TaskScheduler scheduler, Supplier<Duration> waitTimeout, Clock clock, Logger logger) {
        this.source = Objects.requireNonNull(source, "source");
        this.writes = Objects.requireNonNull(writes, "writes");
        this.drain = Objects.requireNonNull(drain, "drain");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.waitTimeout = Objects.requireNonNull(waitTimeout, "waitTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @return le type du stockage actif
     */
    public StorageType sourceType() {
        return source.type();
    }

    /**
     * @param target       type vise
     * @param settings     section stockage en vigueur
     * @param redisEnabled Redis active
     * @return le motif de refus, ou vide si la migration est possible
     */
    public Optional<Refusal> refusal(StorageType target, StorageSettings settings, boolean redisEnabled) {
        if (target == source.type()) {
            return Optional.of(Refusal.SAME_TYPE);
        }
        if (target.sqlServer() && settings.sql().username().isBlank()) {
            return Optional.of(Refusal.SQL_USERNAME_MISSING);
        }
        if (target.singleServer() && redisEnabled) {
            return Optional.of(Refusal.REDIS_ENABLED);
        }
        return Optional.empty();
    }

    /**
     * Lance la migration (maintenance immediate, copie sur l'executeur IO).
     *
     * @param target   type vise (sans motif de refus)
     * @param settings section stockage en vigueur (sous-section du type vise)
     * @param done     issue, livree dans le contexte global
     * @return {@code false} si une migration est deja en cours ou terminee (rien n'est lance)
     */
    public boolean start(StorageType target, StorageSettings settings, Consumer<Outcome> done) {
        Objects.requireNonNull(done, "done");
        if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
            return false;
        }
        if (!drain.pause()) {
            state.set(State.IDLE);
            return false;
        }
        logger.warning(ConsoleText.tr("Migration {0} -> {1} : maintenance, nouvelles operations refusees",
                source.type(), target));
        StorageSettings targetSettings = new StorageSettings(target, settings.sql(), settings.sqlite(), settings.file(),
                settings.mongodb(), settings.saveIntervalSeconds(), settings.drivers());
        try {
            scheduler.runAsync(() -> {
                Outcome outcome = run(targetSettings);
                scheduler.runGlobal(() -> done.accept(outcome));
            });
        } catch (RejectedExecutionException saturated) {
            done.accept(fail(new MigrationException(ConsoleText.tr("executeur d'entrees/sorties sature, reessayez"))));
        }
        return true;
    }

    private Outcome run(StorageSettings targetSettings) {
        try {
            Duration timeout = waitTimeout.get();
            if (!drain.awaitIdle(timeout)) {
                throw new MigrationException(ConsoleText.tr("{0} operation(s) joueur toujours en cours apres {1} ms",
                        drain.activeFlows(), timeout.toMillis()));
            }
            if (!writes.freeze(timeout)) {
                throw new MigrationException(ConsoleText.tr("ecritures toujours en cours apres {0} ms",
                        timeout.toMillis()));
            }
            logger.info(
                    ConsoleText.tr("Migration : operations en cours terminees, ecritures gelees ; ouverture de la cible {0}",
                            targetSettings.type()));
            MigrationReport report;
            try (Lifecycle resources = new Lifecycle(logger)) {
                ActiveStorage target = opener.open(targetSettings, resources);
                logger.info(ConsoleText.tr("Migration : cible {0} ouverte ({1})", target.type(), target.location()));
                report = new StorageMigration(step -> logger.info(ConsoleText.tr("Migration : {0}", step)), clock)
                        .migrate(source.backend().content(), target.backend().content());
            }
            state.set(State.DONE);
            logger.warning(
                    ConsoleText.tr("Migration {0} -> {1} terminee et verifiee : {2}. Mettez storage.type: {3} puis redemarrez le serveur ; NovaBank reste en maintenance jusque-la.",
                            source.type(), targetSettings.type(), report.describe(), targetSettings.type()));
            return new Outcome(true, report.describe());
        } catch (MigrationException failure) {
            return fail(failure);
        } catch (StartupException | StorageException failure) {
            return fail(new MigrationException(ConsoleText.tr("cible indisponible : {0}",
                    failure.getMessage()), failure));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fail(new MigrationException(
                    ConsoleText.tr("migration interrompue (arret du serveur)"), interrupted));
        } catch (RuntimeException unexpected) {
            logger.log(Level.SEVERE, ConsoleText.tr("Migration : erreur inattendue"), unexpected);
            return fail(new MigrationException(ConsoleText.tr("erreur inattendue : {0}", unexpected), unexpected));
        }
    }

    private Outcome fail(MigrationException failure) {
        writes.unfreeze();
        drain.resume();
        state.set(State.IDLE);
        logger.severe(ConsoleText.tr("Migration en echec : {0}. Stockage actif {1} inchange, maintenance levee.",
                failure.getMessage(), source.type()));
        return new Outcome(false, failure.getMessage());
    }
}
