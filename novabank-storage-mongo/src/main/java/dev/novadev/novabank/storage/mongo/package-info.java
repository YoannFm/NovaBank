/**
 * Stockage MongoDB des banques (module embarque, decision D18).
 *
 * <p>Collections : {@code banks} (un document par banque, lignes de stock embarquees),
 * {@code operations} (journal, {@code _id} = identifiant d'operation), {@code pending_deliveries},
 * {@code active_banks} et {@code schema}. Montants en texte decimal canonique (echelle 4) :
 * relecture exacte quelle que soit leur taille, sans la limite de 34 chiffres de Decimal128.
 *
 * <p>Atomicite (C7) : transactions multi-documents si le serveur les supporte (replica set ou
 * cluster shard), sinon protocole du journal en attente (voir {@link
 * dev.novadev.novabank.storage.mongo.PendingOperations}).
 *
 * <p>Ce module n'est jamais inclus a plat dans le jar du plugin : il est extrait et charge dans
 * un chargeur isole avec le pilote telecharge. Il ne voit du plugin que l'API et le coeur.
 */
package dev.novadev.novabank.storage.mongo;
