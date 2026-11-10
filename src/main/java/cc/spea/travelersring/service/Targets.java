package cc.spea.travelersring.service;

import cc.spea.travelersring.data.PlayerRingData.SpawnRecord;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.service.Destination.Failure;
import cc.spea.travelersring.service.Destination.Kind;
import cc.spea.travelersring.service.Destination.Resolution;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The teleport destinations a given ring tier offers a given player RIGHT NOW. Always recomputed server-side;
 * the client's idea of the list is never trusted.
 */
public final class Targets {
    public static final String KEY_PREFIX = "spawn:";

    private Targets() {
    }

    /**
     * @param block    the bed/anchor block position for display (null if none)
     * @param fallback true if the Overworld entry fell back to the world spawn because the recorded spawn is invalid
     */
    public record Target(String key, ResourceLocation dimension, @Nullable BlockPos block, Resolution resolution,
                         boolean fallback, @Nullable Failure fallbackReason) {
        public boolean valid() {
            return resolution.isOk();
        }
    }

    public static String keyFor(ResourceLocation dim) {
        return KEY_PREFIX + dim;
    }

    public static List<Target> compute(ServerPlayer player, RingTier tier) {
        SpawnTracker.syncWithVanilla(player);
        ResourceLocation ow = Level.OVERWORLD.location();
        List<Target> out = new ArrayList<>();
        switch (tier) {
            case BASE -> out.add(overworldTarget(player));
            case NETHERITE -> {
                // In a respawn-anchor dimension (the Nether) the first upgrade also offers the latest anchor.
                ResourceKey<Level> cur = player.level().dimension();
                if (player.level().dimensionType().respawnAnchorWorks() && !cur.location().equals(ow)) {
                    out.add(recordedTarget(player, cur.location(), true));
                }
                out.add(overworldTarget(player));
            }
            case NETHER_STAR -> {
                boolean sawOverworld = false;
                for (Map.Entry<ResourceLocation, SpawnRecord> e : SpawnTracker.all(player)) {
                    if (e.getKey().equals(ow)) {
                        out.add(overworldTarget(player));
                        sawOverworld = true;
                    } else {
                        out.add(recordedTarget(player, e.getKey(), true));
                    }
                }
                if (!sawOverworld) {
                    out.add(overworldTarget(player));
                }
            }
        }
        return out;
    }

    public static Optional<Target> find(ServerPlayer player, RingTier tier, String key) {
        return compute(player, tier).stream().filter(t -> t.key().equals(key)).findFirst();
    }

    private static Target recordedTarget(ServerPlayer player, ResourceLocation dim, boolean requireCharge) {
        SpawnRecord rec = SpawnTracker.get(player, dim);
        if (rec == null) {
            return new Target(keyFor(dim), dim, null, Resolution.fail(Failure.NO_SPAWN, Kind.BED), false, null);
        }
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim);
        Resolution r = SpawnResolver.resolveRecorded(player.server, key, rec, requireCharge);
        return new Target(keyFor(dim), dim, rec.pos(), r, false, null);
    }

    /** The player's latest Overworld spawn if valid, else the Overworld world spawn. */
    private static Target overworldTarget(ServerPlayer player) {
        ResourceLocation ow = Level.OVERWORLD.location();
        SpawnRecord rec = SpawnTracker.get(player, ow);
        Failure why = null;
        if (rec != null) {
            Resolution r = SpawnResolver.resolveRecorded(player.server, Level.OVERWORLD, rec, true);
            if (r.isOk()) {
                return new Target(keyFor(ow), ow, rec.pos(), r, false, null);
            }
            why = r.failure();
        }
        Resolution world = SpawnResolver.resolveWorldSpawn(player.server);
        return new Target(keyFor(ow), ow, null, world, true, why);
    }
}
