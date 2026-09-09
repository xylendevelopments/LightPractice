package gg.lightpractice.util;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny reusable cooldown tracker used for duel requests, pearl usage, daily rewards and command
 * spam protection. Backed by a concurrent map so it is safe to touch from async callbacks.
 */
public final class Cooldowns<K> {

    private final Map<K, Long> stamps = new ConcurrentHashMap<K, Long>();

    public boolean ready(K key) {
        Long stamp = stamps.get(key);
        return stamp == null || stamp <= System.currentTimeMillis();
    }

    /** Milliseconds left on the cooldown, {@code 0} when it is ready. */
    public long remaining(K key) {
        Long stamp = stamps.get(key);
        if (stamp == null) {
            return 0L;
        }
        long left = stamp - System.currentTimeMillis();
        return Math.max(0L, left);
    }

    public void mark(K key, long durationMillis) {
        if (durationMillis <= 0L) {
            stamps.remove(key);
            return;
        }
        stamps.put(key, System.currentTimeMillis() + durationMillis);
    }

    /** Marks the cooldown when it is ready and reports whether the action may happen. */
    public boolean tryUse(K key, long durationMillis) {
        if (!ready(key)) {
            return false;
        }
        mark(key, durationMillis);
        return true;
    }

    public void remove(K key) {
        stamps.remove(key);
    }

    public void clear() {
        stamps.clear();
    }

    public int size() {
        return stamps.size();
    }

    /** Drops expired entries; called from the central housekeeping task instead of per player tasks. */
    public void purge() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<K, Long>> iterator = stamps.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }
}
