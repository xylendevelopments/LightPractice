package gg.lightpractice.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration driven debug output.
 *
 * <p>Nothing is logged unless the matching category (or {@code ALL}) is enabled in {@code config.yml},
 * so the console stays quiet during normal operation while every subsystem keeps its diagnostics.</p>
 */
public final class Debug {

    private static volatile boolean enabled;
    private static volatile Set<DebugCategory> categories = Collections.emptySet();

    private Debug() {
    }

    public static void configure(boolean debugEnabled, Set<DebugCategory> activeCategories) {
        enabled = debugEnabled;
        categories = activeCategories == null ? Collections.<DebugCategory>emptySet()
                : Collections.unmodifiableSet(new HashSet<DebugCategory>(activeCategories));
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isEnabled(DebugCategory category) {
        return enabled && (categories.contains(DebugCategory.ALL) || categories.contains(category));
    }

    public static Set<DebugCategory> categories() {
        return categories;
    }

    public static void log(DebugCategory category, String message) {
        if (!isEnabled(category)) {
            return;
        }
        DebugOutput.print(category, message, null);
    }

    public static void log(DebugCategory category, String message, Object... args) {
        if (!isEnabled(category)) {
            return;
        }
        DebugOutput.print(category, format(message, args), null);
    }

    public static void warn(DebugCategory category, String message) {
        DebugOutput.print(category, message, null);
    }

    public static void error(DebugCategory category, String message, Throwable throwable) {
        DebugOutput.print(category, message, throwable);
    }

    /**
     * Replaces every {@code {}} marker with the next argument, mirroring SLF4J style formatting
     * without pulling in a logging dependency.
     */
    static String format(String message, Object... args) {
        if (message == null) {
            return "";
        }
        if (args == null || args.length == 0 || message.indexOf("{}") < 0) {
            return message;
        }
        StringBuilder builder = new StringBuilder(message.length() + 32);
        int index = 0;
        int position = 0;
        while (position < message.length()) {
            int marker = message.indexOf("{}", position);
            if (marker < 0 || index >= args.length) {
                break;
            }
            builder.append(message, position, marker).append(stringify(args[index++]));
            position = marker + 2;
        }
        builder.append(message.substring(position));
        return builder.toString();
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            return Arrays.deepToString(new Object[]{value});
        }
        return String.valueOf(value);
    }

    /**
     * Indirection so {@link #log} can be called before the plugin instance is handed to the debug
     * output (for example from static initialisers during early bootstrapping).
     */
    static final class DebugOutput {
        private static volatile java.util.logging.Logger logger;

        private DebugOutput() {
        }

        static void attach(java.util.logging.Logger target) {
            logger = target;
        }

        static void print(DebugCategory category, String message, Throwable throwable) {
            java.util.logging.Logger target = logger;
            String line = "[LightPractice Debug][" + category.name().toLowerCase(Locale.ROOT) + "] " + message;
            if (target == null) {
                System.out.println(line);
                if (throwable != null) {
                    throwable.printStackTrace();
                }
                return;
            }
            if (throwable == null) {
                target.info(line);
            } else {
                target.log(java.util.logging.Level.WARNING, line, throwable);
            }
        }
    }

    static void attach(java.util.logging.Logger target) {
        DebugOutput.attach(target);
    }
}
