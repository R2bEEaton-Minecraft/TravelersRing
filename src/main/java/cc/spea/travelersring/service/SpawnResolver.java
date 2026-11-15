package cc.spea.travelersring.service;

import cc.spea.travelersring.config.RingConfig;
import cc.spea.travelersring.data.PlayerRingData.SpawnRecord;
import cc.spea.travelersring.ring.ReturnPoint;
import cc.spea.travelersring.service.Destination.Failure;
import cc.spea.travelersring.service.Destination.Kind;
import cc.spea.travelersring.service.Destination.Resolution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Turns a recorded spawnpoint / the world spawn / a ring return point into a validated {@link Destination}.
 * Everything here is side-effect free: in particular it NEVER consumes a respawn-anchor charge (only a real vanilla
 * death-respawn does that).
 */
public final class SpawnResolver {
    private SpawnResolver() {
    }

    /**
     * @param requireAnchorCharge ring teleports require a charged anchor even for {@code /spawnpoint}-forced anchors;
     *                            the death-respawn offer mirrors vanilla (forced anchors respawn uncharged).
     */
    public static Resolution resolveRecorded(MinecraftServer server, ResourceKey<Level> dim, SpawnRecord rec, boolean requireAnchorCharge) {
        ServerLevel level = server.getLevel(dim);
        if (level == null) {
            return Resolution.fail(Failure.DIMENSION_MISSING, Kind.BED);
        }
        BlockPos pos = rec.pos();
        if (!level.getWorldBorder().isWithinBounds(pos) || pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return Resolution.fail(Failure.UNSAFE, Kind.BED);
        }
        BlockState state = level.getBlockState(pos); // loads the chunk
        Block block = state.getBlock();

        if (block instanceof RespawnAnchorBlock) {
            int charge = state.getValue(RespawnAnchorBlock.CHARGE);
            if (charge <= 0 && (requireAnchorCharge || !rec.forced())) {
                return Resolution.fail(Failure.ANCHOR_EMPTY, Kind.ANCHOR);
            }
            if (!RespawnAnchorBlock.canSetSpawn(level)) {
                return Resolution.fail(Failure.ANCHOR_UNUSABLE, Kind.ANCHOR);
            }
            Optional<Vec3> stand = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, pos);
            return stand.map(v -> Resolution.ok(towards(level, v, pos, Kind.ANCHOR)))
                    .orElseGet(() -> Resolution.fail(Failure.ANCHOR_OBSTRUCTED, Kind.ANCHOR));
        }
        if (block instanceof BedBlock) {
            if (!BedBlock.canSetSpawn(level)) {
                return Resolution.fail(Failure.BED_UNUSABLE, Kind.BED);
            }
            Optional<Vec3> stand = BedBlock.findStandUpPosition(EntityType.PLAYER, level, pos, state.getValue(BedBlock.FACING), rec.angle());
            return stand.map(v -> Resolution.ok(towards(level, v, pos, Kind.BED)))
                    .orElseGet(() -> Resolution.fail(Failure.BED_OBSTRUCTED, Kind.BED));
        }
        if (!rec.forced()) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            // Modded beds / respawn blocks that implement Forge's hook say where to stand themselves.
            Optional<Vec3> stand = state.getRespawnPosition(EntityType.PLAYER, level, pos, rec.angle(), null);
            if (stand.isPresent()) {
                return Resolution.ok(towards(level, stand.get(), pos, Kind.SPAWN_BLOCK)).withBlock(id);
            }
            // Modded spawn blocks that handle respawning their own way (e.g. Enigmatic Legacy's Dimensional Anchor):
            // accept the block the spawn was set on if it is still there. Old records (no block saved) only accept
            // anchor-like blocks, which carry a charge.
            boolean same = rec.block() != null ? rec.block().equals(id) : !state.isAir() && chargeOf(state).isPresent();
            if (same && !state.isAir()) {
                if (chargeOf(state).filter(c -> c <= 0).isPresent()) {
                    return Resolution.fail(Failure.SPAWN_BLOCK_EMPTY, Kind.SPAWN_BLOCK).withBlock(id);
                }
                int r = Math.max(2, RingConfig.SAFE_SEARCH_RADIUS.get());
                return SafeSpot.find(level, Vec3.atBottomCenterOf(pos.above()), r, 3)
                        .map(v -> Resolution.ok(towards(level, v, pos, Kind.SPAWN_BLOCK)).withBlock(id))
                        .orElseGet(() -> Resolution.fail(Failure.SPAWN_BLOCK_OBSTRUCTED, Kind.SPAWN_BLOCK).withBlock(id));
            }
            if (rec.block() != null && !(BuiltInRegistries.BLOCK.get(rec.block()) instanceof BedBlock)) {
                return Resolution.fail(Failure.SPAWN_BLOCK_GONE, Kind.SPAWN_BLOCK).withBlock(rec.block());
            }
            return Resolution.fail(Failure.SPAWN_BLOCK_MISSING, Kind.BED);
        }
        // Forced spawn (/spawnpoint): same rule vanilla uses.
        BlockState above = level.getBlockState(pos.above());
        if (block.isPossibleToRespawnInThis(state) && above.getBlock().isPossibleToRespawnInThis(above)) {
            return Resolution.ok(new Destination(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, rec.angle(), 0f, Kind.FORCED_SPAWN));
        }
        return Resolution.fail(Failure.UNSAFE, Kind.FORCED_SPAWN);
    }

    /** Overworld world spawn, nudged to a safe standing spot. */
    public static Resolution resolveWorldSpawn(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int r = Math.max(2, RingConfig.SAFE_SEARCH_RADIUS.get());
        Optional<Vec3> spot = SafeSpot.find(level, Vec3.atBottomCenterOf(spawn), r, 8);
        if (spot.isEmpty()) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            spot = SafeSpot.find(level, Vec3.atBottomCenterOf(top), r, 4);
        }
        float angle = level.getSharedSpawnAngle();
        return spot.map(v -> Resolution.ok(new Destination(level, v.x, v.y, v.z, angle, 0f, Kind.WORLD_SPAWN)))
                .orElseGet(() -> Resolution.fail(Failure.UNSAFE, Kind.WORLD_SPAWN));
    }

    /** A ring's saved departure point: exact spot if safe, otherwise the nearest safe spot, otherwise a failure. */
    public static Resolution resolveReturn(MinecraftServer server, ReturnPoint rp) {
        ServerLevel level = server.getLevel(rp.dimensionKey());
        if (level == null) {
            return Resolution.fail(Failure.DIMENSION_MISSING, Kind.RETURN_POINT);
        }
        Vec3 desired = new Vec3(rp.x(), rp.y(), rp.z());
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(desired))) {
            return Resolution.fail(Failure.UNSAFE, Kind.RETURN_POINT);
        }
        Optional<Vec3> spot = SafeSpot.find(level, desired, RingConfig.SAFE_SEARCH_RADIUS.get(), RingConfig.SAFE_SEARCH_VERTICAL.get());
        return spot.map(v -> Resolution.ok(new Destination(level, v.x, v.y, v.z, rp.yaw(), rp.pitch(), Kind.RETURN_POINT)))
                .orElseGet(() -> Resolution.fail(Failure.UNSAFE, Kind.RETURN_POINT));
    }

    /** The block's charge, if it has an integer property called "charges" (vanilla anchor) or "charge" (modded anchors). */
    public static Optional<Integer> chargeOf(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (p instanceof IntegerProperty ip && (p.getName().equals("charges") || p.getName().equals("charge"))) {
                return Optional.of(state.getValue(ip));
            }
        }
        return Optional.empty();
    }

    private static Destination towards(ServerLevel level, Vec3 stand, BlockPos block, Kind kind) {
        Vec3 d = Vec3.atBottomCenterOf(block).subtract(stand).normalize();
        float yaw = (float) Mth.wrapDegrees(Mth.atan2(d.z, d.x) * (180.0 / Math.PI) - 90.0);
        return new Destination(level, stand.x, stand.y, stand.z, yaw, 0f, kind);
    }
}
