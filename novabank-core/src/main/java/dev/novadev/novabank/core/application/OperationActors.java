package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.OperationId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Joueur a l'origine des operations lancees sur ce serveur : le journal ne le garde pas pour un depot, mais les
 * evenements publics et le salon Discord le citent. Memoire bornee aux operations recentes (l'evenement suit
 * l'operation de quelques millisecondes).
 */
public final class OperationActors {

    static final int CAPACITY = 4096;

    private final Map<OperationId, UUID> actors = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<OperationId, UUID> eldest) {
            return size() > CAPACITY;
        }
    };

    /**
     * @param operation operation sur le point d'etre enregistree
     * @param player    joueur qui la lance
     */
    public synchronized void remember(OperationId operation, UUID player) {
        actors.put(Objects.requireNonNull(operation, "operation"), Objects.requireNonNull(player, "player"));
    }

    /**
     * @param operation operation enregistree
     * @return le joueur qui l'a lancee sur ce serveur, vide sinon (API, autre serveur, administration)
     */
    public synchronized Optional<UUID> actor(OperationId operation) {
        return Optional.ofNullable(actors.get(operation));
    }
}
