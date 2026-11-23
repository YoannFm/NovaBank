package dev.novadev.novabank.api.entity;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reference vers l'entite proprietaire d'une banque : un joueur, une ile, une
 * faction, une ville, un claim...
 *
 * <p>Le type est une chaine et non une enumeration : chaque integration tierce peut
 * declarer son propre type sans modifier l'API. Il est normalise en minuscules
 * ({@link Locale#ROOT}) et doit ensuite respecter {@code [a-z0-9_.-]+}.
 * L'identifiant est celui de l'entite dans son plugin d'origine (UUID, nom,
 * identifiant numerique...), conserve tel quel ; il ne peut pas etre vide.
 *
 * @param type type d'entite normalise, par exemple {@code player} ou {@code island}
 * @param id   identifiant externe de l'entite, non vide
 */
public record EntityRef(String type, String id) {

    /** Type des banques personnelles de joueur. */
    public static final String PLAYER = "player";
    /** Type des banques d'ile (plugins skyblock). */
    public static final String ISLAND = "island";
    /** Type des banques de faction. */
    public static final String FACTION = "faction";

    private static final Pattern TYPE_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    /**
     * Construit une reference en normalisant le type.
     *
     * @throws NullPointerException     si une partie est nulle
     * @throws IllegalArgumentException si le type est invalide ou l'identifiant vide
     */
    public EntityRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        type = type.toLowerCase(Locale.ROOT);
        if (!TYPE_PATTERN.matcher(type).matches()) {
            throw new IllegalArgumentException("Invalid entity type (expected [a-z0-9_.-]+): '" + type + "'");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("Empty entity id");
        }
    }

    /**
     * Reference vers un joueur.
     *
     * @param playerId UUID du joueur
     * @return la reference {@code player:<uuid>}
     */
    public static EntityRef player(UUID playerId) {
        return new EntityRef(PLAYER, playerId.toString());
    }

    /**
     * Forme textuelle {@code type:id}, destinee aux logs et messages.
     *
     * @return la reference au format texte
     */
    @Override
    public String toString() {
        return type + ':' + id;
    }
}
