/**
 * Pilotes de base de donnees obtenus a l'execution (decision D6) : les pilotes ne sont pas
 * inclus dans le jar du plugin (licences GPL/LGPL, taille). Ils sont telecharges depuis
 * Maven Central (ou un miroir), verifies par une somme SHA-256 epinglee dans le code,
 * mis en cache dans le dossier des bibliotheques, puis charges dans un chargeur de
 * classes isole. Point d'entree : {@link dev.novadev.novabank.libraries.JdbcDrivers}.
 */
package dev.novadev.novabank.libraries;
