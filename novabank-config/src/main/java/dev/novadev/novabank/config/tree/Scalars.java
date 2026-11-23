package dev.novadev.novabank.config.tree;

import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Conversions des scalaires de l'arbre generique (texte, nombres exacts, bornes),
 * sans etat ni rapport d'issues : {@link ConfigNode} decide quoi signaler.
 */
final class Scalars {

    private Scalars() {
    }

    static String scalarText(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    static Optional<BigDecimal> toDecimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return Optional.of(decimal);
        }
        if (raw instanceof BigInteger integer) {
            return Optional.of(new BigDecimal(integer));
        }
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            return Optional.of(BigDecimal.valueOf(((Number) raw).longValue()));
        }
        if (raw instanceof Double || raw instanceof Float) {
            double value = ((Number) raw).doubleValue();
            // Float passe par son texte pour ne pas exposer l'imprecision binaire (1.1f).
            return Double.isFinite(value) ? Optional.of(new BigDecimal(raw.toString())) : Optional.empty();
        }
        if (raw instanceof String text) {
            try {
                return Optional.of(new BigDecimal(text.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static Optional<Long> exactLong(BigDecimal decimal) {
        try {
            return Optional.of(decimal.longValueExact());
        } catch (ArithmeticException e) {
            return Optional.empty();
        }
    }

    static String rangeText(long min, long max) {
        boolean noMin = min == Integer.MIN_VALUE || min == Long.MIN_VALUE;
        boolean noMax = max == Integer.MAX_VALUE || max == Long.MAX_VALUE;
        if (noMax) {
            return ConsoleText.tr("superieur ou egal a {0}", min);
        }
        if (noMin) {
            return ConsoleText.tr("inferieur ou egal a {0}", max);
        }
        return ConsoleText.tr("compris entre {0} et {1}", min, max);
    }
}
