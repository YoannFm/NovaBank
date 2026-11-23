package dev.novadev.novabank.bukkit.platform;

import java.util.Objects;
import java.util.Optional;

/**
 * Environnement d'execution constate au demarrage.
 *
 * @param rawVersion  texte brut de {@code Bukkit.getBukkitVersion()}
 * @param version     version lue, vide si le format n'est pas reconnu
 * @param platform    plateforme detectee
 * @param serverName  nom et version de l'implementation ({@code Bukkit.getVersion()})
 * @param javaVersion version complete de Java
 */
public record ServerEnvironment(String rawVersion, Optional<MinecraftVersion> version, ServerPlatform platform,
                                String serverName, Runtime.Version javaVersion) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public ServerEnvironment {
        Objects.requireNonNull(rawVersion, "rawVersion");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(javaVersion, "javaVersion");
    }

    /**
     * Construit l'environnement a partir des informations du serveur.
     *
     * @param bukkitVersion texte de {@code Bukkit.getBukkitVersion()}
     * @param serverName    texte de {@code Bukkit.getVersion()}
     * @param presence      sonde de presence de classes du serveur
     * @return l'environnement
     */
    public static ServerEnvironment detect(String bukkitVersion, String serverName, ClassPresence presence) {
        return new ServerEnvironment(bukkitVersion, MinecraftVersion.parse(bukkitVersion),
                PlatformDetector.detect(presence), serverName, Runtime.version());
    }

    /**
     * @return la version lue, ou le texte brut si le format n'est pas reconnu
     */
    public String versionLabel() {
        return version.map(MinecraftVersion::toString).orElse(rawVersion);
    }
}
