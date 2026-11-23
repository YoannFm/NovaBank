package dev.novadev.novabank.bukkit.platform;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version de Minecraft comparable : ancien schema {@code 1.21.11} et nouveau schema
 * {@code annee.drop.hotfix} ({@code 26.3}, {@code 26.1.2}).
 *
 * @param major premier nombre ({@code 1} ou l'annee, par exemple {@code 26})
 * @param minor deuxieme nombre
 * @param patch troisieme nombre, {@code 0} s'il est absent
 */
public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {

    /** Version minimale supportee (decision D7). */
    public static final MinecraftVersion MINIMUM = new MinecraftVersion(1, 17, 0);

    private static final Pattern LEADING_VERSION = Pattern.compile("^(\\d{1,4})\\.(\\d{1,4})(?:\\.(\\d{1,4}))?");

    private static final Comparator<MinecraftVersion> ORDER = Comparator.comparingInt(MinecraftVersion::major)
            .thenComparingInt(MinecraftVersion::minor)
            .thenComparingInt(MinecraftVersion::patch);

    /**
     * @throws IllegalArgumentException si un nombre est negatif
     */
    public MinecraftVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Version negative : {0}.{1}.{2}", major, minor, patch));
        }
    }

    /**
     * Lit la version en tete d'une chaine de {@code Bukkit.getBukkitVersion()}, par exemple
     * {@code 1.21.11-R0.1-SNAPSHOT}, {@code 1.17.1-R0.1-SNAPSHOT} ou {@code 26.3.build.226-stable}.
     *
     * @param bukkitVersion texte fourni par le serveur
     * @return la version, vide si le texte ne commence pas par {@code X.Y}
     */
    public static Optional<MinecraftVersion> parse(String bukkitVersion) {
        if (bukkitVersion == null) {
            return Optional.empty();
        }
        Matcher matcher = LEADING_VERSION.matcher(bukkitVersion.strip());
        if (!matcher.find()) {
            return Optional.empty();
        }
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return Optional.of(new MinecraftVersion(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), patch));
    }

    /**
     * @param other version de reference
     * @return {@code true} si cette version est anterieure a {@code other}
     */
    public boolean isOlderThan(MinecraftVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        return ORDER.compare(this, other);
    }

    /**
     * @return {@code 1.21.11}, {@code 26.3} (le correctif {@code 0} n'est pas affiche)
     */
    @Override
    public String toString() {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }
}
