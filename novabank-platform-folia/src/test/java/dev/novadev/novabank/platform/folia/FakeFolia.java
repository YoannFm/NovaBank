package dev.novadev.novabank.platform.folia;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/**
 * Serveur Folia factice, fidele au code de Folia 1.21.11 (FoliaGlobalRegionScheduler,
 * FoliaEntityScheduler, lus le 2026-10-27) sur les points utilises par NovaBank :
 * planification refusee (IllegalPluginAccessException) plugin desactive, delai nul refuse,
 * {@code null} rendu par le planificateur d'une entite deja retiree, rappel "retired" a la
 * suppression de l'entite, taches d'un plugin desactive ignorees sans rappel, planificateur
 * Bukkit inexistant (UnsupportedOperationException). Les ticks sont simules a la demande.
 */
final class FakeFolia {

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final Set<UUID> retired = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ownedHere = ConcurrentHashMap.newKeySet();
    private final List<FakeTask> global = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<FakeTask>> entities = new ConcurrentHashMap<>();

    final Plugin plugin = proxy(Plugin.class, (method, args) -> switch (method) {
        case "isEnabled" -> enabled.get();
        case "getName" -> "NovaBank";
        case "getServer" -> server();
        default -> unsupported(method);
    });

    private final GlobalRegionScheduler globalScheduler = proxy(GlobalRegionScheduler.class, (method, args) -> {
        checkEnabled();
        return switch (method) {
            case "execute" -> {
                Runnable task = (Runnable) args[1];
                global.add(new FakeTask(1, -1, scheduled -> task.run(), null));
                yield null;
            }
            case "run" -> plan(global, args[1], 1, -1, null);
            case "runDelayed" -> plan(global, args[1], positive((Long) args[2]), -1, null);
            case "runAtFixedRate" -> plan(global, args[1], positive((Long) args[2]), positive((Long) args[3]), null);
            default -> unsupported(method);
        };
    });

    private final Server server = proxy(Server.class, (method, args) -> switch (method) {
        case "getPlayer" -> args[0] instanceof UUID id && online.contains(id) ? player(id) : null;
        case "isOwnedByCurrentRegion" -> ownedHere.contains(((Entity) args[0]).getUniqueId());
        case "getGlobalRegionScheduler" -> globalScheduler;
        case "getScheduler" -> throw new UnsupportedOperationException("Folia : planificateur Bukkit inexistant");
        default -> unsupported(method);
    });

    Server server() {
        return server;
    }

    void join(UUID playerId) {
        online.add(playerId);
        retired.remove(playerId);
    }

    /** Le joueur est manipulable depuis le thread du test (sa region, ou thread d'arret). */
    void ownHere(UUID playerId) {
        ownedHere.add(playerId);
    }

    /** Joueur encore rendu par getPlayer mais entite deja retiree : son planificateur renvoie null. */
    void retireSilently(UUID playerId) {
        retired.add(playerId);
    }

    /** Deconnexion : entite retiree, rappels "retired" des taches en attente (plugin actif seulement). */
    void quit(UUID playerId) {
        online.remove(playerId);
        retired.add(playerId);
        for (FakeTask task : entities.getOrDefault(playerId, List.of())) {
            task.retire(enabled.get());
        }
    }

    void disablePlugin() {
        enabled.set(false);
    }

    List<FakeTask> global() {
        return global;
    }

    List<FakeTask> entity(UUID playerId) {
        return entities.getOrDefault(playerId, List.of());
    }

    /** Un tick de la region globale. */
    void tickGlobal() {
        tick(global);
    }

    /** Un tick de la region du joueur. */
    void tickPlayer(UUID playerId) {
        tick(entity(playerId));
    }

    private void tick(List<FakeTask> tasks) {
        for (FakeTask task : List.copyOf(tasks)) {
            task.tick(enabled.get());
        }
    }

    private Player player(UUID playerId) {
        EntityScheduler scheduler = proxy(EntityScheduler.class, (method, args) -> {
            checkEnabled();
            if (!"run".equals(method)) {
                return unsupported(method);
            }
            if (retired.contains(playerId)) {
                return null;
            }
            return plan(entities.computeIfAbsent(playerId, id -> new CopyOnWriteArrayList<>()), args[1], 1, -1,
                    (Runnable) args[2]);
        });
        return proxy(Player.class, (method, args) -> switch (method) {
            case "getUniqueId" -> playerId;
            case "getScheduler" -> scheduler;
            default -> unsupported(method);
        });
    }

    @SuppressWarnings("unchecked")
    private FakeTask plan(List<FakeTask> tasks, Object consumer, long delay, long period, Runnable retiredCallback) {
        FakeTask task = new FakeTask(delay, period, (Consumer<ScheduledTask>) consumer, retiredCallback);
        tasks.add(task);
        return task;
    }

    private void checkEnabled() {
        if (!enabled.get()) {
            throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
        }
    }

    private static long positive(long ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("Delay ticks may not be <= 0");
        }
        return ticks;
    }

    private static Object unsupported(String method) {
        throw new UnsupportedOperationException(method);
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(String method, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(FakeFolia.class.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> type.getSimpleName();
                        };
                    }
                    return handler.handle(method.getName(), args);
                }));
    }

    /** Tache planifiee, executee par les ticks simules. */
    final class FakeTask implements ScheduledTask {

        final long delay;
        final long period;
        private final Consumer<ScheduledTask> run;
        private final Runnable retired;
        private ExecutionState state = ExecutionState.IDLE;

        FakeTask(long delay, long period, Consumer<ScheduledTask> run, Runnable retired) {
            this.delay = delay;
            this.period = period;
            this.run = run;
            this.retired = retired;
        }

        void tick(boolean pluginEnabled) {
            if (state != ExecutionState.IDLE) {
                return;
            }
            if (!pluginEnabled) {
                // Comme Folia : tache d'un plugin desactive ignoree, sans rappel.
                state = ExecutionState.CANCELLED;
                return;
            }
            run.accept(this);
            if (period <= 0 && state == ExecutionState.IDLE) {
                state = ExecutionState.FINISHED;
            }
        }

        void retire(boolean pluginEnabled) {
            if (state != ExecutionState.IDLE) {
                return;
            }
            state = ExecutionState.CANCELLED;
            if (pluginEnabled && retired != null) {
                retired.run();
            }
        }

        @Override
        public Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return period > 0;
        }

        @Override
        public CancelledState cancel() {
            state = ExecutionState.CANCELLED;
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public ExecutionState getExecutionState() {
            return state;
        }
    }
}
