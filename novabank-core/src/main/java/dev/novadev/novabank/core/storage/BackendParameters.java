package dev.novadev.novabank.core.storage;

import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parametres d'ouverture d'un backend, sans dependance au modele de configuration : reglages
 * texte par cle, horloge et journal.
 *
 * <p>Les cles marquees secretes (URI avec identifiants, mots de passe) ne sont jamais
 * restituees par {@link #toString()} ni par les messages d'erreur de cette classe.
 */
public final class BackendParameters {

    private static final String MASK = "****";

    private final Map<String, String> values;
    private final Set<String> secretKeys;
    private final Clock clock;
    private final Logger logger;

    private BackendParameters(Builder builder) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(builder.values));
        this.secretKeys = Collections.unmodifiableSet(new LinkedHashSet<>(builder.secretKeys));
        this.clock = builder.clock;
        this.logger = builder.logger;
    }

    /**
     * @param clock  horloge des dates techniques du backend
     * @param logger journal du backend (celui du plugin)
     * @return un constructeur de parametres vide
     */
    public static Builder builder(Clock clock, Logger logger) {
        return new Builder(clock, logger);
    }

    /**
     * @param key cle du reglage
     * @return la valeur
     * @throws IllegalArgumentException si le reglage est absent
     */
    public String text(String key) {
        String value = values.get(Objects.requireNonNull(key, "key"));
        if (value == null) {
            throw new IllegalArgumentException(ConsoleText.tr("Parametre de stockage manquant : {0}", key));
        }
        return value;
    }

    /**
     * @param key cle du reglage
     * @param min valeur minimale acceptee
     * @param max valeur maximale acceptee
     * @return la valeur entiere
     * @throws IllegalArgumentException si le reglage est absent, non entier ou hors bornes
     */
    public int integer(String key, int min, int max) {
        String text = text(key);
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(ConsoleText.tr("Parametre de stockage {0} non entier : {1}",
                    key, display(key, text)),
                    notANumber);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(ConsoleText.tr("Parametre de stockage {0} hors de [{1}, {2}] : {3}",
                    key, min, max, display(key, text)));
        }
        return value;
    }

    /**
     * @param key cle du reglage
     * @return {@code true} si la cle a ete declaree secrete
     */
    public boolean isSecret(String key) {
        return secretKeys.contains(key);
    }

    /**
     * @return les cles presentes, dans l'ordre d'ajout
     */
    public Set<String> keys() {
        return values.keySet();
    }

    /**
     * @return l'horloge du backend
     */
    public Clock clock() {
        return clock;
    }

    /**
     * @return le journal du backend
     */
    public Logger logger() {
        return logger;
    }

    /**
     * @return les reglages, valeurs secretes masquees
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("BackendParameters{");
        String separator = "";
        for (Map.Entry<String, String> entry : values.entrySet()) {
            text.append(separator).append(entry.getKey()).append('=').append(display(entry.getKey(), entry.getValue()));
            separator = ", ";
        }
        return text.append('}').toString();
    }

    private String display(String key, String value) {
        return secretKeys.contains(key) ? MASK : value;
    }

    /**
     * Constructeur de {@link BackendParameters}.
     */
    public static final class Builder {

        private final Map<String, String> values = new LinkedHashMap<>();
        private final Set<String> secretKeys = new LinkedHashSet<>();
        private final Clock clock;
        private final Logger logger;

        private Builder(Clock clock, Logger logger) {
            this.clock = Objects.requireNonNull(clock, "clock");
            this.logger = Objects.requireNonNull(logger, "logger");
        }

        /**
         * @param key   cle du reglage
         * @param value valeur
         * @return ce constructeur
         */
        public Builder put(String key, String value) {
            values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            secretKeys.remove(key);
            return this;
        }

        /**
         * @param key   cle du reglage
         * @param value valeur
         * @return ce constructeur
         */
        public Builder put(String key, long value) {
            return put(key, Long.toString(value));
        }

        /**
         * @param key   cle d'un reglage secret
         * @param value valeur, jamais affichee
         * @return ce constructeur
         */
        public Builder putSecret(String key, String value) {
            put(key, value);
            secretKeys.add(key);
            return this;
        }

        /**
         * @return les parametres figes
         */
        public BackendParameters build() {
            return new BackendParameters(this);
        }
    }
}
