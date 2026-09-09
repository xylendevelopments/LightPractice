package gg.lightpractice.arena.schematic;

import org.bukkit.Location;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Backend used to paste and capture arena regions.
 *
 * <p>Two implementations ship with the plugin: a FastAsyncWorldEdit/WorldEdit bridge that is used when
 * either plugin is installed (1.8.9 servers usually run the FAWE fork, whose API matches WorldEdit 6)
 * and a self contained provider that reads and writes LightPractice's own region format. The fallback
 * means arena resets keep working on a server where WorldEdit is missing, outdated or failing.</p>
 */
public interface SchematicProvider {

    /** Human readable backend name, shown by {@code /lightpractice info}. */
    String name();

    boolean isAvailable();

    /** File extensions this provider can read, without the leading dot. */
    List<String> extensions();

    /**
     * Pastes a schematic so its minimum corner lands on {@code origin}. The callback is always invoked
     * on the main thread with {@code true} on success.
     */
    void paste(File file, Location origin, Consumer<Boolean> callback);

    /** Captures a region into a new schematic file, callback on the main thread. */
    void save(File file, Location minimum, Location maximum, Consumer<Boolean> callback);
}
