package dev.novadev.novabank.libraries.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Construit des jars de test : sources Java compilees a la volee et ressources texte.
 */
public final class TestJars {

    private TestJars() {
    }

    /**
     * @param workDir   dossier de travail (sources, classes, jar)
     * @param jarName   nom du jar produit dans {@code workDir}
     * @param sources   nom complet de classe vers son code source
     * @param resources chemin de ressource vers son contenu texte
     * @return le jar
     */
    public static Path build(Path workDir, String jarName, Map<String, String> sources, Map<String, String> resources) {
        return build(workDir, jarName, sources, resources, false);
    }

    /**
     * Variante dont les sources peuvent utiliser les classes du classpath de test (API, coeur).
     *
     * @param workDir   dossier de travail
     * @param jarName   nom du jar produit
     * @param sources   sources Java par nom de classe
     * @param resources ressources texte par chemin
     * @return le jar produit
     */
    public static Path buildAgainstClasspath(Path workDir, String jarName, Map<String, String> sources,
                                             Map<String, String> resources) {
        return build(workDir, jarName, sources, resources, true);
    }

    private static Path build(Path workDir, String jarName, Map<String, String> sources, Map<String, String> resources,
                              boolean withClasspath) {
        try {
            Path sourceDir = Files.createDirectories(workDir.resolve(jarName + "-src"));
            Path classDir = Files.createDirectories(workDir.resolve(jarName + "-classes"));
            List<Path> files = new ArrayList<>();
            for (Map.Entry<String, String> source : sources.entrySet()) {
                Path file = sourceDir.resolve(source.getKey().replace('.', '/') + ".java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.getValue());
                files.add(file);
            }
            compile(files, classDir, withClasspath);
            for (Map.Entry<String, String> resource : resources.entrySet()) {
                Path file = classDir.resolve(resource.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, resource.getValue());
            }
            Path jar = workDir.resolve(jarName);
            writeJar(classDir, jar);
            return jar;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void compile(List<Path> files, Path classDir, boolean withClasspath) throws IOException {
        if (files.isEmpty()) {
            return;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(files);
            List<String> options = new ArrayList<>(List.of("--release", "17", "-proc:none", "-d", classDir.toString()));
            if (withClasspath) {
                options.addAll(List.of("-classpath", System.getProperty("java.class.path")));
            }
            if (!compiler.getTask(null, fileManager, null, options, null, units).call()) {
                throw new IllegalStateException("Compilation des sources de test echouee : " + files);
            }
        }
    }

    private static void writeJar(Path classDir, Path jar) throws IOException {
        List<Path> entries;
        try (Stream<Path> walk = Files.walk(classDir)) {
            entries = walk.filter(Files::isRegularFile).sorted().toList();
        }
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jarOut = new JarOutputStream(out)) {
            for (Path entry : entries) {
                jarOut.putNextEntry(new JarEntry(classDir.relativize(entry).toString().replace('\\', '/')));
                jarOut.write(Files.readAllBytes(entry));
                jarOut.closeEntry();
            }
        }
    }
}
