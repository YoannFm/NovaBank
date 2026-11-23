package dev.novadev.novabank.bukkit.platform;

import java.util.Objects;

/**
 * Detecte la plateforme par la presence de classes caracteristiques de chaque famille.
 */
public final class PlatformDetector {

    /** Classe propre a Folia. */
    public static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";
    /** Configuration Paper 1.19 et plus. */
    public static final String PAPER_MARKER = "io.papermc.paper.configuration.Configuration";
    /** Configuration Paper jusqu'a 1.18 (absente des versions recentes). */
    public static final String LEGACY_PAPER_MARKER = "com.destroystokyo.paper.PaperConfig";
    /** Configuration Spigot (implementation serveur). */
    public static final String SPIGOT_MARKER = "org.spigotmc.SpigotConfig";

    private PlatformDetector() {
    }

    /**
     * @param presence sonde de presence de classes
     * @return la plateforme la plus specifique detectee
     */
    public static ServerPlatform detect(ClassPresence presence) {
        Objects.requireNonNull(presence, "presence");
        if (presence.isPresent(FOLIA_MARKER)) {
            return ServerPlatform.FOLIA;
        }
        if (presence.isPresent(PAPER_MARKER) || presence.isPresent(LEGACY_PAPER_MARKER)) {
            return ServerPlatform.PAPER;
        }
        if (presence.isPresent(SPIGOT_MARKER)) {
            return ServerPlatform.SPIGOT;
        }
        return ServerPlatform.BUKKIT;
    }
}
