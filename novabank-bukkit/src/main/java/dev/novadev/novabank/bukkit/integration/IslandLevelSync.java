package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.BankInfo;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.event.BankOperationEvent;
import dev.novadev.novabank.config.model.IslandLevelSettings;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Ajoute la valeur de la banque d'une ile au niveau de l'ile de SuperiorSkyblock2. A chaque operation sur une banque
 * d'ile, la valeur COURANTE de la banque est relue puis resynchronisee : le bonus de l'ile est corrige de la seule
 * difference avec ce que NovaBank y avait deja mis (retenu dans l'ile elle-meme), donc les bonus venus d'ailleurs ne sont
 * jamais touches et un depot, un retrait, une annulation ou une reinitialisation donnent le meme resultat.
 */
public final class IslandLevelSync implements Listener {

    /** Un bonus d'ile, vu par la synchronisation. */
    public interface IslandBonus {

        /** @return ce que NovaBank a deja ajoute a ce bonus (zero au depart) */
        BigDecimal applied(IslandLevelSettings.Target target);

        /** @return le bonus actuel de l'ile */
        BigDecimal bonus(IslandLevelSettings.Target target);

        /**
         * @param target     bonus modifie
         * @param newBonus   nouveau bonus de l'ile
         * @param newApplied nouvelle part de NovaBank dans ce bonus
         */
        void update(IslandLevelSettings.Target target, BigDecimal newBonus, BigDecimal newApplied);
    }

    private final Supplier<IslandLevelSettings> settings;
    private final Function<BankId, CompletableFuture<Optional<BankInfo>>> banks;
    private final Function<String, Optional<IslandBonus>> islands;
    private final java.util.function.Consumer<Runnable> mainThread;
    private final Logger logger;
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * @param settings   reglage en vigueur (relu a chaque operation : un rechargement s'applique sans redemarrage)
     * @param banks      lecture d'une banque
     * @param islands    ile par identifiant (UUID), vide si elle n'existe pas
     * @param mainThread execute une tache sur le thread qui peut modifier SuperiorSkyblock2
     * @param logger     journal
     */
    public IslandLevelSync(Supplier<IslandLevelSettings> settings,
                           Function<BankId, CompletableFuture<Optional<BankInfo>>> banks,
                           Function<String, Optional<IslandBonus>> islands, java.util.function.Consumer<Runnable> mainThread,
                           Logger logger) {
        this.settings = settings;
        this.banks = banks;
        this.islands = islands;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /**
     * @param event operation enregistree (thread d'entrees/sorties)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOperation(BankOperationEvent event) {
        if (settings.get().enabled() && EntityRef.ISLAND.equals(event.owner().type())) {
            sync(event.bankId(), event.owner().id());
        }
    }

    /**
     * Resynchronise une ile avec la valeur courante de sa banque.
     *
     * @param bank     banque de l'ile
     * @param islandId identifiant de l'ile
     */
    public void sync(BankId bank, String islandId) {
        banks.apply(bank).whenComplete((info, failure) -> {
            if (failure != null) {
                logger.log(Level.WARNING, ConsoleText.tr("Niveau d'ile : lecture de la banque impossible"), failure);
            } else if (info.isPresent()) {
                mainThread.accept(() -> apply(islandId, info.get().value()));
            }
        });
    }

    private void apply(String islandId, BigDecimal bankValue) {
        IslandLevelSettings now = settings.get();
        if (!now.enabled()) {
            return;
        }
        try {
            synchronized (locks.computeIfAbsent(islandId, id -> new Object())) {
                islands.apply(islandId).ifPresent(island -> {
                    for (IslandLevelSettings.Target target : IslandLevelSettings.Target.values()) {
                        // L'autre bonus perd la part de NovaBank : changer apply-to ne laisse rien derriere.
                        BigDecimal wanted = target == now.target() ? bankValue.multiply(now.multiplier()) : BigDecimal.ZERO;
                        BigDecimal applied = island.applied(target);
                        if (wanted.compareTo(applied) != 0) {
                            island.update(target, island.bonus(target).subtract(applied).add(wanted), wanted);
                        }
                    }
                });
            }
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, ConsoleText.tr("Niveau d'ile : mise a jour de l'ile {0} impossible",
                    islandId), failure);
        }
    }
}
