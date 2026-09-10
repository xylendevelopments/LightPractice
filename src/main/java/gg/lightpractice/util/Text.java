package gg.lightpractice.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Text helpers used by every user facing component. All colours are stored in configuration with
 * the '{@code &}' code and translated here so that no other class has to think about formatting.
 */
public final class Text {

    private Text() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static List<String> color(Collection<String> input) {
        List<String> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        for (String line : input) {
            result.add(color(line));
        }
        return result;
    }

    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(color(input));
    }

    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String lower = input.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder(lower.length());
        boolean start = true;
        for (char character : lower.toCharArray()) {
            if (start && Character.isLetter(character)) {
                builder.append(Character.toUpperCase(character));
                start = false;
            } else {
                builder.append(character);
                if (character == ' ') {
                    start = true;
                }
            }
        }
        return builder.toString();
    }

    /** Formats a duration as {@code 1h 05m 12s}, dropping leading units that are zero. */
    public static String duration(long millis) {
        if (millis < 0L) {
            millis = 0L;
        }
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60L;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60L;
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        StringBuilder builder = new StringBuilder();
        if (hours > 0L) {
            builder.append(hours).append('h').append(' ');
        }
        if (hours > 0L || minutes > 0L) {
            builder.append(String.format(Locale.ROOT, "%02dm", minutes)).append(' ');
        }
        builder.append(String.format(Locale.ROOT, "%02ds", seconds));
        return builder.toString();
    }

    /** Formats a duration as {@code 1:05} / {@code 1:02:05} which is used by match scoreboards. */
    public static String clock(long millis) {
        long total = Math.max(0L, millis) / 1000L;
        long seconds = total % 60L;
        long minutes = (total / 60L) % 60L;
        long hours = total / 3600L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public static String plural(long amount, String singular) {
        return amount == 1L ? singular : singular + "s";
    }

    public static String join(Collection<String> values, String separator) {
        return String.join(separator, values);
    }

    public static String trimToFit(String input, int max) {
        if (input == null) {
            return "";
        }
        return input.length() <= max ? input : input.substring(0, max);
    }
}
