package dev.novadev.novabank.api.item;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cle d'un item deposable, au format {@code namespace:id}.
 *
 * <p>Exemples valides : {@code minecraft:stone}, {@code itemsadder:pack/ruby_block},
 * {@code nexo:ruby_block}. Le namespace designe la source de l'item
 * ({@code minecraft} pour le vanilla, sinon le fournisseur d'items custom).
 *
 * <p>Validation stricte, sans normalisation : le namespace respecte
 * {@code [a-z0-9_.-]+} et l'identifiant {@code [a-z0-9_./-]+}. Une majuscule ou un
 * caractere hors de ces ensembles est refuse plutot que corrige, pour qu'une cle
 * ait une seule ecriture possible (elle sert de cle de stockage).
 *
 * @param namespace source de l'item, non vide
 * @param id        identifiant de l'item dans sa source, non vide
 */
public record ItemKey(String namespace, String id) {

    /** Namespace des items vanilla. */
    public static final String MINECRAFT = "minecraft";

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_./-]+");
    private static final char SEPARATOR = ':';

    /**
     * Construit une cle en validant ses deux parties.
     *
     * @throws NullPointerException     si une partie est nulle
     * @throws IllegalArgumentException si une partie ne respecte pas son format
     */
    public ItemKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(id, "id");
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid item namespace (expected [a-z0-9_.-]+): '" + namespace + "'");
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid item id (expected [a-z0-9_./-]+): '" + id + "'");
        }
    }

    /**
     * Lit une cle au format {@code namespace:id}. Le namespace est obligatoire.
     *
     * @param text texte a lire, non nul
     * @return la cle correspondante
     * @throws IllegalArgumentException si le texte n'est pas une cle valide
     */
    public static ItemKey parse(String text) {
        Objects.requireNonNull(text, "text");
        int separator = text.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("Item key without namespace (expected namespace:id): '" + text + "'");
        }
        return new ItemKey(text.substring(0, separator), text.substring(separator + 1));
    }

    /**
     * Cree la cle d'un item vanilla.
     *
     * @param id identifiant vanilla en minuscules, par exemple {@code stone}
     * @return la cle {@code minecraft:<id>}
     */
    public static ItemKey minecraft(String id) {
        return new ItemKey(MINECRAFT, id);
    }

    /**
     * Forme canonique {@code namespace:id}, relue a l'identique par {@link #parse(String)}.
     *
     * @return la cle au format texte
     */
    @Override
    public String toString() {
        return namespace + SEPARATOR + id;
    }
}
