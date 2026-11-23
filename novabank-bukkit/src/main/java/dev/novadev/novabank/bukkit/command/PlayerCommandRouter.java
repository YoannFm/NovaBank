package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.application.Amount;
import dev.novadev.novabank.core.application.ItemChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Commande des joueurs : {@code deposit <item|hand|all> [quantite]},
 * {@code withdraw <item> <quantite|all>}, {@code info}, {@code select <type>}, {@code top [type [categorie]]}, {@code open},
 * {@code help} ; sans argument : {@code open} (GUI de banque). Lit les arguments et delegue a {@link PlayerActions} ; les
 * droits d'operation sont reverifies cote serveur par la couche application.
 */
public final class PlayerCommandRouter implements CommandRouter {

    private static final String DEPOSIT = "deposit";
    private static final String WITHDRAW = "withdraw";
    private static final String INFO = "info";
    private static final String SELECT = "select";
    private static final String TOP = "top";
    private static final String HELP = "help";
    private static final String OPEN = "open";
    private static final String HAND = "hand";
    private static final String ADMIN = "admin";
    private static final List<String> SUBCOMMANDS = List.of(DEPOSIT, WITHDRAW, INFO, SELECT, TOP, OPEN, HELP);
    private static final List<String> SCOPED_SUBCOMMANDS = List.of(DEPOSIT, WITHDRAW, INFO, TOP, OPEN, HELP);

    private final PlayerActions actions;
    private final CompletionSource completions;
    private final Supplier<Language> language;
    private final AdminSubcommand admin;
    private final List<String> subcommands;
    private final String helpKey;

    /**
     * @param actions     actions
     * @param completions donnees de completion
     * @param language    langue en vigueur
     */
    public PlayerCommandRouter(PlayerActions actions, CompletionSource completions, Supplier<Language> language) {
        this(actions, completions, language, null);
    }

    /**
     * @param actions     actions
     * @param completions donnees de completion
     * @param language    langue en vigueur
     * @param admin       menu principal d'administration ({@code /bank admin}), {@code null} s'il n'est pas assemble
     */
    public PlayerCommandRouter(PlayerActions actions, CompletionSource completions, Supplier<Language> language,
                               AdminSubcommand admin) {
        this(actions, completions, language, admin, SUBCOMMANDS, "bank.help");
    }

    private PlayerCommandRouter(PlayerActions actions, CompletionSource completions, Supplier<Language> language,
                                AdminSubcommand admin, List<String> subcommands, String helpKey) {
        this.admin = admin;
        this.subcommands = subcommands;
        this.helpKey = helpKey;
        this.actions = Objects.requireNonNull(actions, "actions");
        this.completions = Objects.requireNonNull(completions, "completions");
        this.language = Objects.requireNonNull(language, "language");
    }

    /**
     * Commande limitee a un type de banque, montee dans un hote ({@code /is bank}) : ni {@code select} ni {@code admin}.
     *
     * @param actions     actions limitees au type ({@link ScopedPlayerActions})
     * @param completions donnees de completion
     * @param language    langue en vigueur
     * @return la commande
     */
    public static PlayerCommandRouter scoped(PlayerActions actions, CompletionSource completions,
                                             Supplier<Language> language) {
        return new PlayerCommandRouter(actions, completions, language, null, SCOPED_SUBCOMMANDS, "bank.help-scoped");
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        Language text = language.get();
        Optional<UUID> player = actor.playerId();
        if (player.isEmpty()) {
            actor.send(text.chat("general.players-only"));
            return;
        }
        if (!actor.hasPermission(Permissions.USE)) {
            actor.send(text.chat("general.no-permission"));
            return;
        }
        String subcommand = args.length == 0 ? OPEN : args[0].toLowerCase(Locale.ROOT);
        if (!subcommands.contains(subcommand) && !(subcommand.equals(ADMIN) && admin != null)) {
            subcommand = "";
        }
        switch (subcommand) {
            case DEPOSIT -> deposit(actor, player.get(), label, args);
            case WITHDRAW -> withdraw(actor, player.get(), label, args);
            case INFO -> actions.info(actor, player.get(), label);
            case OPEN -> actions.open(actor, player.get());
            case TOP -> {
                if (args.length > 3) {
                    help(actor, label);
                } else {
                    // type ou type#categorie
                    String scope = args.length == 1 ? null : args.length == 2 ? args[1].toLowerCase(Locale.ROOT)
                            : args[1].toLowerCase(Locale.ROOT) + "#" + args[2].toLowerCase(Locale.ROOT);
                    actions.top(actor, player.get(), scope);
                }
            }
            case SELECT -> {
                if (args.length != 2) {
                    help(actor, label);
                } else {
                    actions.select(actor, player.get(), args[1].toLowerCase(Locale.ROOT));
                }
            }
            case HELP -> help(actor, label);
            case ADMIN -> {
                if (!actor.hasPermission(admin.permission())) {
                    actor.send(text.chat("general.no-permission"));
                } else {
                    admin.execute(actor, label, new String[0]);
                }
            }
            default -> actor.send(text.chat("general.unknown-subcommand", "argument", args[0], "usage",
                    "/" + label + " " + HELP));
        }
    }

    @Override
    public List<String> complete(CommandActor actor, String[] args) {
        if (actor.playerId().isEmpty() || !actor.hasPermission(Permissions.USE) || args.length == 0) {
            return List.of();
        }
        String last = args[args.length - 1];
        if (args.length == 1) {
            return CommandArguments.matching(admin != null && actor.hasPermission(admin.permission())
                    ? java.util.stream.Stream.concat(subcommands.stream(), java.util.stream.Stream.of(ADMIN)).toList()
                    : subcommands, last);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (subcommand) {
                case DEPOSIT -> CommandArguments.matching(depositTargets(), last);
                case WITHDRAW -> CommandArguments.matching(withdrawTargets(actor.playerId().get()), last);
                case SELECT, TOP -> subcommands.contains(subcommand)
                        ? CommandArguments.matching(completions.entityTypes(), last) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3 && (subcommand.equals(WITHDRAW)
                || subcommand.equals(DEPOSIT) && !args[1].equalsIgnoreCase(CommandArguments.ALL))) {
            return CommandArguments.matching(List.of(CommandArguments.ALL), last);
        }
        return List.of();
    }

    private void deposit(CommandActor actor, UUID player, String label, String[] args) {
        if (args.length < 2 || args.length > 3 || args[1].equalsIgnoreCase(CommandArguments.ALL) && args.length == 3) {
            help(actor, label);
            return;
        }
        Optional<Amount> amount = args.length == 3 ? amount(actor, args[2]) : Optional.of(Amount.everything());
        if (amount.isEmpty()) {
            return;
        }
        if (args[1].equalsIgnoreCase(CommandArguments.ALL)) {
            actions.deposit(actor, player, ItemChoice.allAccepted(), Amount.everything());
        } else if (args[1].equalsIgnoreCase(HAND)) {
            actions.deposit(actor, player, ItemChoice.held(), amount.get());
        } else {
            item(actor, args[1]).ifPresent(item -> actions.deposit(actor, player, ItemChoice.of(item), amount.get()));
        }
    }

    private void withdraw(CommandActor actor, UUID player, String label, String[] args) {
        if (args.length != 3) {
            help(actor, label);
            return;
        }
        Optional<ItemKey> item = item(actor, args[1]);
        if (item.isEmpty()) {
            return;
        }
        amount(actor, args[2]).ifPresent(amount -> actions.withdraw(actor, player, item.get(), amount));
    }

    private Optional<ItemKey> item(CommandActor actor, String text) {
        Optional<ItemKey> item = CommandArguments.item(text);
        if (item.isEmpty()) {
            actor.send(language.get().chat("general.invalid-item", "value", text));
        }
        return item;
    }

    private Optional<Amount> amount(CommandActor actor, String text) {
        Optional<Amount> amount = CommandArguments.amount(text);
        if (amount.isEmpty()) {
            actor.send(language.get().chat("general.invalid-quantity", "value", text));
        }
        return amount;
    }

    private void help(CommandActor actor, String label) {
        actor.send(language.get().text(helpKey, "label", label));
    }

    private List<String> depositTargets() {
        List<String> targets = new ArrayList<>(List.of(HAND, CommandArguments.ALL));
        completions.acceptedItems().forEach(item -> targets.add(CommandArguments.shortName(item)));
        return targets;
    }

    private List<String> withdrawTargets(UUID player) {
        List<String> targets = new ArrayList<>();
        completions.acceptedItems().forEach(item -> targets.add(CommandArguments.shortName(item)));
        completions.knownStock(player).forEach(item -> targets.add(CommandArguments.shortName(item)));
        return targets;
    }
}
