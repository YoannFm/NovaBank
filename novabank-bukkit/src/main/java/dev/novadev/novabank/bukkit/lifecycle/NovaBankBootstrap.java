package dev.novadev.novabank.bukkit.lifecycle;

import dev.novadev.novabank.bukkit.command.ConfigAdminCommand;
import dev.novadev.novabank.bukkit.command.MigrationCommand;
import dev.novadev.novabank.config.ConfigEditor;
import dev.novadev.novabank.bukkit.command.PendingConfirmations;
import dev.novadev.novabank.bukkit.command.ScheduledAdminActions;
import dev.novadev.novabank.bukkit.config.ActiveConfiguration;
import dev.novadev.novabank.bukkit.config.BukkitItemAvailability;
import dev.novadev.novabank.bukkit.config.BukkitVanillaItems;
import dev.novadev.novabank.bukkit.config.ConfigFile;
import dev.novadev.novabank.bukkit.config.ConfigChanges;
import dev.novadev.novabank.bukkit.config.ConfigDeployer;
import dev.novadev.novabank.bukkit.config.ConfigWriter;
import dev.novadev.novabank.bukkit.config.ConfigurationSource;
import dev.novadev.novabank.bukkit.config.ReloadOutcome;
import dev.novadev.novabank.bukkit.config.ValidationLog;
import dev.novadev.novabank.bukkit.diagnostic.DiagnosticCollector;
import dev.novadev.novabank.bukkit.diagnostic.RecentErrors;
import dev.novadev.novabank.bukkit.message.ActiveLanguage;
import dev.novadev.novabank.bukkit.message.LanguageLoader;
import dev.novadev.novabank.bukkit.message.LogMessages;
import dev.novadev.novabank.bukkit.platform.ClassPresence;
import dev.novadev.novabank.bukkit.platform.Compatibility;
import dev.novadev.novabank.bukkit.platform.ServerEnvironment;
import dev.novadev.novabank.api.event.ComponentStateEvent;
import dev.novadev.novabank.bukkit.publicapi.BankEventPublisher;
import dev.novadev.novabank.bukkit.publicapi.ComponentEvents;
import dev.novadev.novabank.bukkit.scheduler.IoExecutor;
import dev.novadev.novabank.bukkit.scheduler.PlatformSchedulers;
import dev.novadev.novabank.bukkit.scheduler.PluginScheduler;
import dev.novadev.novabank.bukkit.scheduler.SchedulerFactory;
import dev.novadev.novabank.bukkit.storage.ActiveStorage;
import dev.novadev.novabank.bukkit.storage.JulLibraryLogger;
import dev.novadev.novabank.bukkit.storage.StorageAssembly;
import dev.novadev.novabank.bukkit.storage.StorageMigrator;
import dev.novadev.novabank.bukkit.sync.SyncAssembly;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.config.model.LeaderboardSettings;
import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.application.OperationActors;
import dev.novadev.novabank.core.storage.FreezableBackend;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.ranking.LeaderboardService;
import dev.novadev.novabank.core.sync.LocalChangeSink;
import dev.novadev.novabank.core.sync.PublishingBackend;
import dev.novadev.novabank.core.sync.SyncCoordinator;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.JdbcDrivers;
import dev.novadev.novabank.libraries.module.EmbeddedModules;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;

/**
 * Assemble NovaBank a l'activation et le demonte a la desactivation.
 *
 * <p>Ordre d'assemblage : environnement (refus d'une version trop ancienne), configuration
 * (refus si ERREUR), langue, pilote JDBC, stockage, executeur IO et planificateur de la
 * plateforme (Bukkit/Paper ou Folia, {@link PlatformSchedulers}), couche
 * application, commandes et ecouteur ({@link ApplicationAssembly}). Chaque
 * ressource est enregistree dans le {@link Lifecycle} des son ouverture : un echec a
 * n'importe quelle etape ferme ce qui a deja ete ouvert, dans l'ordre inverse. A l'arret,
 * l'ecouteur et les commandes sont retires, la phase d'arret ({@link ShutdownDrain})
 * termine les depots et retraits en cours, l'executeur IO termine ses taches, puis le
 * stockage et le pilote sont fermes.
 */
public final class NovaBankBootstrap implements AutoCloseable {

    /** SQLite n'a qu'un ecrivain : deux threads suffisent (une ecriture, une lecture). */
    private static final int IO_THREADS = 2;
    private static final int IO_QUEUE_CAPACITY = 1_000;
    private static final Duration IO_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final PluginHost host;
    private final Logger logger;
    private final Lifecycle lifecycle;

    /**
     * @param host ce qui charge NovaBank (plugin autonome, module ou addon d'un hote)
     */
    public NovaBankBootstrap(PluginHost host) {
        this.host = Objects.requireNonNull(host, "host");
        this.logger = host.logger();
        this.lifecycle = new Lifecycle(logger);
    }

    /**
     * Assemble le plugin. En cas de refus ou d'echec, tout ce qui a ete ouvert est ferme.
     *
     * @return {@code true} si NovaBank est operationnel, {@code false} s'il doit etre desactive
     */
    public boolean start() {
        long start = System.nanoTime();
        try {
            assemble();
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
            logger.info(LogMessages.started(pluginVersion(), elapsedMillis));
            return true;
        } catch (StartupException refused) {
            logger.severe(refused.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.SEVERE, LogMessages.unexpectedStartupFailure(), failure);
        }
        lifecycle.close();
        return false;
    }

    /**
     * Ferme toutes les ressources puis verifie qu'aucun thread NovaBank ne subsiste.
     */
    @Override
    public void close() {
        lifecycle.close();
        List<String> remaining = RemainingThreads.named(RemainingThreads.NOVABANK_PREFIX);
        if (remaining.isEmpty()) {
            logger.info(LogMessages.stopped());
        } else {
            logger.warning(LogMessages.threadsRemaining(remaining));
        }
    }

    private void assemble() {
        Instant startedAt = Instant.now();
        Server server = host.owner().getServer();
        ServerEnvironment environment = detectEnvironment(server);
        // Choisi avant toute ouverture : un JAR sans planificateur de la plateforme est refuse d'emblee.
        SchedulerFactory schedulers = schedulerFactory(environment);
        Path dataFolder = host.dataFolder();

        ConfigurationSource source = new ConfigurationSource(
                new ConfigFile(dataFolder.resolve(ConfigFile.NAME), () -> host.resource(ConfigFile.NAME),
                        () -> host.resource(ConfigFile.ENGLISH)),
                new NovaBankConfigLoader(new BukkitItemAvailability(new BukkitVanillaItems(),
                        server.getPluginManager()::isPluginEnabled)),
                logger);
        ActiveConfiguration configuration = loadConfiguration(source);
        ConsoleText.language(configuration.config().language());
        ActiveLanguage language = new ActiveLanguage(new LanguageLoader(dataFolder, host::resource, logger),
                configuration.config().language());

        StorageMigrator.TargetOpener storageOpener = storageOpener(dataFolder);
        ActiveStorage storage = storageOpener.open(configuration.config().storage(), lifecycle);
        logger.info(LogMessages.storageReady(storage.type(), storage.location(), storage.backend().schemaVersion()));
        ComponentEvents.fire(server.getPluginManager(), ComponentStateEvent.Kind.STORAGE, storage.type().name(), true,
                "schema v" + storage.backend().schemaVersion());
        // Ports du stockage vus par l'application : ecritures gelables pendant une migration.
        FreezableBackend writes = new FreezableBackend(storage.backend());

        IoExecutor io = lifecycle.register("executeur IO",
                new IoExecutor(IO_THREADS, IO_QUEUE_CAPACITY, IO_SHUTDOWN_TIMEOUT, logger));
        PluginScheduler scheduler = schedulers.create(host.owner(), io, logger);
        FlowDrain drain = new FlowDrain();
        // Enregistre apres l'executeur IO, donc ferme avant lui : les operations en cours se
        // terminent pendant que la base est encore ouverte.
        lifecycle.register("phase d'arret", new ShutdownDrain(drain, scheduler,
                () -> Duration.ofMillis(configuration.config().security().shutdownDrainTimeoutMs()), logger));

        // Classements : cache en memoire mis a jour par chaque changement (local ou distant) et recalcule
        // en entier a chaque periode ; sources de verite = stockage.
        BankLedger valuation = new BankLedger(writes.banks(), configuration.catalog(), writes.deliveries(),
                configuration.config().banks().undeliveredPolicy(), Clock.systemUTC());
        LeaderboardService leaderboards = lifecycle.register("classements", new LeaderboardService(writes.banks(),
                writes.content(), (bank, category) -> categoryValue(configuration, valuation, bank, category),
                configuration::rankedTypes, Clock.systemUTC(), logger));
        leaderboards.start(scheduler, leaderboardPeriod(configuration));
        ChangeFanout menuChanges = new ChangeFanout(logger);
        // Synchronisation multi-serveur : ses banques signalent chaque mutation acceptee, tout le reste est inchange.
        Optional<SyncCoordinator> sync = configuration.config().redis().enabled()
                ? Optional.of(new SyncAssembly(logger, Clock.systemUTC()).open(configuration.config().redis(), writes,
                        scheduler, lifecycle, change -> {
                            leaderboards.onRemoteChange(change);
                            menuChanges.onRemoteChange(change);
                        }))
                : Optional.empty();
        // Modifications de configuration en jeu : sauvegarde du fichier avant ecriture, validation complete, puis, avec Redis,
        // diffusion des sections partagees aux autres serveurs (qui sauvegardent et valident de leur cote).
        Path configPath = dataFolder.resolve(ConfigFile.NAME);
        ConfigWriter configWriter = new ConfigWriter(configPath);
        ConfigChanges configChanges = new ConfigChanges(new ConfigDeployer(configPath, configWriter, source::validate),
                scheduler, () -> reload(configuration, language, source, leaderboards),
                command -> sync.map(coordinator -> coordinator.broadcastControl(command)).orElse(false),
                () -> sync.isPresent() && configuration.config().redis().deployConfig(), logger);
        // Rechargement demande par un autre serveur (/novabank reload la-bas) : chacun relit SON config.yml.
        sync.ifPresent(coordinator -> coordinator.onControl(command -> {
            if (command.startsWith(ConfigChanges.COMMAND)) {
                configChanges.receive(command.substring(ConfigChanges.COMMAND.length()));
            } else if ("reload".equals(command)) {
                scheduler.runGlobal(() -> {
                    ReloadOutcome outcome = reload(configuration, language, source, leaderboards);
                    logger.info(outcome.applied() ? ConsoleText.tr("Rechargement demande par un autre serveur : applique")
                    : ConsoleText.tr("Rechargement demande par un autre serveur : refuse (configuration precedente conservee)"));
                });
            }
        }));
        OperationActors actors = new OperationActors();
        BankEventPublisher events = new BankEventPublisher(server.getPluginManager(), logger, actors);
        sync.ifPresent(coordinator -> ComponentEvents.fire(server.getPluginManager(), ComponentStateEvent.Kind.SYNC,
                "Redis", true, configuration.config().redis().host() + ":" + configuration.config().redis().port()));
        LocalChangeSink changes = sync.<LocalChangeSink>map(coordinator -> LocalChangeSink.all(leaderboards, events,
                menuChanges, coordinator)).orElse(LocalChangeSink.all(leaderboards, events, menuChanges));
        StorageBackend application = new PublishingBackend(writes, changes, Clock.systemUTC());

        java.util.List<String> loadedComponents = new java.util.concurrent.CopyOnWriteArrayList<>();
        RecentErrors recentErrors = lifecycle.register("erreurs recentes", new RecentErrors(logger));
        DiagnosticCollector diagnostics = new DiagnosticCollector(pluginVersion(), environment, configuration, storage,
                io, sync, leaderboards, recentErrors, loadedComponents);
        ScheduledAdminActions actions = new ScheduledAdminActions(scheduler, diagnostics,
                () -> {
                    ReloadOutcome outcome = reload(configuration, language, source, leaderboards);
                    if (outcome.applied()) {
                        sync.ifPresent(coordinator -> coordinator.broadcastControl("reload"));
                    }
                    return outcome;
                }, language, logger);
        Supplier<Duration> waitTimeout = () -> Duration.ofMillis(configuration.config().security().shutdownDrainTimeoutMs());
        StorageMigrator migrator = new StorageMigrator(storage, writes, drain, storageOpener, scheduler, waitTimeout,
                Clock.systemUTC(), logger);
        ConfigAdminCommand configCommand = new ConfigAdminCommand(source::load,
                new ConfigEditor(new NovaBankConfigLoader(new BukkitItemAvailability(new BukkitVanillaItems(),
                        server.getPluginManager()::isPluginEnabled))),
                configPath, configWriter, configChanges, scheduler, actions, language::get);
        MigrationCommand migrations = new MigrationCommand(migrator, configuration::config,
                new PendingConfirmations<>(Clock.systemUTC()), language);
        new ApplicationAssembly(host, lifecycle, actors).assemble(configuration, language, application, scheduler, drain, actions,
                migrations, startedAt, leaderboards,
                () -> scheduler.runGlobal(() -> reload(configuration, language, source, leaderboards)), menuChanges,
                configCommand, configChanges,
                () -> source.load().tree(),
                loadedComponents);
    }

    private static ReloadOutcome reload(ActiveConfiguration configuration, ActiveLanguage language,
                                        ConfigurationSource source, LeaderboardService leaderboards) {
        ReloadOutcome outcome = configuration.apply(source.load());
        if (outcome.applied()) {
            language.reload(configuration.config().language());
            ConsoleText.language(configuration.config().language());
            // Prix ou types classes peuvent avoir change.
            leaderboards.requestRebuild();
        }
        return outcome;
    }

    /** Valeur d'une banque, ou de ses seules lignes d'une categorie d'items ({@code null} : toute la banque). */
    private static java.math.BigDecimal categoryValue(ActiveConfiguration configuration, BankLedger ledger, Bank bank,
                                                      String category) {
        if (category == null) {
            return ledger.valueOf(bank);
        }
        java.util.Set<dev.novadev.novabank.api.item.ItemKey> members = new java.util.HashSet<>();
        configuration.config().items().forEach(item -> {
            if (item.category().filter(category::equals).isPresent()) {
                members.add(item.key());
            }
        });
        return ledger.lineValues(bank).entrySet().stream().filter(line -> members.contains(line.getKey()))
                .map(java.util.Map.Entry::getValue).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    /** Periode de recalcul complet : la plus courte des periodes configurees (60 s sans classement). */
    private static Duration leaderboardPeriod(ActiveConfiguration configuration) {
        return configuration.config().leaderboards().values().stream().filter(LeaderboardSettings::enabled)
                .map(settings -> Duration.ofSeconds(settings.refreshSeconds())).min(Comparator.naturalOrder())
                .orElse(Duration.ofSeconds(60));
    }

    private ServerEnvironment detectEnvironment(Server server) {
        ServerEnvironment environment = ServerEnvironment.detect(server.getBukkitVersion(), server.getVersion(),
                ClassPresence.of(server.getClass().getClassLoader()));
        logger.info(LogMessages.environmentDetected(environment));
        Compatibility.warning(environment).ifPresent(logger::warning);
        Compatibility.refusalReason(environment).ifPresent(reason -> {
            throw new StartupException(reason);
        });
        return environment;
    }

    private ActiveConfiguration loadConfiguration(ConfigurationSource source) {
        ConfigLoadResult loaded = source.load();
        ValidationLog.log(logger, loaded.report());
        if (loaded.config().isEmpty()) {
            throw new StartupException(LogMessages.configRejected(loaded.report().errors().size()));
        }
        return new ActiveConfiguration(loaded);
    }

    /**
     * @return l'ouverture d'un stockage (actif au demarrage, cible d'une migration) : pilotes et
     *         modules dans le dossier configure, ressources enregistrees dans le cycle de vie fourni
     */
    private StorageMigrator.TargetOpener storageOpener(Path dataFolder) {
        StorageAssembly assembly = new StorageAssembly(dataFolder, Clock.systemUTC(), logger);
        ClassLoader pluginLoader = host.classLoader();
        JulLibraryLogger libraryLogger = new JulLibraryLogger(logger);
        return (settings, resources) -> {
            Path libraries = assembly.driverDirectory(settings);
            return assembly.open(settings, JdbcDrivers.create(libraries, List.of(), pluginLoader, libraryLogger),
                    EmbeddedModules.create(libraries, List.of(), pluginLoader, libraryLogger), resources);
        };
    }

    private String pluginVersion() {
        return host.version();
    }

    private SchedulerFactory schedulerFactory(ServerEnvironment environment) {
        try {
            return PlatformSchedulers.forPlatform(environment.platform(), host.classLoader());
        } catch (IllegalStateException unavailable) {
            throw new StartupException(LogMessages.schedulerUnavailable(environment, unavailable.getMessage()));
        }
    }
}
