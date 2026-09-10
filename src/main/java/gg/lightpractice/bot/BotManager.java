package gg.lightpractice.bot;

import gg.lightpractice.api.BotService;
import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaManager;
import gg.lightpractice.combat.CombatManager;
import gg.lightpractice.combat.DamageVerdict;
import gg.lightpractice.config.ConfigFile;
import gg.lightpractice.integration.citizens.CitizensBridge;
import gg.lightpractice.kit.Kit;
import gg.lightpractice.kit.KitManager;
import gg.lightpractice.match.EndCause;
import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchManager;
import gg.lightpractice.match.MatchPlayer;
import gg.lightpractice.match.MatchRequest;
import gg.lightpractice.match.MatchType;
import gg.lightpractice.model.KnockbackProfile;
import gg.lightpractice.player.LightPlayer;
import gg.lightpractice.player.PlayerManager;
import gg.lightpractice.service.LightService;
import gg.lightpractice.service.PluginCore;
import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Items;
import gg.lightpractice.util.Locations;
import gg.lightpractice.util.Text;
import gg.lightpractice.util.Visuals;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.EntityDamageEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Practice bots.
 *
 * <p>A bot is a Citizens NPC with a synthetic uuid, so it takes part in a normal
 * {@link MatchType#BOT} match: the match engine pairs it, spawns it, eliminates it and decides the result
 * exactly like it would for a second player. What the match engine cannot do is think, and that is what
 * the single artificial intelligence task here does for every bot at once: move, strafe, keep range, use
 * items and attack on the reaction time of its preset.</p>
 *
 * <p>Damage is handled in both directions without touching vanilla health: hits on the body are cancelled
 * and applied to the health of the bot, hits from the bot are marked as internal match damage so the kit
 * rules of the fight still apply to the player.</p>
 */
public final class BotManager implements BotService, LightService {

    private final PluginCore core;
    private final Map<String, BotPreset> presets = new ConcurrentHashMap<String, BotPreset>();
    private final Map<String, PracticeBot> bots = new ConcurrentHashMap<String, PracticeBot>();
    private final Map<UUID, String> botsByUuid = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, String> botsByOwner = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, String> botsByEntity = new ConcurrentHashMap<UUID, String>();
    private final Random random = new Random();
    private CitizensBridge bridge;
    private BukkitTask aiTask;

    private boolean enabled = true;
    private int maxBots = 8;
    private int aiIntervalTicks = 2;
    private int matchCountdown = 3;
    private String defaultKit = "nodebuff";
    private String defaultPreset = "normal";
    private String arenaName = "";

    public BotManager(PluginCore core, CitizensBridge bridge) {
        this.core = core;
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "bots";
    }

    @Override
    public int startupOrder() {
        return 69;
    }

    @Override
    public void onLoad() {
        reload();
    }

    @Override
    public void onEnable() {
        reload();
        if (!isAvailable()) {
            Debug.warn(DebugCategory.BOT,
                    "Bots need Citizens: {} is reported by the bridge. The bot command will explain this "
                            + "to players instead of spawning anything.", backendName());
            return;
        }
        aiTask = core.tasks().timer(new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, aiIntervalTicks, aiIntervalTicks);
    }

    @Override
    public void onDisable() {
        removeAll();
        core.tasks().cancel(aiTask);
        aiTask = null;
        presets.clear();
        botsByUuid.clear();
        botsByOwner.clear();
        botsByEntity.clear();
    }

    @Override
    public void onReload() {
        reload();
    }

    public void reload() {
        ConfigFile file = core.configs().bots();
        file.reload();
        this.enabled = file.getBoolean("settings.enabled", true);
        this.maxBots = Math.max(1, file.getInt("settings.max-bots", 8));
        this.aiIntervalTicks = Math.max(1, Math.min(20, file.getInt("settings.ai-interval-ticks", 2)));
        this.matchCountdown = Math.max(0, file.getInt("settings.match-countdown-seconds", 3));
        this.defaultKit = file.getString("settings.default-kit", "nodebuff");
        this.defaultPreset = file.getString("settings.default-preset", "normal");
        this.arenaName = file.getString("settings.arena", "");
        presets.clear();
        ConfigurationSection root = file.section("presets");
        if (root == null || root.getKeys(false).isEmpty()) {
            installDefaults(file);
            root = file.section("presets");
        }
        if (root != null) {
            for (String key : root.getKeys(false)) {
                BotPreset preset = BotPreset.read(key, root.getConfigurationSection(key));
                if (preset != null && preset.enabled()) {
                    presets.put(preset.id(), preset);
                }
            }
        }
        Debug.log(DebugCategory.BOT, "Bot presets loaded: {}, backend {}", presets.keySet(),
                backendName());
    }

    private void installDefaults(ConfigFile file) {
        write(file, "easy", "&aEasy Bot", "nodebuff", "EASY", 20.0D, false, false, false, false);
        write(file, "normal", "&eNormal Bot", "nodebuff", "NORMAL", 20.0D, false, false, false, false);
        write(file, "hard", "&cHard Bot", "nodebuff", "HARD", 20.0D, true, false, false, true);
        write(file, "insane", "&4Insane Bot", "nodebuff", "INSANE", 20.0D, true, true, true, true);
        write(file, "sumo", "&6Sumo Bot", "sumo", "HARD", 20.0D, false, false, false, false);
        write(file, "debuff", "&5Debuff Bot", "debuff", "NORMAL", 20.0D, false, false, false, false);
        ConfigurationSection debuff = file.getOrCreateSection("presets.debuff");
        debuff.set("use-potions", true);
        debuff.set("potion.effect", "POISON");
        debuff.set("potion.duration-seconds", 5);
        debuff.set("potion.amplifier", 0);
        ConfigurationSection sumo = file.getOrCreateSection("presets.sumo");
        sumo.set("attack-range", 2.5D);
        sumo.set("preferred-range", 1.2D);
        file.save();
        Debug.log(DebugCategory.CONFIG, "Installed the default bot presets into bots.yml");
    }

    private void write(ConfigFile file, String id, String name, String kit, String difficulty, double health,
                       boolean strafe, boolean pearls, boolean rods, boolean apples) {
        ConfigurationSection section = file.getOrCreateSection("presets." + id);
        section.set("display-name", name);
        section.set("kit", kit);
        section.set("difficulty", difficulty);
        section.set("health", health);
        section.set("name", "&c{preset}");
        section.set("strafe", strafe);
        section.set("use-pearls", pearls);
        section.set("use-rods", rods);
        section.set("use-apples", apples);
        section.set("icon.material", "IRON_SWORD");
        section.set("enabled", true);
    }

    // --------------------------------------------------------------- BotService

    @Override
    public boolean isAvailable() {
        return enabled && bridge != null && bridge.available();
    }

    @Override
    public String backendName() {
        return bridge == null ? "none" : bridge.backend();
    }

    public CitizensBridge bridge() {
        return bridge;
    }

    public void bridge(CitizensBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public List<BotPreset> presets() {
        List<BotPreset> values = new ArrayList<BotPreset>(presets.values());
        Collections.sort(values, new Comparator<BotPreset>() {
            @Override
            public int compare(BotPreset left, BotPreset right) {
                int byOrder = Integer.compare(left.order(), right.order());
                return byOrder != 0 ? byOrder : left.id().compareTo(right.id());
            }
        });
        return values;
    }

    @Override
    public BotPreset preset(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(java.util.Locale.ROOT);
        BotPreset preset = presets.get(key);
        if (preset != null) {
            return preset;
        }
        for (BotPreset candidate : presets.values()) {
            if (Text.strip(candidate.displayName()).equalsIgnoreCase(Text.strip(id))) {
                return candidate;
            }
        }
        return null;
    }

    public List<String> presetIds() {
        List<String> ids = new ArrayList<String>(presets.keySet());
        Collections.sort(ids);
        return ids;
    }

    @Override
    public boolean spawn(Player owner, String presetId) {
        if (owner == null) {
            return false;
        }
        if (!enabled) {
            core.messages().send(owner, "bot.disabled");
            return false;
        }
        if (!isAvailable()) {
            core.messages().send(owner, "bot.unavailable", "{backend}", backendName());
            return false;
        }
        boolean noId = presetId == null || presetId.trim().isEmpty();
        BotPreset preset = preset(noId ? defaultPreset : presetId);
        if (preset == null && !noId) {
            preset = preset(defaultPreset);
        }
        if (preset == null && !presets.isEmpty()) {
            preset = presets().get(0);
        }
        if (preset == null) {
            core.messages().send(owner, "bot.unknown-preset", "{preset}", String.valueOf(presetId));
            return false;
        }
        if (!preset.allowed(owner)) {
            core.messages().send(owner, "bot.no-permission", "{preset}", preset.displayName());
            return false;
        }
        if (bots.size() >= maxBots) {
            core.messages().send(owner, "bot.limit", "{max}", String.valueOf(maxBots));
            return false;
        }
        if (hasBot(owner.getUniqueId())) {
            core.messages().send(owner, "bot.already-has",
                    "{bot}", bot(owner.getUniqueId()).name());
            return false;
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(owner);
        if (session != null && (session.inMatch() || session.inQueue() || session.inEvent()
                || session.inTournament() || session.botId() != null)) {
            core.messages().send(owner, "bot.busy");
            return false;
        }
        KitManager kits = core.optional(KitManager.class);
        String kitId = preset.kitId().isEmpty() ? defaultKit : preset.kitId();
        Kit kit = kits == null ? null : kits.get(kitId);
        if (kit == null || !kit.enabled()) {
            core.messages().send(owner, "bot.kit-missing", "{kit}", kitId);
            return false;
        }
        Arena arena = resolveArena(kit);
        if (arena == null) {
            core.messages().send(owner, "bot.no-arena", "{kit}", kit.displayName());
            return false;
        }
        UUID botUuid = botUuid(preset, owner.getUniqueId());
        String botName = Text.color(preset.namePattern()
                .replace("{name}", owner.getName())
                .replace("{preset}", Text.capitalize(preset.id()))
                .replace("{difficulty}", preset.difficulty().displayName()));
        PracticeBot bot = new PracticeBot("bot-" + UUID.randomUUID().toString().substring(0, 8), preset,
                owner.getUniqueId(), owner.getName(), botUuid, botName);
        register(bot);
        MatchRequest request = new MatchRequest(kit, MatchType.BOT, false).source("bot");
        request.arena(arena);
        request.countdownSeconds(matchCountdown);
        request.resetArena(true);
        request.spectatorsAllowed(true);
        request.addTeam(owner.getName(), Collections.singletonList(owner.getUniqueId()))
                .spawnKey(Arena.SPAWN_RED);
        request.addTeam(Text.strip(botName), Collections.singletonList(botUuid)).spawnKey(Arena.SPAWN_BLUE);
        MatchManager matches = core.optional(MatchManager.class);
        Match match = matches == null ? null : matches.startMatch(request);
        if (match == null) {
            unregister(bot);
            core.messages().send(owner, "bot.spawn-failed", "{preset}", preset.displayName());
            Debug.warn(DebugCategory.BOT, "The bot match of {} could not start ({})", owner.getName(),
                    request.problems());
            return false;
        }
        bot.matchId(match.identifier());
        if (session != null) {
            session.botId(bot.id());
        }
        bot.target(owner.getUniqueId());
        core.messages().send(owner, "bot.spawned",
                "{bot}", botName,
                "{preset}", preset.displayName(),
                "{difficulty}", preset.difficulty().displayName(),
                "{kit}", kit.displayName(),
                "{arena}", arena.name());
        Debug.log(DebugCategory.BOT, "Spawned bot {} ({}) for {} in match {}", bot.id(), preset.id(),
                owner.getName(), match.identifier());
        return true;
    }

    /** Deterministic uuid per preset and owner, so a bot keeps its identity across respawns. */
    private UUID botUuid(BotPreset preset, UUID owner) {
        String seed = "lightpractice:bot:" + preset.id() + ':' + owner;
        return UUID.nameUUIDFromBytes(seed.getBytes(Charset.forName("UTF-8")));
    }

    private Arena resolveArena(Kit kit) {
        ArenaManager arenas = core.optional(ArenaManager.class);
        if (arenas == null) {
            return null;
        }
        if (arenaName != null && !arenaName.isEmpty()) {
            Arena configured = arenas.get(arenaName);
            if (configured != null && configured.enabled() && arenas.isAvailable(configured)) {
                return configured;
            }
        }
        return arenas.findAvailable(kit);
    }

    @Override
    public PracticeBot bot(UUID owner) {
        if (owner == null) {
            return null;
        }
        String id = botsByOwner.get(owner);
        return id == null ? null : bots.get(id);
    }

    @Override
    public PracticeBot botById(String botId) {
        return botId == null ? null : bots.get(botId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Bot fighting under a synthetic match uuid, {@code null} for real players. */
    public PracticeBot botByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String id = botsByUuid.get(uuid);
        return id == null ? null : bots.get(id);
    }

    /** Bot that owns an entity, {@code null} when the entity is not one of ours. */
    public PracticeBot botByEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = botsByEntity.get(entity.getUniqueId());
        return id == null ? null : bots.get(id);
    }

    @Override
    public Collection<PracticeBot> bots() {
        return Collections.unmodifiableCollection(bots.values());
    }

    @Override
    public boolean hasBot(UUID owner) {
        return owner != null && botsByOwner.containsKey(owner);
    }

    /** True when a uuid belongs to a bot, used by the match engine and listeners. */
    public boolean isBot(UUID uuid) {
        return uuid != null && botsByUuid.containsKey(uuid);
    }

    /** True when an entity is one of our bots, used by damage and interaction listeners. */
    public boolean isBotEntity(Entity entity) {
        return botByEntity(entity) != null;
    }

    /** Display name of a bot uuid, {@code null} when the uuid is not a bot. */
    public String nameOf(UUID uuid) {
        PracticeBot bot = botByUuid(uuid);
        return bot == null ? null : bot.name();
    }

    @Override
    public boolean remove(Player owner) {
        PracticeBot bot = owner == null ? null : bot(owner.getUniqueId());
        if (bot == null) {
            core.messages().send(owner, "bot.none");
            return false;
        }
        removeById(bot.id());
        core.messages().send(owner, "bot.removed", "{bot}", bot.name());
        return true;
    }

    @Override
    public boolean removeById(String botId) {
        PracticeBot bot = botById(botId);
        if (bot == null) {
            return false;
        }
        Match match = matchOf(bot);
        if (match != null && !match.isFinished()) {
            MatchManager matches = core.optional(MatchManager.class);
            if (matches != null) {
                matches.end(match, null, EndCause.ABANDONED);
            }
        }
        despawn(bot.uuid());
        unregister(bot);
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(bot.owner());
        if (session != null && bot.id().equals(session.botId())) {
            session.botId(null);
        }
        Debug.log(DebugCategory.BOT, "Removed bot {} of {}", bot.id(), bot.ownerName());
        return true;
    }

    @Override
    public void removeAll() {
        for (String id : new ArrayList<String>(bots.keySet())) {
            PracticeBot bot = bots.get(id);
            if (bot == null) {
                continue;
            }
            despawn(bot.uuid());
            unregister(bot);
        }
        bots.clear();
        botsByUuid.clear();
        botsByOwner.clear();
        botsByEntity.clear();
    }

    private void register(PracticeBot bot) {
        bots.put(bot.id(), bot);
        botsByUuid.put(bot.uuid(), bot.id());
        if (bot.owner() != null) {
            botsByOwner.put(bot.owner(), bot.id());
        }
    }

    private void unregister(PracticeBot bot) {
        bots.remove(bot.id());
        botsByUuid.remove(bot.uuid());
        if (bot.owner() != null) {
            botsByOwner.remove(bot.owner());
        }
        if (bot.entity() != null) {
            botsByEntity.remove(bot.entity().getUniqueId());
        }
        bot.state(BotState.REMOVED);
    }

    // ------------------------------------------------------------- match engine

    /** Places the body of a bot, called by the match engine while it prepares participants. */
    public void place(UUID uuid, Location spawn) {
        PracticeBot bot = botByUuid(uuid);
        if (bot == null) {
            return;
        }
        if (spawn == null || spawn.getWorld() == null) {
            Debug.warn(DebugCategory.BOT, "Bot {} has no usable spawn", bot.id());
            return;
        }
        Location at = Locations.center(spawn);
        if (bot.hasBody()) {
            bot.entity().teleport(at);
            bot.location(at);
            bot.state(BotState.ACTIVE);
            return;
        }
        Object npc = bridge.create(bot.uuid(), Text.strip(bot.name()), at, bot.preset().skinName());
        if (npc == null) {
            Debug.warn(DebugCategory.BOT, "Citizens could not create the body of bot {}", bot.id());
            return;
        }
        Entity entity = bridge.entityOf(npc);
        bot.npc(npc);
        bot.entity(entity);
        bot.location(at);
        bot.state(BotState.ACTIVE);
        bot.health(bot.preset().health());
        if (entity != null) {
            botsByEntity.put(entity.getUniqueId(), bot.id());
        }
        Debug.log(DebugCategory.BOT, "Bot {} spawned at {}", bot.id(), Locations.format(at));
    }

    /** Removes the body of a bot, called when the match engine eliminates it. */
    public void despawn(UUID uuid) {
        PracticeBot bot = botByUuid(uuid);
        if (bot == null) {
            return;
        }
        if (bot.entity() != null) {
            botsByEntity.remove(bot.entity().getUniqueId());
        }
        if (bridge != null) {
            bridge.remove(bot.npc());
        }
        bot.npc(null);
        bot.entity(null);
        if (bot.state() != BotState.REMOVED) {
            bot.state(BotState.DEAD);
        }
    }

    /** Cleans up the bots of a finished match. */
    public void handleMatchEnd(Match match) {
        if (match == null) {
            return;
        }
        List<String> finished = new ArrayList<String>();
        for (PracticeBot bot : bots.values()) {
            if (match.identifier().equals(bot.matchId())) {
                finished.add(bot.id());
            }
        }
        for (String id : finished) {
            PracticeBot bot = bots.get(id);
            if (bot == null) {
                continue;
            }
            despawn(bot.uuid());
            unregister(bot);
            PlayerManager players = core.optional(PlayerManager.class);
            LightPlayer session = players == null ? null : players.get(bot.owner());
            if (session != null && id.equals(session.botId())) {
                session.botId(null);
            }
            Player owner = Bukkit.getPlayer(bot.owner());
            if (owner != null) {
                core.messages().send(owner, "bot.match-over",
                        "{bot}", bot.name(),
                        "{accuracy}", String.valueOf(bot.accuracyPercent()),
                        "{damage}", String.valueOf(bot.damageDealt()),
                        "{seconds}", String.valueOf(Math.max(0L, bot.ageMillis() / 1000L)));
            }
            Debug.log(DebugCategory.BOT,
                    "Bot {} finished: {} health left, {} hits, {}% accuracy, {} damage dealt",
                    bot.id(), bot.health(), bot.hitsLanded(), bot.accuracyPercent(), bot.damageDealt());
        }
    }

    /** Removes the bot of a player who disconnected. */
    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        PracticeBot bot = bot(player.getUniqueId());
        if (bot != null) {
            removeById(bot.id());
        }
    }

    private Match matchOf(PracticeBot bot) {
        MatchManager matches = core.optional(MatchManager.class);
        if (matches == null || bot == null || bot.matchId() == null) {
            return null;
        }
        return matches.get(bot.matchId());
    }

    // ------------------------------------------------------------- damage routing

    /**
     * Handles damage done to a bot body.
     *
     * @return true when the event belonged to one of our bots and was handled
     */
    public boolean handleBotDamaged(EntityDamageByEntityEvent event) {
        if (event == null) {
            return false;
        }
        PracticeBot bot = botByEntity(event.getEntity());
        if (bot == null) {
            return false;
        }
        event.setCancelled(true);
        if (!bot.isAlive()) {
            return true;
        }
        Match match = matchOf(bot);
        if (match == null || !match.isRunning()) {
            return true;
        }
        Player attacker = attackerOf(event.getDamager());
        double damage = event.getDamage();
        if (damage <= 0.0D) {
            return true;
        }
        boolean died = bot.damage(damage);
        Location at = bot.location();
        if (at != null) {
            Visuals.effect(at, "CRIT", 6, 0.3D, 0.3D, 0.3D, 0.0D, 32, 0);
            Visuals.sound(at, "HURT_FLESH", 1.0F, 1.0F);
        }
        knockback(bot, attacker, match);
        if (attacker != null) {
            MatchPlayer participant = match.participant(attacker);
            if (participant != null) {
                participant.registerHit(match.comboWindowMillis());
                participant.addDamage(damage);
                participant.lastAttacker(bot.uuid());
            }
            CombatManager combat = core.optional(CombatManager.class);
            if (combat != null) {
                combat.tag(bot.uuid(), attacker.getUniqueId());
            }
        }
        if (died) {
            onBotDeath(bot, match, attacker);
        }
        return true;
    }

    /** Cancels environment damage on bots, whose health is tracked by this class only. */
    public boolean handleBotEnvironmentDamage(EntityDamageEvent event) {
        if (event == null) {
            return false;
        }
        PracticeBot bot = botByEntity(event.getEntity());
        if (bot == null) {
            return false;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.setCancelled(true);
        }
        return true;
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            LivingEntity shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    private void knockback(PracticeBot bot, Player attacker, Match match) {
        Entity entity = bot.entity();
        if (entity == null || attacker == null || entity.isDead()) {
            return;
        }
        Location from = attacker.getLocation();
        Location to = entity.getLocation();
        if (from == null || to == null) {
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        KnockbackProfile profile = combat == null ? KnockbackProfile.DEFAULT : combat.profile(match);
        Vector direction = to.toVector().subtract(from.toVector()).setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            direction = from.getDirection().setY(0.0D);
            if (direction.lengthSquared() < 0.0001D) {
                return;
            }
        }
        Vector velocity = profile.calculate(direction.normalize(), attacker.isSprinting(), false);
        if (velocity != null) {
            entity.setVelocity(velocity);
        }
    }

    private void onBotDeath(PracticeBot bot, Match match, Player attacker) {
        bot.state(BotState.DEAD);
        if (attacker != null) {
            MatchPlayer participant = match.participant(attacker);
            if (participant != null) {
                participant.addKill(1);
            }
            CombatManager combat = core.optional(CombatManager.class);
            if (combat != null) {
                combat.clearTag(bot.uuid());
            }
        }
        match.broadcast("bot.defeated",
                "{bot}", bot.name(),
                "{player}", attacker == null ? "nobody" : attacker.getName(),
                "{hits}", String.valueOf(bot.hitsLanded()),
                "{damage}", String.valueOf(bot.damageTaken()));
        Debug.log(DebugCategory.BOT, "Bot {} died after {} hit(s) and {} damage", bot.id(),
                bot.hitsLanded(), bot.damageTaken());
        match.eliminate(bot.uuid(), EndCause.KNOCKOUT);
    }

    // ------------------------------------------------------------------------ ai

    /** One shared task drives every bot, so no bot ever owns its own scheduler. */
    private void tick() {
        if (bots.isEmpty()) {
            return;
        }
        MatchManager matches = core.optional(MatchManager.class);
        List<String> finished = new ArrayList<String>();
        for (PracticeBot bot : bots.values()) {
            try {
                if (bot.state() == BotState.REMOVED) {
                    finished.add(bot.id());
                    continue;
                }
                Match match = matches == null || bot.matchId() == null ? null : matches.get(bot.matchId());
                if (match == null || match.isFinished()) {
                    finished.add(bot.id());
                    continue;
                }
                if (!bot.hasBody()) {
                    if (match.isLive()) {
                        place(bot.uuid(), bot.location());
                    }
                    if (!bot.hasBody()) {
                        continue;
                    }
                }
                if (!match.isLive()) {
                    continue;
                }
                think(bot, match);
            } catch (Throwable throwable) {
                Debug.error(DebugCategory.BOT, "The artificial intelligence of " + bot.id() + " failed",
                        throwable);
            }
        }
        for (String id : finished) {
            removeById(id);
        }
    }

    private void think(PracticeBot bot, Match match) {
        BotPreset preset = bot.preset();
        Player target = targetOf(bot, match);
        if (target == null) {
            return;
        }
        bot.target(target.getUniqueId());
        Location at = bot.location();
        Location there = target.getLocation();
        if (at == null || there == null || at.getWorld() == null || !at.getWorld().equals(there.getWorld())) {
            return;
        }
        double distance = at.distance(there);
        if (bot.healReady()) {
            bot.heal(preset.healAmount());
            bot.healed();
            Visuals.sound(at, "EAT", 1.0F, 1.0F);
            Visuals.effect(at, "HEART", 4, 0.3D, 0.3D, 0.3D, 0.0D, 32, 0);
        }
        if (preset.usePotions() && bot.potionReady() && distance <= preset.potionRange()
                && random.nextDouble() <= preset.potionChance()) {
            throwPotion(bot, match, target);
        }
        if (preset.usePearls() && bot.pearlReady() && distance > preset.attackRange() * 3.0D) {
            closeGap(bot, target);
        }
        if (preset.useRods() && bot.rodReady() && distance > preset.attackRange() && distance <= 10.0D
                && random.nextDouble() <= preset.aggression()) {
            pullTarget(bot, match, target, distance);
        }
        move(bot, at, there, distance);
        bridge.face(bot.npc(), there);
        if (distance > preset.attackRange() || !bot.attackReady()) {
            return;
        }
        if (random.nextDouble() <= preset.accuracy()) {
            attack(bot, match, target);
        } else {
            bot.attacked();
            bot.missed();
        }
    }

    /** The opponent of a bot: the first alive human participant of its match. */
    private Player targetOf(PracticeBot bot, Match match) {
        UUID remembered = bot.target();
        if (remembered != null) {
            MatchPlayer participant = match.participant(remembered);
            Player player = Bukkit.getPlayer(remembered);
            if (participant != null && !participant.eliminated() && player != null && player.isOnline()
                    && !player.isDead() && !isBot(remembered)) {
                return player;
            }
        }
        for (Player player : match.alivePlayers()) {
            if (player == null || isBot(player.getUniqueId())) {
                continue;
            }
            return player;
        }
        return null;
    }

    /** Keeps the bot at its preferred distance: closing in, stepping back or circling the target. */
    private void move(PracticeBot bot, Location at, Location there, double distance) {
        BotPreset preset = bot.preset();
        if (distance > preset.attackRange() * 0.9D) {
            if (random.nextDouble() <= preset.aggression()) {
                bridge.moveTo(bot.npc(), there);
            }
            return;
        }
        if (!preset.strafe() || System.currentTimeMillis() - bot.lastStrafeAt() < 900L) {
            return;
        }
        bot.strafed();
        Vector delta = there.toVector().subtract(at.toVector()).setY(0.0D);
        if (delta.lengthSquared() < 0.0001D) {
            return;
        }
        delta.normalize();
        Location point;
        if (distance < preset.preferredRange() * 0.6D) {
            point = at.clone().add(delta.multiply(-preset.preferredRange()));
        } else {
            Vector sideways = new Vector(-delta.getZ(), 0.0D, delta.getX())
                    .multiply(bot.nextStrafeDirection() * preset.preferredRange());
            point = there.clone().add(sideways);
        }
        point.setY(there.getY());
        bridge.moveTo(bot.npc(), point);
    }

    private void attack(PracticeBot bot, Match match, Player target) {
        BotPreset preset = bot.preset();
        Location at = bot.location();
        if (at == null) {
            return;
        }
        double damage = preset.rollDamage(random);
        CombatManager combat = core.optional(CombatManager.class);
        DamageVerdict verdict = combat == null ? DamageVerdict.allow(damage)
                : combat.verdict(match, target, null, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage);
        if (!verdict.allowed()) {
            bot.missed();
            return;
        }
        bot.attacked();
        bot.dealtDamage(verdict.damage());
        // the damage is applied without a damager entity, the match marks it as its own so the kit rules
        // of the fight let it through and the knockback below gives it direction
        match.allowNextDamage(target);
        target.damage(verdict.damage());
        if (combat != null) {
            combat.tag(target.getUniqueId(), bot.uuid());
            combat.applyKnockback(target, at, true, combat.profile(match));
        }
        Visuals.sound(target, "HURT_FLESH", 1.0F, 1.0F);
        Visuals.effect(target.getLocation(), "CRIT", 4, 0.3D, 0.3D, 0.3D, 0.0D, 32, 0);
        MatchPlayer participant = match.participant(target);
        if (participant != null) {
            participant.lastAttacker(bot.uuid());
        }
        PlayerManager players = core.optional(PlayerManager.class);
        LightPlayer session = players == null ? null : players.get(target);
        if (session != null) {
            session.damageFrom(bot.uuid());
        }
    }

    /** Simulated splash potion: the effect lands on the target with the visuals of a thrown potion. */
    private void throwPotion(PracticeBot bot, Match match, Player target) {
        BotPreset preset = bot.preset();
        CombatManager combat = core.optional(CombatManager.class);
        if (combat != null && !combat.potionAllowed(target, match, true)) {
            return;
        }
        PotionEffectType type = PotionEffectType.getByName(preset.potionEffect());
        if (type == null) {
            Debug.warn(DebugCategory.BOT, "Bot preset {} uses the unknown potion effect '{}'",
                    preset.id(), preset.potionEffect());
            preset.usePotions(false);
            return;
        }
        bot.usedPotion();
        target.addPotionEffect(new PotionEffect(type, preset.potionDurationSeconds() * 20,
                preset.potionAmplifier()), true);
        Location at = target.getLocation();
        Visuals.effect(at, "POTION_BREAK", 25, 0.4D, 0.4D, 0.4D, 0.0D, 48, 0);
        Visuals.sound(at, "GLASS", 1.0F, 1.0F);
        Debug.log(DebugCategory.BOT, "Bot {} threw {} at {}", bot.id(), type.getName(),
                target.getName());
    }

    /** Simulated ender pearl: the body is moved next to the target with the visuals of a teleport. */
    private void closeGap(PracticeBot bot, Player target) {
        Location there = target.getLocation();
        if (there == null || there.getWorld() == null) {
            return;
        }
        bot.usedPearl();
        Location from = bot.location();
        if (from != null) {
            Visuals.effect(from, "PORTAL", 30, 0.3D, 0.6D, 0.3D, 0.2D, 48, 0);
            Visuals.sound(from, "PORTAL_TRAVEL", 0.6F, 1.4F);
        }
        Location to = Locations.center(there);
        if (from != null) {
            Vector approach = there.toVector().subtract(from.toVector()).setY(0.0D);
            if (approach.lengthSquared() > 0.0001D) {
                to = Locations.center(there.clone().subtract(approach.normalize().multiply(1.2D)));
            }
        }
        if (bridge.teleport(bot.npc(), to)) {
            bot.location(to);
        }
        Visuals.effect(to, "PORTAL", 30, 0.3D, 0.6D, 0.3D, 0.2D, 48, 0);
        Visuals.sound(to, "PORTAL_TRAVEL", 0.6F, 1.0F);
    }

    /** Simulated fishing rod pull: the target is dragged towards the bot. */
    private void pullTarget(PracticeBot bot, Match match, Player target, double distance) {
        Location at = bot.location();
        Location there = target.getLocation();
        if (at == null || there == null) {
            return;
        }
        CombatManager combat = core.optional(CombatManager.class);
        double strength = combat == null ? 1.1D : combat.rodPullStrength();
        Vector direction = at.toVector().subtract(there.toVector()).setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            return;
        }
        bot.usedRod();
        target.setVelocity(direction.normalize().multiply(strength).setY(0.25D));
        Visuals.sound(at, "BAT_TAKEOFF", 0.7F, 1.3F);
        Visuals.effect(there, "PORTAL", 12, 0.2D, 0.2D, 0.2D, 0.1D, 32, 0);
        core.messages().send(target, "bot.pulled", "{bot}", bot.name(),
                "{distance}", String.valueOf((int) Math.round(distance)));
    }

    // --------------------------------------------------------------- introspection

    public boolean enabled() {
        return enabled;
    }

    public int maxBots() {
        return maxBots;
    }

    public int aiIntervalTicks() {
        return aiIntervalTicks;
    }

    public int matchCountdown() {
        return matchCountdown;
    }

    public String defaultKit() {
        return defaultKit;
    }

    public String defaultPreset() {
        return defaultPreset;
    }

    /** Line describing a bot for the debug command and menus. */
    public String describe(PracticeBot bot) {
        if (bot == null) {
            return "no bot";
        }
        return bot.name() + " &7(" + bot.preset().difficulty().displayName() + "&7) &f"
                + (int) bot.health() + "&7/&f" + (int) bot.maxHealth() + " HP &7"
                + bot.state().configKey();
    }

    /** Menu icon of a preset. */
    public org.bukkit.inventory.ItemStack icon(BotPreset preset) {
        if (preset == null) {
            return Items.item(Material.PAPER, 1, (short) 0, Text.color("&7Unknown bot"),
                    new ArrayList<String>());
        }
        return preset.iconItem();
    }

    /** Snapshot of every bot, used by the debug command. */
    public List<String> describeAll() {
        List<String> lines = new ArrayList<String>();
        for (PracticeBot bot : bots.values()) {
            lines.add(describe(bot) + " &7owner &f" + bot.ownerName() + " &7accuracy &f"
                    + bot.accuracyPercent() + "%");
        }
        if (lines.isEmpty()) {
            lines.add(Text.color("&7No bots are spawned."));
        }
        return lines;
    }

    /** Whether the bot system can run at all, used by commands and menus. */
    public String availability() {
        if (!enabled) {
            return "disabled";
        }
        return bridge == null ? "no bridge" : backendName();
    }

    /** Presets a player is allowed to spawn, used by the bot menu. */
    public List<BotPreset> presetsFor(Player player) {
        List<BotPreset> allowed = new ArrayList<BotPreset>();
        for (BotPreset preset : presets()) {
            if (preset.allowed(player)) {
                allowed.add(preset);
            }
        }
        return allowed;
    }
}
