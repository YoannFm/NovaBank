package dev.novadev.novabank.libraries.module;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.LibraryLogger;
import dev.novadev.novabank.libraries.cache.LocalLibraryCache;
import dev.novadev.novabank.libraries.classloading.IsolatedClassLoader;
import dev.novadev.novabank.libraries.classloading.ParentFirstPrefixes;
import dev.novadev.novabank.libraries.download.ArtifactDownloader;
import dev.novadev.novabank.libraries.download.DownloadSettings;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Chargeur des modules embarques : extrait et verifie le jar du module, obtient ses pilotes
 * epingles (cache ou telechargement), cree un chargeur isole contenant module + pilotes, puis
 * instancie la classe d'entree par son nom connu (pas de {@code ServiceLoader} : le parent
 * pourrait declarer d'autres fournisseurs).
 *
 * <p>Seuls le JDK, le slf4j du serveur, l'API et le coeur de NovaBank sont partages avec le
 * plugin ({@link #PARENT_FIRST_PREFIXES}) ; tout le reste, dont le paquet du module et ses
 * pilotes, est cherche d'abord dans les jars du module (child-first).
 */
public final class EmbeddedModules {

    /** Paquets du plugin visibles par les modules : API publique et coeur (SPI des backends). */
    public static final List<String> SHARED_PACKAGES = List.of("dev.novadev.novabank.api.",
            "dev.novadev.novabank.core.");

    /** Attente maximale de la fin des threads d'un module a sa fermeture. */
    public static final Duration THREADS_TIMEOUT = Duration.ofSeconds(10);

    /** Prefixes reserves au parent dans le chargeur d'un module. */
    public static final List<String> PARENT_FIRST_PREFIXES = ParentFirstPrefixes.withShared(SHARED_PACKAGES);

    private final ModuleExtractor extractor;
    private final LocalLibraryCache cache;
    private final ClassLoader parent;
    private final LibraryLogger logger;

    /**
     * @param extractor extracteur des jars de modules
     * @param cache     cache des pilotes
     * @param parent    chargeur parent des modules (celui du plugin)
     * @param logger    journal
     */
    public EmbeddedModules(ModuleExtractor extractor, LocalLibraryCache cache, ClassLoader parent,
                           LibraryLogger logger) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Chargeur avec les reglages de telechargement par defaut (Maven Central puis miroirs).
     *
     * @param directory dossier des bibliotheques ({@code plugins/NovaBank/libs})
     * @param mirrors   miroirs Maven de repli, eventuellement vide
     * @param plugin    chargeur du plugin : source des ressources et parent des modules
     * @param logger    journal
     * @return le chargeur de modules
     */
    public static EmbeddedModules create(Path directory, List<URI> mirrors, ClassLoader plugin, LibraryLogger logger) {
        ArtifactDownloader downloader = new ArtifactDownloader(DownloadSettings.defaults(mirrors), logger);
        return new EmbeddedModules(new ModuleExtractor(directory, plugin, logger),
                new LocalLibraryCache(directory, downloader, logger), plugin, logger);
    }

    /**
     * @param module    module a charger
     * @param entryType type attendu de la classe d'entree, visible du parent
     * @param <T>       type de l'entree
     * @return le module charge, a fermer apres les ressources creees par son entree
     * @throws LibraryException si le module ou un pilote ne peut pas etre obtenu, verifie ou charge
     */
    public <T> LoadedModule<T> load(EmbeddedModule module, Class<T> entryType) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(entryType, "entryType");
        List<Path> jars = new ArrayList<>();
        jars.add(extractor.extract(module));
        jars.addAll(cache.resolveAll(module.libraries()));
        IsolatedClassLoader loader = new IsolatedClassLoader("NovaBank-module-" + module.id(), IsolatedClassLoader.toUrls(jars), parent,
                PARENT_FIRST_PREFIXES);
        try {
            T entry = instantiate(module, entryType, loader);
            logger.info(ConsoleText.tr("Module {0} charge ({1} pilote(s) epingle(s))",
                    module.id(), module.libraries().size()));
            return new LoadedModule<>(module, entry, loader, logger, THREADS_TIMEOUT);
        } catch (RuntimeException | LinkageError failure) {
            try {
                loader.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static <T> T instantiate(EmbeddedModule module, Class<T> entryType, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(module.entryClassName(), true, loader);
            if (type.getClassLoader() != loader) {
                throw new LibraryException(ConsoleText.tr("Module {0} : {1} n'est pas fournie par le jar du module",
                        module.id(), module.entryClassName()));
            }
            if (!entryType.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
                throw new LibraryException(ConsoleText.tr("Module {0} : {1} n'implemente pas {2}",
                        module.id(), module.entryClassName(), entryType.getName()));
            }
            return entryType.cast(type.getConstructor().newInstance());
        } catch (ReflectiveOperationException failure) {
            throw new LibraryException(ConsoleText.tr("Module {0} : instanciation de {1} impossible : {2}",
                    module.id(), module.entryClassName(), failure), failure);
        }
    }
}
