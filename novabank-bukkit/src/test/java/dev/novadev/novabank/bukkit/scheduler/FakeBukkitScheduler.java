package dev.novadev.novabank.bukkit.scheduler;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Planificateur Bukkit factice : enregistre les taches, les execute a la demande
 * (simulation de ticks). Seules les methodes utilisees par {@link BukkitTaskScheduler}
 * sont implementees.
 */
public final class FakeBukkitScheduler {

    /** Tache planifiee. */
    record Planned(Runnable task, long delay, long period, AtomicBoolean cancelled) {
    }

    private final List<Planned> planned = new ArrayList<>();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean refuse = new AtomicBoolean();

    public final BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {BukkitScheduler.class}, (proxy, method, args) -> switch (method.getName()) {
                case "runTask" -> {
                    if (!enabled.get() || refuse.get()) {
                        // Comme CraftScheduler : plus aucune tache acceptee une fois le plugin desactive.
                        throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
                    }
                    yield plan((Runnable) args[1], 0, -1);
                }
                case "runTaskLater" -> plan((Runnable) args[1], (Long) args[2], -1);
                case "runTaskTimer" -> plan((Runnable) args[1], (Long) args[2], (Long) args[3]);
                default -> throw new UnsupportedOperationException(method.getName());
            });

    public final Plugin plugin = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Plugin.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "isEnabled" -> enabled.get();
                case "getName" -> "NovaBank";
                default -> throw new UnsupportedOperationException(method.getName());
            });

    public void disablePlugin() {
        enabled.set(false);
    }

    /** Simule une desactivation survenue entre la verification et la planification. */
    void refuseTasksWhileEnabled() {
        refuse.set(true);
    }

    List<Planned> planned() {
        return planned;
    }

    /** Execute une fois chaque tache non annulee (un tick simule pour toutes). */
    public void runAll() {
        for (Planned task : List.copyOf(planned)) {
            if (!task.cancelled().get()) {
                task.task().run();
            }
        }
    }

    private BukkitTask plan(Runnable task, long delay, long period) {
        AtomicBoolean cancelled = new AtomicBoolean();
        planned.add(new Planned(task, delay, period, cancelled));
        return (BukkitTask) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {BukkitTask.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "cancel" -> {
                        cancelled.set(true);
                        yield null;
                    }
                    case "isCancelled" -> cancelled.get();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
