package gg.lightpractice.arena.schematic;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Tasks;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Dependency free schematic provider using LightPractice's own {@code .lpschem} format.
 *
 * <p>File IO happens on the async scheduler and block changes are applied in configurable batches on
 * the main thread, because Bukkit world access is not thread safe on 1.8. Batching keeps a large arena
 * reset from freezing the server, and the batch size is a configuration value rather than a hardcoded
 * number.</p>
 */
public final class InternalSchematicProvider implements SchematicProvider {

    private static final String MAGIC = "LPSCHM";
    private static final int VERSION = 1;

    private final Tasks tasks;
    private final ConfigFile config;
    private int blocksPerTick = 8000;
    private int maximumVolume = 4000000;

    public InternalSchematicProvider(Tasks tasks, ConfigFile config) {
        this.tasks = tasks;
        this.config = config;
        reload();
    }

    public void reload() {
        this.blocksPerTick = Math.max(500, config.getInt("schematics.blocks-per-tick", 8000));
        this.maximumVolume = Math.max(10000, config.getInt("schematics.maximum-volume", 4000000));
    }

    @Override
    public String name() {
        return "LightPractice internal";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<String> extensions() {
        return Arrays.asList("lpschem");
    }

    @Override
    public void paste(final File file, final Location origin, final Consumer<Boolean> callback) {
        if (file == null || origin == null || origin.getWorld() == null) {
            finish(callback, false);
            return;
        }
        tasks.async(new Runnable() {
            @Override
            public void run() {
                final RegionSnapshot snapshot = read(file);
                tasks.sync(new Runnable() {
                    @Override
                    public void run() {
                        if (snapshot == null) {
                            finish(callback, false);
                            return;
                        }
                        apply(snapshot, origin, callback);
                    }
                });
            }
        });
    }

    @Override
    public void save(final File file, final Location minimum, final Location maximum, final Consumer<Boolean> callback) {
        if (file == null || minimum == null || maximum == null || minimum.getWorld() == null
                || minimum.getWorld() != maximum.getWorld()) {
            finish(callback, false);
            return;
        }
        capture(minimum, maximum, new Consumer<RegionSnapshot>() {
            @Override
            public void accept(final RegionSnapshot snapshot) {
                if (snapshot == null) {
                    finish(callback, false);
                    return;
                }
                tasks.async(new Runnable() {
                    @Override
                    public void run() {
                        boolean written = write(file, snapshot);
                        finish(callback, written);
                    }
                });
            }
        });
    }

    private void apply(final RegionSnapshot snapshot, final Location origin, final Consumer<Boolean> callback) {
        final World world = origin.getWorld();
        final int baseX = origin.getBlockX();
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ();
        final int total = snapshot.volume();
        final int[] cursor = new int[]{0};
        final BukkitTask[] handle = new BukkitTask[1];
        handle[0] = tasks.timer(new Runnable() {
            @Override
            public void run() {
                try {
                    int end = Math.min(total, cursor[0] + blocksPerTick);
                    int width = snapshot.width();
                    int length = snapshot.length();
                    int plane = width * length;
                    for (int index = cursor[0]; index < end; index++) {
                        int y = index / plane;
                        int remainder = index - (y * plane);
                        int z = remainder / width;
                        int x = remainder - (z * width);
                        Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                        short id = snapshot.ids()[index];
                        byte data = snapshot.data()[index];
                        if (block.getTypeId() != id || block.getData() != data) {
                            block.setTypeIdAndData(id, data, false);
                        }
                    }
                    cursor[0] = end;
                    if (cursor[0] >= total) {
                        tasks.cancel(handle[0]);
                        Debug.log(DebugCategory.SCHEMATIC, "Internal paste finished ({} blocks)", total);
                        finish(callback, true);
                    }
                } catch (Throwable throwable) {
                    tasks.cancel(handle[0]);
                    Debug.error(DebugCategory.SCHEMATIC, "Internal paste failed", throwable);
                    finish(callback, false);
                }
            }
        }, 1L, 1L);
    }

    private void capture(final Location minimum, final Location maximum, final Consumer<RegionSnapshot> callback) {
        final World world = minimum.getWorld();
        final int width = Math.abs(maximum.getBlockX() - minimum.getBlockX()) + 1;
        final int height = Math.abs(maximum.getBlockY() - minimum.getBlockY()) + 1;
        final int length = Math.abs(maximum.getBlockZ() - minimum.getBlockZ()) + 1;
        final long volume = (long) width * height * length;
        if (volume > maximumVolume) {
            Debug.warn(DebugCategory.SCHEMATIC, "Region " + volume + " blocks exceeds the configured maximum of "
                    + maximumVolume + ", capture aborted");
            callback.accept(null);
            return;
        }
        final int baseX = Math.min(minimum.getBlockX(), maximum.getBlockX());
        final int baseY = Math.min(minimum.getBlockY(), maximum.getBlockY());
        final int baseZ = Math.min(minimum.getBlockZ(), maximum.getBlockZ());
        final RegionSnapshot snapshot = new RegionSnapshot(width, height, length);
        final int total = snapshot.volume();
        final int[] cursor = new int[]{0};
        final BukkitTask[] handle = new BukkitTask[1];
        handle[0] = tasks.timer(new Runnable() {
            @Override
            public void run() {
                try {
                    int end = Math.min(total, cursor[0] + blocksPerTick);
                    int plane = width * length;
                    for (int index = cursor[0]; index < end; index++) {
                        int y = index / plane;
                        int remainder = index - (y * plane);
                        int z = remainder / width;
                        int x = remainder - (z * width);
                        Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                        snapshot.ids()[index] = (short) block.getTypeId();
                        snapshot.data()[index] = block.getData();
                    }
                    cursor[0] = end;
                    if (cursor[0] >= total) {
                        tasks.cancel(handle[0]);
                        Debug.log(DebugCategory.SCHEMATIC, "Captured {} blocks", total);
                        callback.accept(snapshot);
                    }
                } catch (Throwable throwable) {
                    tasks.cancel(handle[0]);
                    Debug.error(DebugCategory.SCHEMATIC, "Region capture failed", throwable);
                    callback.accept(null);
                }
            }
        }, 1L, 1L);
    }

    private RegionSnapshot read(File file) {
        DataInputStream input = null;
        try {
            input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
            String magic = input.readUTF();
            if (!MAGIC.equals(magic)) {
                Debug.warn(DebugCategory.SCHEMATIC, file.getName() + " is not a LightPractice schematic");
                return null;
            }
            int version = input.readInt();
            if (version != VERSION) {
                Debug.warn(DebugCategory.SCHEMATIC,
                        file.getName() + " uses unsupported schematic version " + version);
                return null;
            }
            int width = input.readInt();
            int height = input.readInt();
            int length = input.readInt();
            if (width <= 0 || height <= 0 || length <= 0) {
                return null;
            }
            long volume = (long) width * height * length;
            if (volume > maximumVolume) {
                Debug.warn(DebugCategory.SCHEMATIC,
                        file.getName() + " holds " + volume + " blocks, above the configured maximum");
                return null;
            }
            int size = (int) volume;
            short[] ids = new short[size];
            byte[] data = new byte[size];
            for (int index = 0; index < size; index++) {
                ids[index] = input.readShort();
                data[index] = input.readByte();
            }
            return new RegionSnapshot(width, height, length, ids, data);
        } catch (IOException exception) {
            Debug.error(DebugCategory.SCHEMATIC, "Could not read " + file.getName(), exception);
            return null;
        } finally {
            close(input);
        }
    }

    private boolean write(File file, RegionSnapshot snapshot) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Debug.warn(DebugCategory.SCHEMATIC, "Could not create " + parent.getAbsolutePath());
        }
        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))));
            output.writeUTF(MAGIC);
            output.writeInt(VERSION);
            output.writeInt(snapshot.width());
            output.writeInt(snapshot.height());
            output.writeInt(snapshot.length());
            short[] ids = snapshot.ids();
            byte[] data = snapshot.data();
            for (int index = 0; index < ids.length; index++) {
                output.writeShort(ids[index]);
                output.writeByte(data[index]);
            }
            output.flush();
            Debug.log(DebugCategory.SCHEMATIC, "Wrote {} ({} blocks)", file.getName(), ids.length);
            return true;
        } catch (IOException exception) {
            Debug.error(DebugCategory.SCHEMATIC, "Could not write " + file.getName(), exception);
            return false;
        } finally {
            close(output);
        }
    }

    private void close(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // the operation result was already reported
        }
    }

    private void finish(Consumer<Boolean> callback, boolean success) {
        if (callback != null) {
            callback.accept(success);
        }
    }
}
