package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.access.Permissions;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Accueil de {@code /novabank} (auteur, version, description, liens) suivi de l'aide de TOUTES les commandes. Ouvert a
 * tous, mais chacun ne voit que les commandes qu'il peut utiliser : l'aide n'accorde aucun droit. Chaque commande est
 * cliquable et pre-remplit la barre de saisie sans l'executer.
 */
public final class HelpCommand implements AdminSubcommand {

    /** Serveur Discord de NovaDev. */
    static final String DISCORD = "https://discord.gg/HtjPAfqUXu";
    /** Depot GitHub du plugin. */
    static final String GITHUB = "https://github.com/YoannFm/NovaBank";

    /** Une commande de l'aide : identifiant du texte, saisie affichee, saisie pre-remplie, droits exiges. */
    private record Entry(String id, String shown, String suggest, List<String> permissions) {
    }

    private final Supplier<Language> language;
    private final String author;
    private final String version;
    private final String playerLabel;

    /**
     * @param language    langue en vigueur
     * @param author      auteur (plugin.yml)
     * @param version     version (plugin.yml), affichee precedee de {@code v}
     * @param playerLabel nom de la commande des joueurs ({@code bank} par defaut)
     */
    public HelpCommand(Supplier<Language> language, String author, String version, String playerLabel) {
        this.language = Objects.requireNonNull(language, "language");
        this.author = Objects.requireNonNull(author, "author");
        this.version = Objects.requireNonNull(version, "version");
        this.playerLabel = Objects.requireNonNull(playerLabel, "playerLabel");
    }

    @Override
    public String permission() {
        return Permissions.USE;
    }

    @Override
    public List<String> complete(String[] args) {
        return List.of();
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        Language text = language.get();
        actor.send(text.text("help.header"));
        actor.send(text.text("help.author", "author", author));
        actor.send(text.text("help.version", "version", "v" + version));
        actor.send(text.text("help.description"));
        actor.sendLink(text.text("help.discord", "url", DISCORD), DISCORD, text.text("help.hover-link"));
        actor.sendLink(text.text("help.github", "url", GITHUB), GITHUB, text.text("help.hover-link"));
        actor.send("");
        actor.send(text.text("help.commands-title"));
        for (Entry entry : entries(label)) {
            if (entry.permissions().stream().allMatch(actor::hasPermission)) {
                actor.sendSuggestion(text.text("help.line", "command", entry.shown(), "description",
                        text.text("help.desc." + entry.id())), entry.suggest(),
                        text.text("help.hover", "command", entry.shown()));
            }
        }
    }

    private List<Entry> entries(String admin) {
        String bank = "/" + playerLabel;
        String nova = "/" + admin;
        List<String> use = List.of(Permissions.USE);
        List<String> root = List.of(Permissions.ADMIN);
        return List.of(
                new Entry("open", bank, bank, use),
                new Entry("deposit", bank + " deposit <item|hand|all> [quantite]", bank + " deposit ",
                        List.of(Permissions.DEPOSIT)),
                new Entry("withdraw", bank + " withdraw <item> <quantite|all>", bank + " withdraw ",
                        List.of(Permissions.WITHDRAW)),
                new Entry("info", bank + " info", bank + " info", use),
                new Entry("select", bank + " select <type>", bank + " select ", use),
                new Entry("top", bank + " top [type]", bank + " top ", use),
                new Entry("help", nova + " help", nova + " help", List.of()),
                new Entry("hub", bank + " admin", bank + " admin", root),
                new Entry("diagnostic", nova + " diagnostic", nova + " diagnostic", root),
                new Entry("reload", nova + " reload", nova + " reload", root),
                new Entry("bank", nova + " bank <joueur>", nova + " bank ", root),
                new Entry("history", nova + " history <joueur> [page]", nova + " history ", root),
                new Entry("reset", nova + " reset <joueur>", nova + " reset ",
                        List.of(Permissions.ADMIN, Permissions.ADMIN_RESET)),
                new Entry("resync", nova + " resync", nova + " resync", root),
                new Entry("discord", nova + " discord test", nova + " discord test", root),
                new Entry("transfer", nova + " transfer <source> <cible>", nova + " transfer ",
                        List.of(Permissions.ADMIN, Permissions.ADMIN_TRANSFER)),
                new Entry("migrate", nova + " migrate <type>", nova + " migrate ",
                        List.of(Permissions.ADMIN, Permissions.ADMIN_MIGRATE)),
                new Entry("config", nova + " config <get|set|push>", nova + " config ",
                        List.of(Permissions.ADMIN, Permissions.ADMIN_CONFIG)),
                new Entry("gui", nova + " gui", nova + " gui", List.of(Permissions.ADMIN, Permissions.ADMIN_GUI)),
                new Entry("items", nova + " items", nova + " items",
                        List.of(Permissions.ADMIN, Permissions.ADMIN_ITEMS)));
    }
}
