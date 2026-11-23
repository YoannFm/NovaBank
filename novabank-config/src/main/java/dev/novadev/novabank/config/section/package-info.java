/**
 * Un parseur par section de {@code config.yml}. Chaque parseur lit sa section via
 * {@link dev.novadev.novabank.config.tree.ConfigNode}, applique les valeurs par defaut,
 * valide et produit le record du modele ; les issues sont accumulees, jamais levees.
 */
package dev.novadev.novabank.config.section;
