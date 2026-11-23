package dev.novadev.novabank.libraries.classloading;

import java.util.ArrayList;
import java.util.List;

/**
 * Prefixes <em>parent-first</em> des chargeurs isoles, communs aux pilotes JDBC et aux modules
 * embarques ; chaque usage y ajoute les paquets du plugin qu'il partage.
 *
 * <ul>
 *   <li>{@code java.}, {@code javax.}, {@code jdk.}, {@code sun.} : classes du JDK (par exemple
 *       {@code java.sql.Driver} doit etre la meme classe des deux cotes) ;</li>
 *   <li>{@code org.slf4j.} : journal optionnel des pilotes, absent de leurs jars ; s'il est fourni
 *       par le serveur, les pilotes l'utilisent, sinon ils se replient sur
 *       {@code java.util.logging}. Une seconde copie dans le chargeur isole separerait le pilote de
 *       la configuration de journal du serveur.</li>
 * </ul>
 *
 * <p>Le prefixe slf4j vise le slf4j du SERVEUR, jamais celui que le JAR du plugin embarque
 * (relocalise sous {@code dev.novadev.novabank.libs.slf4j}, reserve a HikariCP). Il est construit
 * a l'execution : le plugin shade relocalise aussi les constantes texte qui commencent par
 * {@code org.slf4j.}, ce qui le transformerait en prefixe relocalise.
 */
public final class ParentFirstPrefixes {

    /** Paquets du JDK. */
    public static final List<String> JDK = List.of("java.", "javax.", "jdk.", "sun.");

    private ParentFirstPrefixes() {
    }

    /**
     * @param sharedPackages prefixes des paquets du plugin partages avec le chargeur isole
     * @return les prefixes du JDK, de slf4j (serveur) puis les paquets partages
     */
    public static List<String> withShared(List<String> sharedPackages) {
        List<String> prefixes = new ArrayList<>(JDK);
        prefixes.add(serverSlf4j());
        prefixes.addAll(sharedPackages);
        return List.copyOf(prefixes);
    }

    /**
     * @return {@code org.slf4j.}, hors constante pour echapper a la relocalisation du shade
     */
    private static String serverSlf4j() {
        return String.join(".", "org", "slf4j", "");
    }
}
