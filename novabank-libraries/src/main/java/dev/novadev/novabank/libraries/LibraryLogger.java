package dev.novadev.novabank.libraries;

/**
 * Port de journalisation du module : la plateforme le branche sur le journal du serveur.
 * Les implementations doivent accepter des appels concurrents.
 */
public interface LibraryLogger {

    /**
     * @param message evenement normal (telechargement, chargement d'un pilote)
     */
    void info(String message);

    /**
     * @param message anomalie surmontee (essai echoue, fichier de cache corrompu)
     */
    void warning(String message);
}
