package cc.spea.travelersring.gametest;

import cc.spea.travelersring.data.PlayerRingData.SpawnRecord;
import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.service.CostCalculator;
import cc.spea.travelersring.service.Destination;
import cc.spea.travelersring.service.Destination.Kind;
import cc.spea.travelersring.service.Destination.Resolution;
import cc.spea.travelersring.service.RespawnChoice;
import cc.spea.travelersring.service.RingService;
import cc.spea.travelersring.service.RingService.Action;
import cc.spea.travelersring.service.RingService.Status;
import cc.spea.travelersring.service.SpawnResolver;
import cc.spea.travelersring.service.SpawnTracker;
import cc.spea.travelersring.service.Targets;
import cc.spea.travelersring.config.RingConfig.CostMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static cc.spea.travelersring.gametest.TestSupport.check;
import static cc.spea.travelersring.gametest.TestSupport.eq;
import static cc.spea.travelersring.gametest.TestSupport.run;

/**
 * Client report 2026-11-15: Enigmatic Legacy's Dimensional Anchor (a modded spawn block in the End) showed as
 * "The End: Bed", unavailable, "the bed is gone". Also: in-Nether trips cost 8x per block.
 */
@GameTestHolder("travelersring")
@PrefixGameTestTemplate(false)
public class ModdedSpawnTests {
    private static final ResourceLocation END = Level.END.location();
    private static final ResourceLocation EL_ANCHOR = new ResourceLocation("enigmaticlegacy", "end_anchor");

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void same_dimension_trips_cost_real_blocks_not_overworld_equivalent(GameTestHelper helper) {
        run(helper, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            ServerLevel nether = TestSupport.nether(server);
            BlockPos start = new BlockPos(-2000, 90, 2000);
            TestSupport.platform(nether, start, 2, Blocks.NETHERRACK, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(helper.getLevel(), id);
            try {
                p.teleportTo(nether, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                var s = new CostCalculator.Settings(CostMode.PER_BLOCK, 10, 4.0, 0, 0.5, 0.5);
                Destination anchor100 = new Destination(nether, start.getX() + 100.5, start.getY(), start.getZ() + 0.5, 0f, 0f, Kind.ANCHOR);
                eq(200, RingService.costTo(p, RingTier.NETHERITE, anchor100, s), "100 Nether blocks x 4 x 1/2 (was 1600)");
                eq(400, RingService.costTo(p, RingTier.BASE, anchor100, s), "base multiplier");
                // Cross-dimension is unchanged: Overworld-equivalent coordinates
                Destination owOrigin = new Destination(helper.getLevel(), 0.5, 64, 0.5, 0f, 0f, Kind.BED);
                double ox = (start.getX() + 0.5) * 8, oz = (start.getZ() + 0.5) * 8;
                int expected = (int) Math.ceil(Math.sqrt((ox - 0.5) * (ox - 0.5) + (oz - 0.5) * (oz - 0.5)) * 4.0 * 0.5 - 1e-9);
                eq(expected, RingService.costTo(p, RingTier.NETHERITE, owOrigin, s), "Nether -> Overworld still uses Overworld-equivalent distance");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    /** A spawn block that doesn't implement Forge's respawn hook. Lodestone stands in for a modded block here. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void a_modded_spawn_block_that_is_still_there_is_a_valid_destination(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel end = server.getLevel(Level.END);
            check(end != null, "the End exists");
            BlockPos base = new BlockPos(3000, 80, 3000);
            TestSupport.platform(end, base, 4, Blocks.END_STONE, 5);
            BlockPos block = base.offset(2, 0, 0);
            end.setBlock(block, Blocks.LODESTONE.defaultBlockState(), 3);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                p.setRespawnPosition(Level.END, block, 0f, false, false); // what a modded spawn block does
                SpawnRecord rec = SpawnTracker.get(p, END);
                eq(new ResourceLocation("minecraft", "lodestone"), rec.block(), "the block is remembered");

                Resolution r = SpawnResolver.resolveRecorded(server, Level.END, rec, true);
                check(r.isOk(), "valid while the block is there: " + (r.isOk() ? "" : r.failure().key));
                eq(Kind.SPAWN_BLOCK, r.kind(), "kind");
                eq(rec.block(), r.block(), "block id travels to the screen for its name");
                Destination d = r.destination();
                check(new Vec3(d.x(), d.y(), d.z()).distanceTo(Vec3.atBottomCenterOf(block)) < 4, "next to the block");
                check(!end.getBlockState(BlockPos.containing(d.x(), d.y(), d.z())).is(Blocks.LODESTONE), "not inside it");

                // Replaced by something else -> gone, named
                end.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
                r = SpawnResolver.resolveRecorded(server, Level.END, rec, true);
                eq("spawn_block_gone", r.isOk() ? "ok" : r.failure().key, "different block");
                eq(rec.block(), r.block(), "still labelled by the original block");

                // A record from before 1.0.3 (no block saved) on a block without a charge: not trusted
                end.setBlock(block, Blocks.LODESTONE.defaultBlockState(), 3);
                SpawnRecord legacy = new SpawnRecord(block, 0f, false, 0L);
                eq("spawn_block_missing", SpawnResolver.resolveRecorded(server, Level.END, legacy, true).failure().key, "legacy, no charge property");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    /** Real Enigmatic Legacy. Only meaningful with -PwithEnigmatic; passes trivially (and says so) otherwise. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void enigmatic_legacy_dimensional_anchor(GameTestHelper helper) {
        run(helper, () -> {
            if (!ModList.get().isLoaded("enigmaticlegacy")) {
                System.out.println("[travelersring] enigmatic_legacy_dimensional_anchor: Enigmatic Legacy not loaded, skipped");
                return;
            }
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel end = server.getLevel(Level.END);
            Block anchorBlock = BuiltInRegistries.BLOCK.get(EL_ANCHOR);
            BlockState fresh = anchorBlock.defaultBlockState();
            IntegerProperty charge = (IntegerProperty) fresh.getProperties().stream().filter(pr -> pr instanceof IntegerProperty && pr.getName().startsWith("charge")).findFirst().orElseThrow();

            BlockPos base = new BlockPos(3500, 80, -3500);
            TestSupport.platform(end, base, 5, Blocks.END_STONE, 5);
            BlockPos a1 = base.offset(3, 0, 0);
            end.setBlock(a1, fresh.setValue(charge, 2), 3);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                p.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, level.getSharedSpawnPos(), 0f, true, false);
                p.teleportTo(end, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0f, 0f);
                TestSupport.equip(p, TestSupport.ring(2));
                TestSupport.setXp(p, 100000);

                // 1) Set the spawn the way a player does: right-click the charged Dimensional Anchor with an empty hand
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                p.gameMode.useItemOn(p, end, ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(a1), Direction.UP, a1, false));
                eq(a1, p.getRespawnPosition(), "Enigmatic set the spawn");
                eq(EL_ANCHOR, SpawnTracker.get(p, END).block(), "recorded with its block id");

                Targets.Target t = Targets.compute(p, RingTier.NETHER_STAR).stream().filter(x -> x.key().equals(Targets.keyFor(END))).findFirst().orElseThrow();
                check(t.valid(), "End entry valid (was: 'the bed is gone'): " + (t.valid() ? "" : t.resolution().failure().key));
                eq(EL_ANCHOR, t.resolution().block(), "labelled as the Dimensional Anchor");

                // Teleport there from the Overworld
                p.teleportTo(level, 0.5, level.getSharedSpawnPos().getY() + 1, 0.5, 0f, 0f);
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, Targets.keyFor(END)).status(), "teleport to the Dimensional Anchor");
                check(p.level() == end && p.position().distanceTo(Vec3.atBottomCenterOf(a1)) < 4, "next to it, at " + p.position());
                eq(2, end.getBlockState(a1).getValue(charge), "no charge used");
                RingService.perform(p, RingSource.CURIOS, Action.DELETE, "");

                // 2) Empty anchor -> unavailable with a reason
                end.setBlock(a1, fresh.setValue(charge, 0), 3);
                t = Targets.compute(p, RingTier.NETHER_STAR).stream().filter(x -> x.key().equals(Targets.keyFor(END))).findFirst().orElseThrow();
                eq("spawn_block_empty", t.valid() ? "ok" : t.resolution().failure().key, "empty Dimensional Anchor");

                // 3) Charging a different one with an ender pearl counts (charge_counts_blocks default)
                BlockPos a2 = base.offset(-3, 0, 0);
                end.setBlock(a2, fresh.setValue(charge, 0), 3);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL, 4));
                p.gameMode.useItemOn(p, end, p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(a2), Direction.UP, a2, false));
                check(end.getBlockState(a2).getValue(charge) > 0, "charged with a pearl");
                SpawnTracker.flushAnchorChecks(server);
                // (Same game tick as the spawn set above, so the real spawn wins the tie here; check the record itself.)
                eq(a2, cc.spea.travelersring.data.PlayerRingData.get(server).peek(id).anchors.get(END).pos(), "charged Dimensional Anchor recorded for the ring");
                // In real play the charge comes later than the spawn set; age the spawn record to model that
                var entry = cc.spea.travelersring.data.PlayerRingData.get(server).peek(id);
                SpawnRecord old = entry.spawns.get(END);
                entry.spawns.put(END, new SpawnRecord(old.pos(), old.angle(), old.forced(), old.time() - 100, old.block()));
                eq(a2, SpawnTracker.getForRing(p, END).pos(), "newer charged anchor wins");
                t = Targets.compute(p, RingTier.NETHER_STAR).stream().filter(x -> x.key().equals(Targets.keyFor(END))).findFirst().orElseThrow();
                check(t.valid(), "and it's usable: " + (t.valid() ? "" : t.resolution().failure().key));

                // 4) Death-screen choice: respawn at the (real spawn) Dimensional Anchor via Enigmatic's own respawn code
                end.setBlock(a1, fresh.setValue(charge, 2), 3);
                p.setRespawnPosition(Level.OVERWORLD, level.getSharedSpawnPos(), 0f, true, false); // vanilla spawn elsewhere
                // ...with Enigmatic in the pack, the Nether Star ring must still keep the held item and armor
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                sword.setHoverName(net.minecraft.network.chat.Component.literal("el-sword"));
                ItemStack apple = new ItemStack(Items.APPLE, 3);
                apple.setHoverName(net.minecraft.network.chat.Component.literal("el-apple"));
                p.getInventory().selected = 0;
                p.getInventory().items.set(0, sword);
                p.getInventory().items.set(9, apple);
                p.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
                BlockPos deathAt = p.blockPosition();
                p.hurt(level.damageSources().genericKill(), 10000f);
                check(!p.isAlive(), "dead");
                check(RespawnChoice.choose(p, Targets.keyFor(END)), "Dimensional Anchor offered and accepted");
                ServerPlayer np = server.getPlayerList().respawn(p, false);
                check(np.level() == end, "respawned in the End, in " + np.level().dimension().location());
                check(np.position().distanceTo(Vec3.atBottomCenterOf(a1)) < 4, "next to the Dimensional Anchor, at " + np.position());
                eq(Level.OVERWORLD, np.getRespawnDimension(), "real spawn restored afterwards");
                check(np.getInventory().items.get(0).getHoverName().getString().equals("el-sword"), "held item kept alongside Enigmatic");
                check(np.getInventory().armor.get(3).is(Items.IRON_HELMET), "armor kept alongside Enigmatic");
                check(np.getInventory().items.stream().noneMatch(x -> x.getHoverName().getString().equals("el-apple")), "loose item not kept");
                var around = TestSupport.itemsAround(end, deathAt, 6);
                StringBuilder seen = new StringBuilder();
                around.forEach(e -> seen.append(BuiltInRegistries.ITEM.getKey(e.getItem().getItem())).append(' '));
                end.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(deathAt).inflate(6), e -> !(e instanceof ServerPlayer))
                        .forEach(e -> seen.append("| entity ").append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())).append(' '));
                System.out.println("[travelersring] left at the death spot with Enigmatic loaded: " + seen);
                TestSupport.discardAll(around);
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }
}
