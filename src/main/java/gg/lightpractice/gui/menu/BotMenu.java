package gg.lightpractice.gui.menu;

import gg.lightpractice.bot.BotManager;
import gg.lightpractice.bot.BotPreset;
import gg.lightpractice.bot.PracticeBot;
import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.PagedMenu;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bot preset picker.
 *
 * <p>Spawning a bot needs a Citizens NPC, so when the bridge is unavailable the menu explains that instead
 * of showing presets that cannot work. Clicking a preset starts a bot match for the viewer.</p>
 */
public final class BotMenu extends PagedMenu {

    public BotMenu(PluginCore core) {
        super(core, title(core), 4, null, "&7No bot presets are configured.");
    }

    private static String title(PluginCore core) {
        return core.configs().gui().getString("titles.bots", "&8Practice Bots");
    }

    @Override
    public boolean liveData() {
        return true;
    }

    @Override
    public List<Button> entries(final Player viewer) {
        List<Button> entries = new ArrayList<Button>();
        final BotManager bots = core.optional(BotManager.class);
        if (bots == null || viewer == null) {
            return entries;
        }
        if (!bots.isAvailable()) {
            entries.add(Button.display(Items.item(Material.BARRIER, 1, (short) 0,
                    Text.color("&cBots are unavailable"),
                    lore("&7Bots are built on Citizens NPCs.",
                            "&7Backend: &f" + bots.backendName(),
                            "&7Install Citizens or enable bots in &fbots.yml&7."))));
            return entries;
        }
        for (final BotPreset preset : bots.presetsFor(viewer)) {
            List<String> lore = new ArrayList<String>();
            for (String line : preset.lore()) {
                lore.add(line);
            }
            lore.add(Text.color("&7Difficulty: " + preset.difficulty().displayName()));
            lore.add(Text.color("&7Kit: &f" + (preset.kitId().isEmpty() ? bots.defaultKit() : preset.kitId())));
            lore.add(Text.color("&7Health: &f" + (int) preset.health()));
            lore.add(Text.color("&7Accuracy: &f" + (int) Math.round(preset.accuracy() * 100.0D) + "%"));
            lore.add(Text.color("&7Reaction: &f" + preset.reactionMillis() + "ms"));
            lore.add(Text.color("&7Damage: &f" + preset.minDamage() + " &7- &f" + preset.maxDamage()));
            lore.add(Text.color("&7&m                        "));
            lore.add(Text.color("&aClick &7to fight this bot"));
            entries.add(Button.of(preset.iconItem(), new ClickAction() {
                @Override
                public void click(Player player, ClickType type) {
                    close(player);
                    bots.spawn(player, preset.id());
                }
            }));
        }
        return entries;
    }

    @Override
    protected void decorate(Map<Integer, Button> map, Player viewer) {
        final BotManager bots = core.optional(BotManager.class);
        int lastRow = (rows() - 1) * 9;
        PracticeBot own = bots == null || viewer == null ? null : bots.bot(viewer.getUniqueId());
        ItemStack item = own == null
                ? Items.item(Material.INK_SACK, 1, (short) 8, Text.color("&7Remove Bot"),
                        lore("&7You have no bot spawned"))
                : Items.item(Material.INK_SACK, 1, (short) 1, Text.color("&cRemove Bot"),
                        lore("&7Removes &f" + Text.strip(own.name()),
                                "&7Health left: &f" + (int) own.health() + "&7/&f" + (int) own.maxHealth(),
                                "&7Accuracy: &f" + own.accuracyPercent() + "%"));
        map.put(Integer.valueOf(lastRow + 3), own == null ? Button.display(item) : Button.of(item,
                new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        bots.remove(player);
                        refresh(player);
                    }
                }));
        ItemStack info = Items.item(Material.BOOK, 1, (short) 0, Text.color("&eAbout Bots"),
                lore("&7Bots fight you in a real match",
                        "&7They strafe, combo and use items",
                        "&7Active bots: &f" + (bots == null ? 0 : bots.bots().size()) + "&7/&f"
                                + (bots == null ? 0 : bots.maxBots()),
                        "&7Backend: &f" + (bots == null ? "none" : bots.backendName())));
        map.put(Integer.valueOf(lastRow + 6), Button.display(info));
    }

}
