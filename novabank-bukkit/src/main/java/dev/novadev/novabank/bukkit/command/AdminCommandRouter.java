package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.access.Permissions;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Aiguillage des sous-commandes d'administration et completion, sans dependance serveur :
 * {@code diagnostic}, {@code reload}, {@code bank <joueur>}, {@code reset <joueur> [confirm]}
 * (permission dediee), {@code history <joueur> [page]}, {@code migrate <type> [confirm]}
 * (permission dediee).
 */
public final class AdminCommandRouter implements CommandRouter {

    /** Sous-commandes, dans l'ordre de l'aide. */
    public static final List<String> SUBCOMMANDS = List.of("admin", "bank", "config", "diagnostic", "discord", "gui", "help",
            "history", "items", "migrate", "reload", "reset", "resync", "transfer");

    /** Types de stockage proposes par la completion de {@code migrate}. */
    private static final List<String> STORAGE_TYPES = Arrays.stream(StorageType.values())
            .map(type -> type.name().toLowerCase(Locale.ROOT)).toList();

    private static final String CONFIRM = "confirm";

    /** Sous-commandes qui n'existent que si elles sont branchees. */
    private static final List<String> EXTRA_NAMES = List.of("admin", "discord", "gui", "items", "help", "resync");

    /** Aide : ouverte a tous, sans droit particulier. */
    private static final String HELP = "help";

    private final AdminActions actions;
    private final BankAdminActions banks;
    private final MigrationActions migrations;
    private final CompletionSource completions;
    private final Supplier<Language> language;
    private final TransferAdminCommand transfers;
    private final ConfigAdminCommand configs;
    private final Map<String, AdminSubcommand> extras;

    /**
     * @param actions     diagnostic et rechargement
     * @param banks       actions sur les banques
     * @param migrations  migration du stockage
     * @param completions donnees de completion
     * @param language    langue en vigueur
     */
    public AdminCommandRouter(AdminActions actions, BankAdminActions banks, MigrationActions migrations,
                              CompletionSource completions, Supplier<Language> language) {
        this(actions, banks, migrations, completions, language, null, null);
    }

    /**
     * @param actions     diagnostic et rechargement
     * @param banks       actions sur les banques
     * @param migrations  migration du stockage
     * @param completions donnees de completion
     * @param language    langue en vigueur
     * @param transfers   transfert de banque a banque, {@code null} s'il n'est pas assemble
     */
    public AdminCommandRouter(AdminActions actions, BankAdminActions banks, MigrationActions migrations,
                              CompletionSource completions, Supplier<Language> language,
                              TransferAdminCommand transfers) {
        this(actions, banks, migrations, completions, language, transfers, null);
    }

    /**
     * @param actions     diagnostic et rechargement
     * @param banks       actions sur les banques
     * @param migrations  migration du stockage
     * @param completions donnees de completion
     * @param language    langue en vigueur
     * @param transfers   transfert de banque a banque, {@code null} s'il n'est pas assemble
     * @param configs     configuration en jeu, {@code null} si elle n'est pas assemblee
     */
    public AdminCommandRouter(AdminActions actions, BankAdminActions banks, MigrationActions migrations,
                              CompletionSource completions, Supplier<Language> language,
                              TransferAdminCommand transfers, ConfigAdminCommand configs) {
        this(actions, banks, migrations, completions, language, transfers, configs, Map.of());
    }

    /**
     * @param actions     diagnostic et rechargement
     * @param banks       actions sur les banques
     * @param migrations  migration du stockage
     * @param completions donnees de completion
     * @param language    langue en vigueur
     * @param transfers   transfert de banque a banque, {@code null} s'il n'est pas assemble
     * @param configs     configuration en jeu, {@code null} si elle n'est pas assemblee
     * @param extras      sous-commandes branchees par nom ({@code gui}, {@code items}), chacune avec sa permission
     */
    public AdminCommandRouter(AdminActions actions, BankAdminActions banks, MigrationActions migrations,
                              CompletionSource completions, Supplier<Language> language,
                              TransferAdminCommand transfers, ConfigAdminCommand configs,
                              Map<String, AdminSubcommand> extras) {
        this.transfers = transfers;
        this.configs = configs;
        this.extras = Map.copyOf(extras);
        this.actions = Objects.requireNonNull(actions, "actions");
        this.banks = Objects.requireNonNull(banks, "banks");
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        this.completions = Objects.requireNonNull(completions, "completions");
        this.language = Objects.requireNonNull(language, "language");
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        Language text = language.get();
        AdminSubcommand help = extras.get(HELP);
        if (help != null && (args.length == 0 || args[0].equalsIgnoreCase(HELP))) {
            help.execute(actor, label, new String[0]);
            return;
        }
        if (!actor.hasPermission(Permissions.ADMIN)) {
            actor.send(text.chat("general.no-permission"));
            return;
        }
        if (args.length == 0) {
            actor.send(text.chat("general.usage", "usage", usage(label)));
            return;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "diagnostic" -> actions.diagnostic(actor);
            case "reload" -> actions.reload(actor);
            case "bank" -> withArguments(actor, label, args, 2, "bank", () -> banks.bank(actor, label, args[1]));
            case "reset" -> reset(actor, label, args);
            case "history" -> history(actor, label, args);
            case "migrate" -> migrate(actor, label, args);
            case "transfer" -> transfer(actor, label, args);
            case "config" -> config(actor, label, args);
            default -> {
                AdminSubcommand extra = extras.get(subcommand);
                if (extra == null) {
                    actor.send(text.chat("general.unknown-subcommand", "argument", args[0], "usage", usage(label)));
                } else if (!actor.hasPermission(extra.permission())) {
                    actor.send(text.chat("general.no-permission"));
                } else {
                    extra.execute(actor, label, Arrays.copyOfRange(args, 1, args.length));
                }
            }
        }
    }

    @Override
    public List<String> complete(CommandActor actor, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (!actor.hasPermission(Permissions.ADMIN)) {
            return args.length == 1 && extras.containsKey(HELP) ? CommandArguments.matching(List.of(HELP), args[0])
                    : List.of();
        }
        String last = args[args.length - 1];
        if (args.length == 1) {
            return CommandArguments.matching(SUBCOMMANDS.stream()
                    .filter(name -> !name.equals("reset") || actor.hasPermission(Permissions.ADMIN_RESET))
                    .filter(name -> !name.equals("migrate") || actor.hasPermission(Permissions.ADMIN_MIGRATE))
                    .filter(name -> !name.equals("config") || actor.hasPermission(Permissions.ADMIN_CONFIG))
                    .filter(name -> !name.equals("transfer") || actor.hasPermission(Permissions.ADMIN_TRANSFER))
                    .filter(name -> !EXTRA_NAMES.contains(name) || extras.containsKey(name)
                            && actor.hasPermission(extras.get(name).permission()))
                    .toList(), last);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        AdminSubcommand extra = extras.get(subcommand);
        if (extra != null) {
            return actor.hasPermission(extra.permission())
                    ? CommandArguments.matching(extra.complete(Arrays.copyOfRange(args, 1, args.length)), last)
                    : List.of();
        }
        boolean targetsPlayer = subcommand.equals("bank") || subcommand.equals("history")
                || subcommand.equals("reset") && actor.hasPermission(Permissions.ADMIN_RESET);
        if (args.length == 2 && targetsPlayer) {
            return CommandArguments.matching(completions.onlinePlayers(), last);
        }
        if (args.length == 3 && subcommand.equals("reset") && actor.hasPermission(Permissions.ADMIN_RESET)) {
            return CommandArguments.matching(List.of(CONFIRM), last);
        }
        if (subcommand.equals("config") && args.length == 2 && actor.hasPermission(Permissions.ADMIN_CONFIG)) {
            return CommandArguments.matching(List.of("get", "set", "push"), last);
        }
        if (subcommand.equals("transfer") && args.length == 4 && actor.hasPermission(Permissions.ADMIN_TRANSFER)) {
            return CommandArguments.matching(List.of(CONFIRM), last);
        }
        boolean migrates = subcommand.equals("migrate") && actor.hasPermission(Permissions.ADMIN_MIGRATE);
        if (migrates && args.length == 2) {
            return CommandArguments.matching(STORAGE_TYPES, last);
        }
        if (migrates && args.length == 3) {
            return CommandArguments.matching(List.of(CONFIRM), last);
        }
        return List.of();
    }

    private void reset(CommandActor actor, String label, String[] args) {
        if (!actor.hasPermission(Permissions.ADMIN_RESET)) {
            actor.send(language.get().chat("general.no-permission"));
            return;
        }
        boolean confirmed = args.length == 3 && args[2].equalsIgnoreCase(CONFIRM);
        int expected = confirmed ? 3 : 2;
        withArguments(actor, label, args, expected, "reset",
                () -> banks.reset(actor, label, args[1], confirmed));
    }

    private void config(CommandActor actor, String label, String[] args) {
        if (configs == null || !actor.hasPermission(Permissions.ADMIN_CONFIG)) {
            actor.send(language.get().chat("general.no-permission"));
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("get") && args.length == 3) {
            configs.get(actor, args[2]);
        } else if (action.equals("push") && args.length == 2) {
            configs.push(actor);
        } else if (action.equals("set") && args.length >= 4) {
            configs.set(actor, args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
        } else {
            actor.send(language.get().chat("general.usage", "usage",
                    language.get().text("admin.syntax.config", "label", label)));
        }
    }

    private void transfer(CommandActor actor, String label, String[] args) {
        if (transfers == null || !actor.hasPermission(Permissions.ADMIN_TRANSFER)) {
            actor.send(language.get().chat("general.no-permission"));
            return;
        }
        boolean confirmed = args.length == 4 && args[3].equalsIgnoreCase(CONFIRM);
        withArguments(actor, label, args, confirmed ? 4 : 3, "transfer",
                () -> transfers.transfer(actor, label, args[1], args[2], confirmed));
    }

    private void migrate(CommandActor actor, String label, String[] args) {
        if (!actor.hasPermission(Permissions.ADMIN_MIGRATE)) {
            actor.send(language.get().chat("general.no-permission"));
            return;
        }
        boolean confirmed = args.length == 3 && args[2].equalsIgnoreCase(CONFIRM);
        withArguments(actor, label, args, confirmed ? 3 : 2, "migrate", () -> {
            Optional<StorageType> target = Arrays.stream(StorageType.values())
                    .filter(type -> type.name().equalsIgnoreCase(args[1])).findFirst();
            if (target.isEmpty()) {
                actor.send(language.get().chat("admin.migrate.unknown-type", "type", args[1], "types",
                        String.join(", ", STORAGE_TYPES)));
                return;
            }
            migrations.migrate(actor, label, target.get(), args[1], confirmed);
        });
    }

    private void history(CommandActor actor, String label, String[] args) {
        if (args.length == 3) {
            Optional<Integer> page = CommandArguments.page(args[2]);
            if (page.isEmpty()) {
                actor.send(language.get().chat("general.invalid-page", "value", args[2]));
                return;
            }
            banks.history(actor, label, args[1], page.get());
            return;
        }
        withArguments(actor, label, args, 2, "history", () -> banks.history(actor, label, args[1], 1));
    }

    private void withArguments(CommandActor actor, String label, String[] args, int expected, String subcommand,
                            Runnable action) {
        if (args.length != expected) {
            actor.send(language.get().chat("general.usage", "usage",
                    language.get().text("admin.syntax." + subcommand, "label", label)));
            return;
        }
        action.run();
    }

    private static String usage(String label) {
        return "/" + label + " <" + String.join("|", SUBCOMMANDS) + ">";
    }
}
