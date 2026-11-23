package dev.novadev.novabank.libraries.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.libraries.JdbcDrivers;
import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.artifact.ArtifactChecksums;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import dev.novadev.novabank.libraries.cache.LocalLibraryCache;
import dev.novadev.novabank.libraries.classloading.IsolatedClassLoader;
import dev.novadev.novabank.libraries.download.ArtifactDownloader;
import dev.novadev.novabank.libraries.download.DownloadSettings;
import dev.novadev.novabank.libraries.testing.RecordingLogger;
import dev.novadev.novabank.libraries.testing.TestArtifacts;
import dev.novadev.novabank.libraries.testing.TestJars;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mecanisme generique des modules embarques, avec un faux module construit a la volee : ressource
 * et somme dans un faux jar de plugin, pilote epingle deja present dans le cache.
 */
class EmbeddedModulesTest {

    private static final String RESOURCE = "backends/fake.jar";
    private static final String ENTRY = "dev.novadev.novabank.fakemodule.FakeEntry";
    private static final String NOT_AN_ENTRY = "dev.novadev.novabank.fakemodule.NotAnEntry";
    private static final String UNLOADED = "dev.novadev.novabank.fakemodule.LoadedLater";
    private static final String DRIVER_CLASS = "org.example.fakedriver.FakeDriver";
    private static final String PLUGIN_PROBE = "dev.novadev.novabank.libraries.classloading.fixture.PluginProbe";
    private static final String CORE_CLASS = Money.class.getName();
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Class<Supplier<String>> SUPPLIER = (Class) Supplier.class;

    @TempDir
    Path dir;

    private final RecordingLogger logger = new RecordingLogger();
    private Path pluginResources;
    private Path libs;
    private Path moduleJar;
    private LibraryArtifact driver;
    private URLClassLoader pluginJar;
    private LoadedModule<?> loaded;

    @BeforeEach
    void buildFakeModule() throws IOException {
        moduleJar = TestJars.buildAgainstClasspath(dir, "fake-module.jar", Map.of(
                ENTRY, """
                        package dev.novadev.novabank.fakemodule;
                        public final class FakeEntry implements java.util.function.Supplier<String> {
                            public FakeEntry() {}
                            public String get() {
                                return new org.example.fakedriver.FakeDriver().name() + " "
                                        + dev.novadev.novabank.core.repository.RepositorySupport.class.getSimpleName();
                            }
                        }""",
                NOT_AN_ENTRY, "package dev.novadev.novabank.fakemodule; public final class NotAnEntry {}",
                UNLOADED, "package dev.novadev.novabank.fakemodule; public final class LoadedLater {}",
                PLUGIN_PROBE, "package dev.novadev.novabank.libraries.classloading.fixture;"
                        + " public final class PluginProbe { public static String origin() { return \"module\"; } }",
                CORE_CLASS, "package dev.novadev.novabank.core.money; public final class Money {}",
                DRIVER_CLASS, "package org.example.fakedriver; public final class FakeDriver {"
                        + " public String name() { return \"faux-module\"; } }"), Map.of());
        Path driverJar = TestJars.build(dir, "fake-driver.jar", Map.of(DRIVER_CLASS,
                "package org.example.fakedriver; public final class FakeDriver {"
                        + " public String name() { return \"pilote\"; } }"), Map.of());
        driver = TestArtifacts.pinned("fake-driver", driverJar);
        libs = dir.resolve("plugins/NovaBank/libs");
        Files.createDirectories(libs);
        Files.copy(driverJar, libs.resolve(driver.fileName()));
        pluginResources = dir.resolve("plugin-jar");
        Files.createDirectories(pluginResources.resolve("backends"));
        Files.copy(moduleJar, pluginResources.resolve(RESOURCE));
        writeChecksum(sha256(moduleJar) + "  mongodb.jar\n");
        pluginJar = new URLClassLoader(new URL[] {pluginResources.toUri().toURL()}, null);
    }

    @AfterEach
    void close() throws IOException {
        if (loaded != null) {
            loaded.close();
        }
        pluginJar.close();
    }

    @Test
    void le_module_est_extrait_verifie_et_charge_avec_ses_pilotes() throws Exception {
        LoadedModule<Supplier<String>> module = load(module(ENTRY));
        loaded = module;

        Path extracted = libs.resolve(RESOURCE);
        assertThat(extracted).exists();
        assertThat(sha256(extracted)).isEqualTo(sha256(moduleJar));
        assertThat(module.entry().getClass().getClassLoader()).isSameAs(module.classLoader())
                .isInstanceOf(IsolatedClassLoader.class);
        assertThat(module.entry().get()).as("pilote epingle child-first, coeur partage").isEqualTo("faux-module RepositorySupport");
        assertThat(logger.lines()).anyMatch(line -> line.contains("Module fake extrait : backends/fake.jar"));
    }

    @Test
    void seuls_le_jdk_l_api_et_le_coeur_sont_demandes_au_parent() throws Exception {
        LoadedModule<Supplier<String>> module = load(module(ENTRY));
        loaded = module;
        ClassLoader loader = module.classLoader();

        assertThat(loader.loadClass(CORE_CLASS)).as("coeur partage malgre la copie du module").isSameAs(Money.class);
        assertThat(loader.loadClass("java.util.List")).isSameAs(List.class);
        Class<?> probe = loader.loadClass(PLUGIN_PROBE);
        assertThat(probe.getClassLoader()).as("paquet NovaBank non partage : child-first").isSameAs(loader);
        assertThat(probe.getMethod("origin").invoke(null)).isEqualTo("module");
        assertThat(EmbeddedModules.PARENT_FIRST_PREFIXES).containsExactly("java.", "javax.", "jdk.", "sun.",
                "org.slf4j.", "dev.novadev.novabank.api.", "dev.novadev.novabank.core.");
        assertThat(JdbcDrivers.PARENT_FIRST_PREFIXES).as("pilotes JDBC inchanges").containsExactly("java.", "javax.",
                "jdk.", "sun.", "org.slf4j.", "dev.novadev.novabank.");
    }

    @Test
    void une_copie_intacte_est_reutilisee_et_une_copie_modifiee_est_reextraite() throws Exception {
        load(module(ENTRY)).close();
        RecordingLogger second = new RecordingLogger();
        modules(second).load(module(ENTRY), SUPPLIER).close();
        assertThat(second.lines()).noneMatch(line -> line.contains("extrait"));

        Path extracted = libs.resolve(RESOURCE);
        Files.write(extracted, "altere".getBytes(StandardCharsets.UTF_8));
        RecordingLogger third = new RecordingLogger();
        loaded = modules(third).load(module(ENTRY), SUPPLIER);

        assertThat(sha256(extracted)).isEqualTo(sha256(moduleJar));
        assertThat(third.lines()).anyMatch(line -> line.startsWith("INFO") && line.contains("copie extraite differente"));
    }

    @Test
    void une_ressource_qui_ne_correspond_pas_a_sa_somme_est_refusee_sans_rien_laisser() throws Exception {
        writeChecksum("0".repeat(64));

        assertThatThrownBy(() -> load(module(ENTRY))).isInstanceOf(LibraryException.class)
                .hasMessageContaining("ne correspond pas a sa somme");
        try (Stream<Path> files = Files.list(libs.resolve("backends"))) {
            assertThat(files).as("ni jar ni fichier partiel").isEmpty();
        }
    }

    @Test
    void une_somme_absente_ou_illisible_est_refusee() throws Exception {
        writeChecksum("pas une somme");
        assertThatThrownBy(() -> load(module(ENTRY))).isInstanceOf(LibraryException.class).hasMessageContaining("illisible");

        Files.delete(pluginResources.resolve(RESOURCE + ".sha256"));
        assertThatThrownBy(() -> load(module(ENTRY))).isInstanceOf(LibraryException.class)
                .hasMessageContaining("absente du jar du plugin");
    }

    @Test
    void une_classe_d_entree_absente_ou_d_un_autre_type_est_refusee() {
        assertThatThrownBy(() -> load(module(NOT_AN_ENTRY))).isInstanceOf(LibraryException.class)
                .hasMessageContaining("n'implemente pas java.util.function.Supplier");
        assertThatThrownBy(() -> load(module("dev.novadev.novabank.fakemodule.Absent")))
                .isInstanceOf(LibraryException.class).hasMessageContaining("instanciation");
        assertThatThrownBy(() -> load(module(CORE_CLASS))).as("classe du parent, pas du module")
                .isInstanceOf(LibraryException.class).hasMessageContaining("n'est pas fournie par le jar du module");
    }

    @Test
    void la_fermeture_ferme_le_chargeur_une_seule_fois() throws Exception {
        LoadedModule<Supplier<String>> module = load(module(ENTRY));
        ClassLoader loader = module.classLoader();

        module.close();
        module.close();

        assertThatThrownBy(() -> loader.loadClass(UNLOADED)).isInstanceOf(ClassNotFoundException.class);
        assertThat(logger.lines()).filteredOn(line -> line.contains("decharge")).hasSize(1);
    }

    @Test
    void la_fermeture_attend_les_threads_du_module_avant_de_fermer_le_chargeur() throws Exception {
        LoadedModule<Supplier<String>> module = load(module(ENTRY));
        AtomicReference<Object> lateLoad = new AtomicReference<>();
        Thread worker = module.withContextClassLoader(() -> {
            // Comme un thread de surveillance de pilote : herite du chargeur de contexte du module.
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(300);
                    lateLoad.set(Thread.currentThread().getContextClassLoader().loadClass(UNLOADED));
                } catch (ReflectiveOperationException | InterruptedException failure) {
                    lateLoad.set(failure);
                }
            }, "module-worker");
            thread.start();
            return thread;
        });

        module.close();

        assertThat(worker.isAlive()).isFalse();
        assertThat(lateLoad.get()).isInstanceOf(Class.class);
        assertThat(logger.lines()).noneMatch(line -> line.contains("encore actif"));
    }

    @Test
    void le_chargeur_de_contexte_est_celui_du_module_pendant_l_action_puis_restaure() {
        LoadedModule<Supplier<String>> module = load(module(ENTRY));
        loaded = module;
        ClassLoader before = Thread.currentThread().getContextClassLoader();

        ClassLoader during = module.withContextClassLoader(() -> Thread.currentThread().getContextClassLoader());

        assertThat(during).isSameAs(module.classLoader());
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(before);
    }

    @Test
    void un_chemin_de_ressource_hors_du_dossier_est_refuse() {
        assertThatThrownBy(() -> new EmbeddedModule("x", "../evil.jar", ENTRY, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddedModule("x", "/abs.jar", ENTRY, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new EmbeddedModule("x", "backends/x.jar", ENTRY, List.of()).checksumResource())
                .isEqualTo("backends/x.jar.sha256");
    }

    private LoadedModule<Supplier<String>> load(EmbeddedModule module) {
        return modules(logger).load(module, SUPPLIER);
    }

    private EmbeddedModules modules(RecordingLogger log) {
        ArtifactDownloader unused = new ArtifactDownloader(DownloadSettings.defaults(List.of()), log);
        return new EmbeddedModules(new ModuleExtractor(libs, pluginJar, log), new LocalLibraryCache(libs, unused, log),
                getClass().getClassLoader(), log);
    }

    private EmbeddedModule module(String entryClass) {
        return new EmbeddedModule("fake", RESOURCE, entryClass, List.of(driver));
    }

    private void writeChecksum(String content) throws IOException {
        Files.writeString(pluginResources.resolve(RESOURCE + ".sha256"), content);
    }

    private static String sha256(Path file) throws IOException {
        return ArtifactChecksums.hex(ArtifactChecksums.sha256(file));
    }
}
