package dev.novadev.novabank.libraries.classloading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.libraries.JdbcDrivers;
import dev.novadev.novabank.libraries.testing.TestJars;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IsolatedClassLoaderTest {

    private static final String SHARED = "org.example.isolation.SharedProbe";
    private static final String PARENT_ONLY = "org.example.isolation.ParentOnlyProbe";
    private static final String JAR_ONLY = "org.example.isolation.JarOnlyProbe";
    private static final String PLUGIN = "dev.novadev.novabank.libraries.classloading.fixture.PluginProbe";
    private static final String PLUGIN_JAR_ONLY = "dev.novadev.novabank.libraries.classloading.fixture.Injected";

    @TempDir
    Path dir;

    private IsolatedClassLoader loader;

    @BeforeEach
    void buildJar() throws IOException {
        Path jar = TestJars.build(dir, "isolation.jar", Map.of(
                SHARED, probe(SHARED),
                JAR_ONLY, probe(JAR_ONLY),
                PLUGIN, probe(PLUGIN),
                PLUGIN_JAR_ONLY, probe(PLUGIN_JAR_ONLY)), Map.of(
                "org/example/isolation/shared.txt", "jar",
                "org/example/isolation/jar-only.txt", "jar",
                "dev/novadev/novabank/libraries/classloading/fixture/plugin.txt", "jar"));
        loader = new IsolatedClassLoader("test", List.of(jar.toUri().toURL()), getClass().getClassLoader(),
                JdbcDrivers.PARENT_FIRST_PREFIXES);
    }

    @AfterEach
    void closeLoader() throws IOException {
        loader.close();
    }

    @Test
    void child_first_la_version_du_jar_masque_celle_du_parent() throws Exception {
        Class<?> shared = loader.loadClass(SHARED);

        assertThat(shared.getClassLoader()).isSameAs(loader);
        assertThat(origin(shared)).isEqualTo("jar");
        assertThat(origin(Class.forName(SHARED))).isEqualTo("parent");
        assertThat(loader.loadClass(SHARED)).isSameAs(shared);
    }

    @Test
    void parent_first_pour_les_prefixes_reserves() throws Exception {
        Class<?> plugin = loader.loadClass(PLUGIN);

        assertThat(plugin.getClassLoader()).isSameAs(getClass().getClassLoader());
        assertThat(origin(plugin)).isEqualTo("parent");
        assertThat(loader.loadClass("java.sql.Driver")).isSameAs(java.sql.Driver.class);
    }

    @Test
    void un_jar_ne_peut_pas_injecter_une_classe_sous_un_prefixe_reserve() {
        assertThatThrownBy(() -> loader.loadClass(PLUGIN_JAR_ONLY)).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void repli_sur_le_parent_et_classes_propres_au_jar() throws Exception {
        assertThat(loader.loadClass(PARENT_ONLY).getClassLoader()).isSameAs(getClass().getClassLoader());
        assertThat(origin(loader.loadClass(JAR_ONLY))).isEqualTo("jar");
        assertThatThrownBy(() -> loader.loadClass("org.example.isolation.Missing"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void resolution_demandee() throws Exception {
        assertThat(loader.loadClass(SHARED, true).getClassLoader()).isSameAs(loader);
    }

    @Test
    void ressources_child_first_sauf_prefixes_reserves() throws Exception {
        assertThat(read(loader.getResource("org/example/isolation/shared.txt"))).isEqualTo("jar");
        assertThat(read(loader.getResource("org/example/isolation/jar-only.txt"))).isEqualTo("jar");
        assertThat(read(loader.getResource("dev/novadev/novabank/libraries/classloading/fixture/plugin.txt")))
                .isEqualTo("parent");
        try (InputStream in = loader.getResourceAsStream("org/example/isolation/shared.txt")) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim()).isEqualTo("jar");
        }

        List<URL> shared = Collections.list(loader.getResources("org/example/isolation/shared.txt"));
        assertThat(shared).hasSize(2);
        assertThat(read(shared.get(0))).isEqualTo("jar");
        assertThat(read(shared.get(1))).isEqualTo("parent");
        assertThat(Collections.list(loader.getResources(
                "dev/novadev/novabank/libraries/classloading/fixture/plugin.txt")))
                .singleElement().satisfies(url -> assertThat(read(url)).isEqualTo("parent"));
    }

    @Test
    void copie_une_classe_du_plugin_dans_le_chargeur() throws Exception {
        Class<?> copy = loader.defineCopyOf(Class.forName(PLUGIN));

        assertThat(copy.getClassLoader()).isSameAs(loader);
        assertThat(copy).isNotSameAs(Class.forName(PLUGIN));
        assertThat(origin(copy)).isEqualTo("parent");
        assertThatThrownBy(() -> loader.defineCopyOf(Class.forName(PLUGIN))).isInstanceOf(LinkageError.class);
    }

    @Test
    void copie_impossible_sans_bytecode_accessible() throws Exception {
        byte[] bytecode;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(PLUGIN.replace('.', '/') + ".class")) {
            bytecode = in.readAllBytes();
        }
        Class<?> withoutResource = new BytesLoader().define(PLUGIN, bytecode);

        assertThatThrownBy(() -> loader.defineCopyOf(withoutResource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bytecode introuvable");
        assertThatThrownBy(() -> loader.defineCopyOf(String.class)).isInstanceOf(NullPointerException.class);
    }

    private static String probe(String className) {
        int dot = className.lastIndexOf('.');
        return "package " + className.substring(0, dot) + ";\n"
                + "public final class " + className.substring(dot + 1) + " {\n"
                + "    public static String origin() { return \"jar\"; }\n"
                + "}\n";
    }

    private static String origin(Class<?> type) throws ReflectiveOperationException {
        return (String) type.getMethod("origin").invoke(null);
    }

    private static String read(URL url) {
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Chargeur sans ressources qui definit une classe a partir d'octets. */
    private static final class BytesLoader extends ClassLoader {
        BytesLoader() {
            super(null);
        }

        Class<?> define(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }
}
