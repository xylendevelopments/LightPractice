package gg.lightpractice.statistics;

import gg.lightpractice.api.event.LightPracticeDivisionChangeEvent;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.config.Messages;
import gg.lightpractice.model.Division;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rating divisions loaded from {@code divisions.yml}.
 *
 * <p>Divisions are pure data: a rating range, an icon and two messages. The manager resolves the
 * division of a rating and reports changes so tab lists, scoreboards and menus can show them without
 * knowing the brackets.</p>
 */
public final class DivisionManager implements LightService {

    private final PluginCore core;
    private final List<Division> divisions = new CopyOnWriteArrayList<Division>();
    private Division unranked;

    public DivisionManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "divisions";
    }

    @Override
    public int startupOrder() {
        return 45;
    }

    @Override
    public void onLoad() {
        load();
    }

    @Override
    public void onReload() {
        load();
    }

    public void load() {
        ConfigFile file = core.configs().divisions();
        file.reload();
        divisions.clear();
        ConfigurationSection root = file.section("divisions");
        if (root == null || root.getKeys(false).isEmpty()) {
            installDefaults(file);
            root = file.section("divisions");
        }
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                Division division = read(key, section);
                if (division != null) {
                    divisions.add(division);
                }
            }
        }
        Collections.sort(divisions, new Comparator<Division>() {
            @Override
            public int compare(Division left, Division right) {
                return Integer.compare(left.minimumElo(), right.minimumElo());
            }
        });
        ConfigurationSection unrankedSection = file.section("unranked");
        this.unranked = unrankedSection == null ? fallbackUnranked() : read("unranked", unrankedSection);
        Debug.log(DebugCategory.STATISTICS, "Loaded {} division(s)", divisions.size());
    }

    private Division read(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String color = section.getString("color", "&7");
        int minimum = section.getInt("min-elo", section.getInt("minimum-elo", 0));
        int maximum = section.getInt("max-elo", section.getInt("maximum-elo", Integer.MAX_VALUE));
        String icon = section.getString("icon", "IRON_INGOT");
        short data = (short) section.getInt("icon-data", 0);
        int tiers = Math.max(1, section.getInt("tiers", 1));
        String promote = section.getString("promote-message", "progress.division-promote");
        String demote = section.getString("demote-message", "progress.division-demote");
        if (maximum < minimum) {
            Debug.warn(DebugCategory.CONFIG, "Division " + id + " has max-elo below min-elo, using min-elo");
            maximum = minimum;
        }
        return new Division(id.toLowerCase(Locale.ROOT), name, color, minimum, maximum, icon, data, tiers,
                promote, demote);
    }

    private Division fallbackUnranked() {
        return new Division("unranked", "&7Unranked", "&7", 0, Integer.MAX_VALUE, "INK_SACK", (short) 8, 1,
                "progress.division-promote", "progress.division-demote");
    }

    /** Writes a sensible ladder into divisions.yml when the file holds none. */
    private void installDefaults(ConfigFile file) {
        ConfigurationSection unrankedSection = file.getOrCreateSection("unranked");
        unrankedSection.set("name", "&7Unranked");
        unrankedSection.set("color", "&7");
        unrankedSection.set("icon", "INK_SACK");
        unrankedSection.set("icon-data", 8);
        unrankedSection.set("tiers", 1);

        write(file, "bronze", "&6Bronze", "&6", 0, 1199, "CLAY_BRICK", 0, 5);
        write(file, "silver", "&fSilver", "&f", 1200, 1399, "IRON_INGOT", 0, 5);
        write(file, "gold", "&eGold", "&e", 1400, 1599, "GOLD_INGOT", 0, 5);
        write(file, "platinum", "&bPlatinum", "&b", 1600, 1799, "DIAMOND", 0, 5);
        write(file, "diamond", "&dDiamond", "&d", 1800, 1999, "EMERALD", 0, 4);
        write(file, "master", "&cMaster", "&c", 2000, Integer.MAX_VALUE, "NETHER_STAR", 0, 1);
        file.set("settings.promote-message", "progress.division-promote");
        file.set("settings.demote-message", "progress.division-demote");
        file.save();
        Debug.log(DebugCategory.CONFIG, "Installed the default divisions into divisions.yml");
    }

    private void write(ConfigFile file, String id, String name, String color, int minimum, int maximum,
                       String icon, int data, int tiers) {
        ConfigurationSection section = file.getOrCreateSection("divisions." + id);
        section.set("name", name);
        section.set("color", color);
        section.set("min-elo", minimum);
        section.set("max-elo", maximum);
        section.set("icon", icon);
        section.set("icon-data", data);
        section.set("tiers", tiers);
    }

    public List<Division> divisions() {
        return Collections.unmodifiableList(new ArrayList<Division>(divisions));
    }

    public Division unranked() {
        return unranked == null ? fallbackUnranked() : unranked;
    }

    public Division byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Division division : divisions) {
            if (division.id().equals(key)) {
                return division;
            }
        }
        return null;
    }

    /** Division that contains the rating, falling back to the closest bracket. */
    public Division byElo(int elo) {
        Division best = null;
        for (Division division : divisions) {
            if (division.contains(elo)) {
                return division;
            }
            if (best == null || Math.abs(division.minimumElo() - elo) < Math.abs(best.minimumElo() - elo)) {
                best = division;
            }
        }
        return best == null ? unranked() : best;
    }

    /** Next higher division, or {@code null} at the top of the ladder. */
    public Division next(Division division) {
        if (division == null) {
            return null;
        }
        Division result = null;
        for (Division candidate : divisions) {
            if (candidate.minimumElo() > division.maximumElo()
                    && (result == null || candidate.minimumElo() < result.minimumElo())) {
                result = candidate;
            }
        }
        return result;
    }

    /** Previous lower division, or {@code null} at the bottom of the ladder. */
    public Division previous(Division division) {
        if (division == null) {
            return null;
        }
        Division result = null;
        for (Division candidate : divisions) {
            if (candidate.maximumElo() < division.minimumElo()
                    && (result == null || candidate.maximumElo() > result.maximumElo())) {
                result = candidate;
            }
        }
        return result;
    }

    /**
     * Reports a division change after a rating update.
     *
     * @return true when the division actually changed
     */
    public boolean check(Profile profile, String kitId, int previousElo, int newElo) {
        if (profile == null) {
            return false;
        }
        Division previous = byElo(previousElo);
        Division current = byElo(newElo);
        if (previous == null || current == null || previous.id().equals(current.id())) {
            return false;
        }
        core.plugin().getServer().getPluginManager()
                .callEvent(new LightPracticeDivisionChangeEvent(profile, previous, current, kitId));
        Player player = core.plugin().getServer().getPlayer(profile.uuid());
        if (player != null && player.isOnline()) {
            Messages messages = core.messages();
            String key = current.minimumElo() > previous.minimumElo() ? current.promoteMessage()
                    : current.demoteMessage();
            messages.send(player, key,
                    "{previous}", previous.displayWithTier(previousElo),
                    "{division}", current.displayWithTier(newElo),
                    "{kit}", kitId == null ? "" : kitId,
                    "{elo}", String.valueOf(newElo));
        }
        Debug.log(DebugCategory.STATISTICS, "{} moved from {} to {} in kit {}", profile.name(),
                previous.id(), current.id(), kitId);
        return true;
    }

    /** Divisions of every cached profile, used by the division leaderboard menu. */
    public int size() {
        return divisions.size();
    }

    /** Rating needed to reach a division, {@code -1} when it does not exist. */
    public int ratingRequired(String id) {
        Division division = byId(id);
        return division == null ? -1 : division.minimumElo();
    }
}
