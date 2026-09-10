package gg.lightpractice.kit;

import gg.lightpractice.api.KitService;
import gg.lightpractice.api.event.LightPracticeKitCreateEvent;
import gg.lightpractice.api.event.LightPracticeKitUpdateEvent;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.kit.rule.KitRuleRegistry;
import gg.lightpractice.profile.KitLoadout;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads, resolves and persists kits.
 *
 * <p>Kits are read from {@code kits.yml} and their {@code rules} section is resolved into a
 * {@link gg.lightpractice.kit.rule.KitRuleSet} through the {@link KitRuleRegistry}. Everything a kit
 * does in a match therefore comes from configuration plus rules, and the manager itself never knows
 * what a Sumo or BedFight kit is.</p>
 */
public final class KitManager implements KitService, LightService {

    private final PluginCore core;
    private final KitRuleRegistry ruleRegistry;
    private final Map<String, Kit> kits = new ConcurrentHashMap<String, Kit>();
    private final Object saveLock = new Object();

    public KitManager(PluginCore core, KitRuleRegistry ruleRegistry) {
        this.core = core;
        this.ruleRegistry = ruleRegistry == null ? new KitRuleRegistry() : ruleRegistry;
    }

    @Override
    public void onLoad() {
        reload();
        if (kits.isEmpty()) {
            installDefaults();
        }
    }

    @Override
    public String name() {
        return "kits";
    }

    @Override
    public int startupOrder() {
        return 50;
    }

    // ------------------------------------------------------------------ loading

    public void reload() {
        ConfigFile file = core.configs().kits();
        file.reload();
        kits.clear();
        ConfigurationSection root = file.section("kits");
        if (root == null) {
            Debug.log(DebugCategory.CONFIG, "kits.yml holds no 'kits' section");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Kit kit = Kit.read(key, section);
            if (kit == null) {
                continue;
            }
            kit.ruleSet(ruleRegistry.build(kit));
            kits.put(kit.id(), kit);
        }
        Debug.log(DebugCategory.KIT, "Loaded {} kit(s): {}", kits.size(), names());
    }

    /** Writes the bundled default kits and persists them, used on a first run only. */
    public void installDefaults() {
        int installed = DefaultKits.install(this);
        if (installed > 0) {
            saveAll();
            Debug.log(DebugCategory.KIT, "Installed {} default kit(s)", installed);
        }
    }

    @Override
    public void saveAll() {
        synchronized (saveLock) {
            ConfigFile file = core.configs().kits();
            YamlClear.clear(file.config(), "kits");
            for (Kit kit : sorted()) {
                ConfigurationSection section = file.getOrCreateSection("kits." + kit.id());
                kit.write(section);
            }
            file.save();
        }
    }

    @Override
    public boolean save(Kit kit) {
        if (kit == null) {
            return false;
        }
        register(kit);
        synchronized (saveLock) {
            ConfigFile file = core.configs().kits();
            ConfigurationSection section = file.getOrCreateSection("kits." + kit.id());
            kit.write(section);
            file.save();
        }
        core.plugin().getServer().getPluginManager().callEvent(new LightPracticeKitUpdateEvent(kit));
        return true;
    }

    /** Rebuilds the rule sets of every kit, used after a rules.yml or kits.yml reload. */
    public void rebuildRules() {
        for (Kit kit : kits.values()) {
            kit.ruleSet(ruleRegistry.build(kit));
        }
        Debug.log(DebugCategory.KIT, "Rebuilt the rules of {} kit(s)", kits.size());
    }

    // -------------------------------------------------------------- registration

    private void register(Kit kit) {
        if (kit == null) {
            return;
        }
        kits.put(kit.id(), kit);
    }

    @Override
    public Kit create(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        String key = normalize(id);
        if (kits.containsKey(key)) {
            return null;
        }
        Kit kit = new Kit(key);
        kit.displayName(gg.lightpractice.util.Text.capitalize(key));
        kit.enabled(false);
        kit.rule("no-fall-damage", Boolean.TRUE);
        kit.rule("no-hunger", Boolean.TRUE);
        kit.rule("no-regen", Boolean.TRUE);
        kit.rule("time-limit", DefaultKits.options("seconds", 600));
        kit.ruleSet(ruleRegistry.build(kit));
        LightPracticeKitCreateEvent event = new LightPracticeKitCreateEvent(kit);
        core.plugin().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.KIT, "Creation of kit {} was cancelled", key);
            return null;
        }
        register(kit);
        return kit;
    }

    /**
     * Creates a kit from what a player is currently holding and wearing.
     *
     * @return the new kit, or {@code null} when the id is taken or the player is missing
     */
    public Kit create(String id, Player creator) {
        Kit kit = create(id);
        if (kit == null || creator == null) {
            return kit;
        }
        kit.contents(creator.getInventory().getContents());
        kit.armor(creator.getInventory().getArmorContents());
        List<StoredPotionEffect> effects = new ArrayList<StoredPotionEffect>();
        for (org.bukkit.potion.PotionEffect effect : creator.getActivePotionEffects()) {
            effects.add(new StoredPotionEffect(effect.getType().getName(), effect.getAmplifier(),
                    effect.getDuration() >= 1000000 ? -1 : Math.max(1, effect.getDuration() / 20),
                    effect.isAmbient(), !effect.getParticles()));
        }
        kit.clearEffects();
        for (StoredPotionEffect effect : effects) {
            kit.addEffect(effect);
        }
        ItemStack held = creator.getItemInHand();
        if (held != null && held.getType() != Material.AIR) {
            kit.icon(held.getType(), held.getDurability());
        }
        return kit;
    }

    @Override
    public boolean delete(String id) {
        Kit removed = id == null ? null : kits.remove(normalize(id));
        if (removed == null) {
            return false;
        }
        synchronized (saveLock) {
            ConfigFile file = core.configs().kits();
            file.remove("kits." + removed.id());
            file.save();
        }
        Debug.log(DebugCategory.KIT, "Deleted kit {}", removed.id());
        return true;
    }

    @Override
    public boolean exists(String id) {
        return id != null && kits.containsKey(normalize(id));
    }

    // ------------------------------------------------------------------- lookup

    @Override
    public Kit get(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        String key = normalize(id);
        Kit kit = kits.get(key);
        if (kit != null) {
            return kit;
        }
        for (Kit candidate : kits.values()) {
            if (candidate.displayName() != null
                    && gg.lightpractice.util.Text.strip(candidate.displayName()).equalsIgnoreCase(gg.lightpractice.util.Text.strip(id))) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public Collection<Kit> kits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    /** Every kit ordered by the configured {@code order} value, then by id. */
    public List<Kit> sorted() {
        List<Kit> result = new ArrayList<Kit>(kits.values());
        Collections.sort(result, new Comparator<Kit>() {
            @Override
            public int compare(Kit left, Kit right) {
                int byOrder = Integer.compare(left.order(), right.order());
                return byOrder != 0 ? byOrder : left.id().compareTo(right.id());
            }
        });
        return result;
    }

    @Override
    public List<String> names() {
        List<String> names = new ArrayList<String>();
        for (Kit kit : sorted()) {
            names.add(kit.id());
        }
        return names;
    }

    @Override
    public List<Kit> enabled() {
        List<Kit> result = new ArrayList<Kit>();
        for (Kit kit : sorted()) {
            if (kit.enabled()) {
                result.add(kit);
            }
        }
        return result;
    }

    @Override
    public List<Kit> queueable(boolean ranked) {
        List<Kit> result = new ArrayList<Kit>();
        for (Kit kit : sorted()) {
            if (!kit.enabled()) {
                continue;
            }
            if (ranked ? kit.ranked() : kit.unranked()) {
                result.add(kit);
            }
        }
        return result;
    }

    @Override
    public List<Kit> duellable() {
        List<Kit> result = new ArrayList<Kit>();
        for (Kit kit : sorted()) {
            if (kit.enabled() && kit.duel()) {
                result.add(kit);
            }
        }
        return result;
    }

    @Override
    public List<Kit> editable() {
        List<Kit> result = new ArrayList<Kit>();
        for (Kit kit : sorted()) {
            if (kit.enabled() && kit.editable()) {
                result.add(kit);
            }
        }
        return result;
    }

    public KitRuleRegistry ruleRegistry() {
        return ruleRegistry;
    }

    // -------------------------------------------------------------------- apply

    /**
     * Equips a player with a kit, using their saved loadout when the kit is editable.
     *
     * @param custom true to honour the player's edited loadout
     */
    public void applyKit(Player player, Kit kit, boolean custom) {
        if (player == null || kit == null) {
            return;
        }
        KitLoadout loadout = null;
        if (custom && kit.editable()) {
            ProfileManager profiles = core.optional(ProfileManager.class);
            Profile profile = profiles == null ? null : profiles.getProfile(player.getUniqueId());
            loadout = profile == null ? null : profile.loadout(kit.id());
        }
        kit.apply(player, loadout);
    }

    public void applyKit(Player player, Kit kit) {
        applyKit(player, kit, true);
    }

    /**
     * Adds kits without firing events, used for the bundled defaults on a first run.
     *
     * @return how many kits were actually added
     */
    int install(List<Kit> additions) {
        int count = 0;
        if (additions == null) {
            return 0;
        }
        for (Kit kit : additions) {
            if (kit == null || kits.containsKey(kit.id())) {
                continue;
            }
            kit.ruleSet(ruleRegistry.build(kit));
            kits.put(kit.id(), kit);
            count++;
        }
        return count;
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
