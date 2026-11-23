package dev.novadev.novabank.libraries.classloading.fixture;

/**
 * Classe du plugin, aussi presente dans le jar de test : prouve le parent-first.
 */
public final class PluginProbe {

    private PluginProbe() {
    }

    public static String origin() {
        return "parent";
    }
}
