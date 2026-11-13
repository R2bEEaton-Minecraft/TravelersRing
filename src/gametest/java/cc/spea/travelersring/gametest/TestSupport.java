package cc.spea.travelersring.gametest;

import cc.spea.travelersring.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared helpers for the in-world GameTests. */
final class TestSupport {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    static {
        // The teleport/menu tests check exact XP arithmetic written against a flat 10-point cost. Pin that in memory
        // for the test run so it doesn't depend on the shipped default (PER_BLOCK); the per-block maths is covered
        // separately by CostCalculator tests, and config_defaults_match_brief checks the real defaults.
        cc.spea.travelersring.config.RingConfig.COST_MODE.set(cc.spea.travelersring.config.RingConfig.CostMode.FLAT);
        cc.spea.travelersring.config.RingConfig.FLAT_COST.set(10);
    }

    private TestSupport() {
    }

    interface Body {
        void run() throws Exception;
    }

    /** Runs a test body; any assertion/exception becomes a GameTest failure with a readable message. */
    static void run(GameTestHelper helper, Body body) {
        try {
            body.run();
        } catch (GameTestAssertException e) {
            throw e;
        } catch (Throwable t) {
            t.printStackTrace();
            helper.fail("Unexpected " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return;
        }
        helper.succeed();
    }

    static void check(boolean cond, String message) {
        if (!cond) {
            throw new GameTestAssertException(message);
        }
    }

    static void eq(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new GameTestAssertException(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** A real ServerPlayer wired into the PlayerList (so respawn/relog paths work) that is NOT creative. */
    static ServerPlayer mockPlayer(ServerLevel level, UUID id) {
        MinecraftServer server = level.getServer();
        ServerPlayer p = new ServerPlayer(server, level, new GameProfile(id, "ring-test-" + COUNTER.incrementAndGet())) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // gives the Connection a real (in-memory) channel, as Forge's network hooks require
        server.getPlayerList().placeNewPlayer(connection, p);
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return p;
    }

    static ServerPlayer mockPlayer(ServerLevel level) {
        return mockPlayer(level, UUID.randomUUID());
    }

    static void cleanup(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            server.getPlayerList().remove(p);
        }
    }

    static void setXp(ServerPlayer p, int points) {
        p.experienceLevel = 0;
        p.experienceProgress = 0f;
        p.totalExperience = 0;
        p.giveExperiencePoints(points);
    }

    static void equip(ServerPlayer p, ItemStack ring) {
        CuriosApi.getCuriosInventory(p).resolve().orElseThrow()
                .getStacksHandler("ring").orElseThrow().getStacks().setStackInSlot(0, ring);
    }

    static ItemStack equipped(ServerPlayer p) {
        return CuriosApi.getCuriosInventory(p).resolve().orElseThrow()
                .getStacksHandler("ring").orElseThrow().getStacks().getStackInSlot(0);
    }

    static ItemStack ring(int tier) {
        return new ItemStack(switch (tier) {
            case 0 -> ModItems.TRAVELERS_RING.get();
            case 1 -> ModItems.TRAVELERS_RING_NETHERITE.get();
            default -> ModItems.TRAVELERS_RING_NETHER_STAR.get();
        });
    }

    /** A solid platform of the given radius under {@code stand}, with {@code headroom} blocks of air above. */
    static void platform(ServerLevel level, BlockPos stand, int radius, Block floor, int headroom) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlock(stand.offset(dx, -1, dz), floor.defaultBlockState(), 3);
                for (int dy = 0; dy < headroom; dy++) {
                    level.setBlock(stand.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Places a bed with its FOOT at {@code foot} facing {@code facing}. Returns the foot position (what vanilla records). */
    static BlockPos bed(ServerLevel level, BlockPos foot, Direction facing) {
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(foot.relative(facing), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return foot;
    }

    static void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
    }

    static List<ItemEntity> itemsAround(ServerLevel level, BlockPos center, double radius) {
        return new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius)));
    }

    static void discardAll(List<ItemEntity> items) {
        items.forEach(ItemEntity::discard);
    }

    static ServerLevel nether(MinecraftServer server) {
        return server.getLevel(Level.NETHER);
    }
}
