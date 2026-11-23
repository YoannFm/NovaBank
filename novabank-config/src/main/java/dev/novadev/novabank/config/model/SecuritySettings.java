package dev.novadev.novabank.config.model;

/**
 * Section {@code security} : protections anti-duplication.
 *
 * @param savePlayerData         sauvegarder les donnees du joueur juste apres chaque validation
 *                               en base (ferme la fenetre de duplication en cas de crash du serveur)
 * @param shutdownDrainTimeoutMs attente maximale, a l'arret de NovaBank, de la fin des depots et
 *                               retraits en cours avant la fermeture de la base
 */
public record SecuritySettings(boolean savePlayerData, long shutdownDrainTimeoutMs) {
}
