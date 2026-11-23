package dev.novadev.novabank.bukkit.platform;

/**
 * Famille de serveur, de la plus specifique a la plus generale.
 */
public enum ServerPlatform {

    /** Folia : planificateurs par region, pas de thread principal. */
    FOLIA("Folia"),
    /** Paper (ou derive) sans regions. */
    PAPER("Paper"),
    /** Spigot. */
    SPIGOT("Spigot"),
    /** CraftBukkit ou implementation inconnue. */
    BUKKIT("Bukkit");

    private final String displayName;

    ServerPlatform(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return nom affiche
     */
    public String displayName() {
        return displayName;
    }
}
