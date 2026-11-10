package cc.spea.travelersring.service;

import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Exact XP-POINT accounting. Costs are in points (not levels), so we convert level+progress to a point total,
 * subtract, and convert back. We write the fields directly instead of calling {@code giveExperiencePoints(-n)}
 * so another mod cancelling/altering XP events cannot turn a teleport into a free one.
 */
public final class XpHelper {
    private XpHelper() {
    }

    public static int xpNeededForNextLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    /** Total points needed to reach the START of {@code level}. */
    public static long pointsForLevel(int level) {
        if (level <= 16) {
            return (long) level * level + 6L * level;
        }
        if (level <= 31) {
            return Math.round(2.5 * level * level - 40.5 * level + 360);
        }
        return Math.round(4.5 * level * level - 162.5 * level + 2220);
    }

    public static int levelForPoints(long points) {
        int level = 0;
        while (pointsForLevel(level + 1) <= points) {
            level++;
        }
        return level;
    }

    public static int totalPoints(Player player) {
        long total = pointsForLevel(player.experienceLevel)
                + Math.round(player.experienceProgress * xpNeededForNextLevel(player.experienceLevel));
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Spends {@code cost} points. Returns false (changing nothing) if the player cannot afford it. */
    public static boolean trySpend(ServerPlayer player, int cost) {
        if (cost <= 0) {
            return true;
        }
        int total = totalPoints(player);
        if (total < cost) {
            return false;
        }
        setTotalPoints(player, total - cost);
        player.totalExperience = Math.max(0, player.totalExperience - cost);
        player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
        return true;
    }

    private static void setTotalPoints(Player player, int points) {
        int level = levelForPoints(points);
        long into = points - pointsForLevel(level);
        player.experienceLevel = level;
        player.experienceProgress = Math.min(0.9999f, (float) into / (float) xpNeededForNextLevel(level));
    }
}
