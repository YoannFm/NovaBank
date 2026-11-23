/**
 * Chargement de {@code config.yml} cote plateforme : copie du fichier par defaut, lecture
 * par {@code YamlConfiguration} (seul lecteur YAML fiable de 1.17 a 26.x, voir HANDOFF 2.2),
 * conversion en arbre generique pour {@link dev.novadev.novabank.config.NovaBankConfigLoader},
 * disponibilite des items et rechargement a chaud.
 */
package dev.novadev.novabank.bukkit.config;
