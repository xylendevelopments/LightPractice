package gg.lightpractice.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Player latency lookup for Minecraft 1.8.9.
 *
 * <p>{@code Player#getPing()} does not exist in the 1.8 API, so the value is read from the server
 * implementation once and cached. When the field cannot be resolved (for example on a fork that
 * renamed it) the method returns {@code -1} and every ping aware feature - most importantly ranked
 * matchmaking - simply skips its ping filter instead of breaking.</p>
 */
public final class Ping {

    private static final String[] CANDIDATE_FIELDS = {"ping", "latency", "playerPing"};
    private static Method getHandle;
    private static String handleClassName;
    private static boolean resolved;
    private static String resolvedField;

    private Ping() {
    }

    public static int of(Player player) {
        if (player == null || !player.isOnline()) {
            return -1;
        }
        try {
            Object handle = handle(player);
            if (handle == null) {
                return -1;
            }
            String field = fieldName(handle.getClass());
            if (field == null) {
                return -1;
            }
            Object value = Reflect.field(handle, field);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Throwable throwable) {
            return -1;
        }
    }

    private static synchronized Object handle(Player player) {
        if (getHandle == null) {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String version = packageName.substring(packageName.lastIndexOf('.') + 1);
            Class<?> craftPlayer = Reflect.findClass("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            if (craftPlayer == null) {
                craftPlayer = Reflect.findClass("org.bukkit.craftbukkit.entity.CraftPlayer");
            }
            if (craftPlayer == null) {
                return null;
            }
            getHandle = Reflect.method(craftPlayer, "getHandle");
            if (getHandle == null) {
                getHandle = Reflect.methodByName(craftPlayer, "getHandle", 0);
            }
        }
        if (getHandle == null) {
            return null;
        }
        try {
            return getHandle.invoke(player);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static synchronized String fieldName(Class<?> handleClass) {
        if (resolved && handleClassName != null && handleClassName.equals(handleClass.getName())) {
            return resolvedField;
        }
        resolvedField = null;
        for (String candidate : CANDIDATE_FIELDS) {
            if (Reflect.field(probe(handleClass), candidate) != null || hasField(handleClass, candidate)) {
                resolvedField = candidate;
                break;
            }
        }
        handleClassName = handleClass.getName();
        resolved = true;
        if (resolvedField == null) {
            Debug.log(DebugCategory.INTEGRATION,
                    "No ping field found on {}, ping filters are disabled", handleClass.getName());
        }
        return resolvedField;
    }

    private static Object probe(Class<?> handleClass) {
        return null;
    }

    private static boolean hasField(Class<?> type, String name) {
        Class<?> walker = type;
        while (walker != null && walker != Object.class) {
            try {
                walker.getDeclaredField(name);
                return true;
            } catch (NoSuchFieldException ignored) {
                walker = walker.getSuperclass();
            }
        }
        return false;
    }

    /** True when latency data is available on this server build. */
    public static boolean supported(Player sample) {
        return of(sample) >= 0;
    }
}
