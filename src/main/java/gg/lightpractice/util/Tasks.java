package gg.lightpractice.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Single entry point for scheduling. Every task created through this class is tracked so that
 * {@link #cancelAll()} can guarantee nothing keeps running after the plugin disables, which is the
 * main leak source in practice cores that schedule per player tasks.
 */
public final class Tasks {

    private final Plugin plugin;
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();

    public Tasks(Plugin plugin) {
        this.plugin = plugin;
    }

    public Plugin plugin() {
        return plugin;
    }

    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    public BukkitTask sync(Runnable runnable) {
        return register(Bukkit.getScheduler().runTask(plugin, guard(runnable)));
    }

    public BukkitTask syncLater(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0L) {
            return sync(runnable);
        }
        return register(Bukkit.getScheduler().runTaskLater(plugin, guard(runnable), delayTicks));
    }

    public BukkitTask timer(Runnable runnable, long periodTicks) {
        return timer(runnable, periodTicks, periodTicks);
    }

    public BukkitTask timer(Runnable runnable, long delayTicks, long periodTicks) {
        return register(Bukkit.getScheduler().runTaskTimer(plugin, guard(runnable), delayTicks, periodTicks));
    }

    public BukkitTask async(Runnable runnable) {
        return register(Bukkit.getScheduler().runTaskAsynchronously(plugin, guard(runnable)));
    }

    public BukkitTask asyncLater(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0L) {
            return async(runnable);
        }
        return register(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, guard(runnable), delayTicks));
    }

    public BukkitTask asyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return register(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, guard(runnable), delayTicks, periodTicks));
    }

    /** Runs immediately when already on the main thread, otherwise hands the work to the scheduler. */
    public void syncOrRun(Runnable runnable) {
        if (isMainThread()) {
            runnable.run();
        } else {
            sync(runnable);
        }
    }

    public void cancel(BukkitTask task) {
        if (task == null) {
            return;
        }
        try {
            task.cancel();
        } catch (IllegalStateException ignored) {
            // scheduler already shut down
        }
        tasks.remove(task);
    }

    public void cancelAll() {
        for (BukkitTask task : tasks) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
                // scheduler already shut down
            }
        }
        tasks.clear();
    }

    public int activeTaskCount() {
        return tasks.size();
    }

    private BukkitTask register(BukkitTask task) {
        if (task == null) {
            return null;
        }
        tasks.add(task);
        return task;
    }

    private Runnable guard(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.SEVERE, "Unhandled exception inside a LightPractice task", throwable);
            }
        };
    }
}
