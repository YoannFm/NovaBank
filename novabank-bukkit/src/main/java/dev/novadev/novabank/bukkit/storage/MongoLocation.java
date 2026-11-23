package dev.novadev.novabank.bukkit.storage;

/**
 * Emplacement d'une base MongoDB pour les journaux : hotes et base, sans identifiants ni options.
 */
final class MongoLocation {

    private MongoLocation() {
    }

    /**
     * @param uri      URI de connexion (secret)
     * @param database base configuree
     * @return {@code hotes/base}, par exemple {@code h1:27017,h2:27017/novabank}
     */
    static String describe(String uri, String database) {
        String rest = uri.substring(uri.indexOf("://") + 3);
        int end = firstIndexOf(rest, '/', '?');
        String authority = rest.substring(0, end);
        String hosts = authority.substring(authority.lastIndexOf('@') + 1);
        return hosts + "/" + database;
    }

    private static int firstIndexOf(String text, char first, char second) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == first || text.charAt(index) == second) {
                return index;
            }
        }
        return text.length();
    }
}
