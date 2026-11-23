/**
 * Modules embarques (decision D18) : jar d'un composant (backend MongoDB, plus tard Redis)
 * compile contre un pilote telecharge, inclus comme ressource dans le jar du plugin avec sa
 * somme SHA-256 generee au build, extrait et verifie dans le dossier des bibliotheques, puis
 * charge avec ses pilotes epingles dans un chargeur isole. Ce paquet ne connait aucun module
 * en particulier : la description de chaque module est une donnee ({@link
 * dev.novadev.novabank.libraries.module.EmbeddedModule}).
 */
package dev.novadev.novabank.libraries.module;
