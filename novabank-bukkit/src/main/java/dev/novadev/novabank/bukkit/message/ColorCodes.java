package dev.novadev.novabank.bukkit.message;

import java.util.Objects;

/**
 * Conversion des codes couleur saisis par l'administrateur en codes Minecraft, cote
 * serveur : {@code &0}-{@code &9}, {@code &a}-{@code &f}, styles {@code &k}-{@code &o},
 * {@code &r}, et couleurs hexadecimales {@code &#RRGGBB} (Minecraft 1.16 et plus, donc
 * toutes les versions supportees). Un {@code &} qui ne precede pas un code reste tel quel.
 */
public final class ColorCodes {

    /** Caractere de section des codes Minecraft. */
    private static final char SECTION = '§';

    private static final String CODES = "0123456789abcdefklmnor";
    private static final String HEX = "0123456789abcdef";

    private ColorCodes() {
    }

    /**
     * @param text texte avec codes {@code &}
     * @return texte avec codes Minecraft
     */
    public static String translate(String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&' && isHexColor(text, i)) {
                out.append(SECTION).append('x');
                for (int j = i + 2; j < i + 8; j++) {
                    out.append(SECTION).append(Character.toLowerCase(text.charAt(j)));
                }
                i += 8;
            } else if (c == '&' && i + 1 < text.length() && CODES.indexOf(Character.toLowerCase(text.charAt(i + 1))) >= 0) {
                out.append(SECTION).append(Character.toLowerCase(text.charAt(i + 1)));
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isHexColor(String text, int start) {
        if (start + 8 > text.length() || text.charAt(start + 1) != '#') {
            return false;
        }
        for (int j = start + 2; j < start + 8; j++) {
            if (HEX.indexOf(Character.toLowerCase(text.charAt(j))) < 0) {
                return false;
            }
        }
        return true;
    }
}
