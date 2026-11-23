package dev.novadev.novabank.core.sync;

import java.util.Objects;

/**
 * Notification diffusee sur le bus.
 *
 * @param originServer serveur qui a ecrit le changement
 * @param change       changement
 */
public record SyncEvent(String originServer, BankChange change) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public SyncEvent {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(change, "change");
    }
}
