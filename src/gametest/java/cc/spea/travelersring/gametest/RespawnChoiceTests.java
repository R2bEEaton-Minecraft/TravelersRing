package cc.spea.travelersring.gametest;

import cc.spea.travelersring.data.PlayerRingData;
import cc.spea.travelersring.net.RespawnOptionView;
import cc.spea.travelersring.service.RespawnChoice;
import cc.spea.travelersring.service.SpawnTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static cc.spea.travelersring.gametest.TestSupport.check;
import static cc.spea.travelersring.gametest.TestSupport.eq;
import static cc.spea.travelersring.gametest.TestSupport.run;

@GameTestHolder("travelersring")
@PrefixGameTestTemplate(false)
public class RespawnChoiceTests {
    private static final String OW = "spawn:minecraft:overworld";
    private static final String NETHER = "spawn:minecraft:the_nether";

    private record World(ServerLevel level, ServerLevel nether, BlockPos start, BlockPos owSpawn, BlockPos nStart, BlockPos anchor) {
    }

    private static World world(GameTestHelper helper, int netherOffset, int charge) {
        ServerLevel level = helper.getLevel();
        ServerLevel nether = TestSupport.nether(level.getServer());
        check(nether != null, "Nether exists");
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        TestSupport.platform(level, start, 4, Blocks.STONE, 4);
        BlockPos owSpawn = start.offset(3, 0, 3);
        BlockPos nStart = new BlockPos(1500 + netherOffset, 90, 1500);
        TestSupport.platform(nether, nStart, 5, Blocks.NETHERRACK, 6);
        BlockPos anchor = nStart.offset(2, 0, 2);
        nether.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, charge), 3);
        return new World(level, nether, start, owSpawn, nStart, anchor);
    }

    /** Overworld forced spawn, then Nether anchor (so vanilla's single spawn is the anchor), Nether Star ring equipped. */
    private static ServerPlayer player(World w, UUID id) {
        ServerPlayer p = TestSupport.mockPlayer(w.level(), id);
        p.teleportTo(w.level(), w.start().getX() + 0.5, w.start().getY(), w.start().getZ() + 0.5, 0f, 0f);
        p.setRespawnPosition(Level.OVERWORLD, w.owSpawn(), 0f, true, false);
        p.setRespawnPosition(Level.NETHER, w.anchor(), 0f, false, false);
        TestSupport.equip(p, TestSupport.ring(2));
        return p;
    }

    private static void kill(World w, ServerPlayer p) {
        p.hurt(w.level().damageSources().genericKill(), 10000f); // the real damage -> death path
        check(!p.isAlive(), "dead");
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void offer_lists_every_recorded_spawn_and_ignores_unknown_choices(GameTestHelper helper) {
        run(helper, () -> {
            World w = world(helper, 0, 3);
            MinecraftServer server = w.level().getServer();
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(w, id);
            try {
                check(!RespawnChoice.choose(p, OW), "a LIVING player's choose request is ignored");
                kill(w, p);
                check(RespawnChoice.hasOffer(p), "equipped Nether Star ring at death => offer");
                List<RespawnOptionView> o = RespawnChoice.buildOptions(p);
                List<String> keys = o.stream().map(RespawnOptionView::key).toList();
                check(keys.contains("default") && keys.contains(OW) && keys.contains(NETHER) && keys.contains("world"), "options: " + keys);
                check(o.stream().allMatch(RespawnOptionView::valid), "all options valid at the moment");
                check(!RespawnChoice.choose(p, "spawn:minecraft:the_end"), "unknown dimension key ignored");
                check(!RespawnChoice.choose(p, "spawn:travelersring:gone"), "absent dimension key ignored");
                check(!RespawnChoice.choose(p, "../../x"), "garbage key ignored");
                check(!RespawnChoice.choose(p, ""), "empty key ignored");
                PlayerRingData.Entry e = PlayerRingData.get(server).peek(id);
                check(e == null || e.swap == null, "rejected choices change nothing");
                eq(w.anchor(), p.getRespawnPosition(), "vanilla spawn untouched by rejected choices");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void choosing_overworld_respawns_there_then_restores_the_real_spawn(GameTestHelper helper) {
        run(helper, () -> {
            World w = world(helper, 100, 3);
            MinecraftServer server = w.level().getServer();
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(w, id);
            try {
                var recBefore = SpawnTracker.get(p, Level.NETHER.location());
                kill(w, p);
                check(RespawnChoice.choose(p, OW), "valid choice accepted");
                eq(w.owSpawn(), p.getRespawnPosition(), "dead player's spawn temporarily points at the choice");
                check(PlayerRingData.get(server).peek(id).swap != null, "real spawn saved (persisted) for restoration");

                ServerPlayer np = server.getPlayerList().respawn(p, false);
                check(np.level() == w.level(), "respawned in the Overworld");
                check(np.position().distanceTo(Vec3.atBottomCenterOf(w.owSpawn())) < 2.5, "respawned at the chosen spawn, at " + np.position());
                eq(Level.NETHER, np.getRespawnDimension(), "REAL spawn dimension restored (Nether anchor)");
                eq(w.anchor(), np.getRespawnPosition(), "REAL spawn position restored");
                check(PlayerRingData.get(server).peek(id).swap == null, "swap record cleared");
                eq(3, w.nether().getBlockState(w.anchor()).getValue(RespawnAnchorBlock.CHARGE), "choosing a different spawn does not drain the anchor");
                eq(recBefore, SpawnTracker.get(np, Level.NETHER.location()), "tracked records not reordered/overwritten by the choice");
                check(!RespawnChoice.hasOffer(np), "offer cleared");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void choosing_a_charged_anchor_respawns_in_the_nether_using_vanilla_rules(GameTestHelper helper) {
        run(helper, () -> {
            World w = world(helper, 200, 3);
            MinecraftServer server = w.level().getServer();
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(w, id);
            try {
                p.setRespawnPosition(Level.OVERWORLD, w.owSpawn(), 0f, true, false); // vanilla spawn = Overworld; anchor only tracked
                kill(w, p);
                check(RespawnChoice.choose(p, NETHER), "anchor choice accepted");
                ServerPlayer np = server.getPlayerList().respawn(p, false);
                check(np.level() == w.nether(), "respawned in the Nether");
                check(np.position().distanceTo(Vec3.atBottomCenterOf(w.anchor())) < 3, "next to the anchor, at " + np.position());
                eq(2, w.nether().getBlockState(w.anchor()).getValue(RespawnAnchorBlock.CHARGE), "vanilla anchor-respawn rule: one charge used (ring teleports never do this)");
                eq(Level.OVERWORLD, np.getRespawnDimension(), "real (Overworld) spawn restored");
                eq(w.owSpawn(), np.getRespawnPosition(), "real spawn position restored");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void a_choice_that_goes_bad_before_respawn_cannot_strand_the_player(GameTestHelper helper) {
        run(helper, () -> {
            World w = world(helper, 300, 1);
            MinecraftServer server = w.level().getServer();
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(w, id);
            try {
                kill(w, p);
                check(RespawnChoice.choose(p, NETHER), "choice valid when made");
                w.nether().setBlock(w.anchor(), Blocks.AIR.defaultBlockState(), 3);   // destroyed on the death screen
                ServerPlayer np = server.getPlayerList().respawn(p, false);
                check(np.isAlive() && np.level() == w.level().getServer().overworld(), "fell back to the Overworld world spawn instead of being stranded");
                eq(w.anchor(), np.getRespawnPosition(), "real spawn restored (still the original Nether anchor record)");
                check(PlayerRingData.get(server).peek(id).swap == null, "swap cleared");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void invalid_options_are_refused_and_default_choice_resets(GameTestHelper helper) {
        run(helper, () -> {
            World w = world(helper, 400, 0);   // anchor with NO charge
            MinecraftServer server = w.level().getServer();
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(w, id);
            try {
                p.setRespawnPosition(Level.OVERWORLD, w.owSpawn(), 0f, true, false);
                kill(w, p);
                RespawnOptionView nether = RespawnChoice.buildOptions(p).stream().filter(o -> o.key().equals(NETHER)).findFirst().orElseThrow();
                check(!nether.valid(), "uncharged anchor offered but marked invalid: " + nether.reason());
                eq("anchor_empty", nether.reason(), "reason");
                check(!RespawnChoice.choose(p, NETHER), "an invalid option cannot be chosen (server-side validation)");
                check(RespawnChoice.choose(p, "world"), "world spawn is always choosable");
                check(p.getRespawnPosition() == null, "world spawn choice clears the temporary spawn");
                check(RespawnChoice.choose(p, "default"), "default resets");
                eq(w.owSpawn(), p.getRespawnPosition(), "default restored the real spawn");
                check(PlayerRingData.get(server).peek(id).swap == null, "swap cleared by default choice");
                ServerPlayer np = server.getPlayerList().respawn(p, false);
                check(np.level() == w.level() && np.position().distanceTo(Vec3.atBottomCenterOf(w.owSpawn())) < 2.5, "default respawn = real spawn");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void world_spawn_choice_respawns_at_world_spawn_and_restores(GameTestHelper helper) {
        run(helper, () -> {
            World w = world(helper, 500, 2);
            MinecraftServer server = w.level().getServer();
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(w, id);
            try {
                kill(w, p);
                check(RespawnChoice.choose(p, "world"), "world spawn choice");
                ServerPlayer np = server.getPlayerList().respawn(p, false);
                BlockPos ws = server.overworld().getSharedSpawnPos();
                check(np.level() == server.overworld() && Math.hypot(np.getX() - ws.getX(), np.getZ() - ws.getZ()) < 20, "at world spawn: " + np.position());
                eq(w.anchor(), np.getRespawnPosition(), "real spawn restored after world-spawn choice");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void no_offer_without_equipped_nether_star_ring_and_swap_never_outlives_a_login(GameTestHelper helper) {
        run(helper, () -> {
            World w = world(helper, 600, 2);
            MinecraftServer server = w.level().getServer();
            UUID id = UUID.randomUUID();
            ServerPlayer p = player(w, id);
            try {
                TestSupport.equip(p, TestSupport.ring(1));
                kill(w, p);
                check(!RespawnChoice.hasOffer(p), "tier-1 ring: no respawn choice");
                check(!RespawnChoice.choose(p, OW), "so a forged choose request does nothing");
                server.getPlayerList().respawn(p, false);
            } finally {
                TestSupport.cleanup(server, id);
            }

            // A living player with a leftover swap (e.g. disconnected on the death screen) gets the real spawn back at login.
            UUID id2 = UUID.randomUUID();
            ServerPlayer q = player(w, id2);
            try {
                PlayerRingData data = PlayerRingData.get(server);
                data.entry(id2).swap = new PlayerRingData.SwapRecord(Level.NETHER.location(), w.anchor(), 0f, false);
                SpawnTracker.withoutRecording(() -> q.setRespawnPosition(Level.OVERWORLD, w.owSpawn(), 0f, true, false));
                RespawnChoice.onLogin(q);
                eq(w.anchor(), q.getRespawnPosition(), "login restored the real spawn");
                eq(Level.NETHER, q.getRespawnDimension(), "login restored the real dimension");
                check(data.peek(id2).swap == null, "swap cleared");
            } finally {
                TestSupport.cleanup(server, id2);
            }
        });
    }
}
