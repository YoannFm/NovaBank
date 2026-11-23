package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.access.Permissions;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@code /novabank discord test} : envoie un message d'essai dans le salon Discord et affiche la reponse de Discord
 * (salon relie, jeton refuse, salon introuvable...), sans attendre un depot.
 */
public final class DiscordCommand implements AdminSubcommand {

    private static final String TEST = "test";

    private final Supplier<Language> language;
    private final Consumer<Consumer<String>> test;

    /**
     * @param language langue en vigueur
     * @param test     envoie le message d'essai et transmet chaque compte rendu (texte de chat)
     */
    public DiscordCommand(Supplier<Language> language, Consumer<Consumer<String>> test) {
        this.language = Objects.requireNonNull(language, "language");
        this.test = Objects.requireNonNull(test, "test");
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        if (args.length != 1 || !TEST.equalsIgnoreCase(args[0])) {
            actor.send(language.get().chat("general.usage", "usage", "/" + label + " discord test"));
            return;
        }
        test.accept(actor::send);
    }

    @Override
    public List<String> complete(String[] args) {
        return args.length == 1 ? CommandArguments.matching(List.of(TEST), args[0]) : List.of();
    }
}
