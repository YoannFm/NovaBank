package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.ranking.LeaderboardLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texte des placeholders {@code %novabank_...%}, sans dependance a PlaceholderAPI :
 * {@code value} (banque du type par defaut), {@code value_<type>} (valeur de la banque du joueur pour ce type),
 * {@code top_<type>_<rang>_name} et {@code top_<type>_<rang>_value} (classement). Tout est lu en memoire.
 */
public final class PlaceholderValues {

    private static final Pattern TOP = Pattern.compile("top_(.+)_(\\d{1,3})_(name|value)");
    private static final String VALUE = "value";

    private final Supplier<String> defaultType;
    private final BiFunction<UUID, String, java.util.Optional<BigDecimal>> playerValue;
    private final BiFunction<String, Integer, List<LeaderboardLine>> top;
    private final Function<EntityRef, String> names;

    /**
     * @param defaultType type de banque de {@code %novabank_value%}
     * @param playerValue valeur de la banque d'un joueur pour un type (derniere valeur connue)
     * @param top         classement d'un type, limite en parametre
     * @param names       nom affichable d'une entite
     */
    public PlaceholderValues(Supplier<String> defaultType, BiFunction<UUID, String, java.util.Optional<BigDecimal>> playerValue,
                             BiFunction<String, Integer, List<LeaderboardLine>> top, Function<EntityRef, String> names) {
        this.defaultType = defaultType;
        this.playerValue = playerValue;
        this.top = top;
        this.names = names;
    }

    /**
     * @param player joueur pour qui le placeholder est demande, {@code null} si aucun
     * @param params partie apres {@code novabank_}
     * @return le texte, ou {@code null} si le placeholder est inconnu
     */
    public String resolve(UUID player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals(VALUE)) {
            return value(player, defaultType.get());
        }
        if (key.startsWith("value_")) {
            return value(player, key.substring("value_".length()));
        }
        Matcher matcher = TOP.matcher(key);
        if (matcher.matches()) {
            int rank = Integer.parseInt(matcher.group(2));
            if (rank < 1) {
                return null;
            }
            List<LeaderboardLine> lines = top.apply(matcher.group(1), rank);
            LeaderboardLine line = rank <= lines.size() ? lines.get(rank - 1) : null;
            if (matcher.group(3).equals("name")) {
                return line == null ? "-" : names.apply(line.owner());
            }
            return line == null ? "0" : plain(line.value());
        }
        return null;
    }

    private String value(UUID player, String type) {
        return player == null ? "0" : plain(playerValue.apply(player, type).orElse(BigDecimal.ZERO));
    }

    private static String plain(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
