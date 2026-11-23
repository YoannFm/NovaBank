package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ecriture et lecture d'un arbre de textes sur une seule ligne (contenu d'une ligne du journal
 * d'ecriture) : sous-ensemble strict de JSON limite aux tables, listes et textes, sans espace ;
 * tout caractere de controle d'un texte est echappe, la ligne ne contient donc jamais de saut de
 * ligne. Independant de Gson et de SnakeYAML : le journal a le meme format en YAML et en JSON.
 */
final class CompactTree {

    private CompactTree() {
    }

    /**
     * @param tree arbre de {@link Map}, {@link List} et {@link String}
     * @return sa forme compacte, sur une ligne
     */
    static String write(Map<String, Object> tree) {
        StringBuilder text = new StringBuilder(256);
        writeValue(text, tree);
        return text.toString();
    }

    /**
     * @param text forme compacte
     * @return l'arbre
     * @throws CorruptDataException si le texte n'est pas une forme compacte complete
     */
    static Map<String, Object> read(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        if (parser.position != text.length()) {
            throw new CorruptDataException(ConsoleText.tr("contenu inattendu a la position {0}", parser.position));
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new CorruptDataException(ConsoleText.tr("table attendue"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tree = (Map<String, Object>) value;
        return tree;
    }

    private static void writeValue(StringBuilder text, Object value) {
        if (value instanceof Map<?, ?> map) {
            text.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    text.append(',');
                }
                first = false;
                writeString(text, (String) entry.getKey());
                text.append(':');
                writeValue(text, entry.getValue());
            }
            text.append('}');
        } else if (value instanceof List<?> list) {
            text.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    text.append(',');
                }
                writeValue(text, list.get(index));
            }
            text.append(']');
        } else {
            writeString(text, (String) value);
        }
    }

    private static void writeString(StringBuilder text, String value) {
        text.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                text.append('\\').append(character);
            } else if (character < 0x20 || character == 0x7f || character == 0x2028 || character == 0x2029) {
                text.append(String.format("\\u%04x", (int) character));
            } else {
                text.append(character);
            }
        }
        text.append('"');
    }

    /** Analyseur strict de la forme compacte. */
    private static final class Parser {

        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        Object value() {
            char next = peek();
            if (next == '{') {
                return object();
            }
            if (next == '[') {
                return array();
            }
            if (next == '"') {
                return string();
            }
            throw error("valeur attendue");
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            if (peek() == '}') {
                position++;
                return map;
            }
            do {
                String key = string();
                expect(':');
                if (map.put(key, value()) != null) {
                    throw error("cle en double '" + key + "'");
                }
            } while (consume(','));
            expect('}');
            return map;
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            expect('[');
            if (peek() == ']') {
                position++;
                return list;
            }
            do {
                list.add(value());
            } while (consume(','));
            expect(']');
            return list;
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (true) {
                if (position >= text.length()) {
                    throw error("texte non termine");
                }
                char character = text.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (position >= text.length()) {
                    throw error("echappement incomplet");
                }
                char escaped = text.charAt(position++);
                if (escaped == '"' || escaped == '\\') {
                    value.append(escaped);
                } else if (escaped == 'u' && position + 4 <= text.length()) {
                    try {
                        value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    } catch (NumberFormatException invalid) {
                        throw error("echappement unicode invalide");
                    }
                    position += 4;
                } else {
                    throw error("echappement invalide");
                }
            }
        }

        private char peek() {
            if (position >= text.length()) {
                throw error("fin de ligne inattendue");
            }
            return text.charAt(position);
        }

        private void expect(char expected) {
            if (peek() != expected) {
                throw error("'" + expected + "' attendu");
            }
            position++;
        }

        private boolean consume(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private CorruptDataException error(String message) {
            return new CorruptDataException(ConsoleText.tr("{0} a la position {1}", message, position));
        }
    }
}
