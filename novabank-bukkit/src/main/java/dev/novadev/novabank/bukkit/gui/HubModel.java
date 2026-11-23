package dev.novadev.novabank.bukkit.gui;

/**
 * Etat lu et actions declenchees par le menu principal d'administration ({@link AdminHub}). Implemente par la commande,
 * qui connait les brouillons des editeurs et le chemin d'enregistrement.
 */
public interface HubModel {

    /** @return nombre d'items du brouillon d'items (ou du fichier s'il n'y a pas de brouillon) */
    int itemsCount();

    /** @return {@code true} si le brouillon d'items n'est pas enregistre */
    boolean itemsDirty();

    /** @return nombre de lignes du brouillon de GUI (ou du GUI en vigueur) */
    int guiRows();

    /** @return {@code true} si le brouillon de GUI n'est pas enregistre */
    boolean guiDirty();

    /** @return {@code true} si le brouillon des parametres n'est pas enregistre */
    boolean settingsDirty();

    /** Ouvre le menu des parametres (tous les autres reglages de config.yml). */
    void openSettings();

    /** Ouvre l'editeur d'items. */
    void openItems();

    /** Ouvre l'editeur visuel de la disposition du GUI. */
    void openGui();

    /** @param delta variation du nombre de lignes du GUI (brouillon) */
    void changeRows(int delta);

    /** Remet la disposition par defaut dans le brouillon de GUI. */
    void resetGui();

    /** @return {@code true} si un renvoi aux autres serveurs est possible (sinon le menu l'annonce sans confirmation) */
    boolean canPush();

    /** Demande une confirmation (ecran ANNULER / CONFIRMER) avant d'executer une action du menu. */
    void confirm(Runnable action);

    /** Enregistre et deploie tous les brouillons modifies, en une seule ecriture. */
    void save();

    /** Abandonne tous les brouillons. */
    void cancel();

    /** Renvoie la configuration actuelle aux autres serveurs. */
    void push();

    /** Recharge config.yml. */
    void reload();
}
