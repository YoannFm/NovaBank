package dev.novadev.novabank.core.application;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Enchainement d'etapes d'une operation de joueur, alternant le thread du joueur et
 * l'executeur d'entrees/sorties.
 *
 * <p>Garanties : le resultat est livre une seule fois, sur le thread du joueur (perdu s'il
 * s'est deconnecte) ; le verrou du joueur est libere des que le resultat est connu ; une
 * exception inattendue dans une etape, ou un executeur IO sature, passe par
 * {@link #failed(RuntimeException)} pour que chaque flux remette les items en lieu sur.
 *
 * @param <R> type du resultat
 */
abstract class Flow<R> {

    /** Journal commun des flux. */
    static final System.Logger LOGGER = System.getLogger("NovaBank");

    final OperationPorts ports;
    final UUID playerId;
    private final Consumer<R> done;
    private final Runnable release;
    private final AtomicBoolean finished = new AtomicBoolean();

    /**
     * @param ports    collaborateurs
     * @param playerId joueur
     * @param done     recepteur du resultat (thread du joueur)
     * @param release  liberation du verrou du joueur
     */
    Flow(OperationPorts ports, UUID playerId, Consumer<R> done, Runnable release) {
        this.ports = Objects.requireNonNull(ports, "ports");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.done = Objects.requireNonNull(done, "done");
        this.release = Objects.requireNonNull(release, "release");
    }

    /**
     * Etape sur le thread du joueur. Pendant l'arret de NovaBank ({@link FlowDrain}), la
     * branche "joueur parti" est prise meme s'il est connecte : l'inventaire n'est plus
     * touche et les items a rendre passent en livraison differee (ou suivent D13).
     *
     * @param task      etape si le joueur est connecte
     * @param ifOffline etape s'il est parti (ne touche ni au joueur ni au monde, voir
     *                  {@link TaskScheduler#runForPlayer})
     */
    final void onPlayer(Runnable task, Runnable ifOffline) {
        Runnable offline = guarded(ifOffline);
        Runnable online = guarded(task);
        ports.scheduler().runForPlayer(playerId, () -> {
            if (ports.drain().isShuttingDown()) {
                offline.run();
            } else {
                online.run();
            }
        }, offline);
    }

    /**
     * @return motif d'une operation interrompue avant de toucher a l'inventaire : arret de
     *         NovaBank en cours, ou deconnexion du joueur
     */
    final TransferStatus interruptedStatus() {
        return ports.drain().isShuttingDown() ? TransferStatus.SHUTTING_DOWN : TransferStatus.PLAYER_OFFLINE;
    }

    /**
     * Etape sur l'executeur d'entrees/sorties.
     *
     * @param task etape
     */
    final void onIo(Runnable task) {
        try {
            ports.scheduler().runAsync(guarded(task));
        } catch (RejectedExecutionException saturated) {
            failed(saturated);
        }
    }

    /**
     * Termine le flux : libere le verrou puis livre le resultat (une seule fois).
     *
     * @param result resultat
     */
    final void finish(R result) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        release.run();
        ports.scheduler().runForPlayer(playerId, () -> done.accept(result), () -> {
            // Joueur parti : le resultat n'a plus de destinataire.
        });
    }

    /**
     * Echec inattendu d'une etape (exception ou executeur sature). Le flux doit mettre les
     * items en lieu sur puis terminer.
     *
     * @param failure cause
     */
    abstract void failed(RuntimeException failure);

    private Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException failure) {
                failed(failure);
            }
        };
    }
}
