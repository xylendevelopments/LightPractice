package gg.lightpractice.gui.menu;

import gg.lightpractice.gui.Button;
import gg.lightpractice.gui.ClickAction;
import gg.lightpractice.gui.Menu;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.kit.StoredPotionEffect;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.profile.KitLoadout;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kit editor.
 *
 * <p>The editor works on a copy of the loadout: items are picked up with a left click and placed with the
 * next click, a right click drops an item and nothing is written to the profile until the player saves.
 * Closing without saving discards the copy and says so, so nobody loses a loadout by accident.</p>
 */
public final class KitEditorMenu extends Menu {

    private static final int CONTENT_SLOTS = 36;
    private static final int ARMOR_SLOT = 36;
    private static final int HELD_SLOT = 45;
    private static final int SAVE_SLOT = 48;
    private static final int RESET_SLOT = 49;
    private static final int DROP_SLOT = 50;
    private static final int BACK_SLOT = 53;

    private final String kitId;
    private final ItemStack[] contents = new ItemStack[CONTENT_SLOTS];
    private final ItemStack[] armor = new ItemStack[4];
    private ItemStack held;
    private int heldSlot = -1;
    private boolean loaded;
    private boolean dirty;

    public KitEditorMenu(PluginCore core, String kitId) {
        super(core, buildTitle(core, kitId), 54);
        this.kitId = kitId == null ? "" : kitId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String buildTitle(PluginCore core, String kitId) {
        String base = core.configs().gui().getString("titles.kit-editor", "&8Editing");
        return base + " &7- &f" + (kitId == null ? "kit" : kitId);
    }

    public String kitId() {
        return kitId;
    }

    public boolean dirty() {
        return dirty;
    }

    @Override
    public void onOpen(Player viewer) {
        load(viewer);
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null || viewer == null ? null : players.get(viewer);
        if (session != null) {
            session.editingKit(kitId);
        }
    }

    @Override
    public void onClose(Player viewer) {
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null || viewer == null ? null : players.get(viewer);
        if (session != null && kitId.equals(session.editingKit())) {
            session.editingKit(null);
        }
        if (dirty && viewer != null) {
            core.messages().send(viewer, "kit-editor.unsaved", "{kit}", kitId);
        }
        loaded = false;
        dirty = false;
        held = null;
        heldSlot = -1;
    }

    /** Copies the saved loadout, or the kit defaults when nothing was saved yet. */
    private void load(Player viewer) {
        if (loaded) {
            return;
        }
        loaded = true;
        dirty = false;
        held = null;
        heldSlot = -1;
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            contents[slot] = null;
        }
        for (int slot = 0; slot < armor.length; slot++) {
            armor[slot] = null;
        }
        Kit kit = kit();
        Profile profile = profile(viewer);
        KitLoadout loadout = profile == null ? null : profile.loadout(kitId);
        ItemStack[] source = loadout != null ? loadout.contents() : (kit == null ? null : kit.contents());
        ItemStack[] sourceArmor = loadout != null ? loadout.armor() : (kit == null ? null : kit.armor());
        if (source != null) {
            for (int slot = 0; slot < CONTENT_SLOTS && slot < source.length; slot++) {
                contents[slot] = Items.cloneOrNull(source[slot]);
            }
        }
        if (sourceArmor != null) {
            for (int slot = 0; slot < armor.length && slot < sourceArmor.length; slot++) {
                armor[slot] = Items.cloneOrNull(sourceArmor[slot]);
            }
        }
    }

    @Override
    public Map<Integer, Button> buttons(Player viewer) {
        load(viewer);
        Map<Integer, Button> map = new LinkedHashMap<Integer, Button>();
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            map.put(Integer.valueOf(slot), editable(slot, contents[slot]));
        }
        // armor is shown helmet first, the stored order is boots first like a player inventory
        for (int index = 0; index < armor.length; index++) {
            int stored = armor.length - 1 - index;
            map.put(Integer.valueOf(ARMOR_SLOT + index), editable(ARMOR_SLOT + index, armor[stored]));
        }
        map.put(Integer.valueOf(40), Button.display(Items.item(Material.THIN_GLASS, 1, (short) 0,
                Text.color("&7"), new ArrayList<String>())));
        Kit kit = kit();
        int effectSlot = 41;
        if (kit != null) {
            for (StoredPotionEffect effect : kit.effects()) {
                if (effect == null || effectSlot > 43) {
                    break;
                }
                map.put(Integer.valueOf(effectSlot), Button.display(Items.item(Material.POTION, 1,
                        (short) 0, Text.color("&d" + effect.type()),
                        lore("&7Amplifier: &f" + effect.amplifier(),
                                "&7Duration: &f" + effect.durationSeconds() + "s",
                                "&7Applied when the kit is given"))));
                effectSlot++;
            }
        }
        map.put(Integer.valueOf(44), Button.display(Items.item(Material.CHEST, 1, (short) 0,
                Text.color("&b" + (kit == null ? kitId : kit.displayName())),
                kit == null ? lore("&7This kit is unknown")
                        : lore("&7Rules: &f" + kit.ruleIds().size(),
                                "&7Editable: " + (kit.editable() ? "&ayes" : "&cno"),
                                "&7Health: &f" + (int) kit.health(),
                                "&7&m                        ",
                                "&7Left click an item to pick it up",
                                "&7Click again to place it",
                                "&7Right click an item to drop it"))));
        ItemStack heldItem = held == null
                ? Items.item(Material.STAINED_GLASS_PANE, 1, (short) 8, Text.color("&7Holding Nothing"),
                        lore("&7Pick an item up to move it"))
                : Items.decorate(Items.cloneOrNull(held), Text.color("&eHolding"),
                        lore("&7Click a slot to place it"));
        map.put(Integer.valueOf(HELD_SLOT), Button.display(heldItem));
        map.put(Integer.valueOf(SAVE_SLOT), Button.of(Items.item(Material.INK_SACK, 1, (short) 10,
                Text.color("&aSave Loadout"),
                lore("&7Store this arrangement on your profile",
                        dirty ? "&eYou have unsaved changes" : "&7Nothing changed yet")),
                new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        save(player);
                    }
                }));
        map.put(Integer.valueOf(RESET_SLOT), Button.of(Items.item(Material.INK_SACK, 1, (short) 1,
                Text.color("&cReset Loadout"),
                lore("&7Throw away your copy and reload the kit defaults")),
                new ClickAction() {
                    @Override
                    public void click(Player player, ClickType type) {
                        reset(player);
                    }
                }));
        map.put(Integer.valueOf(DROP_SLOT), held == null
                ? Button.display(Items.item(Material.STAINED_GLASS_PANE, 1, (short) 8,
                        Text.color("&7Drop"), lore("&7Nothing is picked up")))
                : Button.of(Items.item(Material.STAINED_GLASS_PANE, 1, (short) 14,
                        Text.color("&cDrop Held Item"), lore("&7Destroy the item you are holding")),
                        new ClickAction() {
                            @Override
                            public void click(Player player, ClickType type) {
                                held = null;
                                heldSlot = -1;
                                dirty = true;
                                refresh(player);
                            }
                        }));
        map.put(Integer.valueOf(BACK_SLOT), backButton(new KitSelectMenu(core)));
        return map;
    }

    /** One editable slot: pick up, place, swap or drop. */
    private Button editable(final int slot, ItemStack item) {
        ItemStack shown = item == null ? new ItemStack(Material.AIR) : Items.cloneOrNull(item);
        return Button.of(shown, new ClickAction() {
            @Override
            public void click(Player player, ClickType type) {
                interact(player, slot, type);
            }
        });
    }

    private void interact(Player player, int slot, ClickType type) {
        if (slot < 0 || slot >= ARMOR_SLOT + armor.length) {
            return;
        }
        if (type == ClickType.RIGHT && held == null) {
            ItemStack current = read(slot);
            if (current != null && current.getType() != Material.AIR) {
                write(slot, null);
                dirty = true;
                core.messages().send(player, "kit-editor.dropped", "{item}", name(current));
                refresh(player);
            }
            return;
        }
        if (held != null) {
            ItemStack current = read(slot);
            write(slot, held);
            if (current != null && current.getType() != Material.AIR) {
                held = current;
                heldSlot = slot;
            } else {
                held = null;
                heldSlot = -1;
            }
            dirty = true;
            refresh(player);
            return;
        }
        ItemStack picked = read(slot);
        if (picked == null || picked.getType() == Material.AIR) {
            return;
        }
        held = picked;
        heldSlot = slot;
        write(slot, null);
        dirty = true;
        refresh(player);
    }

    private ItemStack read(int slot) {
        if (slot < CONTENT_SLOTS) {
            return contents[slot];
        }
        int index = armor.length - 1 - (slot - ARMOR_SLOT);
        return index < 0 || index >= armor.length ? null : armor[index];
    }

    private void write(int slot, ItemStack item) {
        ItemStack value = Items.cloneOrNull(item);
        if (slot < CONTENT_SLOTS) {
            contents[slot] = value;
            return;
        }
        int index = armor.length - 1 - (slot - ARMOR_SLOT);
        if (index >= 0 && index < armor.length) {
            armor[index] = value;
        }
    }

    private String name(ItemStack item) {
        if (item == null) {
            return "nothing";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return Text.strip(item.getItemMeta().getDisplayName());
        }
        return Text.capitalize(item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    /** Writes the copy to the profile and saves it. */
    private void save(Player player) {
        Kit kit = kit();
        if (kit == null) {
            core.messages().send(player, "kit-editor.kit-missing", "{kit}", kitId);
            return;
        }
        if (!kit.editable()) {
            core.messages().send(player, "kit.not-editable", "{kit}", kit.displayName());
            return;
        }
        Profile profile = profile(player);
        if (profile == null) {
            core.messages().send(player, "kit-editor.profile-missing");
            return;
        }
        ItemStack[] savedContents = new ItemStack[CONTENT_SLOTS];
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            savedContents[slot] = Items.cloneOrNull(contents[slot]);
        }
        ItemStack[] savedArmor = new ItemStack[armor.length];
        for (int slot = 0; slot < armor.length; slot++) {
            savedArmor[slot] = Items.cloneOrNull(armor[slot]);
        }
        profile.loadout(kitId, new KitLoadout(savedContents, savedArmor));
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles != null) {
            profiles.save(profile);
        }
        dirty = false;
        held = null;
        heldSlot = -1;
        core.messages().send(player, "kit-editor.saved", "{kit}", kit.displayName());
        refresh(player);
    }

    /** Discards the copy and reloads the defaults of the kit. */
    private void reset(Player player) {
        Profile profile = profile(player);
        if (profile != null) {
            profile.loadout(kitId, null);
        }
        loaded = false;
        load(player);
        dirty = false;
        core.messages().send(player, "kit-editor.reset", "{kit}", kitId);
        refresh(player);
    }

    private Kit kit() {
        KitManager kits = core.optional(KitManager.class);
        return kits == null ? null : kits.get(kitId);
    }

    private Profile profile(Player viewer) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || viewer == null ? null : profiles.getProfile(viewer);
    }

    /** Slot the picked up item came from, {@code -1} when nothing is held. */
    public int heldSlot() {
        return heldSlot;
    }

}
