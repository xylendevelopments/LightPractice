package gg.lightpractice.api;

import gg.lightpractice.cosmetic.Cosmetic;
import gg.lightpractice.match.Match;
import gg.lightpractice.model.CosmeticType;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Cosmetic registry, purchases and playback. */
public interface CosmeticService {

    Cosmetic get(String id);

    Collection<Cosmetic> cosmetics();

    List<Cosmetic> byType(CosmeticType type);

    List<CosmeticType> types();

    boolean isUnlocked(UUID uuid, String cosmeticId);

    /** Purchases a cosmetic with coins (or Vault money when configured), never twice. */
    boolean purchase(Player player, Cosmetic cosmetic);

    boolean equip(Player player, Cosmetic cosmetic);

    boolean unequip(Player player, CosmeticType type);

    String equipped(UUID uuid, CosmeticType type);

    /** Plays the killer cosmetics and kill message after a match death. */
    void applyKill(Match match, UUID killer, UUID victim);

    /** Plays victory cosmetics for the winners of a finished match. */
    void applyVictory(Match match, Collection<UUID> winners);

    void reload();
}
