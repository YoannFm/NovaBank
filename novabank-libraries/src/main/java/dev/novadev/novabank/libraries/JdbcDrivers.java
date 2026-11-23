package dev.novadev.novabank.libraries;

import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.cache.LocalLibraryCache;
import dev.novadev.novabank.libraries.catalog.DriverCatalog;
import dev.novadev.novabank.libraries.catalog.DriverSpec;
import dev.novadev.novabank.libraries.classloading.IsolatedClassLoader;
import dev.novadev.novabank.libraries.classloading.ParentFirstPrefixes;
import dev.novadev.novabank.libraries.download.ArtifactDownloader;
import dev.novadev.novabank.libraries.download.DownloadSettings;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fabrique des pilotes JDBC : obtient les artefacts epingles (cache ou telechargement),
 * cree un chargeur isole et instancie la classe du pilote par son nom connu.
 *
 * <p>Le pilote n'est pas inscrit dans {@code DriverManager} par NovaBank et n'est pas
 * cherche par {@code ServiceLoader} : le parent (serveur) declare souvent d'autres
 * versions des memes pilotes, le nom de classe du catalogue est sans ambiguite.
 */
public final class JdbcDrivers {

    /**
     * Prefixes reserves au parent : ceux de {@link ParentFirstPrefixes} (JDK, slf4j du serveur)
     * et {@code dev.novadev.novabank.}, tout NovaBank (API, coeur, ce module) : aucun jar
     * telecharge ne peut substituer une classe du plugin.
     */
    public static final List<String> PARENT_FIRST_PREFIXES =
            ParentFirstPrefixes.withShared(List.of("dev.novadev.novabank."));

    /** Sous-dossier des bibliotheques natives extraites par les pilotes. */
    public static final String NATIVE_DIRECTORY = "native";

    private final LocalLibraryCache cache;
    private final ClassLoader parent;
    private final LibraryLogger logger;

    /**
     * @param cache  cache des artefacts
     * @param parent chargeur parent des pilotes (celui du plugin)
     * @param logger journal
     */
    public JdbcDrivers(LocalLibraryCache cache, ClassLoader parent, LibraryLogger logger) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Fabrique avec les reglages de telechargement par defaut (Maven Central puis miroirs).
     *
     * @param directory dossier des bibliotheques ({@code plugins/NovaBank/libs})
     * @param mirrors   miroirs Maven de repli, eventuellement vide
     * @param parent    chargeur parent des pilotes (celui du plugin)
     * @param logger    journal
     * @return la fabrique
     */
    public static JdbcDrivers create(Path directory, List<URI> mirrors, ClassLoader parent, LibraryLogger logger) {
        ArtifactDownloader downloader = new ArtifactDownloader(DownloadSettings.defaults(mirrors), logger);
        return new JdbcDrivers(new LocalLibraryCache(directory, downloader, logger), parent, logger);
    }

    /**
     * @param type backend de stockage configure
     * @return le pilote charge, vide si le backend n'utilise pas JDBC
     * @throws LibraryException si le pilote ne peut pas etre obtenu ou charge
     */
    public Optional<LoadedDriver> forStorage(StorageType type) {
        return DriverCatalog.forStorage(type).map(this::load);
    }

    /**
     * @param spec pilote a charger
     * @return le pilote charge, a fermer apres le stockage qui l'utilise
     * @throws LibraryException si le pilote ne peut pas etre obtenu ou charge
     */
    public LoadedDriver load(DriverSpec spec) {
        Objects.requireNonNull(spec, "spec");
        List<URL> jars = IsolatedClassLoader.toUrls(cache.resolveAll(spec.artifacts()));
        spec.nativeDirectoryProperty().ifPresent(this::configureNativeDirectory);
        IsolatedClassLoader loader = new IsolatedClassLoader("NovaBank-" + spec.id(), jars, parent,
                PARENT_FIRST_PREFIXES);
        try {
            Driver driver = instantiate(spec, loader);
            logger.info(ConsoleText.tr("Pilote {0} charge ({1}, version {2}.{3})", spec.id(), spec.driverClassName(),
                    driver.getMajorVersion(), driver.getMinorVersion()));
            return new LoadedDriver(spec, driver, loader, logger);
        } catch (RuntimeException | LinkageError failure) {
            try {
                loader.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static Driver instantiate(DriverSpec spec, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(spec.driverClassName(), true, loader);
            if (!Driver.class.isAssignableFrom(type)) {
                throw new LibraryException(ConsoleText.tr("{0} n'est pas un pilote JDBC", spec.driverClassName()));
            }
            return (Driver) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new LibraryException(ConsoleText.tr("Instanciation du pilote {0} ({1}) impossible : {2}",
                    spec.id(), spec.driverClassName(), failure), failure);
        }
    }

    /**
     * Dirige l'extraction de la bibliotheque native du pilote vers {@code libs/native} au
     * lieu du dossier temporaire du systeme (souvent monte {@code noexec} chez les
     * hebergeurs). Limite : c'est une propriete systeme, globale a la JVM ; une valeur deja
     * definie (par l'administrateur, {@code -D...}) est conservee.
     */
    private void configureNativeDirectory(String property) {
        Path directory = cache.directory().resolve(NATIVE_DIRECTORY);
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new LibraryException(ConsoleText.tr("Creation du dossier natif impossible : {0}",
                    directory), failure);
        }
        String current = System.getProperty(property);
        if (current == null) {
            System.setProperty(property, directory.toString());
        } else if (!current.equals(directory.toString())) {
            logger.info(ConsoleText.tr("Propriete {0} deja definie ({1}) : conservee", property, current));
        }
    }
}
