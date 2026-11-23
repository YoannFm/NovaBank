package dev.novadev.novabank.libraries.classloading;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

/**
 * Chargeur de classes des jars telecharges, en <em>child-first</em>.
 *
 * <p>Spigot et Paper embarquent deja leurs propres versions de sqlite-jdbc et d'un
 * connecteur MySQL, visibles depuis le chargeur du plugin : en delegation classique, ces
 * versions masqueraient les versions epinglees. Ici, une classe ou une ressource est
 * cherchee d'abord dans les jars de ce chargeur, puis dans le parent.
 *
 * <p>Exception : les noms commencant par un prefixe <em>parent-first</em> sont demandes
 * uniquement au parent, jamais aux jars (classes du JDK, API partagees avec le plugin).
 * Un jar ne peut donc pas substituer une de ces classes.
 */
public final class IsolatedClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final List<String> parentFirstPrefixes;
    private final List<String> parentFirstResourcePrefixes;

    /**
     * @param name                nom du chargeur (diagnostic)
     * @param jars                jars a charger, en priorite sur le parent
     * @param parent              chargeur parent (celui du plugin)
     * @param parentFirstPrefixes prefixes de noms de classes reserves au parent, par exemple {@code java.}
     */
    public IsolatedClassLoader(String name, List<URL> jars, ClassLoader parent, List<String> parentFirstPrefixes) {
        super(name, jars.toArray(new URL[0]), Objects.requireNonNull(parent, "parent"));
        this.parentFirstPrefixes = List.copyOf(parentFirstPrefixes);
        List<String> resourcePrefixes = new ArrayList<>(parentFirstPrefixes.size());
        for (String prefix : this.parentFirstPrefixes) {
            resourcePrefixes.add(prefix.replace('.', '/'));
        }
        this.parentFirstResourcePrefixes = List.copyOf(resourcePrefixes);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                type = startsWithAny(name, parentFirstPrefixes) ? getParent().loadClass(name) : loadChildFirst(name);
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }

    @Override
    public URL getResource(String name) {
        if (startsWithAny(name, parentFirstResourcePrefixes)) {
            return getParent().getResource(name);
        }
        URL own = findResource(name);
        return own != null ? own : getParent().getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (startsWithAny(name, parentFirstResourcePrefixes)) {
            return getParent().getResources(name);
        }
        List<URL> urls = Collections.list(findResources(name));
        urls.addAll(Collections.list(getParent().getResources(name)));
        return Collections.enumeration(urls);
    }

    /**
     * Definit dans ce chargeur une copie d'une classe du plugin (memes octets, meme domaine
     * de protection, donc meme origine de code). La copie
     * s'execute avec ce chargeur comme chargeur de classe appelante : utile pour les API
     * du JDK qui filtrent selon l'appelant, comme {@code DriverManager}. La classe modele ne
     * doit referencer que des classes du JDK.
     *
     * @param template classe modele
     * @return la copie, definie une seule fois par chargeur
     * @throws IllegalStateException si le bytecode du modele est introuvable
     * @throws LinkageError          si la copie est deja definie
     */
    public Class<?> defineCopyOf(Class<?> template) {
        String className = template.getName();
        String resource = className.replace('.', '/') + ".class";
        ClassLoader source = Objects.requireNonNull(template.getClassLoader(), "chargeur du modele");
        byte[] bytecode;
        try (InputStream in = source.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(ConsoleText.tr("Bytecode introuvable : {0}", resource));
            }
            bytecode = in.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException(ConsoleText.tr("Lecture impossible de {0}", resource), failure);
        }
        synchronized (getClassLoadingLock(className)) {
            return defineClass(className, bytecode, 0, bytecode.length, template.getProtectionDomain());
        }
    }

    /**
     * @param jars chemins locaux de jars
     * @return leurs URL {@code file:}, dans le meme ordre
     */
    public static List<URL> toUrls(List<Path> jars) {
        List<URL> urls = new ArrayList<>(jars.size());
        for (Path jar : jars) {
            try {
                urls.add(jar.toAbsolutePath().toUri().toURL());
            } catch (MalformedURLException impossible) {
                // Un chemin local absolu donne toujours une URL file: valide.
                throw new IllegalStateException(impossible);
            }
        }
        return urls;
    }

    private Class<?> loadChildFirst(String name) throws ClassNotFoundException {
        try {
            return findClass(name);
        } catch (ClassNotFoundException absentFromJars) {
            return getParent().loadClass(name);
        }
    }

    private static boolean startsWithAny(String name, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
