/**
 * Bus de synchronisation sur Redis Pub/Sub : client RESP minimal (PUBLISH, SUBSCRIBE, AUTH, PING),
 * reconnexion automatique. Redis ne porte aucune donnee de reference : un message perdu est
 * rattrape par la lecture du stockage ({@code SyncCoordinator}).
 */
package dev.novadev.novabank.sync.redis;
