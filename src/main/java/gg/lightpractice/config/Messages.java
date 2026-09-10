package gg.lightpractice.config;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access layer over {@code messages.yml}.
 *
 * <p>Every user facing string in the plugin is resolved through this class: single line messages may
 * be written as a string, multi line messages as a list, and both support {@code {placeholder}}
 * tokens. Unknown keys are reported once through the debug logger instead of spamming the console.</p>
 *
 * <p>Placeholders are passed as alternating {@code "{token}", value} pairs, which keeps call sites short
 * and readable; {@link #placeholders(String...)} converts them.</p>
 */
public final class Messages {

    private final ConfigFile file;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile String prefix = "";

    public Messages(ConfigFile file) {
        this.file = file;
        reload();
    }

    public void reload() {
        this.prefix = Text.color(file.getString("prefix", "&8[&bLight&fPractice&8]&r "));
    }

    public String prefix() {
        return prefix;
    }

    public ConfigFile file() {
        return file;
    }

    public boolean has(String key) {
        return file.contains(key);
    }

    /** Returns the configured value with placeholders applied, without the global prefix. */
    public String raw(String key, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        String value = file.getString(key, null);
        if (value == null) {
            missing(key);
            return "";
        }
        return Text.color(Placeholder.apply(value, placeholders));
    }

    /** Returns the configured value with the global prefix applied. */
    public String prefixed(String key, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        String value = file.getString(key, null);
        if (value == null) {
            missing(key);
            return "";
        }
        String resolved = Placeholder.apply(value, placeholders);
        if (resolved.contains("{prefix}")) {
            return Text.color(resolved.replace("{prefix}", prefix));
        }
        return prefix + Text.color(resolved);
    }

    public List<String> list(String key, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        List<String> values = file.getStringList(key);
        if (values == null || values.isEmpty()) {
            String single = file.getString(key, null);
            if (single == null) {
                missing(key);
                return Collections.emptyList();
            }
            values = new ArrayList<String>();
            values.add(single);
        }
        return Text.color(Placeholder.apply(values, placeholders));
    }

    /**
     * Sends a message key, transparently supporting both single line and list values. Empty values
     * are treated as "message disabled" by administrators.
     */
    public void send(CommandSender sender, String key, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        if (sender == null) {
            return;
        }
        Object value = file.config().get(key);
        if (value == null) {
            missing(key);
            return;
        }
        if (value instanceof List) {
            for (String line : list(key, placeholders)) {
                sender.sendMessage(line);
            }
            return;
        }
        String message = prefixed(key, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(message);
        }
    }

    public void sendList(CommandSender sender, String key, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        if (sender == null) {
            return;
        }
        for (String line : list(key, placeholders)) {
            sender.sendMessage(line);
        }
    }

    public void sendRaw(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(Text.color(message));
    }

    public void broadcast(String key, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        String message = prefixed(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        Bukkit.broadcastMessage(message);
    }

    public void broadcastTo(Collection<? extends Player> players, String key, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        String message = prefixed(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        for (Player player : players) {
            player.sendMessage(message);
        }
    }

    public void title(Player player, String titleKey, String subtitleKey, String... replacements) {
        Placeholder[] placeholders = placeholders(replacements);
        if (player == null) {
            return;
        }
        String title = raw(titleKey, placeholders);
        String subtitle = subtitleKey == null ? "" : raw(subtitleKey, placeholders);
        try {
            player.sendTitle(title, subtitle);
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.COMMAND, "Titles are not supported by this server build: {}",
                    throwable.getMessage());
        }
    }

    /**
     * Turns alternating {@code {token}} and value arguments into placeholders.
     *
     * <p>Tokens may be written with or without their braces, and a trailing token without a value is
     * replaced with an empty string so a malformed call never loses a message.</p>
     */
    public static Placeholder[] placeholders(String... replacements) {
        if (replacements == null || replacements.length == 0) {
            return new Placeholder[0];
        }
        List<Placeholder> values = new ArrayList<Placeholder>();
        for (int index = 0; index < replacements.length; index += 2) {
            String token = replacements[index];
            if (token == null || token.isEmpty()) {
                continue;
            }
            String name = token.startsWith("{") && token.endsWith("}") && token.length() > 2
                    ? token.substring(1, token.length() - 1) : token;
            String value = index + 1 < replacements.length && replacements[index + 1] != null
                    ? replacements[index + 1] : "";
            values.add(Placeholder.of(name, value));
        }
        return values.toArray(new Placeholder[values.size()]);
    }

    private void missing(String key) {
        if (warned.add(key)) {
            Debug.log(DebugCategory.COMMAND, "Message key '{}' is missing from messages.yml", key);
        }
    }

}
