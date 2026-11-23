package dev.novadev.novabank.config.model;

import java.util.Optional;

/**
 * Boutons de la barre de la GUI de banque (derniere ligne du coffre).
 */
public enum GuiButton {

    /** En-tete : banque active, valeur, page. */
    INFO("info"),
    /** Page precedente du stock. */
    PREVIOUS("previous"),
    /** Depot de tous les items acceptes de l'inventaire. */
    DEPOSIT_ALL("deposit-all"),
    /** Page suivante du stock. */
    NEXT("next"),
    /** Fermeture de la GUI. */
    CLOSE("close");

    private final String key;

    GuiButton(String key) {
        this.key = key;
    }

    /**
     * @return cle du bouton dans la configuration et les fichiers de langue
     */
    public String key() {
        return key;
    }

    /**
     * @param key cle de configuration
     * @return le bouton, ou vide si la cle est inconnue
     */
    public static Optional<GuiButton> byKey(String key) {
        for (GuiButton button : values()) {
            if (button.key.equals(key)) {
                return Optional.of(button);
            }
        }
        return Optional.empty();
    }
}
