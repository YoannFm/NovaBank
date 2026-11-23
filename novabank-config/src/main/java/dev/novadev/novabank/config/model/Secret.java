package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Valeur secrete (mot de passe, URI avec identifiants).
 *
 * <p>{@link #toString()} renvoie toujours {@value #MASK} : un secret ne peut pas fuiter
 * par un log ou par le {@code toString()} d'un record qui le contient. La valeur
 * reelle n'est lisible que par un appel explicite a {@link #reveal()}.
 */
public final class Secret {

    /** Texte affiche a la place d'une valeur secrete. */
    public static final String MASK = "****";

    private final String value;

    private Secret(String value) {
        this.value = value;
    }

    /**
     * Enveloppe une valeur secrete.
     *
     * @param value valeur, non nulle (peut etre vide)
     * @return le secret
     */
    public static Secret of(String value) {
        return new Secret(Objects.requireNonNull(value, "value"));
    }

    /**
     * Valeur en clair, a transmettre uniquement au composant qui en a besoin.
     *
     * @return la valeur reelle
     */
    public String reveal() {
        return value;
    }

    /**
     * Indique si la valeur est vide ou ne contient que des espaces.
     *
     * @return {@code true} si aucune valeur utile n'est renseignee
     */
    public boolean isBlank() {
        return value.isBlank();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Secret secret && secret.value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Toujours masque.
     *
     * @return {@value #MASK}
     */
    @Override
    public String toString() {
        return MASK;
    }
}
