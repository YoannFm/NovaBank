package dev.novadev.novabank.bukkit.host;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/**
 * Canal entre les JAR NovaBank d'un meme serveur (plugin autonome, module SuperiorSkyblock2, addon BentoBox, extension
 * Towny...). Chaque JAR embarque sa propre copie des classes NovaBank : ils ne peuvent donc echanger que des types
 * communs a tous, ici un {@link Supplier} du JDK enregistre dans les services Bukkit et reconnu a son texte
 * ({@code toString}). Chaque JAR y annonce son edition ; le moteur, unique, y signale sa presence.
 */
public final class EditionChannel {

    /** Prefixe des annonces d'edition : {@code novabank:edition:<edition>:<version>}. */
    static final String EDITION = "novabank:edition:";
    /** Annonce du moteur en marche : {@code novabank:engine:<version>}. */
    static final String ENGINE = "novabank:engine:";

    private EditionChannel() {
    }

    /**
     * Annonce une edition (ou le moteur). A fermer quand le JAR s'arrete.
     *
     * @param services services du serveur
     * @param owner    plugin proprietaire (l'hote pour un module ou un addon)
     * @param text     texte de l'annonce
     * @return le retrait de l'annonce
     */
    static AutoCloseable announce(ServicesManager services, Plugin owner, String text) {
        Supplier<String> marker = new Marker(text);
        services.register(Supplier.class, marker, owner, ServicePriority.Lowest);
        return () -> services.unregister(Supplier.class, marker);
    }

    /**
     * @param services services du serveur
     * @param owner    plugin proprietaire
     * @param edition  edition de ce JAR ({@code standalone}, {@code superiorskyblock2}...)
     * @param version  version de NovaBank
     * @return le retrait de l'annonce
     */
    public static AutoCloseable announceEdition(ServicesManager services, Plugin owner, String edition, String version) {
        return announce(services, owner, EDITION + edition + ":" + version);
    }

    /**
     * @param services services du serveur
     * @return les editions annoncees par les JAR NovaBank presents
     */
    public static Set<String> editions(ServicesManager services) {
        Set<String> editions = new TreeSet<>();
        for (String text : texts(services)) {
            if (text.startsWith(EDITION)) {
                String rest = text.substring(EDITION.length());
                int colon = rest.indexOf(':');
                editions.add(colon < 0 ? rest : rest.substring(0, colon));
            }
        }
        return editions;
    }

    /**
     * @param services services du serveur
     * @return {@code true} si un moteur NovaBank tourne deja sur ce serveur
     */
    public static boolean engineRunning(ServicesManager services) {
        return texts(services).stream().anyMatch(text -> text.startsWith(ENGINE));
    }

    /**
     * @param provider service enregistre (peut venir d'un autre JAR NovaBank : reconnu a son texte, pas a sa classe)
     * @return {@code true} s'il s'agit d'une annonce NovaBank (edition ou moteur)
     */
    static boolean isAnnouncement(Object provider) {
        return provider instanceof Supplier<?> && String.valueOf(provider).startsWith("novabank:");
    }

    /**
     * @param services services du serveur
     * @param owner    plugin proprietaire du moteur
     * @param version  version de NovaBank
     * @return le retrait de l'annonce, a l'arret du moteur
     */
    public static AutoCloseable announceEngine(ServicesManager services, Plugin owner, String version) {
        return announce(services, owner, ENGINE + version);
    }

    private static Set<String> texts(ServicesManager services) {
        Set<String> texts = new TreeSet<>();
        for (RegisteredServiceProvider<?> registration : services.getRegistrations(Supplier.class)) {
            if (isAnnouncement(registration.getProvider())) {
                texts.add(String.valueOf(registration.getProvider()));
            }
        }
        return texts;
    }

    /** Annonce : seul son texte compte, {@link #get()} ne fait rien d'autre que le rendre. */
    private record Marker(String text) implements Supplier<String> {

        Marker {
            Objects.requireNonNull(text, "text");
        }

        @Override
        public String get() {
            return text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
