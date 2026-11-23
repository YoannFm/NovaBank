package dev.novadev.novabank.bukkit.message;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Langue en vigueur, remplacee a chaud au rechargement de la configuration.
 */
public final class ActiveLanguage implements Supplier<Language> {

    private final LanguageLoader loader;
    private final AtomicReference<Language> current;

    /**
     * @param loader chargeur
     * @param code   langue initiale
     */
    public ActiveLanguage(LanguageLoader loader, String code) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.current = new AtomicReference<>(loader.load(code));
    }

    @Override
    public Language get() {
        return current.get();
    }

    /**
     * Recharge les fichiers de langue.
     *
     * @param code langue a charger
     */
    public void reload(String code) {
        current.set(loader.load(code));
    }
}
