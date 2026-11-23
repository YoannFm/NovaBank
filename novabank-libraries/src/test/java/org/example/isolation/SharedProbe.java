package org.example.isolation;

/**
 * Presente cote parent et dans le jar de test (version "jar") : prouve le child-first.
 */
public final class SharedProbe {

    private SharedProbe() {
    }

    public static String origin() {
        return "parent";
    }
}
