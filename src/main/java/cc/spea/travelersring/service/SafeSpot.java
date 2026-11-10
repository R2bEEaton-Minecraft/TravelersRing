package cc.spea.travelersring.service;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Server-side "is a player safe standing here" checks and a small nearby-spot search. */
public final class SafeSpot {
    /** Max blocks of free fall we accept below a spot before calling it "mid-air". */
    private static final int MAX_DROP = 6;

    private SafeSpot() {
    }

    public static boolean isSafe(ServerLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y, z);
        if (y < level.getMinBuildHeight() + 1 || y > level.getMaxBuildHeight() - 3) {
            return false;
        }
        if (!level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }
        AABB box = EntityType.PLAYER.getDimensions().makeBoundingBox(x, y, z);
        if (!level.noCollision(box)) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (isHarmful(level.getBlockState(p))) {
                return false;
            }
        }
        // Need something to land on (or water) within MAX_DROP blocks, and it must not be harmful.
        for (int d = 0; d <= MAX_DROP; d++) {
            BlockPos below = feet.below(d);
            BlockState s = level.getBlockState(below);
            if (s.getFluidState().is(FluidTags.LAVA)) {
                return false;
            }
            boolean solid = !s.getCollisionShape(level, below).isEmpty();
            boolean water = s.getFluidState().is(FluidTags.WATER);
            if (solid || water) {
                return !isHarmful(s);
            }
        }
        return false;
    }

    private static boolean isHarmful(BlockState s) {
        if (s.getFluidState().is(FluidTags.LAVA) || s.is(BlockTags.FIRE)) {
            return true;
        }
        Block b = s.getBlock();
        if (b == Blocks.CACTUS || b == Blocks.MAGMA_BLOCK || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.WITHER_ROSE
                || b == Blocks.POWDER_SNOW || b == Blocks.COBWEB) {
            return true;
        }
        return b instanceof CampfireBlock && s.getValue(CampfireBlock.LIT);
    }

    /**
     * The exact desired point if safe, otherwise the nearest safe block-centre within the given radius/vertical range
     * (expanding outwards). Empty if nothing nearby is safe.
     */
    public static Optional<Vec3> find(ServerLevel level, Vec3 desired, int radius, int vertical) {
        level.getChunkAt(BlockPos.containing(desired)); // make sure the area is loaded
        if (isSafe(level, desired.x, desired.y, desired.z)) {
            return Optional.of(desired);
        }
        BlockPos base = BlockPos.containing(desired);
        for (int ring = 0; ring <= radius; ring++) {
            for (int step = 0; step <= vertical * 2; step++) {
                // vertical order: 0, +1, -1, +2, -2 ...
                int dy = (step % 2 == 1) ? (step + 1) / 2 : -(step / 2);
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }
                        double cx = base.getX() + dx + 0.5;
                        double cy = base.getY() + dy;
                        double cz = base.getZ() + dz + 0.5;
                        if (isSafe(level, cx, cy, cz)) {
                            return Optional.of(new Vec3(cx, cy, cz));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
