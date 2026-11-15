package cc.spea.travelersring.gametest;

import cc.spea.travelersring.data.PlayerRingData.SpawnRecord;
import cc.spea.travelersring.ring.RingData;
import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.ring.ReturnPoint;
import cc.spea.travelersring.service.RingService;
import cc.spea.travelersring.service.RingService.Action;
import cc.spea.travelersring.service.RingService.Status;
import cc.spea.travelersring.service.SpawnTracker;
import cc.spea.travelersring.service.Destination;
import cc.spea.travelersring.service.Targets;
import cc.spea.travelersring.service.XpHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static cc.spea.travelersring.gametest.TestSupport.check;
import static cc.spea.travelersring.gametest.TestSupport.eq;
import static cc.spea.travelersring.gametest.TestSupport.run;

@GameTestHolder("travelersring")
@PrefixGameTestTemplate(false)
public class SpawnTrackingTests {
    private static final ResourceLocation OW = Level.OVERWORLD.location();
    private static final ResourceLocation NETHER = Level.NETHER.location();

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void latest_spawn_is_tracked_per_dimension(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel nether = TestSupport.nether(server);
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            BlockPos bedA = TestSupport.bed(level, start.offset(-3, 0, 0), Direction.WEST);
            BlockPos bedB = TestSupport.bed(level, start.offset(3, 0, 0), Direction.EAST);
            BlockPos nStart = new BlockPos(700, 90, 700);
            TestSupport.platform(nether, nStart, 4, Blocks.NETHERRACK, 6);
            BlockPos anchor = nStart.offset(2, 0, 2);
            nether.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 1), 3);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                p.setRespawnPosition(Level.OVERWORLD, bedA, 0f, false, false);
                p.setRespawnPosition(Level.NETHER, anchor, 0f, false, false);
                p.setRespawnPosition(Level.OVERWORLD, bedB, 0f, false, false);   // newer Overworld spawn replaces bedA
                List<Map.Entry<ResourceLocation, SpawnRecord>> all = SpawnTracker.all(p);
                eq(2, all.size(), "exactly one record per dimension (not a list of every bed)");
                eq(bedB, SpawnTracker.get(p, OW).pos(), "latest Overworld spawn");
                eq(anchor, SpawnTracker.get(p, NETHER).pos(), "Nether anchor remembered although vanilla now points at the bed");
                eq(bedB, p.getRespawnPosition(), "vanilla's single spawn is the latest set");
                check(Targets.compute(p, RingTier.NETHER_STAR).size() == 2, "Nether Star ring offers one entry per tracked dimension");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void only_real_spawn_setting_events_are_recorded(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel nether = TestSupport.nether(server);
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            BlockPos nStart = new BlockPos(900, 90, 900);
            TestSupport.platform(nether, nStart, 4, Blocks.NETHERRACK, 6);
            BlockPos dryAnchor = nStart.offset(2, 0, 2);
            BlockPos wetAnchor = nStart.offset(-2, 0, 2);
            nether.setBlock(dryAnchor, Blocks.RESPAWN_ANCHOR.defaultBlockState(), 3); // charge 0
            nether.setBlock(wetAnchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 2), 3);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            Consumer<PlayerSetSpawnEvent> canceller = e -> {
                if (e.getNewSpawn() != null && e.getNewSpawn().equals(start.offset(0, 0, 3))) {
                    e.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerSetSpawnEvent.class, canceller);
            try {
                // 1) clicking a DRY anchor does nothing in vanilla -> nothing recorded
                var dryHit = new BlockHitResult(Vec3.atCenterOf(dryAnchor), Direction.UP, dryAnchor, false);
                nether.getBlockState(dryAnchor).use(nether, p, InteractionHand.MAIN_HAND, dryHit);
                check(SpawnTracker.get(p, NETHER) == null, "a click on an uncharged anchor is not a spawn event");

                // 2) clicking a CHARGED anchor really sets the spawn -> recorded
                var wetHit = new BlockHitResult(Vec3.atCenterOf(wetAnchor), Direction.UP, wetAnchor, false);
                nether.getBlockState(wetAnchor).use(nether, p, InteractionHand.MAIN_HAND, wetHit);
                SpawnRecord rec = SpawnTracker.get(p, NETHER);
                check(rec != null && rec.pos().equals(wetAnchor), "charged anchor interaction recorded");
                eq(2, nether.getBlockState(wetAnchor).getValue(RespawnAnchorBlock.CHARGE), "tracking/validation never consumes charge");

                // 3) an event cancelled by another mod is not recorded
                BlockPos vetoed = start.offset(0, 0, 3);
                p.setRespawnPosition(Level.OVERWORLD, vetoed, 0f, true, false);
                check(SpawnTracker.get(p, OW) == null, "cancelled spawn event not recorded");

                // 4) real bed sleeping (vanilla path: spawn is set even when it is too early to sleep)
                p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                BlockPos bed = TestSupport.bed(level, start.offset(1, 0, 1), Direction.EAST);
                level.setDayTime(1000);
                p.startSleepInBed(bed);
                SpawnRecord bedRec = SpawnTracker.get(p, OW);
                check(bedRec != null && bedRec.pos().equals(bed), "bed spawn recorded through the vanilla sleep path: " + bedRec);
                p.stopSleeping();

                // 5) /spawnpoint records a forced spawn
                BlockPos cmdPos = start.offset(-2, 0, -2);
                server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4),
                        "spawnpoint @s " + cmdPos.getX() + " " + cmdPos.getY() + " " + cmdPos.getZ());
                SpawnRecord cmdRec = SpawnTracker.get(p, OW);
                check(cmdRec != null && cmdRec.pos().equals(cmdPos) && cmdRec.forced(), "/spawnpoint recorded as forced: " + cmdRec);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(canceller);
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void current_vanilla_spawn_is_adopted_but_history_is_not_invented(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                // a spawn set while the mod was not tracking (simulated by suppressing the event handler)
                SpawnTracker.withoutRecording(() -> p.setRespawnPosition(Level.OVERWORLD, start, 0f, false, false));
                SpawnRecord r = SpawnTracker.get(p, OW);   // triggers sync with vanilla
                check(r != null && r.pos().equals(start), "current vanilla spawn adopted");
                eq(0L, r.time(), "adopted records are marked as having unknown time");
                check(SpawnTracker.get(p, NETHER) == null, "nothing invented for other dimensions");
                eq(1, SpawnTracker.all(p).size(), "only the one spawn vanilla still knows about");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nether_star_ring_teleports_to_any_recorded_spawn(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel nether = TestSupport.nether(server);
            ServerLevel end = server.getLevel(Level.END);
            check(nether != null && end != null, "Nether and End exist in the test world");
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            BlockPos bed = TestSupport.bed(level, start.offset(3, 0, 0), Direction.EAST);
            BlockPos nStart = new BlockPos(1100, 90, 1100);
            TestSupport.platform(nether, nStart, 4, Blocks.NETHERRACK, 6);
            BlockPos anchor = nStart.offset(2, 0, 2);
            nether.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 4), 3);
            BlockPos eStart = new BlockPos(1100, 80, 1100);
            TestSupport.platform(end, eStart, 4, Blocks.END_STONE, 4);

            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                p.setRespawnPosition(Level.OVERWORLD, bed, 0f, false, false);
                p.setRespawnPosition(Level.NETHER, anchor, 0f, false, false);
                p.setRespawnPosition(Level.END, eStart, 0f, true, false);                 // /spawnpoint-style forced spawn in the End
                SpawnTracker.record(p, new ResourceLocation("travelersring", "gone"), new BlockPos(0, 70, 0), 0f, false); // a dimension that no longer exists
                TestSupport.equip(p, TestSupport.ring(2));
                TestSupport.setXp(p, 100);

                List<Targets.Target> t = Targets.compute(p, RingTier.NETHER_STAR);
                eq(4, t.size(), "one per recorded dimension");
                long valid = t.stream().filter(Targets.Target::valid).count();
                eq(3L, valid, "Overworld bed, Nether anchor, End spawn are valid; the missing dimension is not");
                Targets.Target gone = t.stream().filter(x -> x.dimension().getNamespace().equals("travelersring")).findFirst().orElseThrow();
                eq("dimension_missing", gone.resolution().failure().key, "absent dimension reason");
                eq(Status.DESTINATION_UNAVAILABLE, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, gone.key()).status(), "absent dimension refused");
                eq(100, XpHelper.totalPoints(p), "refusal costs nothing");

                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, "spawn:minecraft:the_nether").status(), "Overworld -> Nether anchor");
                check(p.level() == nether, "now in the Nether");
                eq(95, XpHelper.totalPoints(p), "second upgrade multiplier (provisional 0.5)");
                eq(4, nether.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE), "no anchor charge used");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "Nether -> back");
                check(p.level() == level && p.position().distanceTo(Vec3.atBottomCenterOf(start)) < 1.6, "returned to the Overworld departure point");

                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, "spawn:minecraft:the_end").status(), "Overworld -> End spawn");
                check(p.level() == end, "now in the End");
                // used from the End (any dimension allowed for this tier), straight to the Overworld bed
                RingService.perform(p, RingSource.CURIOS, Action.DELETE, "");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, "spawn:minecraft:overworld").status(), "End -> Overworld bed");
                check(p.level() == level, "back in the Overworld");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    /**
     * Client report (2026-11-15): "the Nether isn't working ... it's using bed instead of the respawn anchor".
     * Charging an anchor with glowstone never sets the spawn, so nothing was recorded and the Nether entry showed as an
     * unavailable "Bed". Now the last CHARGED anchor counts (PDF: "last interacted Respawn Anchor, as long as it's charged").
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void charging_an_anchor_makes_it_the_netherite_rings_nether_destination(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel nether = TestSupport.nether(server);
            BlockPos nStart = new BlockPos(1200, 90, -1200);
            TestSupport.platform(nether, nStart, 5, Blocks.NETHERRACK, 6);
            BlockPos anchor = nStart.offset(3, 0, 3);
            nether.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState(), 3); // charge 0
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                p.teleportTo(nether, nStart.getX() + 0.5, nStart.getY(), nStart.getZ() + 0.5, 0f, 0f);
                TestSupport.equip(p, TestSupport.ring(1));
                TestSupport.setXp(p, 5000);

                // Before: no anchor known. The entry must say "Respawn Anchor" with an anchor-specific reason, not "Bed".
                Targets.Target before = Targets.compute(p, RingTier.NETHERITE).get(0);
                eq(Targets.keyFor(NETHER), before.key(), "Nether entry listed first");
                check(!before.valid(), "nothing to go to yet");
                eq(Destination.Kind.ANCHOR, before.resolution().kind(), "shown as a respawn anchor, not a bed");
                eq("no_anchor", before.resolution().failure().key, "reason");

                // Charge it with glowstone through the real interaction path (fires Forge's RightClickBlock)
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLOWSTONE, 4));
                var hit = new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false);
                p.gameMode.useItemOn(p, nether, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                eq(1, nether.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE), "anchor charged");
                check(p.getRespawnPosition() == null || !p.getRespawnPosition().equals(anchor), "charging does not set the spawn (vanilla)");
                SpawnTracker.flushAnchorChecks(server); // what the end-of-tick handler does

                Targets.Target after = Targets.compute(p, RingTier.NETHERITE).get(0);
                check(after.valid(), "charged anchor now offered: " + (after.valid() ? "" : after.resolution().failure().key));
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, Targets.keyFor(NETHER)).status(), "teleport to the charged anchor");
                check(p.position().distanceTo(Vec3.atBottomCenterOf(anchor)) < 3, "next to the anchor, at " + p.position());
                eq(1, nether.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE), "ring teleport does not use the charge");
                check(SpawnTracker.get(p, NETHER) == null, "charging is ring-only; not a death-respawn spawnpoint");

                // The Nether Star ring sees it too, from any dimension
                p.teleportTo(level, 0.5, level.getSharedSpawnPos().getY(), 0.5, 0f, 0f);
                check(Targets.compute(p, RingTier.NETHER_STAR).stream().anyMatch(t -> t.key().equals(Targets.keyFor(NETHER)) && t.valid()),
                        "Nether Star ring lists the charged anchor");

                // A click that adds no charge (empty hand on a dry anchor) records nothing new
                RingService.perform(p, RingSource.CURIOS, Action.DELETE, "");
                BlockPos dry = nStart.offset(-3, 0, 3);
                nether.setBlock(dry, Blocks.RESPAWN_ANCHOR.defaultBlockState(), 3);
                p.teleportTo(nether, nStart.getX() + 0.5, nStart.getY(), nStart.getZ() + 0.5, 0f, 0f);
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                var dryHit = new BlockHitResult(Vec3.atCenterOf(dry), Direction.UP, dry, false);
                p.gameMode.useItemOn(p, nether, ItemStack.EMPTY, InteractionHand.MAIN_HAND, dryHit);
                SpawnTracker.flushAnchorChecks(server);
                eq(anchor, SpawnTracker.getForRing(p, NETHER).pos(), "still the anchor that was charged");

                // Setting the spawn at a different charged anchor later wins (newest of the two)
                BlockPos later = nStart.offset(3, 0, -3);
                nether.setBlock(later, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 4), 3);
                var laterHit = new BlockHitResult(Vec3.atCenterOf(later), Direction.UP, later, false);
                p.gameMode.useItemOn(p, nether, ItemStack.EMPTY, InteractionHand.MAIN_HAND, laterHit);
                SpawnTracker.flushAnchorChecks(server);
                check(later.equals(p.getRespawnPosition()), "vanilla set the spawn at the later anchor");
                eq(later, SpawnTracker.getForRing(p, NETHER).pos(), "ring follows the newer spawnpoint (ties go to the real spawn)");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }
}
