package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.config.model.IslandLevelSettings;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Ajoute la valeur de la banque d'une ile au niveau calcule par l'addon Level de BentoBox. L'addon recalcule le niveau
 * depuis les blocs a chaque {@code /is level} et l'annonce par {@code IslandLevelCalculatedEvent} : le bonus y est
 * ajoute ({@code setLevel}), sans rien retenir dans l'ile. La valeur de la banque est lue en arriere-plan ; la derniere
 * valeur connue sert au calcul suivant, et si elle a change, le niveau enregistre est corrige
 * ({@code LevelsManager.setIslandLevel}). Chaque operation sur une banque d'ile met aussi cette valeur a jour : seul le
 * premier {@code /is level} d'une ile sans operation depuis le demarrage peut s'afficher sans le bonus (le niveau
 * enregistre et le classement sont corriges aussitot).
 *
 * <p>Signatures VERIFIEES (javap) avec BentoBox 3.23.4 et Level 2.29.0 : {@code BentoBox.getInstance()},
 * {@code getAddonsManager().getAddonByName(String)} ({@code Optional}), {@code Level.getManager()},
 * {@code LevelsManager.setIslandLevel(World, UUID, long)}, {@code IslandLevelCalculatedEvent.getIsland()},
 * {@code getLevel()}, {@code setLevel(long)}, {@code Island.getUniqueId()}, {@code getWorld()}, {@code getOwner()}.
 */
public final class BentoBoxLevels implements Listener, AutoCloseable {

    private static final String BENTOBOX = "world.bentobox.bentobox.BentoBox";
    private static final String EVENT = "world.bentobox.level.events.IslandLevelCalculatedEvent";

    private final Supplier<IslandLevelSettings> settings;
    private final Function<String, CompletableFuture<Optional<BigDecimal>>> bankValue;
    private final Consumer<Runnable> mainThread;
    private final Logger logger;
    private final Map<String, BigDecimal> known = new ConcurrentHashMap<>();

    /**
     * @param settings   reglage en vigueur ({@code integrations.bentobox}, relu a chaque calcul)
     * @param bankValue  valeur de la banque d'une ile, par identifiant BentoBox de l'ile
     * @param mainThread execute une tache sur le thread qui peut modifier BentoBox
     * @param logger     journal
     */
    public BentoBoxLevels(Supplier<IslandLevelSettings> settings,
                          Function<String, CompletableFuture<Optional<BigDecimal>>> bankValue,
                          Consumer<Runnable> mainThread, Logger logger) {
        this.settings = settings;
        this.bankValue = bankValue;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    /**
     * Branche l'ecoute sur l'addon Level. A appeler une fois le serveur demarre : BentoBox active ses addons pendant son
     * propre demarrage.
     *
     * @param bentobox plugin BentoBox
     * @param plugins  gestionnaire de plugins
     * @param owner    plugin proprietaire de l'ecoute
     * @return vrai si l'addon Level est la et reconnu
     */
    public boolean hook(Plugin bentobox, PluginManager plugins, Plugin owner) {
        Optional<Object> level = Reflect.type(bentobox.getClass().getClassLoader(), BENTOBOX)
                .flatMap(type -> Reflect.call(null, type, "getInstance"))
                .flatMap(instance -> Reflect.call(instance, "getAddonsManager"))
                .flatMap(addons -> Reflect.call(addons, "getAddonByName", "Level"))
                .flatMap(found -> found instanceof Optional<?> addon ? addon.map(Object.class::cast) : Optional.empty());
        Optional<Object> manager = level.flatMap(addon -> Reflect.call(addon, "getManager"));
        Optional<Class<?>> eventType = level.flatMap(addon -> Reflect.type(addon.getClass().getClassLoader(), EVENT))
                .filter(Event.class::isAssignableFrom);
        if (manager.isEmpty() || eventType.isEmpty()) {
            return false;
        }
        Class<?> type = eventType.get();
        plugins.registerEvents(this, owner);
        plugins.registerEvent(type.asSubclass(Event.class), this, EventPriority.HIGH, (listener, event) -> {
            if (type.isInstance(event)) {
                onCalculated(event, manager.get());
            }
        }, owner);
        return true;
    }

    /**
     * Garde la valeur courante de la banque d'une ile, pour le prochain calcul du niveau.
     *
     * @param event operation enregistree (thread d'entrees/sorties)
     */
    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR)
    public void onOperation(dev.novadev.novabank.api.event.BankOperationEvent event) {
        if (settings.get().enabled() && dev.novadev.novabank.api.entity.EntityRef.ISLAND.equals(event.owner().type())) {
            String islandId = event.owner().id();
            bankValue.apply(islandId).thenAccept(value -> known.put(islandId, value.orElse(BigDecimal.ZERO)));
        }
    }

    private void onCalculated(Object event, Object manager) {
        IslandLevelSettings now = settings.get();
        if (!now.enabled()) {
            return;
        }
        Optional<Object> island = Reflect.call(event, "getIsland");
        Optional<String> id = island.flatMap(found -> Reflect.call(found, "getUniqueId")).map(Object::toString);
        Optional<Long> base = Reflect.call(event, "getLevel").filter(Long.class::isInstance).map(Long.class::cast);
        if (id.isEmpty() || base.isEmpty()) {
            return;
        }
        BigDecimal previous = known.get(id.get());
        if (previous != null) {
            Reflect.call(event, "setLevel", base.get() + bonus(previous, now.multiplier()));
        }
        bankValue.apply(id.get()).whenComplete((value, failure) -> {
            if (failure != null) {
                logger.log(Level.WARNING,
                        ConsoleText.tr("Niveau d'ile BentoBox : lecture de la banque impossible"), failure);
                return;
            }
            BigDecimal read = value.orElse(BigDecimal.ZERO);
            known.put(id.get(), read);
            if (previous == null || read.compareTo(previous) != 0) {
                long corrected = base.get() + bonus(read, now.multiplier());
                mainThread.accept(() -> correct(manager, island.get(), corrected));
            }
        });
    }

    private void correct(Object manager, Object island, long level) {
        Optional<Object> world = Reflect.call(island, "getWorld");
        Optional<Object> owner = Reflect.call(island, "getOwner");
        if (world.isPresent() && owner.isPresent()) {
            Reflect.call(manager, "setIslandLevel", world.get(), owner.get(), level);
        }
    }

    /**
     * @param bankValue  valeur de la banque
     * @param multiplier coefficient
     * @return bonus de niveau : valeur x coefficient, arrondi a l'entier inferieur
     */
    static long bonus(BigDecimal bankValue, BigDecimal multiplier) {
        return bankValue.multiply(multiplier).setScale(0, RoundingMode.FLOOR).longValue();
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }
}
