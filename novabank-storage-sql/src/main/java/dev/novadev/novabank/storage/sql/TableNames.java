package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Noms des tables, tous prefixes pour cohabiter avec d'autres plugins dans la meme base.
 *
 * <p>Le prefixe est valide strictement car il est concatene dans le SQL.
 */
final class TableNames {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    private final String prefix;

    /**
     * @param prefix prefixe des tables, {@code [a-z][a-z0-9_]{0,31}}
     * @throws IllegalArgumentException si le prefixe est invalide
     */
    TableNames(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        if (!PREFIX_PATTERN.matcher(prefix).matches()) {
            throw new IllegalArgumentException(
                    ConsoleText.tr("Prefixe de tables invalide (attendu [a-z][a-z0-9_]{0,31}) : '{0}'", prefix));
        }
        this.prefix = prefix;
    }

    String prefix() {
        return prefix;
    }

    String schemaVersion() {
        return prefix + "schema_version";
    }

    String banks() {
        return prefix + "banks";
    }

    String stockLines() {
        return prefix + "stock_lines";
    }

    String operations() {
        return prefix + "operations";
    }

    String pendingDeliveries() {
        return prefix + "pending_deliveries";
    }

    String activeBanks() {
        return prefix + "active_banks";
    }
}
