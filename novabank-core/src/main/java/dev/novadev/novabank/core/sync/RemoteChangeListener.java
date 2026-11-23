package dev.novadev.novabank.core.sync;

/**
 * Receveur des changements faits par les AUTRES serveurs. Appele depuis le thread du bus ou de
 * la tache de reconciliation : doit rendre la main vite et reporter tout travail sur le
 * planificateur. Idempotent : un changement peut etre signale par le bus puis par la lecture
 * du stockage.
 */
@FunctionalInterface
public interface RemoteChangeListener {

    /**
     * @param change la banque et la version qu'elle a atteinte
     */
    void onRemoteChange(BankChange change);
}
