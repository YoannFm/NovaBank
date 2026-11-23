package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.message.Language;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Commandes de banque des joueurs, a monter ou l'on veut : {@code /bank} (toutes les banques, banque active au choix)
 * ou sous la commande d'un hote, limitee a son type ({@code /is bank}, {@code /town bank}...).
 */
public final class BankCommands {

    private final PlayerCommandActions actions;
    private final CompletionSource completions;
    private final Supplier<Language> language;

    /**
     * @param actions     actions des joueurs
     * @param completions donnees de completion
     * @param language    langue en vigueur
     */
    public BankCommands(PlayerCommandActions actions, CompletionSource completions, Supplier<Language> language) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.completions = Objects.requireNonNull(completions, "completions");
        this.language = Objects.requireNonNull(language, "language");
    }

    /**
     * @param admin menu d'administration ({@code /bank admin}), ou {@code null}
     * @return la commande {@code /bank}
     */
    public CommandRouter standard(AdminSubcommand admin) {
        return new PlayerCommandRouter(actions, completions, language, admin);
    }

    /**
     * @param entityType type de banque impose
     * @return la commande limitee a ce type, sans {@code select} ni {@code admin}
     */
    public CommandRouter forType(String entityType) {
        return new CommandRouter() {
            @Override
            public void execute(CommandActor actor, String label, String[] args) {
                scoped(entityType, label).execute(actor, label, args);
            }

            @Override
            public java.util.List<String> complete(CommandActor actor, String[] args) {
                return scoped(entityType, "").complete(actor, args);
            }
        };
    }

    /** Une instance par commande tapee : l'hote peut preferer une entite selon la commande ({@code /ob bank}). */
    private CommandRouter scoped(String entityType, String command) {
        return PlayerCommandRouter.scoped(new ScopedPlayerActions(actions, actions::selectThen, entityType, command),
                completions, language);
    }
}
