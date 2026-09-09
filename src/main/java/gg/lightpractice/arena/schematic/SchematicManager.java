package gg.lightpractice.arena.schematic;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaBounds;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Schematic orchestration: file resolution, provider selection and reset bookkeeping.
 *
 * <p>A reset is refused while the same arena is already being rebuilt, which is what stops two matches
 * from pasting into one region at the same time. The callback handed to a provider is guarded so it
 * fires exactly once even if a backend misbehaves.</p>
 */
public final class SchematicManager implements LightService {

    private static final List<String> SEARCH_EXTENSIONS = Arrays.asList("lpschem", "schem", "schematic");

    private final PluginCore core;
    private final WorldEditSchematicProvider worldEdit;
    private final InternalSchematicProvider internal;
    private final Set<String> resetting = new HashSet<String>();
    private final List<File> searchFolders = new ArrayList<File>();
    private File folder;

    public SchematicManager(PluginCore core) {
        this.core = core;
        this.worldEdit = new WorldEditSchematicProvider(core.plugin(), core.tasks());
        this.internal = new InternalSchematicProvider(core.tasks(), core.configs().config());
    }

    @Override
    public String name() {
        return "SchematicManager";
    }

    @Override
    public void onEnable() {
        refreshFolders();
        worldEdit.detect();
        core.plugin().getLogger().info("Schematic backend: " + providerName());
        if (!worldEdit.isAvailable()) {
            core.plugin().getLogger().info("FastAsyncWorldEdit/WorldEdit was not detected, arena resets use the "
                    + "internal .lpschem provider. Install FastAsyncWorldEdit for faster, fully asynchronous resets.");
        }
    }

    @Override
    public void onReload() {
        internal.reload();
        worldEdit.detect();
        refreshFolders();
    }

    private void refreshFolders() {
        searchFolders.clear();
        folder = core.schematicFolder();
        searchFolders.add(folder);
        Plugin plugin = core.plugin();
        String configured = core.configs().config().getString("schematics.worldedit-folder", "schematics");
        for (String name : new String[]{"FastAsyncWorldEdit", "WorldEdit", "AsyncWorldEdit"}) {
            File candidate = new File(new File(plugin.getDataFolder().getParentFile(), name), configured);
            if (candidate.isDirectory() && !searchFolders.contains(candidate)) {
                searchFolders.add(candidate);
            }
        }
        Debug.log(DebugCategory.SCHEMATIC, "Schematic folders: {}", searchFolders);
    }

    public File folder() {
        return folder == null ? core.schematicFolder() : folder;
    }

    /** Backend in use, WorldEdit/FAWE first because it is faster and fully asynchronous. */
    public SchematicProvider provider() {
        return worldEdit.isAvailable() ? worldEdit : internal;
    }

    public String providerName() {
        return worldEdit.isAvailable() ? worldEdit.name() : internal.name();
    }

    public boolean isWorldEditAvailable() {
        return worldEdit.isAvailable();
    }

    /** Finds a schematic file by name, tolerating missing extensions and both providers' folders. */
    public File resolve(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String trimmed = name.trim();
        for (File search : searchFolders) {
            File direct = new File(search, trimmed);
            if (direct.isFile()) {
                return direct;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            for (String extension : SEARCH_EXTENSIONS) {
                if (lower.endsWith("." + extension)) {
                    continue;
                }
                File candidate = new File(search, trimmed + "." + extension);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** File a captured arena should be written to, keeping the extension of the configured name. */
    public File targetFile(String name) {
        File existing = resolve(name);
        if (existing != null) {
            return existing;
        }
        String trimmed = name == null || name.trim().isEmpty() ? "arena" : name.trim();
        if (!trimmed.contains(".")) {
            trimmed = trimmed + (worldEdit.isAvailable() ? ".schematic" : ".lpschem");
        }
        return new File(folder(), trimmed);
    }

    public List<String> list() {
        List<String> names = new ArrayList<String>();
        for (File search : searchFolders) {
            File[] files = search.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                String lower = file.getName().toLowerCase(Locale.ROOT);
                for (String extension : SEARCH_EXTENSIONS) {
                    if (lower.endsWith("." + extension)) {
                        if (!names.contains(file.getName())) {
                            names.add(file.getName());
                        }
                        break;
                    }
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    public boolean isResetting(Arena arena) {
        return arena != null && resetting.contains(arena.name());
    }

    public Set<String> resettingArenas() {
        return Collections.unmodifiableSet(new HashSet<String>(resetting));
    }

    /**
     * Resets an arena from its configured schematic. The callback receives {@code false} when the arena
     * has no schematic, no bounds, is already resetting or the backend failed.
     */
    public void paste(final Arena arena, final Consumer<Boolean> callback) {
        if (arena == null) {
            finish(callback, false);
            return;
        }
        if (resetting.contains(arena.name())) {
            Debug.log(DebugCategory.ARENA, "Arena {} is already being reset, request ignored", arena.name());
            finish(callback, false);
            return;
        }
        if (!arena.hasSchematic()) {
            Debug.warn(DebugCategory.ARENA, "Arena " + arena.name() + " has no schematic configured");
            finish(callback, false);
            return;
        }
        File file = resolve(arena.schematic());
        if (file == null) {
            Debug.warn(DebugCategory.ARENA, "Schematic '" + arena.schematic() + "' of arena " + arena.name()
                    + " does not exist in " + searchFolders);
            finish(callback, false);
            return;
        }
        World world = arena.world();
        ArenaBounds bounds = arena.bounds();
        if (world == null || bounds == null) {
            Debug.warn(DebugCategory.ARENA, "Arena " + arena.name() + " needs a loaded world and bounds to reset");
            finish(callback, false);
            return;
        }
        Location origin = bounds.minimum(world);
        resetting.add(arena.name());
        arena.markResetting(true);
        Debug.log(DebugCategory.ARENA, "Resetting arena {} with {} using {}", arena.name(), file.getName(),
                providerName());
        final AtomicBoolean handled = new AtomicBoolean(false);
        provider().paste(file, origin, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (!handled.compareAndSet(false, true)) {
                    return;
                }
                resetting.remove(arena.name());
                arena.markResetting(false);
                boolean ok = Boolean.TRUE.equals(success);
                if (!ok) {
                    Debug.warn(DebugCategory.ARENA, "Reset of arena " + arena.name() + " failed, see debug output");
                }
                finish(callback, ok);
            }
        });
    }

    /** Pastes an arbitrary file, used by admin tooling and event arenas. */
    public void paste(File file, Location origin, Consumer<Boolean> callback) {
        if (file == null || origin == null || origin.getWorld() == null) {
            finish(callback, false);
            return;
        }
        provider().paste(file, origin, callback);
    }

    /** Captures the current arena region into its configured schematic file. */
    public void capture(final Arena arena, final Consumer<Boolean> callback) {
        if (arena == null) {
            finish(callback, false);
            return;
        }
        World world = arena.world();
        ArenaBounds bounds = arena.bounds();
        if (world == null || bounds == null) {
            Debug.warn(DebugCategory.ARENA, "Arena " + arena.name() + " needs bounds before it can be captured");
            finish(callback, false);
            return;
        }
        String schematic = arena.schematic();
        if (schematic == null) {
            schematic = arena.name().toLowerCase(Locale.ROOT).replace(' ', '_');
            arena.schematic(schematic);
        }
        File file = targetFile(schematic);
        capture(file, bounds.minimum(world), bounds.maximum(world), new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (Boolean.TRUE.equals(success)) {
                    Debug.log(DebugCategory.ARENA, "Arena {} captured to its schematic", arena.name());
                }
                finish(callback, Boolean.TRUE.equals(success));
            }
        });
    }

    public void capture(File file, Location minimum, Location maximum, Consumer<Boolean> callback) {
        if (file == null || minimum == null || maximum == null) {
            finish(callback, false);
            return;
        }
        provider().save(file, minimum, maximum, callback);
    }

    public boolean hasSchematic(Arena arena) {
        return arena != null && arena.hasSchematic() && resolve(arena.schematic()) != null;
    }

    private void finish(Consumer<Boolean> callback, boolean success) {
        if (callback != null) {
            callback.accept(success);
        }
    }
}
