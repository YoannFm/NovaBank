package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Mise a jour d'un fichier YAML copie chez l'administrateur ({@code lang/*.yml}, {@code config.yml})
 * quand le fichier fourni par une nouvelle version de NovaBank porte une version plus recente :
 *
 * <ul>
 *   <li>copie de sauvegarde horodatee de l'ancien fichier dans {@code backup/} (jamais supprimee) ;</li>
 *   <li>ajout des cles absentes avec leur valeur (et leurs commentaires) du fichier fourni ;</li>
 *   <li>aucune valeur existante n'est modifiee ;</li>
 *   <li>seules les cles declarees retirees par la nouvelle version (avec leurs sous-cles et leurs
 *       commentaires) sont supprimees, elles restent dans la sauvegarde ; les autres cles inconnues
 *       du fichier fourni sont conservees et seulement signalees (cles obsoletes) ;</li>
 *   <li>a l'interieur d'une section libre (cles choisies par l'administrateur, ex. roles par type
 *       d'entite), rien n'est ajoute si la section existe deja ;</li>
 *   <li>la cle de version du fichier prend la version fournie.</li>
 * </ul>
 *
 * <p>Ecriture par {@link YamlConfiguration} (fichier temporaire puis renommage atomique). Les
 * commentaires sont conserves a partir de Minecraft 1.18.1 (API de commentaires de Bukkit, appelee
 * par reflexion car absente de l'API 1.17). En 1.17, Bukkit ne lit pas les commentaires : le
 * fichier reecrit n'en contient plus, la copie de sauvegarde garde l'original commente.
 */
public final class YamlFileUpdater {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String BACKUP_FOLDER = "backup";

    private final String versionKey;
    private final Set<String> freeSections;
    private final IntFunction<Set<String>> removedKeys;
    private final Clock clock;

    /**
     * Bilan d'une mise a jour effectuee.
     *
     * @param file        fichier mis a jour
     * @param fromVersion version du fichier avant (0 si absente)
     * @param toVersion   version fournie
     * @param added       cles ajoutees
     * @param removed     cles retirees (declarees retirees par la nouvelle version)
     * @param obsolete    cles du fichier inconnues du fichier fourni (conservees)
     * @param backup      copie de l'ancien fichier
     * @param comments    {@code true} si les commentaires sont conserves (1.18.1+)
     */
    public record Result(Path file, int fromVersion, int toVersion, List<String> added, List<String> removed,
                         List<String> obsolete, Path backup, boolean comments) {
    }

    /**
     * @param versionKey   cle de version du fichier
     * @param freeSections sections dont les cles sont choisies par l'administrateur
     * @param clock        horloge (nom de la sauvegarde)
     */
    public YamlFileUpdater(String versionKey, Set<String> freeSections, Clock clock) {
        this(versionKey, freeSections, version -> Set.of(), clock);
    }

    /**
     * @param versionKey   cle de version du fichier
     * @param freeSections sections dont les cles sont choisies par l'administrateur
     * @param removedKeys  cles a retirer d'un fichier de la version donnee (avec leurs sous-cles)
     * @param clock        horloge (nom de la sauvegarde)
     */
    public YamlFileUpdater(String versionKey, Set<String> freeSections, IntFunction<Set<String>> removedKeys,
                           Clock clock) {
        this.versionKey = Objects.requireNonNull(versionKey, "versionKey");
        this.freeSections = Set.copyOf(freeSections);
        this.removedKeys = Objects.requireNonNull(removedKeys, "removedKeys");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param file    fichier de l'administrateur
     * @param bundled fichier fourni dans le JAR
     * @return le bilan si le fichier a ete mis a jour ; vide s'il est absent ou deja a jour
     * @throws IOException                   lecture ou ecriture impossible (fichier inchange)
     * @throws InvalidConfigurationException si l'un des deux fichiers est un YAML invalide (fichier
     *                                       inchange)
     */
    public Optional<Result> update(Path file, Supplier<InputStream> bundled)
            throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        YamlConfiguration reference = bundled(file, bundled);
        YamlConfiguration current = new YamlConfiguration();
        current.load(file.toFile());
        int fromVersion = current.isInt(versionKey) ? current.getInt(versionKey) : 0;
        int toVersion = reference.getInt(versionKey);
        if (fromVersion >= toVersion) {
            return Optional.empty();
        }
        List<String> added = new ArrayList<>();
        boolean comments = true;
        for (String key : reference.getKeys(true)) {
            if (key.equals(versionKey) || current.contains(key) || insideExistingFreeSection(current, key)) {
                continue;
            }
            if (reference.isConfigurationSection(key)) {
                current.createSection(key);
            } else {
                current.set(key, reference.get(key));
                added.add(key);
            }
            comments &= copyComments(reference, current, key);
        }
        List<String> removed = new ArrayList<>();
        for (String key : new TreeSet<>(removedKeys.apply(fromVersion))) {
            if (current.contains(key)) {
                current.set(key, null);
                removed.add(key);
            }
        }
        List<String> obsolete = new ArrayList<>();
        for (String key : current.getKeys(true)) {
            if (!current.isConfigurationSection(key) && !reference.contains(key) && !key.equals(versionKey)
                    && !insideFreeSection(key)) {
                obsolete.add(key);
            }
        }
        current.set(versionKey, toVersion);
        Path backup = write(file, current);
        return Optional.of(new Result(file, fromVersion, toVersion, List.copyOf(added), List.copyOf(removed),
                List.copyOf(obsolete), backup, comments && commentsSupported()));
    }

    /**
     * Remplace les commentaires du fichier par ceux d'un autre fichier fourni (autre langue), valeurs inchangees, si le
     * fichier porte encore les commentaires d'origine de {@code previous} sur la cle temoin : des commentaires
     * personnalises ne sont jamais ecrases. Sans API de commentaires (1.17), rien n'est fait.
     *
     * @param file     fichier de l'administrateur
     * @param target   fichier fourni dont les commentaires sont repris
     * @param previous fichier fourni de l'autre langue
     * @param probe    cle dont le commentaire distingue les deux fichiers fournis
     * @return la sauvegarde de l'ancien fichier s'il a ete reecrit
     * @throws IOException                   lecture ou ecriture impossible (fichier inchange)
     * @throws InvalidConfigurationException YAML invalide (fichier inchange)
     */
    public Optional<Path> recomment(Path file, Supplier<InputStream> target, Supplier<InputStream> previous,
                                    String probe) throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(file) || !commentsSupported()) {
            return Optional.empty();
        }
        YamlConfiguration wanted = bundled(file, target);
        YamlConfiguration other = bundled(file, previous);
        YamlConfiguration current = new YamlConfiguration();
        current.load(file.toFile());
        List<String> comments = comments(current, probe);
        if (!comments.equals(comments(other, probe)) || comments.equals(comments(wanted, probe))) {
            return Optional.empty();
        }
        for (String key : current.getKeys(true)) {
            if (wanted.contains(key)) {
                copyComments(wanted, current, key);
            }
        }
        invokeOptions(current, "setHeader", List.class, invokeOptions(wanted, "getHeader", null, null));
        return Optional.of(write(file, current));
    }

    private static YamlConfiguration bundled(Path file, Supplier<InputStream> resource)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream input = resource.get()) {
            if (input == null) {
                throw new IOException(ConsoleText.tr("Ressource {0} absente du JAR", file.getFileName()));
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                yaml.load(reader);
            }
        }
        return yaml;
    }

    @SuppressWarnings("unchecked")
    private static List<String> comments(ConfigurationSection section, String key) {
        try {
            return (List<String>) ConfigurationSection.class.getMethod("getComments", String.class).invoke(section, key);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(ConsoleText.tr("Lecture des commentaires de {0} impossible", key), failure);
        }
    }

    /** En-tete du fichier (API 1.18.1+ des options YAML, appelee par reflexion). */
    private static Object invokeOptions(YamlConfiguration yaml, String name, Class<?> type, Object value) {
        try {
            Object options = yaml.options();
            return type == null ? options.getClass().getMethod(name).invoke(options)
                    : options.getClass().getMethod(name, type).invoke(options, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(ConsoleText.tr("En-tete YAML inaccessible"), failure);
        }
    }

    /** Sauvegarde l'ancien fichier puis ecrit le nouveau (fichier temporaire, renommage atomique). */
    private Path write(Path file, YamlConfiguration content) throws IOException {
        Path backup = backup(file);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, content.saveToString(), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    private boolean insideExistingFreeSection(YamlConfiguration current, String key) {
        return freeSections.stream().anyMatch(section -> key.startsWith(section + ".") && current.contains(section));
    }

    private boolean insideFreeSection(String key) {
        return freeSections.stream().anyMatch(section -> key.equals(section) || key.startsWith(section + "."));
    }

    private Path backup(Path file) throws IOException {
        Path folder = file.resolveSibling(BACKUP_FOLDER);
        Files.createDirectories(folder);
        String stamp = STAMP.format(clock.instant().atZone(ZoneId.systemDefault()));
        Path backup = folder.resolve(file.getFileName() + "." + stamp);
        for (int index = 2; Files.exists(backup); index++) {
            backup = folder.resolve(file.getFileName() + "." + stamp + "-" + index);
        }
        return Files.copy(file, backup);
    }

    /**
     * Copie les commentaires d'une cle (API Bukkit 1.18.1+, absente de l'API compilee 1.17).
     *
     * @return {@code false} si l'API de commentaires n'existe pas sur ce serveur
     */
    private static boolean copyComments(ConfigurationSection from, ConfigurationSection to, String key) {
        try {
            Method get = ConfigurationSection.class.getMethod("getComments", String.class);
            Method set = ConfigurationSection.class.getMethod("setComments", String.class, List.class);
            set.invoke(to, key, get.invoke(from, key));
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException(ConsoleText.tr("Copie des commentaires de {0} impossible", key), failure);
        }
    }

    private static boolean commentsSupported() {
        try {
            ConfigurationSection.class.getMethod("getComments", String.class);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }
}
