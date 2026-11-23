package dev.novadev.novabank.bukkit.lifecycle;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.command.AdminActions;
import dev.novadev.novabank.bukkit.command.AdminCommandRouter;
import dev.novadev.novabank.bukkit.command.AdminHubCommand;
import dev.novadev.novabank.bukkit.command.AdminSubcommand;
import dev.novadev.novabank.bukkit.command.BankAdminCommands;
import dev.novadev.novabank.bukkit.command.ConfigAdminCommand;
import dev.novadev.novabank.bukkit.command.GuiAdminCommand;
import dev.novadev.novabank.bukkit.command.HelpCommand;
import dev.novadev.novabank.bukkit.command.ItemsAdminCommand;
import dev.novadev.novabank.bukkit.command.LeaderboardCommand;
import dev.novadev.novabank.bukkit.command.TransferAdminCommand;
import dev.novadev.novabank.bukkit.command.BukkitCompletions;
import dev.novadev.novabank.bukkit.command.BukkitPlayerLookup;
import dev.novadev.novabank.bukkit.command.CommandActor;
import dev.novadev.novabank.bukkit.command.CommandArguments;
import dev.novadev.novabank.bukkit.command.CommandMapAccess;
import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.bukkit.command.KnownStock;
import dev.novadev.novabank.bukkit.command.MigrationActions;
import dev.novadev.novabank.bukkit.command.PendingConfirmations;
import dev.novadev.novabank.bukkit.command.PlayerCommandActions;
import dev.novadev.novabank.bukkit.command.BankCommands;
import dev.novadev.novabank.bukkit.command.RegisteredCommand;
import dev.novadev.novabank.bukkit.command.RoutedCommand;
import dev.novadev.novabank.bukkit.command.SettingsMenus;
import dev.novadev.novabank.bukkit.command.SenderActor;
import dev.novadev.novabank.bukkit.command.TransferMessages;
import dev.novadev.novabank.bukkit.config.ActiveConfiguration;
import dev.novadev.novabank.bukkit.config.ConfigChanges;
import dev.novadev.novabank.bukkit.gui.BankMenuListener;
import dev.novadev.novabank.bukkit.gui.EditorMenus;
import dev.novadev.novabank.bukkit.gui.BankMenus;
import dev.novadev.novabank.bukkit.gui.CommandMenuBackend;
import dev.novadev.novabank.bukkit.discord.DiscordFeed;
import dev.novadev.novabank.bukkit.gui.MenuRenderer;
import dev.novadev.novabank.bukkit.gui.PriceListScreen;
import dev.novadev.novabank.bukkit.host.EditionChannel;
import dev.novadev.novabank.bukkit.host.HostIntegrations;
import dev.novadev.novabank.bukkit.command.DiscordCommand;
import dev.novadev.novabank.bukkit.command.ResyncCommand;
import dev.novadev.novabank.bukkit.integration.BentoBoxLevels;
import dev.novadev.novabank.bukkit.integration.IslandLevelSync;
import dev.novadev.novabank.bukkit.integration.PlaceholderHook;
import dev.novadev.novabank.bukkit.integration.PlaceholderValues;
import dev.novadev.novabank.bukkit.integration.PlayerBankValues;
import dev.novadev.novabank.bukkit.integration.SimpleClansTags;
import dev.novadev.novabank.bukkit.integration.SuperiorSkyblockLevels;
import dev.novadev.novabank.api.BankInfo;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import dev.novadev.novabank.bukkit.item.BukkitInventoryGateway;
import dev.novadev.novabank.bukkit.item.CustomItemRegistration;
import dev.novadev.novabank.bukkit.item.ItemProviders;
import dev.novadev.novabank.bukkit.item.VanillaItemProvider;
import dev.novadev.novabank.bukkit.publicapi.NovaBankApiImpl;
import dev.novadev.novabank.api.NovaBankApi;
import org.bukkit.plugin.ServicePriority;
import dev.novadev.novabank.bukkit.listener.PlayerConnectionListener;
import dev.novadev.novabank.bukkit.message.ActiveLanguage;
import dev.novadev.novabank.bukkit.scheduler.PluginScheduler;
import dev.novadev.novabank.config.model.AcceptedItem;
import dev.novadev.novabank.config.model.CommandLabel;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.application.BankAdministration;
import dev.novadev.novabank.core.application.BankService;
import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.application.OperationActors;
import dev.novadev.novabank.core.application.OperationPorts;
import dev.novadev.novabank.core.application.PlayerOperationGate;
import dev.novadev.novabank.core.application.ReconciliationReport;
import dev.novadev.novabank.core.application.ScheduledTask;
import dev.novadev.novabank.core.application.StorageRetry;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.application.WithdrawalReconciler;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.ownership.OwnershipRegistry;
import dev.novadev.novabank.core.ranking.LeaderboardService;
import dev.novadev.novabank.core.ownership.PlayerOwnershipProvider;
import dev.novadev.novabank.core.storage.StorageBackend;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Assemble la couche application (banque, operations des joueurs, administration) et ses
 * adaptateurs Bukkit (inventaires, commandes, ecouteur), puis lance la reconciliation des
 * retraits restes reserves sur l'executeur IO. Chaque element fermable est enregistre dans
 * le {@link Lifecycle}.
 */
final class ApplicationAssembly {

    private final PluginHost host;
    private final org.bukkit.plugin.Plugin plugin;
    private final Server server;
    private final Logger logger;
    private final Lifecycle lifecycle;
    private final OperationActors actors;

    ApplicationAssembly(PluginHost host, Lifecycle lifecycle, OperationActors actors) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.host = Objects.requireNonNull(host, "host");
        this.plugin = host.owner();
        this.server = plugin.getServer();
        this.logger = host.logger();
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /**
     * @param configuration configuration en vigueur
     * @param language      langue en vigueur
     * @param storage       stockage ouvert
     * @param scheduler     planificateur
     * @param drain         suivi des operations en cours (phase d'arret)
     * @param adminActions  diagnostic et rechargement
     * @param migrations    migration du stockage
     * @param startedAt     instant de demarrage (reconciliation)
     * @param leaderboards  classements
     * @param configCommand configuration en jeu
     * @param configChanges chemin commun des modifications de configuration (editeurs en jeu)
     * @param rawConfig     contenu brut de config.yml (editeur d'items, menu des parametres)
     * @param loadedComponents recoit les integrations et fournisseurs d'items charges (diagnostic)
     * @param changes       relais des changements de banque (la GUI s'y abonne)
     * @param customItemsLoaded rechargement du catalogue apres le chargement des items d'un plugin custom
     */
    void assemble(ActiveConfiguration configuration, ActiveLanguage language, StorageBackend storage,
                  PluginScheduler scheduler, FlowDrain drain, AdminActions adminActions, MigrationActions migrations,
                  Instant startedAt, LeaderboardService leaderboards, Runnable customItemsLoaded, ChangeFanout changes, ConfigAdminCommand configCommand,
                  ConfigChanges configChanges, Supplier<Map<String, Object>> rawConfig, java.util.List<String> loadedComponents) {
        UndeliveredPolicy policy = configuration.config().banks().undeliveredPolicy();
        BankLedger ledger = new BankLedger(storage.banks(), configuration.catalog(), storage.deliveries(), policy,
                Clock.systemUTC());
        OwnershipRegistry ownership = new OwnershipRegistry();
        ownership.register(new PlayerOwnershipProvider(this::playerName));
        // API publique pour les autres plugins (service Bukkit) ; retiree a l'arret.
        NovaBankApi api = new NovaBankApiImpl(ledger, storage.banks(), leaderboards, scheduler,
                () -> configuration.rules().valuationMode(), ownership);
        server.getServicesManager().register(NovaBankApi.class, api, plugin, ServicePriority.Normal);
        lifecycle.register("API publique", () -> server.getServicesManager().unregister(NovaBankApi.class, api));
        Supplier<OptionalInt> islandResync = wireOptionalPlugins(api, ownership, configuration, scheduler,
                loadedComponents);
        ItemProviders items = new ItemProviders();
        items.register(new VanillaItemProvider(server.getItemFactory()));
        CustomItemRegistration.registerAll(plugin, server.getPluginManager(), items, logger, customItemsLoaded, loadedComponents);
        OperationPorts ports = new OperationPorts(ledger, storage.banks(), storage.deliveries(), storage.activeBanks(),
                ownership, new BukkitInventoryGateway(server::getPlayer, items), this::savePlayerData,
                this::hasPermission, scheduler, configuration::rules, new PlayerOperationGate(Clock.systemUTC()),
                StorageRetry.DEFAULT, drain, actors);
        BankService service = new BankService(ports);
        KnownStock stock = new KnownStock();
        Supplier<TransferMessages> messages = () -> new TransferMessages(language.get(),
                key -> itemName(configuration, key), ownership::displayName, policy,
                configuration.rules().maxQuantityPerOperation());
        BukkitCompletions completions = new BukkitCompletions(configuration::rules, stock, server);

        MenuOpener menus = new MenuOpener();
        PlayerCommandActions playerActions = new PlayerCommandActions(service, messages, stock,
                () -> configuration.rules().enabledTypes(), menus::open,
                new LeaderboardCommand(leaderboards, configuration::rankedTypes, language::get, ownership::displayName));
        EditorMenus editors = lifecycle.register("editeurs en jeu", new EditorMenus(server::createInventory,
                key -> items.provider(key).flatMap(provider -> provider.create(key, 1)), items::identify,
                server::getPlayer, scheduler::canAccessPlayer));
        server.getPluginManager().registerEvents(editors, plugin);
        GuiAdminCommand guiCommand = new GuiAdminCommand(() -> configuration.config().gui(), language::get, editors,
                server::getPlayer, configChanges);
        Supplier<List<?>> rawItems = () -> rawConfig.get().get("items") instanceof List<?> list ? list : List.of();
        ItemsAdminCommand itemsCommand = new ItemsAdminCommand(rawItems, language::get, editors, server::getPlayer,
                id -> Optional.ofNullable(server.getPlayer(id)).flatMap(player -> {
                    org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
                    return held == null || held.getType() == org.bukkit.Material.AIR ? Optional.<ItemKey>empty()
                            : items.identify(held);
                }), configChanges, scheduler);
        SettingsMenus settingsMenus = new SettingsMenus(rawConfig, () -> List.copyOf(ownership.types()), language::get, editors,
                server::getPlayer, scheduler);
        AdminHubCommand hubCommand = new AdminHubCommand(guiCommand, itemsCommand, settingsMenus, editors, server::getPlayer,
                configChanges, adminActions, scheduler, language::get);
        CommandLabel playerLabel = configuration.config().commands().player();
        HelpCommand helpCommand = new HelpCommand(language::get, String.join(", ", host.authors()),
                host.version(), playerLabel.name());
        DiscordFeed discord = lifecycle.register("salon Discord", new DiscordFeed(() -> configuration.config().discord(),
                language, this::playerName, ownership::displayName, key -> itemName(configuration, key),
                scheduler::runGlobal, host.version(), logger));
        server.getPluginManager().registerEvents(discord, plugin);
        ScheduledTask discordTimer = scheduler.runRepeatingAsync(discord::flush, Duration.ofSeconds(1), Duration.ofSeconds(1));
        lifecycle.register("envoi vers Discord", discordTimer::cancel);
        Map<String, AdminSubcommand> editorCommands = Map.of("gui", guiCommand, "items", itemsCommand, "admin",
                hubCommand, "help", helpCommand, "resync", new ResyncCommand(language::get, islandResync),
                "discord", new DiscordCommand(language::get, discord::test));
        BankCommands bankCommands = new BankCommands(playerActions, completions, language);
        register(playerLabel, language.get().text("bank.description"), Permissions.USE, bankCommands.standard(hubCommand));
        wireHosts(configuration, language, ownership, bankCommands, loadedComponents);
        BankAdministration administration = new BankAdministration(ledger, storage.banks(), scheduler);
        followClanTags(administration, configuration);
        CommandLabel adminLabel = configuration.config().commands().admin();
        register(adminLabel, language.get().text("admin.description"), null,
                new AdminCommandRouter(adminActions, new BankAdminCommands(administration,
                        new BukkitPlayerLookup(server, scheduler), new PendingConfirmations<>(Clock.systemUTC()), language,
                        messages, key -> itemName(configuration, key)), migrations, completions, language,
                new TransferAdminCommand(administration, new PendingConfirmations<>(Clock.systemUTC()), language::get,
                        () -> configuration.rules().valuationMode()),
                configCommand, editorCommands));

        PlayerConnectionListener listener = lifecycle.register("ecouteur des connexions",
                new PlayerConnectionListener(service, scheduler, messages, stock, server::getPlayer));
        server.getPluginManager().registerEvents(listener, plugin);
        BankMenus bankMenus = registerMenus(configuration, language, messages, items, playerActions, scheduler, ownership,
                editors, menus::open);
        menus.set(bankMenus);
        changes.add(bankMenus::refreshViewers);
        reconcile(scheduler, new WithdrawalReconciler(ledger, storage.banks()), startedAt,
                configuration.config().storage().type().singleServer());
    }

    /**
     * GUI de banque. Le registre est enregistre apres son ecouteur : a l'arret, les GUI
     * ouvertes sont fermees AVANT que leurs clics cessent d'etre annules.
     */
    private BankMenus registerMenus(ActiveConfiguration configuration, ActiveLanguage language,
                                    Supplier<TransferMessages> messages, ItemProviders items,
                                    PlayerCommandActions playerActions, PluginScheduler scheduler,
                                    OwnershipRegistry ownership, EditorMenus editors, Consumer<UUID> reopen) {
        MenuRenderer renderer = new MenuRenderer(language, key -> itemName(configuration, key),
                ownership::displayName, items);
        BankMenus menus = new BankMenus(server::getPlayer, server::createInventory, renderer,
                new CommandMenuBackend(playerActions, this::actor), () -> configuration.config().gui(),
                view -> messages.get().info(view, configuration.config().commands().player().name()),
                () -> configuration.rules().maxQuantityPerOperation(), scheduler, scheduler::canAccessPlayer,
                player -> editors.open(player, new PriceListScreen(language, priceList(configuration),
                        () -> reopen.accept(player.getUniqueId())), () -> { }), logger);
        BankMenuListener listener = lifecycle.register("ecouteur de la GUI", new BankMenuListener(menus, items));
        server.getPluginManager().registerEvents(listener, plugin);
        return lifecycle.register("GUI ouvertes", menus);
    }

    private CommandActor actor(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player == null ? new SenderActor(server.getConsoleSender()) : new SenderActor(player);
    }

    private void register(CommandLabel label, String description, String permission, CommandRouter router) {
        lifecycle.register("commande /" + label.name(), command(label, description, permission, router));
    }

    private RegisteredCommand command(CommandLabel label, String description, String permission, CommandRouter router) {
        RoutedCommand command = new RoutedCommand(label.name(), label.aliases(), description, permission, plugin,
                router);
        return RegisteredCommand.register(CommandMapAccess.commandMap(server), host.commandPrefix(), command, logger);
    }

    /**
     * Plugins hotes : fournisseur d'entites et pont (commande montee dans l'hote, droits de l'hote) selon les editions
     * NovaBank installees, reevalues a chaque plugin ou edition qui arrive ou repart. Le moteur s'annonce d'abord :
     * les JAR NovaBank charges ensuite le rejoignent au lieu d'en demarrer un second.
     */
    private void wireHosts(ActiveConfiguration configuration, ActiveLanguage language, OwnershipRegistry ownership,
                           BankCommands bankCommands, java.util.List<String> loadedComponents) {
        lifecycle.register("annonce du moteur", EditionChannel.announceEngine(server.getServicesManager(), plugin,
                host.version()));
        HostIntegrations.Commands commands = new HostIntegrations.Commands() {
            @Override
            public CommandRouter forType(String entityType) {
                return bankCommands.forType(entityType);
            }

            @Override
            public AutoCloseable topLevel(CommandLabel label, CommandRouter router) {
                return command(label, description(), Permissions.USE, router);
            }

            @Override
            public String description() {
                return language.get().text("bank.description");
            }
        };
        HostIntegrations hosts = lifecycle.register("integrations des plugins hotes", new HostIntegrations(plugin,
                server.getPluginManager(), server.getServicesManager(), ownership, this::playerName, logger,
                loadedComponents, () -> configuration.config().commands().hosts(), commands));
        server.getPluginManager().registerEvents(hosts, plugin);
        hosts.refresh();
    }

    /** SimpleClans : la banque d'un clan suit son tag (transfert journalise vers la banque du nouveau tag). */
    private void followClanTags(BankAdministration administration, ActiveConfiguration configuration) {
        org.bukkit.plugin.Plugin clans = server.getPluginManager().getPlugin("SimpleClans");
        if (clans == null || !clans.isEnabled()) {
            return;
        }
        SimpleClansTags tags = lifecycle.register("banque des clans renommes", new SimpleClansTags((from, to) ->
                moveClanBank(administration, configuration, from, to)));
        if (tags.hook(clans, server.getPluginManager(), plugin)) {
            logger.info(ConsoleText.tr("Integration : SimpleClans -> la banque d'un clan suit son tag"));
        }
    }

    /**
     * Deplace la banque d'un clan vers celle de son nouveau tag, sauf si celle-ci a deja du stock (ancien clan dissous
     * du meme tag) : la fusion donnerait ce stock au clan renomme, l'administrateur decide alors.
     */
    private void moveClanBank(BankAdministration administration, ActiveConfiguration configuration, EntityRef from,
                              EntityRef to) {
        administration.view(to, target -> {
            if (target.isEmpty()) {
                logger.warning(
                        ConsoleText.tr("SimpleClans : banque {0} non deplacee vers {1} (base indisponible) : /novabank transfer {2} {3}",
                                from, to, from, to));
            } else if (!target.get().stock().isEmpty()) {
                logger.warning(
                        ConsoleText.tr("SimpleClans : la banque {0} a deja du stock (ancien clan du meme tag) : banque {1} non deplacee. Pour fusionner : /novabank transfer {2} {3}",
                                to, from, from, to));
            } else {
                administration.transfer(from, to, configuration.rules().valuationMode(), "SimpleClans (changement de tag)",
                        report -> logger.info(report.map(done -> "SimpleClans : banque " + from + " -> " + to + " ("
                                + done.lines() + " ligne(s), " + done.units() + " unite(s), transfert " + done.transferId()
                                + (done.problems().isEmpty() ? ")" : ", problemes : " + done.problems() + ")"))
                                .orElse("SimpleClans : banque " + from + " non deplacee vers " + to
                                        + " (base indisponible) : /novabank transfer " + from + " " + to)));
            }
        });
    }

    /** Niveau des iles (SuperiorSkyblock2, BentoBox) et placeholders (PlaceholderAPI) : branches seulement si ces plugins sont la. */
    private Supplier<OptionalInt> wireOptionalPlugins(NovaBankApi api, OwnershipRegistry ownership,
                                                     ActiveConfiguration configuration, PluginScheduler scheduler,
                                                     java.util.List<String> loaded) {
        Supplier<OptionalInt> islandResync = OptionalInt::empty;
        org.bukkit.plugin.Plugin skyblock = server.getPluginManager().getPlugin("SuperiorSkyblock2");
        Optional<SuperiorSkyblockLevels> skyblockLevels = skyblock != null && skyblock.isEnabled()
                ? SuperiorSkyblockLevels.create(skyblock.getClass().getClassLoader()) : Optional.empty();
        if (skyblockLevels.isPresent()) {
            SuperiorSkyblockLevels levels = skyblockLevels.get();
            IslandLevelSync sync = new IslandLevelSync(() -> configuration.config().integrations().superiorSkyblock2(),
                    api::bank, levels::island, scheduler::runGlobal, logger);
            server.getPluginManager().registerEvents(sync, plugin);
            lifecycle.register("niveau des iles", () -> org.bukkit.event.HandlerList.unregisterAll(sync));
            loaded.add("SuperiorSkyblock2 -> niveau d'ile (integrations.superiorskyblock2.island-level)");
            islandResync = () -> {
                if (!configuration.config().integrations().superiorSkyblock2().enabled()) {
                    return OptionalInt.empty();
                }
                java.util.List<String> ids = levels.islandIds();
                ids.forEach(id -> sync.sync(api.bankId(new EntityRef(EntityRef.ISLAND, id)), id));
                return OptionalInt.of(ids.size());
            };
        }
        org.bukkit.plugin.Plugin bentobox = server.getPluginManager().getPlugin("BentoBox");
        if (bentobox != null) {
            BentoBoxLevels levels = lifecycle.register("niveau des iles BentoBox", new BentoBoxLevels(
                    () -> configuration.config().integrations().bentoBox(),
                    islandId -> api.bank(api.bankId(new EntityRef(EntityRef.ISLAND, islandId)))
                            .thenApply(info -> info.map(BankInfo::value)),
                    scheduler::runGlobal, logger));
            // BentoBox active ses addons pendant son demarrage : l'addon Level n'est sur d'etre la qu'apres.
            scheduler.runLaterGlobal(() -> {
                if (bentobox.isEnabled() && levels.hook(bentobox, server.getPluginManager(), plugin)) {
                    logger.info(
                            ConsoleText.tr("Integration : BentoBox Level -> niveau d'ile (integrations.bentobox.island-level)"));
                }
            }, java.time.Duration.ofSeconds(1));
        }
        org.bukkit.plugin.Plugin papi = server.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null && papi.isEnabled()) {
            PlayerBankValues values = new PlayerBankValues(player -> {
                CompletableFuture<Map<String, BigDecimal>> result = new CompletableFuture<>();
                scheduler.runGlobal(() -> {
                    try {
                        Map<String, CompletableFuture<Optional<BankInfo>>> banks = new LinkedHashMap<>();
                        ownership.memberships(player, configuration.rules().enabledTypes()).forEach(membership ->
                                banks.put(membership.entity().type(), api.bank(api.bankId(membership.entity()))));
                        CompletableFuture.allOf(banks.values().toArray(CompletableFuture[]::new)).whenComplete((done, failure) -> {
                            if (failure != null) {
                                result.completeExceptionally(failure);
                                return;
                            }
                            Map<String, BigDecimal> read = new LinkedHashMap<>();
                            banks.forEach((type, bank) -> bank.join().ifPresent(info -> read.put(type, info.value())));
                            result.complete(read);
                        });
                    } catch (RuntimeException failure) {
                        result.completeExceptionally(failure);
                    }
                });
                return result;
            }, System::currentTimeMillis, 10_000L);
            PlaceholderValues texts = new PlaceholderValues(() -> configuration.rules().enabledTypes().contains("player")
                    ? "player" : configuration.rules().enabledTypes().get(0), values::value,
                    (type, limit) -> api.entityTypes().contains(type) ? api.top(type, limit) : java.util.List.of(),
                    ownership::displayName);
            PlaceholderHook hook = PlaceholderHook.register(String.join(", ", host.authors()), host.version(), texts);
            lifecycle.register("placeholders", hook::close);
            loaded.add("PlaceholderAPI -> %novabank_value%, %novabank_value_<type>%, %novabank_top_<type>_<n>_name|value%");
        }
        return islandResync;
    }

    /**
     * Stockage exclusif : une fois, sur les retraits reserves avant le demarrage. Stockage partage : chaque minute, sur
     * les seuls retraits orphelins (un retrait recent peut etre en cours sur un autre serveur).
     */
    private void reconcile(TaskScheduler scheduler, WithdrawalReconciler reconciler, Instant startedAt,
                           boolean singleServer) {
        if (!singleServer) {
            ScheduledTask periodic = scheduler.runRepeatingAsync(
                    () -> logReconciliation(reconciler.reconcileStale(Instant.now())), Duration.ZERO, Duration.ofMinutes(1));
            lifecycle.register("reconciliation des retraits", periodic::cancel);
            return;
        }
        try {
            scheduler.runAsync(() -> logReconciliation(reconciler.reconcile(startedAt)));
        } catch (RejectedExecutionException saturated) {
            logger.log(Level.WARNING,
                    ConsoleText.tr("Reconciliation des retraits reportee au prochain demarrage"), saturated);
        }
    }

    private void logReconciliation(ReconciliationReport report) {
        if (report.settled() > 0 || report.failed() > 0) {
            logger.warning(
                    ConsoleText.tr("Reconciliation : {0} retrait(s) reste(s) reserve(s) solde(s) ({1} unite(s) non remise(s)), {2} echec(s)",
                            report.settled(), report.units(), report.failed()));
        }
    }

    private static List<PriceListScreen.Entry> priceList(ActiveConfiguration configuration) {
        return configuration.config().items().stream().map(item -> new PriceListScreen.Entry(item.key(),
                itemName(configuration, item.key()), item.price(), item.category().orElse(null))).toList();
    }

    private static String itemName(ActiveConfiguration configuration, ItemKey key) {
        return configuration.config().items().stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .flatMap(AcceptedItem::displayName)
                .orElseGet(() -> CommandArguments.shortName(key));
    }

    private Optional<String> playerName(UUID playerId) {
        return Optional.ofNullable(server.getOfflinePlayer(playerId).getName());
    }

    private void savePlayerData(UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            player.saveData();
        }
    }

    private boolean hasPermission(UUID playerId, String permission) {
        Player player = server.getPlayer(playerId);
        return player != null && player.hasPermission(permission);
    }

    /**
     * Ouverture de la GUI pour la commande, branchee une fois la GUI assemblee (la GUI
     * depend des actions de la commande).
     */
    private static final class MenuOpener {

        private BankMenus menus;

        void set(BankMenus assembled) {
            this.menus = assembled;
        }

        void open(UUID playerId) {
            if (menus != null) {
                menus.open(playerId);
            }
        }
    }
}
