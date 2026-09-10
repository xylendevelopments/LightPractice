package gg.lightpractice.integration.citizens;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reflection bridge to the Citizens NPC API.
 *
 * <p>Citizens is not a compile time dependency: its artefacts are built for newer Java versions and the
 * plugin has to keep compiling for Java 8, so every call goes through reflection and every failure is
 * contained. When Citizens is missing the bridge reports itself unavailable and the bot system refuses to
 * spawn bots instead of crashing the server.</p>
 */
public final class CitizensBridge {

    /** Negative entity ids keep temporary NPCs out of the Citizens saves file. */
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(-20000);

    private final Plugin plugin;
    private Object registry;
    private Class<?> npcClass;
    private Class<?> registryClass;
    private Method createWithEntityId;
    private Method createWithoutEntityId;
    private Method spawn;
    private Method getEntity;
    private Method despawn;
    private Method deregister;
    private Method npcOfEntity;
    private Method getNavigator;
    private Method navigatorSetTarget;
    private Method faceLocation;
    private Method data;
    private Method dataSet;
    private Method setProtected;
    private boolean available;
    private String backend = "none";

    public CitizensBridge(Plugin plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        if (plugin == null || plugin.getServer().getPluginManager().getPlugin("Citizens") == null) {
            Debug.log(DebugCategory.INTEGRATION, "Citizens is not installed, bots are disabled");
            return;
        }
        Plugin citizens = plugin.getServer().getPluginManager().getPlugin("Citizens");
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            npcClass = Class.forName("net.citizensnpcs.api.npc.NPC");
            registryClass = Class.forName("net.citizensnpcs.api.npc.NPCRegistry");
            registry = api.getMethod("getNPCRegistry").invoke(null);
            if (registry == null) {
                Debug.warn(DebugCategory.INTEGRATION, "Citizens returned no NPC registry");
                return;
            }
            createWithEntityId = find(registryClass, "createNPC",
                    new Class<?>[]{EntityType.class, UUID.class, int.class, String.class});
            createWithoutEntityId = find(registryClass, "createNPC",
                    new Class<?>[]{EntityType.class, UUID.class, String.class});
            spawn = npcClass.getMethod("spawn", Location.class);
            getEntity = npcClass.getMethod("getEntity");
            despawn = npcClass.getMethod("despawn");
            deregister = find(registryClass, "deregister", new Class<?>[]{npcClass});
            npcOfEntity = find(registryClass, "getNPC", new Class<?>[]{Entity.class});
            faceLocation = find(npcClass, "faceLocation", new Class<?>[]{Location.class});
            data = find(npcClass, "data", new Class<?>[0]);
            setProtected = find(npcClass, "setProtected", new Class<?>[]{boolean.class});
            Class<?> navigatorClass = Class.forName("net.citizensnpcs.api.ai.Navigator");
            getNavigator = npcClass.getMethod("getNavigator");
            navigatorSetTarget = navigatorClass.getMethod("setTarget", Location.class);
            if (data != null) {
                Class<?> dataKeyClass = Class.forName("net.citizensnpcs.api.util.DataKey");
                dataSet = find(dataKeyClass, "set", new Class<?>[]{String.class, String.class});
            }
            available = (createWithEntityId != null || createWithoutEntityId != null) && spawn != null
                    && getEntity != null;
            backend = available ? "Citizens " + citizens.getDescription().getVersion() : "none";
            Debug.log(DebugCategory.INTEGRATION, "Citizens bridge ready: {} (navigator {}, facing {})",
                    backend, navigatorSetTarget != null, faceLocation != null);
        } catch (Throwable throwable) {
            available = false;
            backend = "none";
            Debug.error(DebugCategory.INTEGRATION, "The Citizens bridge could not be initialised", throwable);
        }
    }

    private Method find(Class<?> type, String name, Class<?>[] parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean available() {
        return available;
    }

    public String backend() {
        return backend;
    }

    /**
     * Creates and spawns a player NPC.
     *
     * @return the opaque NPC handle, {@code null} when creation failed
     */
    public Object create(UUID id, String name, Location location, String skin) {
        if (!available || id == null || location == null || location.getWorld() == null) {
            return null;
        }
        String plain = Text.strip(name == null || name.isEmpty() ? "Bot" : name);
        if (plain.length() > 16) {
            plain = plain.substring(0, 16);
        }
        try {
            Object npc;
            if (createWithEntityId != null) {
                npc = createWithEntityId.invoke(registry, EntityType.PLAYER, id,
                        ENTITY_IDS.getAndDecrement(), plain);
            } else {
                npc = createWithoutEntityId.invoke(registry, EntityType.PLAYER, id, plain);
            }
            if (npc == null) {
                return null;
            }
            if (setProtected != null) {
                setProtected.invoke(npc, Boolean.TRUE);
            }
            if (data != null && dataSet != null && skin != null && !skin.trim().isEmpty()) {
                Object key = data.invoke(npc);
                if (key != null) {
                    dataSet.invoke(key, "player-skin-name", skin.trim());
                }
            }
            Object spawned = spawn.invoke(npc, location);
            if (spawned instanceof Boolean && !((Boolean) spawned).booleanValue()) {
                Debug.warn(DebugCategory.INTEGRATION, "Citizens refused to spawn an NPC at {}", location);
                remove(npc);
                return null;
            }
            return npc;
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.INTEGRATION, "Could not create a Citizens NPC", throwable);
            return null;
        }
    }

    public Entity entityOf(Object npc) {
        if (npc == null || getEntity == null) {
            return null;
        }
        try {
            Object entity = getEntity.invoke(npc);
            return entity instanceof Entity ? (Entity) entity : null;
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.INTEGRATION, "Could not read the entity of an NPC: {}",
                    throwable.getMessage());
            return null;
        }
    }

    /** True when the entity belongs to an NPC, so listeners can skip ordinary entity rules. */
    public boolean isNpc(Entity entity) {
        if (entity == null || npcOfEntity == null || registry == null) {
            return false;
        }
        try {
            return npcOfEntity.invoke(registry, entity) != null;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean moveTo(Object npc, Location location) {
        if (npc == null || location == null || getNavigator == null || navigatorSetTarget == null) {
            return false;
        }
        try {
            Object navigator = getNavigator.invoke(npc);
            if (navigator == null) {
                return false;
            }
            Object result = navigatorSetTarget.invoke(navigator, location);
            return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.INTEGRATION, "NPC navigation failed: {}", throwable.getMessage());
            return false;
        }
    }

    public boolean face(Object npc, Location location) {
        if (npc == null || location == null || faceLocation == null) {
            return false;
        }
        try {
            Object result = faceLocation.invoke(npc, location);
            return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean remove(Object npc) {
        if (npc == null) {
            return false;
        }
        try {
            if (despawn != null) {
                despawn.invoke(npc);
            }
            if (deregister != null && registry != null) {
                deregister.invoke(registry, npc);
                return true;
            }
            return true;
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.INTEGRATION, "Could not remove an NPC: {}", throwable.getMessage());
            return false;
        }
    }

    /** Teleports the body of an NPC directly, used when navigation is unavailable. */
    public boolean teleport(Object npc, Location location) {
        Entity entity = entityOf(npc);
        if (entity == null || location == null || location.getWorld() == null) {
            return false;
        }
        return entity.teleport(location);
    }
}
