package dev.novadev.novabank.bukkit.message;

import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Textes d'une langue : fichier de l'administrateur, avec repli sur le fichier fourni
 * dans le JAR pour chaque cle absente (signale une seule fois par cle).
 *
 * <p>Placeholders {@code {nom}} remplaces par les valeurs fournies, puis codes couleur
 * convertis ({@link ColorCodes}). Nombres formates selon la locale de la langue (cle
 * {@code locale}), dates selon {@code date-format}. Sans dependance serveur.
 */
public final class Language {

    private static final String PREFIX_KEY = "prefix";
    private static final String LOCALE_KEY = "locale";
    private static final String DATE_FORMAT_KEY = "date-format";
    private static final int VALUE_DECIMALS = 2;

    private final String code;
    private final Map<String, String> messages;
    private final Map<String, String> fallback;
    private final Consumer<String> warnings;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private final Locale locale;
    private final DateTimeFormatter dates;

    /**
     * @param code     code de la langue, pour les avertissements
     * @param messages textes de l'administrateur (cles a points)
     * @param fallback textes du JAR (cles a points)
     * @param warnings journal des cles absentes
     */
    public Language(String code, Map<String, String> messages, Map<String, String> fallback,
                    Consumer<String> warnings) {
        this.code = Objects.requireNonNull(code, "code");
        this.messages = Map.copyOf(messages);
        this.fallback = Map.copyOf(fallback);
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.locale = Locale.forLanguageTag(raw(LOCALE_KEY).replace('_', '-'));
        this.dates = dateFormatter(raw(DATE_FORMAT_KEY), locale);
    }

    /**
     * @return code de la langue
     */
    public String code() {
        return code;
    }

    /**
     * Texte d'une cle, placeholders remplaces et couleurs converties.
     *
     * @param key          cle a points, par exemple {@code player.deposit.success}
     * @param placeholders paires nom, valeur
     * @return le texte
     * @throws IllegalArgumentException si les placeholders ne sont pas des paires
     */
    public String text(String key, Object... placeholders) {
        return ColorCodes.translate(substitute(raw(key), placeholders));
    }

    /**
     * Texte d'une cle precede du prefixe de chat.
     *
     * @param key          cle
     * @param placeholders paires nom, valeur
     * @return le texte prefixe
     */
    public String chat(String key, Object... placeholders) {
        return ColorCodes.translate(raw(PREFIX_KEY) + substitute(raw(key), placeholders));
    }

    /**
     * @param quantity quantite
     * @return la quantite formatee (separateur de milliers de la langue)
     */
    public String quantity(long quantity) {
        return normalizeSpaces(NumberFormat.getIntegerInstance(locale).format(quantity));
    }

    /**
     * @param value montant
     * @return le montant formate, au plus deux decimales
     */
    public String value(BigDecimal value) {
        DecimalFormat format = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(VALUE_DECIMALS);
        format.setMinimumFractionDigits(0);
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return normalizeSpaces(format.format(value));
    }

    /**
     * @param instant instant
     * @return la date formatee dans le fuseau du serveur
     */
    public String date(Instant instant) {
        return dates.format(instant);
    }

    /**
     * Aplatit un arbre YAML en cles a points ({@code a.b.c}), valeurs en texte. Les
     * listes sont jointes par des retours a la ligne.
     *
     * @param tree arbre
     * @return les textes par cle
     */
    public static Map<String, String> flatten(Map<?, ?> tree) {
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", tree, flat);
        return Collections.unmodifiableMap(flat);
    }

    private static void flatten(String prefix, Map<?, ?> tree, Map<String, String> flat) {
        tree.forEach((key, value) -> {
            String path = prefix.isEmpty() ? String.valueOf(key) : prefix + '.' + key;
            if (value instanceof Map<?, ?> nested) {
                flatten(path, nested, flat);
            } else if (value instanceof Iterable<?> lines) {
                StringBuilder joined = new StringBuilder();
                lines.forEach(line -> joined.append(joined.length() == 0 ? "" : "\n").append(line));
                flat.put(path, joined.toString());
            } else if (value != null) {
                flat.put(path, String.valueOf(value));
            }
        });
    }

    private String raw(String key) {
        String text = messages.get(key);
        if (text != null) {
            return text;
        }
        String backup = fallback.get(key);
        if (reported.add(key)) {
            warnings.accept(backup == null
                    ? "Cle de langue inconnue '" + key + "' (lang/" + code + ".yml et fichier du JAR)"
                    : "Cle de langue absente de lang/" + code + ".yml : '" + key + "', texte par defaut utilise");
        }
        return backup == null ? key : backup;
    }

    private static String substitute(String text, Object... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Placeholders attendus par paires nom, valeur"));
        }
        String result = text;
        for (int i = 0; i < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }

    private static String normalizeSpaces(String formatted) {
        // Espaces insecables (fr) : absents de la police Minecraft.
        return formatted.replace(' ', ' ').replace(' ', ' ');
    }

    private DateTimeFormatter dateFormatter(String pattern, Locale formatLocale) {
        try {
            return DateTimeFormatter.ofPattern(pattern, formatLocale).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException invalid) {
            warnings.accept("Format de date invalide dans lang/" + code + ".yml : '" + pattern + "'");
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
        }
    }
}
