package gg.lightpractice.cosmetic;

import gg.lightpractice.api.CosmeticService;
import gg.lightpractice.api.event.LightPracticeCosmeticPurchaseEvent;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.integration.vault.VaultEconomy;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchTeam;
import gg.lightpractice.model.CosmeticType;
import gg.lightpractice.profile.Profile;
import gg.lightpractice.profile.ProfileManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetic registry, purchases and playback.
 *
 * <p>Cosmetics are loaded from {@code cosmetics.yml}; playback is centralised here so a trail or a
 * projectile effect costs one shared task instead of a task per player. Purchases spend coins from the
 * profile, or money through the Vault bridge when the cosmetic is priced that way.</p>
 */
public final class CosmeticManager implements CosmeticService, LightService {

    private final PluginCore core;
    private final Map<String, Cosmetic> cosmetics = new ConcurrentHashMap<String, Cosmetic>();
    private final Map<UUID, Long> trailAt = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Tracked> tracked = new ConcurrentHashMap<UUID, Tracked>();
    private BukkitTask projectileTask;
    private boolean useVault;
    private boolean trailsEnabled = true;

    public CosmeticManager(PluginCore core) {
        this.core = core;
    }

    @Override
    public String name() {
        return "cosmetics";
    }

    @Override
    public int startupOrder() {
        return 68;
    }

    @Override
    public void onLoad() {
        reload();
    }

    @Override
    public void onEnable() {
        reload();
        projectileTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                tickProjectiles();
            }
        }, 2L, 2L);
    }

    @Override
    public void onDisable() {
        core.tasks().cancel(projectileTask);
        projectileTask = null;
        cosmetics.clear();
        trailAt.clear();
        tracked.clear();
    }

    @Override
    public void onReload() {
        reload();
    }

    @Override
    public void reload() {
        ConfigFile file = core.configs().cosmetics();
        file.reload();
        this.useVault = "vault".equalsIgnoreCase(file.getString("settings.currency", "coins"));
        this.trailsEnabled = file.getBoolean("settings.trails", true);
        cosmetics.clear();
        ConfigurationSection root = file.section("cosmetics");
        if (root == null || root.getKeys(false).isEmpty()) {
            installDefaults(file);
            root = file.section("cosmetics");
        }
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                Cosmetic cosmetic = Cosmetic.read(key, section);
                if (cosmetic == null || !cosmetic.enabled()) {
                    continue;
                }
                cosmetics.put(cosmetic.id(), cosmetic);
            }
        }
        Debug.log(DebugCategory.COSMETIC, "Loaded {} cosmetic(s), currency {}", cosmetics.size(),
                useVault ? "vault" : "coins");
    }

    /** Writes a starter set of cosmetics when the file is empty. */
    private void installDefaults(ConfigFile file) {
        write(file, "flames", CosmeticType.KILL_EFFECT, "&cFlames", 250, "FLAME", "", 30, 0.4D, 0.4D, 0.4D, 0.05D,
                Material.BLAZE_POWDER, "&7The victim goes up in flames.");
        write(file, "lightning", CosmeticType.KILL_EFFECT, "&eLightning", 500, "", "AMBIENCE_THUNDER", 0, 0.0D,
                0.0D, 0.0D, 0.0D, Material.GOLD_SWORD, "&7A thunderclap marks the kill.");
        write(file, "hearts", CosmeticType.KILL_EFFECT, "&dHearts", 150, "HEART", "", 25, 0.5D, 0.5D, 0.5D, 0.1D,
                Material.RED_ROSE, "&7Hearts float up from the kill.");
        write(file, "smoke", CosmeticType.KILL_EFFECT, "&8Smoke", 100, "LARGE_SMOKE", "", 40, 0.4D, 0.4D, 0.4D,
                0.02D, Material.COAL, "&7A puff of smoke covers the kill.");
        write(file, "gg", CosmeticType.KILL_MESSAGE, "&aGood Game", 0, "", "", 0, 0.0D, 0.0D, 0.0D, 0.0D,
                Material.BOOK, "&7A polite message after the kill.");
        write(file, "slain", CosmeticType.KILL_MESSAGE, "&cSlain", 200, "", "", 0, 0.0D, 0.0D, 0.0D, 0.0D,
                Material.IRON_SWORD, "&7Announces the kill to the whole match.");
        write(file, "redstone-trail", CosmeticType.TRAIL, "&cRedstone Trail", 300, "COLOURED_DUST", "", 2, 0.0D,
                0.0D, 0.0D, 0.0D, Material.REDSTONE, "&7Red dust follows you everywhere.");
        write(file, "note-trail", CosmeticType.TRAIL, "&bNote Trail", 400, "NOTE", "", 2, 0.0D, 0.0D, 0.0D, 0.0D,
                Material.NOTE_BLOCK, "&7Notes trail behind you.");
        write(file, "arrow-spark", CosmeticType.PROJECTILE_EFFECT, "&6Arrow Sparks", 350, "FIREWORKS_SPARK", "",
                3, 0.05D, 0.05D, 0.05D, 0.0D, Material.ARROW, "&7Your projectiles leave sparks.");
        write(file, "firework-victory", CosmeticType.VICTORY_EFFECT, "&dFireworks", 600, "EXPLOSION_HUGE",
                "FIREWORK_LAUNCH", 40, 0.8D, 0.8D, 0.8D, 0.1D, Material.FIREWORK, "&7Celebrate a win with a bang.");
        write(file, "confetti-victory", CosmeticType.VICTORY_EFFECT, "&aConfetti", 250, "HAPPY_VILLAGER", "", 60,
                1.0D, 1.0D, 1.0D, 0.15D, Material.PAPER, "&7Confetti rains down on the winner.");
        ConfigurationSection message = file.getOrCreateSection("cosmetics.gg");
        message.set("message", "&a{killer} &7said good game to &c{victim}");
        ConfigurationSection slain = file.getOrCreateSection("cosmetics.slain");
        slain.set("message", "&c{victim} &7was slain by &a{killer} &7with &f{kit}");
        file.save();
        Debug.log(DebugCategory.CONFIG, "Installed the default cosmetics into cosmetics.yml");
    }

    private void write(ConfigFile file, String id, CosmeticType type, String name, long cost, String effect,
                       String sound, int particles, double offsetX, double offsetY, double offsetZ, double speed,
                       Material icon, String description) {
        ConfigurationSection section = file.getOrCreateSection("cosmetics." + id);
        section.set("type", type.name());
        section.set("display-name", name);
        section.set("description", Collections.singletonList(description));
        section.set("cost", cost);
        section.set("free", cost <= 0L);
        section.set("icon.material", icon.name());
        section.set("effect", effect);
        section.set("sound", sound);
        section.set("particles", particles);
        section.set("offset-x", offsetX);
        section.set("offset-y", offsetY);
        section.set("offset-z", offsetZ);
        section.set("speed", speed);
        if (type == CosmeticType.TRAIL) {
            section.set("interval-millis", 120L);
        }
    }

    // --------------------------------------------------------- CosmeticService

    @Override
    public Cosmetic get(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(java.util.Locale.ROOT);
        Cosmetic cosmetic = cosmetics.get(key);
        if (cosmetic != null) {
            return cosmetic;
        }
        for (Cosmetic candidate : cosmetics.values()) {
            if (Text.strip(candidate.displayName()).equalsIgnoreCase(Text.strip(id))) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public Collection<Cosmetic> cosmetics() {
        return Collections.unmodifiableCollection(cosmetics.values());
    }

    @Override
    public List<Cosmetic> byType(CosmeticType type) {
        List<Cosmetic> result = new ArrayList<Cosmetic>();
        if (type == null) {
            return result;
        }
        for (Cosmetic cosmetic : cosmetics.values()) {
            if (cosmetic.type() == type) {
                result.add(cosmetic);
            }
        }
        Collections.sort(result, new Comparator<Cosmetic>() {
            @Override
            public int compare(Cosmetic left, Cosmetic right) {
                int byOrder = Integer.compare(left.order(), right.order());
                return byOrder != 0 ? byOrder : left.id().compareTo(right.id());
            }
        });
        return result;
    }

    @Override
    public List<CosmeticType> types() {
        List<CosmeticType> result = new ArrayList<CosmeticType>();
        for (CosmeticType type : CosmeticType.values()) {
            if (!byType(type).isEmpty()) {
                result.add(type);
            }
        }
        return result;
    }

    @Override
    public boolean isUnlocked(UUID uuid, String cosmeticId) {
        Cosmetic cosmetic = get(cosmeticId);
        if (cosmetic == null) {
            return false;
        }
        if (cosmetic.free()) {
            return true;
        }
        Profile profile = profile(uuid);
        if (profile != null && profile.hasCosmetic(cosmetic.id())) {
            return true;
        }
        Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
        return player != null && cosmetic.hasPermission(player);
    }

    @Override
    public boolean purchase(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null) {
            return false;
        }
        if (!cosmetic.enabled()) {
            core.messages().send(player, "cosmetics.disabled");
            return false;
        }
        if (cosmetic.free()) {
            core.messages().send(player, "cosmetics.free", "{cosmetic}", cosmetic.displayName());
            return false;
        }
        if (isUnlocked(player.getUniqueId(), cosmetic.id())) {
            core.messages().send(player, "cosmetics.already-owned", "{cosmetic}", cosmetic.displayName());
            return false;
        }
        Profile profile = profile(player.getUniqueId());
        if (profile == null) {
            core.messages().send(player, "cosmetics.profile-missing");
            return false;
        }
        long cost = cosmetic.cost();
        double money = cosmetic.vaultCost();
        long totalCost = useVault && money > 0.0D ? Math.round(money * 100L) : cost;
        LightPracticeCosmeticPurchaseEvent event =
                new LightPracticeCosmeticPurchaseEvent(player, cosmetic, totalCost);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Debug.log(DebugCategory.COSMETIC, "A plugin cancelled the purchase of {} by {}", cosmetic.id(),
                    player.getName());
            return false;
        }
        if (useVault && money > 0.0D) {
            VaultEconomy economy = core.optional(VaultEconomy.class);
            if (economy == null || !economy.available()) {
                core.messages().send(player, "cosmetics.economy-missing");
                return false;
            }
            if (!economy.has(player.getUniqueId(), money)) {
                core.messages().send(player, "cosmetics.not-enough-money",
                        "{cost}", economy.format(money), "{cosmetic}", cosmetic.displayName());
                return false;
            }
            if (!economy.withdraw(player.getUniqueId(), money)) {
                core.messages().send(player, "cosmetics.purchase-failed", "{cosmetic}", cosmetic.displayName());
                return false;
            }
        } else {
            if (!profile.hasCoins(cost)) {
                core.messages().send(player, "cosmetics.not-enough-coins",
                        "{cost}", String.valueOf(cost),
                        "{balance}", String.valueOf(profile.coins()),
                        "{cosmetic}", cosmetic.displayName());
                return false;
            }
            if (!profile.spendCoins(cost)) {
                core.messages().send(player, "cosmetics.purchase-failed", "{cosmetic}", cosmetic.displayName());
                return false;
            }
        }
        profile.unlockCosmetic(cosmetic.id());
        ProfileManager profiles = core.optional(ProfileManager.class);
        if (profiles != null) {
            profiles.save(profile);
        }
        core.messages().send(player, "cosmetics.purchased",
                "{cosmetic}", cosmetic.displayName(),
                "{cost}", String.valueOf(totalCost),
                "{balance}", String.valueOf(profile.coins()));
        Debug.log(DebugCategory.COSMETIC, "{} bought {} for {}", player.getName(), cosmetic.id(), totalCost);
        return true;
    }

    @Override
    public boolean equip(Player player, Cosmetic cosmetic) {
        if (player == null || cosmetic == null) {
            return false;
        }
        if (!isUnlocked(player.getUniqueId(), cosmetic.id())) {
            core.messages().send(player, "cosmetics.locked", "{cosmetic}", cosmetic.displayName());
            return false;
        }
        Profile profile = profile(player.getUniqueId());
        if (profile == null) {
            core.messages().send(player, "cosmetics.profile-missing");
            return false;
        }
        profile.equip(cosmetic.type(), cosmetic.id());
        core.messages().send(player, "cosmetics.equipped",
                "{cosmetic}", cosmetic.displayName(),
                "{type}", cosmetic.type().configKey());
        Debug.log(DebugCategory.COSMETIC, "{} equipped {} ({})", player.getName(), cosmetic.id(),
                cosmetic.type());
        return true;
    }

    @Override
    public boolean unequip(Player player, CosmeticType type) {
        if (player == null || type == null) {
            return false;
        }
        Profile profile = profile(player.getUniqueId());
        if (profile == null) {
            return false;
        }
        if (profile.equipped(type) == null) {
            core.messages().send(player, "cosmetics.nothing-equipped", "{type}", type.configKey());
            return false;
        }
        profile.equip(type, null);
        core.messages().send(player, "cosmetics.unequipped", "{type}", type.configKey());
        return true;
    }

    @Override
    public String equipped(UUID uuid, CosmeticType type) {
        Profile profile = profile(uuid);
        return profile == null || type == null ? null : profile.equipped(type);
    }

    /** Cosmetic a player currently has equipped and may use, {@code null} when none applies. */
    public Cosmetic active(UUID uuid, CosmeticType type) {
        Profile profile = profile(uuid);
        if (profile == null || type == null) {
            return null;
        }
        String id = profile.equipped(type);
        if (id == null) {
            return null;
        }
        Cosmetic cosmetic = get(id);
        if (cosmetic == null || cosmetic.type() != type) {
            return null;
        }
        Player player = Bukkit.getPlayer(uuid);
        return cosmetic.usable(profile, player) ? cosmetic : null;
    }

    // ----------------------------------------------------------------- playback

    @Override
    public void applyKill(Match match, UUID killer, UUID victim) {
        if (killer == null || victim == null) {
            return;
        }
        Player victimPlayer = Bukkit.getPlayer(victim);
        Location location = victimPlayer == null ? null : victimPlayer.getLocation();
        Cosmetic effect = active(killer, CosmeticType.KILL_EFFECT);
        if (effect != null && location != null) {
            effect.play(location);
        }
        Cosmetic message = active(killer, CosmeticType.KILL_MESSAGE);
        if (message != null && !message.message().isEmpty() && match != null) {
            String killerName = nameOf(match, killer);
            String victimName = nameOf(match, victim);
            String kit = match.kit() == null ? "unknown" : Text.strip(match.kit().displayName());
            String line = Text.color(gg.lightpractice.config.Placeholder.apply(message.message(),
                    gg.lightpractice.config.Placeholder.of("killer", killerName),
                    gg.lightpractice.config.Placeholder.of("victim", victimName),
                    gg.lightpractice.config.Placeholder.of("kit", kit)));
            for (Player player : match.onlinePlayers()) {
                core.messages().sendRaw(player, line);
            }
            for (UUID uuid : match.spectators()) {
                Player spectator = uuid == null ? null : Bukkit.getPlayer(uuid);
                if (spectator != null) {
                    core.messages().sendRaw(spectator, line);
                }
            }
        }
    }

    @Override
    public void applyVictory(Match match, Collection<UUID> winners) {
        if (match == null || winners == null || winners.isEmpty()) {
            return;
        }
        for (UUID uuid : winners) {
            Cosmetic cosmetic = active(uuid, CosmeticType.VICTORY_EFFECT);
            if (cosmetic == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                cosmetic.play(player);
            }
        }
    }

    /** Plays the trail of a moving player, throttled by the cosmetic interval. */
    public void playTrail(Player player) {
        if (player == null || !trailsEnabled || !player.isOnline()) {
            return;
        }
        Cosmetic cosmetic = active(player.getUniqueId(), CosmeticType.TRAIL);
        if (cosmetic == null || cosmetic.effect().isEmpty()) {
            return;
        }
        Long last = trailAt.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last.longValue() < cosmetic.intervalMillis()) {
            return;
        }
        trailAt.put(player.getUniqueId(), Long.valueOf(now));
        cosmetic.play(player.getLocation());
    }

    /** Starts tracking a projectile so its effect is played while it flies. */
    public void trackProjectile(Projectile projectile, Player shooter) {
        if (projectile == null || shooter == null) {
            return;
        }
        Cosmetic cosmetic = active(shooter.getUniqueId(), CosmeticType.PROJECTILE_EFFECT);
        if (cosmetic == null || cosmetic.effect().isEmpty()) {
            return;
        }
        tracked.put(projectile.getUniqueId(), new Tracked(projectile, shooter.getUniqueId()));
    }

    /** Central playback task for every tracked projectile, one task for all players. */
    private void tickProjectiles() {
        if (tracked.isEmpty()) {
            return;
        }
        List<UUID> finished = new ArrayList<UUID>();
        for (Map.Entry<UUID, Tracked> entry : tracked.entrySet()) {
            Tracked trackedProjectile = entry.getValue();
            Projectile projectile = trackedProjectile == null ? null : trackedProjectile.projectile();
            if (projectile == null || projectile.isDead() || !projectile.isValid()) {
                finished.add(entry.getKey());
                continue;
            }
            Cosmetic cosmetic = active(trackedProjectile.shooter(), CosmeticType.PROJECTILE_EFFECT);
            if (cosmetic == null) {
                finished.add(entry.getKey());
                continue;
            }
            Location location = projectile.getLocation();
            if (location != null && location.getWorld() != null) {
                cosmetic.play(location.clone().add(0.0D, 0.3D, 0.0D));
            }
        }
        for (UUID uuid : finished) {
            tracked.remove(uuid);
        }
    }

    /** Stops tracking every projectile of a player, used when a match ends. */
    public void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        trailAt.remove(uuid);
        List<UUID> finished = new ArrayList<UUID>();
        for (Map.Entry<UUID, Tracked> entry : tracked.entrySet()) {
            Tracked value = entry.getValue();
            if (value != null && uuid.equals(value.shooter())) {
                finished.add(entry.getKey());
            }
        }
        for (UUID key : finished) {
            tracked.remove(key);
        }
    }

    /** Projectile reference plus its shooter, so playback never has to scan worlds for entities. */
    private static final class Tracked {

        private final Projectile projectile;
        private final UUID shooter;

        Tracked(Projectile projectile, UUID shooter) {
            this.projectile = projectile;
            this.shooter = shooter;
        }

        Projectile projectile() {
            return projectile;
        }

        UUID shooter() {
            return shooter;
        }
    }

    private String nameOf(Match match, UUID uuid) {
        MatchPlayer participant = match.participant(uuid);
        if (participant != null) {
            return participant.name();
        }
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "unknown" : player.getName();
    }

    private Profile profile(UUID uuid) {
        ProfileManager profiles = core.optional(ProfileManager.class);
        return profiles == null || uuid == null ? null : profiles.getProfile(uuid);
    }

    /** Winners of a match, used by the match end listener to play victory cosmetics. */
    public List<UUID> winnersOf(Match match) {
        List<UUID> winners = new ArrayList<UUID>();
        if (match == null) {
            return winners;
        }
        MatchTeam team = match.winningTeam();
        if (team == null) {
            return winners;
        }
        for (MatchPlayer member : team.members()) {
            winners.add(member.uuid());
        }
        return winners;
    }

    public boolean useVault() {
        return useVault;
    }

    public boolean trailsEnabled() {
        return trailsEnabled;
    }

    public int size() {
        return cosmetics.size();
    }

    /** Every cosmetic id of a type, used by tab completion. */
    public List<String> ids(CosmeticType... types) {
        List<CosmeticType> filter = types == null || types.length == 0
                ? Arrays.asList(CosmeticType.values()) : Arrays.asList(types);
        List<String> ids = new ArrayList<String>();
        Map<String, Cosmetic> ordered = new LinkedHashMap<String, Cosmetic>(cosmetics);
        for (Cosmetic cosmetic : ordered.values()) {
            if (filter.contains(cosmetic.type())) {
                ids.add(cosmetic.id());
            }
        }
        Collections.sort(ids);
        return ids;
    }
}
