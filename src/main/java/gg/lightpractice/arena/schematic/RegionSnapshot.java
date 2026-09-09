package gg.lightpractice.arena.schematic;

/**
 * Raw block data of an arena region, used by the internal schematic provider.
 *
 * <p>Blocks are stored as 1.8 numeric ids plus data values in a flat array indexed by
 * {@code (y * length + z) * width + x}, which keeps capture and paste allocation free and lets the
 * compressed file stay small because untouched arena regions are mostly air.</p>
 */
final class RegionSnapshot {

    private final int width;
    private final int height;
    private final int length;
    private final short[] ids;
    private final byte[] data;

    RegionSnapshot(int width, int height, int length) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.length = Math.max(1, length);
        int volume = this.width * this.height * this.length;
        this.ids = new short[volume];
        this.data = new byte[volume];
    }

    RegionSnapshot(int width, int height, int length, short[] ids, byte[] data) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.ids = ids;
        this.data = data;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int length() {
        return length;
    }

    int volume() {
        return ids.length;
    }

    short[] ids() {
        return ids;
    }

    byte[] data() {
        return data;
    }

    int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    void set(int x, int y, int z, short id, byte value) {
        int index = index(x, y, z);
        if (index < 0 || index >= ids.length) {
            return;
        }
        ids[index] = id;
        data[index] = value;
    }
}
