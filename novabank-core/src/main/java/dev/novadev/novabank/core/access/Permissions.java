package dev.novadev.novabank.core.access;

/**
 * Noms des permissions de NovaBank, verifiees cote serveur a chaque operation.
 */
public final class Permissions {

    /** Utilisation de la banque (consultation, choix de la banque active). */
    public static final String USE = "novabank.use";
    /** Depot d'items. */
    public static final String DEPOSIT = "novabank.deposit";
    /** Retrait d'items. */
    public static final String WITHDRAW = "novabank.withdraw";
    /** Administration (diagnostic, rechargement, consultation et historique d'une banque). */
    public static final String ADMIN = "novabank.admin";
    /** Reinitialisation de la valeur d'une banque. */
    public static final String ADMIN_RESET = "novabank.admin.reset";

    /** Migration du stockage vers un autre backend (avec confirmation). */
    public static final String ADMIN_MIGRATE = "novabank.admin.migrate";

    /** Transfert du stock d'une banque vers une autre : fusion ou dissolution de groupe (avec confirmation). */
    public static final String ADMIN_TRANSFER = "novabank.admin.transfer";

    /** Lecture et modification de la configuration en jeu. */
    public static final String ADMIN_CONFIG = "novabank.admin.config";

    /** Modification des secrets (mots de passe, URI MongoDB) dans le menu des parametres. */
    public static final String ADMIN_SECRETS = "novabank.admin.secrets";

    /** Editeur visuel de la disposition du GUI. */
    public static final String ADMIN_GUI = "novabank.admin.gui";

    /** Edition des items acceptes et de leurs prix. */
    public static final String ADMIN_ITEMS = "novabank.admin.items";

    private Permissions() {
    }
}
