package gg.lightpractice.api;

import gg.lightpractice.bot.BotPreset;
import gg.lightpractice.bot.PracticeBot;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Practice bots. */
public interface BotService {

    /** True when a bot backend (Citizens) is available on this server. */
    boolean isAvailable();

    String backendName();

    List<BotPreset> presets();

    BotPreset preset(String id);

    /** Spawns a bot that fights the given player, optionally inside a real match. */
    boolean spawn(Player owner, String presetId);

    PracticeBot bot(UUID owner);

    PracticeBot botById(String botId);

    Collection<PracticeBot> bots();

    boolean remove(Player owner);

    boolean removeById(String botId);

    /** Removes every bot, used on shutdown and after matches. */
    void removeAll();

    boolean hasBot(UUID owner);
}
