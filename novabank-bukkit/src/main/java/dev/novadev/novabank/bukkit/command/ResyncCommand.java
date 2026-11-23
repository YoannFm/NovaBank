package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.access.Permissions;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * {@code /novabank resync} : resynchronise le niveau de toutes les iles SuperiorSkyblock2 avec leur banque (apres avoir
 * active l'integration ou change son coefficient), sans attendre la prochaine operation de chaque ile.
 */
public final class ResyncCommand implements AdminSubcommand {

    private final Supplier<Language> language;
    private final Supplier<OptionalInt> resync;

    /**
     * @param language langue en vigueur
     * @param resync   lance la resynchronisation et rend le nombre d'iles, vide si l'integration n'est pas active
     */
    public ResyncCommand(Supplier<Language> language, Supplier<OptionalInt> resync) {
        this.language = Objects.requireNonNull(language, "language");
        this.resync = Objects.requireNonNull(resync, "resync");
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        OptionalInt count = resync.get();
        actor.send(count.isPresent() ? language.get().chat("admin.resync.started", "count", count.getAsInt())
                : language.get().chat("admin.resync.unavailable"));
    }

    @Override
    public List<String> complete(String[] args) {
        return List.of();
    }
}
