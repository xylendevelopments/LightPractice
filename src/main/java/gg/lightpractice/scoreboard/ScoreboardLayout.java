package gg.lightpractice.scoreboard;

import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.util.Text;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed contents of {@code scoreboard.yml}.
 *
 * <p>The layout is immutable after reading: a reload builds a new one and hands it to the service, which
 * is why a reload can never leave a half updated board on screen. Lines may be empty strings, they then
 * render as a blank spacer row.</p>
 */
public final class ScoreboardLayout {

    private final boolean enabled;
    private final int updateTicks;
    private final String defaultTitle;
    private final Map<BoardContext, String> titles;
    private final Map<BoardContext, List<String>> lines;
    private final boolean health;

    private ScoreboardLayout(boolean enabled, int updateTicks, String defaultTitle,
                             Map<BoardContext, String> titles, Map<BoardContext, List<String>> lines,
                             boolean health) {
        this.enabled = enabled;
        this.updateTicks = updateTicks;
        this.defaultTitle = defaultTitle;
        this.titles = titles;
        this.lines = lines;
        this.health = health;
    }

    /** Reads the layout, falling back to a disabled layout when the file is missing keys. */
    public static ScoreboardLayout read(ConfigFile file) {
        boolean enabled = file == null || file.getBoolean("enabled", true);
        int updateTicks = file == null ? 10 : Math.max(1, file.getInt("update-ticks", 10));
        String defaultTitle = file == null ? "&6&lLightPractice" : file.getString("title", "&6&lLightPractice");
        boolean health = file != null && file.getBoolean("health-objective", false);
        Map<BoardContext, String> titles = new EnumMap<BoardContext, String>(BoardContext.class);
        Map<BoardContext, List<String>> lines = new EnumMap<BoardContext, List<String>>(BoardContext.class);
        if (file != null) {
            ConfigurationSection titleSection = file.section("titles");
            if (titleSection != null) {
                for (BoardContext context : BoardContext.values()) {
                    String value = titleSection.getString(context.configKey());
                    if (value != null && !value.isEmpty()) {
                        titles.put(context, value);
                    }
                }
            }
            ConfigurationSection lineSection = file.section("lines");
            if (lineSection != null) {
                for (BoardContext context : BoardContext.values()) {
                    List<String> configured = lineSection.getStringList(context.configKey());
                    if (configured == null) {
                        continue;
                    }
                    List<String> parsed = new ArrayList<String>();
                    for (String line : configured) {
                        parsed.add(line == null ? "" : line);
                    }
                    lines.put(context, Collections.unmodifiableList(parsed));
                }
            }
        }
        return new ScoreboardLayout(enabled, updateTicks, defaultTitle, titles, lines, health);
    }

    public boolean enabled() {
        return enabled;
    }

    public int updateTicks() {
        return updateTicks;
    }

    public boolean health() {
        return health;
    }

    public String title(BoardContext context) {
        String value = context == null ? null : titles.get(context);
        return Text.color(value == null || value.isEmpty() ? defaultTitle : value);
    }

    public List<String> lines(BoardContext context) {
        List<String> value = context == null ? null : lines.get(context);
        if (value == null) {
            value = lines.get(BoardContext.LOBBY);
        }
        return value == null ? Collections.<String>emptyList() : value;
    }

    public boolean has(BoardContext context) {
        return context != null && lines.containsKey(context);
    }

    public int size() {
        int total = 0;
        for (List<String> value : lines.values()) {
            total += value.size();
        }
        return total;
    }

    public String toString() {
        return "ScoreboardLayout{enabled=" + enabled + ", update=" + updateTicks + "t, lines=" + size() + '}';
    }
}
