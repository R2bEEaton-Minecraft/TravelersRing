package cc.spea.travelersring.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** The one place that actually moves a player. */
public final class Teleporter {
    private Teleporter() {
    }

    /**
     * Teleports and VERIFIES the player really arrived (another mod can cancel a dimension change). Returns false if
     * the player did not end up at the destination, in which case callers must not charge XP or change ring data.
     */
    public static boolean teleport(ServerPlayer player, Destination d) {
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        var fromLevel = player.serverLevel();
        double fx = player.getX();
        double fy = player.getY();
        double fz = player.getZ();
        try {
            player.teleportTo(d.level(), d.x(), d.y(), d.z(), d.yaw(), d.pitch());
        } catch (RuntimeException e) {
            return false;
        }
        boolean arrived = player.level() == d.level() && player.position().distanceToSqr(d.x(), d.y(), d.z()) < 4.0;
        if (!arrived) {
            return false;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.hurtMarked = true;
        fromLevel.playSound(null, fx, fy, fz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        d.level().playSound(null, d.x(), d.y(), d.z(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }
}
