package gg.lightpractice.player;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import gg.lightpractice.util.Items;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Everything that has to be restored after a match: inventory, armour, vitals, experience, game mode,
 * flight and location.
 *
 * <p>Minecraft 1.8.9 has no off-hand slot, so the snapshot covers the 36 storage slots plus the four
 * armour slots. Restoration is wrapped per step: if one part fails (for example a health value that is
 * out of range for a modified server) the rest is still applied and the failure is logged.</p>
 */
public final class InventorySnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int experienceLevel;
    private final float experienceProgress;
    private final int totalExperience;
    private final GameMode gameMode;
    private final Location location;
    private final List<PotionEffect> effects;
    private final boolean allowFlight;
    private final boolean flying;
    private final float walkSpeed;
    private final float flySpeed;
    private final long capturedAt;

    private InventorySnapshot(Player player) {
        this.contents = Items.copy(player.getInventory().getContents());
        this.armor = Items.copy(player.getInventory().getArmorContents());
        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exhaustion = player.getExhaustion();
        this.experienceLevel = player.getLevel();
        this.experienceProgress = player.getExp();
        this.totalExperience = player.getTotalExperience();
        this.gameMode = player.getGameMode();
        this.location = player.getLocation().clone();
        this.effects = new ArrayList<PotionEffect>(player.getActivePotionEffects());
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.walkSpeed = player.getWalkSpeed();
        this.flySpeed = player.getFlySpeed();
        this.capturedAt = System.currentTimeMillis();
    }

    public static InventorySnapshot capture(Player player) {
        if (player == null) {
            return null;
        }
        try {
            return new InventorySnapshot(player);
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.PLAYER, "Could not capture the state of " + player.getName(), throwable);
            return null;
        }
    }

    public long capturedAt() {
        return capturedAt;
    }

    public ItemStack[] contents() {
        return Items.copy(contents);
    }

    public ItemStack[] armor() {
        return Items.copy(armor);
    }

    public Location location() {
        return location == null ? null : location.clone();
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public boolean hasLocation() {
        return location != null && location.getWorld() != null;
    }

    /**
     * Restores the captured state.
     *
     * @param teleport whether the player should also be moved back to the captured location
     * @return true when every step succeeded
     */
    public boolean restore(Player player, boolean teleport) {
        if (player == null) {
            return false;
        }
        boolean clean = true;
        clean &= apply(new Step("inventory") {
            @Override
            public void run(Player target) {
                target.getInventory().clear();
                target.getInventory().setArmorContents(new ItemStack[4]);
                target.getInventory().setContents(Items.copy(contents));
                target.getInventory().setArmorContents(Items.copy(armor));
                target.updateInventory();
            }
        }, player);
        clean &= apply(new Step("health") {
            @Override
            public void run(Player target) {
                for (PotionEffect effect : new ArrayList<PotionEffect>(target.getActivePotionEffects())) {
                    target.removePotionEffect(effect.getType());
                }
                for (PotionEffect effect : effects) {
                    target.addPotionEffect(effect, true);
                }
                target.setFireTicks(0);
                double ceiling = Math.max(1.0D, maxHealth);
                target.setMaxHealth(maxHealth <= 0.0D ? 20.0D : maxHealth);
                target.setHealth(Math.max(0.5D, Math.min(health, ceiling)));
                target.setFoodLevel(foodLevel);
                target.setSaturation(saturation);
                target.setExhaustion(exhaustion);
            }
        }, player);
        clean &= apply(new Step("experience") {
            @Override
            public void run(Player target) {
                target.setLevel(experienceLevel);
                target.setExp(Math.max(0.0F, Math.min(1.0F, experienceProgress)));
                target.setTotalExperience(totalExperience);
            }
        }, player);
        clean &= apply(new Step("movement") {
            @Override
            public void run(Player target) {
                target.setWalkSpeed(walkSpeed);
                target.setFlySpeed(flySpeed);
                target.setAllowFlight(allowFlight);
                target.setFlying(allowFlight && flying);
                target.setSprinting(false);
            }
        }, player);
        clean &= apply(new Step("gamemode") {
            @Override
            public void run(Player target) {
                if (gameMode != null && target.getGameMode() != gameMode) {
                    target.setGameMode(gameMode);
                }
            }
        }, player);
        if (teleport) {
            clean &= apply(new Step("teleport") {
                @Override
                public void run(Player target) {
                    if (hasLocation()) {
                        target.teleport(location);
                    }
                }
            }, player);
        }
        if (!clean) {
            Debug.log(DebugCategory.PLAYER, "State of {} was restored with at least one failed step",
                    player.getName());
        }
        return clean;
    }

    private static boolean apply(Step step, Player player) {
        try {
            step.run(player);
            return true;
        } catch (Throwable throwable) {
            Debug.error(DebugCategory.PLAYER, "Could not restore '" + step.name + "' for " + player.getName(),
                    throwable);
            return false;
        }
    }

    /** One named restoration step, so a failure can be reported precisely. */
    private abstract static class Step {
        private final String name;

        Step(String name) {
            this.name = name;
        }

        abstract void run(Player player);
    }

    public Collection<PotionEffect> effects() {
        return new ArrayList<PotionEffect>(effects);
    }
}
