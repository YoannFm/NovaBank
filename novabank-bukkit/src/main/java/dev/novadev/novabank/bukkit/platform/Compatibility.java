package dev.novadev.novabank.bukkit.platform;

import dev.novadev.novabank.bukkit.message.LogMessages;
import java.util.Objects;
import java.util.Optional;

/**
 * Decide si NovaBank peut demarrer dans l'environnement constate.
 */
public final class Compatibility {

    /**
     * Version minimale de Folia : la plus ancienne publiee par PaperMC (1.19.4, premiere API
     * de planificateurs par region), testee reellement (smoke) ; voir README-BUILD.md.
     */
    public static final MinecraftVersion FOLIA_MINIMUM = new MinecraftVersion(1, 19, 4);

    private Compatibility() {
    }

    /**
     * @param environment environnement constate
     * @return la raison du refus, vide si le demarrage est possible
     */
    public static Optional<String> refusalReason(ServerEnvironment environment) {
        Objects.requireNonNull(environment, "environment");
        if (environment.platform() == ServerPlatform.FOLIA) {
            return environment.version()
                    .filter(version -> version.isOlderThan(FOLIA_MINIMUM))
                    .map(version -> LogMessages.foliaVersionTooOld(version, FOLIA_MINIMUM));
        }
        return environment.version()
                .filter(version -> version.isOlderThan(MinecraftVersion.MINIMUM))
                .map(version -> LogMessages.versionTooOld(version, MinecraftVersion.MINIMUM));
    }

    /**
     * @param environment environnement constate
     * @return un avertissement si la version n'a pas pu etre lue (compatibilite non verifiee)
     */
    public static Optional<String> warning(ServerEnvironment environment) {
        return environment.version().isPresent()
                ? Optional.empty()
                : Optional.of(LogMessages.unknownVersionFormat(environment.rawVersion()));
    }
}
