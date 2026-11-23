package dev.novadev.novabank.bukkit.gui;

import java.util.List;

/**
 * Ecran d'une GUI ouverte pour un joueur (thread du joueur).
 */
public interface MenuScreen {

    /**
     * Affiche le contenu : ouvre le coffre au premier appel, le met a jour ensuite.
     *
     * @param model contenu
     */
    void show(MenuModel model);

    /**
     * Ferme le coffre (au tick suivant).
     */
    void close();

    /**
     * Remplace le coffre par la liste des items acceptes et de leur prix (au tick suivant).
     */
    void showPrices();

    /**
     * @param lines messages a envoyer au joueur
     */
    void send(List<String> lines);
}
