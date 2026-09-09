package gg.lightpractice.arena.schematic;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Reflect;
import gg.lightpractice.util.Tasks;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * FastAsyncWorldEdit / WorldEdit bridge.
 *
 * <p>On Minecraft 1.8.9 the ecosystem ships either WorldEdit 6 or the FAWE fork of it, and neither jar
 * is reliably resolvable at build time, so the bridge resolves the WorldEdit 6 API
 * ({@code SchematicFormat}, {@code CuboidClipboard}, {@code EditSession}) reflectively. Every step is
 * verified: when a call cannot be made the provider reports itself unavailable and
 * {@code SchematicManager} falls back to the internal provider instead of leaving an arena unreset.</p>
 *
 * <p>Pasting runs on the async scheduler only when FastAsyncWorldEdit is installed, because that is the
 * build whose edit sessions are thread safe. With plain WorldEdit the operation stays on the main
 * thread, which is what that library expects.</p>
 */
public final class WorldEditSchematicProvider implements SchematicProvider {

    private final Plugin plugin;
    private final Tasks tasks;
    private volatile boolean available;
    private volatile boolean asyncSafe;
    private volatile String backend = "none";

    public WorldEditSchematicProvider(Plugin plugin, Tasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        detect();
    }

    /** Re-checks the environment; called on enable and on reload when WorldEdit loads later. */
    public void detect() {
        PluginManager manager = plugin.getServer().getPluginManager();
        Plugin fawe = manager.getPlugin("FastAsyncWorldEdit");
        Plugin worldEdit = manager.getPlugin("WorldEdit");
        boolean installed = (fawe != null && fawe.isEnabled()) || (worldEdit != null && worldEdit.isEnabled());
        boolean apiReady = Reflect.findClass("com.sk89q.worldedit.WorldEdit") != null
                && Reflect.findClass("com.sk89q.worldedit.schematic.SchematicFormat") != null
                && Reflect.findClass("com.sk89q.worldedit.CuboidClipboard") != null
                && Reflect.findClass("com.sk89q.worldedit.bukkit.BukkitUtil") != null
                && Reflect.findClass("com.sk89q.worldedit.Vector") != null;
        this.asyncSafe = fawe != null && fawe.isEnabled();
        this.available = installed && apiReady;
        if (!installed) {
            this.backend = "not installed";
        } else if (!apiReady) {
            this.backend = "installed but exposes an unsupported API";
        } else if (asyncSafe) {
            this.backend = "FastAsyncWorldEdit";
        } else {
            this.backend = "WorldEdit";
        }
        Debug.log(DebugCategory.SCHEMATIC, "WorldEdit bridge available={} backend={}", available, backend);
    }

    @Override
    public String name() {
        return backend;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public boolean isAsyncSafe() {
        return asyncSafe;
    }

    @Override
    public List<String> extensions() {
        return Arrays.asList("schem", "schematic");
    }

    @Override
    public void paste(final File file, final Location origin, final Consumer<Boolean> callback) {
        if (!available || file == null || origin == null || origin.getWorld() == null) {
            finish(callback, false);
            return;
        }
        if (asyncSafe) {
            tasks.async(new Runnable() {
                @Override
                public void run() {
                    final boolean success = pasteBlocking(file, origin);
                    tasks.sync(new Runnable() {
                        @Override
                        public void run() {
                            finish(callback, success);
                        }
                    });
                }
            });
            return;
        }
        finish(callback, pasteBlocking(file, origin));
    }

    @Override
    public void save(final File file, final Location minimum, final Location maximum, final Consumer<Boolean> callback) {
        if (!available || file == null || minimum == null || maximum == null
                || minimum.getWorld() == null || minimum.getWorld() != maximum.getWorld()) {
            finish(callback, false);
            return;
        }
        Runnable work = new Runnable() {
            @Override
            public void run() {
                final boolean success = saveBlocking(file, minimum, maximum);
                tasks.sync(new Runnable() {
                    @Override
                    public void run() {
                        finish(callback, success);
                    }
                });
            }
        };
        if (asyncSafe) {
            tasks.async(work);
        } else {
            work.run();
        }
    }

    private boolean pasteBlocking(File file, Location origin) {
        try {
            Object format = formatFor(file);
            if (format == null) {
                Debug.warn(DebugCategory.SCHEMATIC, "WorldEdit found no format for " + file.getName());
                return false;
            }
            Object clipboard = Reflect.invoke(format, "load", file);
            if (clipboard == null) {
                return false;
            }
            Object session = editSession(origin.getWorld());
            if (session == null) {
                return false;
            }
            try {
                Object target = vector(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
                Reflect.invoke(clipboard, "paste", session, target, Boolean.FALSE);
                flush(session);
                Debug.log(DebugCategory.SCHEMATIC, "WorldEdit pasted {} at {}", file.getName(), origin);
                return true;
            } finally {
                close(session);
            }
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.SCHEMATIC, "WorldEdit paste of " + file.getName() + " failed", throwable);
            return false;
        }
    }

    private boolean saveBlocking(File file, Location minimum, Location maximum) {
        try {
            int width = Math.abs(maximum.getBlockX() - minimum.getBlockX()) + 1;
            int height = Math.abs(maximum.getBlockY() - minimum.getBlockY()) + 1;
            int length = Math.abs(maximum.getBlockZ() - minimum.getBlockZ()) + 1;
            Object origin = vector(Math.min(minimum.getBlockX(), maximum.getBlockX()),
                    Math.min(minimum.getBlockY(), maximum.getBlockY()),
                    Math.min(minimum.getBlockZ(), maximum.getBlockZ()));
            Object size = vector(width, height, length);
            Class<?> clipboardClass = Reflect.findClass("com.sk89q.worldedit.CuboidClipboard");
            Object clipboard = Reflect.newInstance(clipboardClass, origin, size);
            if (clipboard == null) {
                return false;
            }
            Object session = editSession(minimum.getWorld());
            if (session == null) {
                return false;
            }
            try {
                Reflect.invoke(clipboard, "copy", session);
                Object format = formatFor(file);
                if (format == null) {
                    format = Reflect.staticField(Reflect.findClass("com.sk89q.worldedit.schematic.SchematicFormat"),
                            "MCEDIT");
                }
                if (format == null) {
                    return false;
                }
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    Debug.warn(DebugCategory.SCHEMATIC, "Could not create " + parent.getAbsolutePath());
                }
                Reflect.invoke(format, "save", clipboard, file);
                Debug.log(DebugCategory.SCHEMATIC, "WorldEdit saved {} ({} blocks)", file.getName(),
                        width * height * length);
                return true;
            } finally {
                close(session);
            }
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.SCHEMATIC, "WorldEdit capture failed", throwable);
            return false;
        }
    }

    private Object formatFor(File file) {
        Class<?> formatClass = Reflect.findClass("com.sk89q.worldedit.schematic.SchematicFormat");
        if (formatClass == null) {
            return null;
        }
        Object format = Reflect.callStatic(formatClass, "getFormat", file);
        if (format != null) {
            return format;
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".schematic")) {
            return Reflect.staticField(formatClass, "MCEDIT");
        }
        return Reflect.staticField(formatClass, "SPONGE");
    }

    private Object editSession(World world) {
        Class<?> worldEditClass = Reflect.findClass("com.sk89q.worldedit.WorldEdit");
        Object instance = Reflect.callStatic(worldEditClass, "getInstance");
        if (instance == null) {
            return null;
        }
        Object factory = Reflect.call(instance, "getEditSessionFactory");
        if (factory == null) {
            return null;
        }
        Class<?> bukkitUtil = Reflect.findClass("com.sk89q.worldedit.bukkit.BukkitUtil");
        Object localWorld = Reflect.callStatic(bukkitUtil, "getLocalWorld", world);
        if (localWorld == null) {
            return null;
        }
        return Reflect.call(factory, "getEditSession", localWorld, Integer.valueOf(-1));
    }

    private Object vector(double x, double y, double z) {
        Class<?> vectorClass = Reflect.findClass("com.sk89q.worldedit.Vector");
        return Reflect.newInstance(vectorClass, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
    }

    private void flush(Object session) {
        if (Reflect.methodByName(session.getClass(), "flushQueue", 0) != null) {
            Reflect.call(session, "flushQueue");
            return;
        }
        if (Reflect.methodByName(session.getClass(), "commit", 0) != null) {
            Reflect.call(session, "commit");
        }
    }

    private void close(Object session) {
        if (Reflect.methodByName(session.getClass(), "close", 0) != null) {
            Reflect.call(session, "close");
        }
    }

    private void finish(Consumer<Boolean> callback, boolean success) {
        if (callback != null) {
            callback.accept(success);
        }
    }
}
