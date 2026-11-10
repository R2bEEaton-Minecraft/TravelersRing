package cc.spea.travelersring.gametest;

import cc.spea.travelersring.net.MenuData;
import cc.spea.travelersring.ring.RingData;
import cc.spea.travelersring.ring.RingLocator;
import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.ring.ReturnPoint;
import cc.spea.travelersring.service.CostCalculator;
import cc.spea.travelersring.service.RingService;
import cc.spea.travelersring.service.RingService.Action;
import cc.spea.travelersring.service.RingService.Result;
import cc.spea.travelersring.service.RingService.Status;
import cc.spea.travelersring.service.Targets;
import cc.spea.travelersring.service.XpHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static cc.spea.travelersring.gametest.TestSupport.check;
import static cc.spea.travelersring.gametest.TestSupport.eq;
import static cc.spea.travelersring.gametest.TestSupport.run;

@GameTestHolder("travelersring")
@PrefixGameTestTemplate(false)
public class TeleportTests {
    private static final String OW_KEY = "spawn:minecraft:overworld";
    private static final String NETHER_KEY = "spawn:minecraft:the_nether";

    private static double dist(ServerPlayer p, BlockPos b) {
        return p.position().distanceTo(Vec3.atBottomCenterOf(b));
    }

    private static void stand(ServerPlayer p, BlockPos pos) {
        p.teleportTo((ServerLevel) p.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void base_ring_cycle_persists_across_logout(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            BlockPos spawn = start.offset(3, 0, 3);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                stand(p, start);
                p.setRespawnPosition(Level.OVERWORLD, spawn, 0f, true, false);
                TestSupport.equip(p, TestSupport.ring(0));
                TestSupport.setXp(p, 100);

                Result first = RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY);
                eq(Status.SUCCESS, first.status(), "first teleport");
                check(dist(p, spawn) < 1.6, "player is at the recorded spawn, not " + p.position());
                eq(90, XpHelper.totalPoints(p), "flat 10 XP points charged once");
                Optional<ReturnPoint> saved = RingData.getReturn(TestSupport.equipped(p));
                check(saved.isPresent(), "departure saved on the individual ring");
                eq("minecraft:overworld", saved.get().dimension().toString(), "saved dimension");
                check(Math.abs(saved.get().x() - (start.getX() + 0.5)) < 0.01 && Math.abs(saved.get().z() - (start.getZ() + 0.5)) < 0.01, "saved coordinates");

                // duplicate / replayed request: must not charge or overwrite the saved departure
                Result dup = RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY);
                eq(Status.ALREADY_SAVED, dup.status(), "duplicate teleport request");
                eq(90, XpHelper.totalPoints(p), "no second charge");
                eq(saved, RingData.getReturn(TestSupport.equipped(p)), "saved departure not overwritten");

                // logout / login: ring data (and Curios slot) must survive being written to disk and re-read
                server.getPlayerList().remove(p);
                ServerPlayer p2 = TestSupport.mockPlayer(level, id);
                p = p2;
                ItemStack after = TestSupport.equipped(p2);
                check(RingTier.fromStack(after).isPresent(), "ring still equipped after relog: " + after);
                eq(saved, RingData.getReturn(after), "saved return survived logout");
                eq(90, XpHelper.totalPoints(p2), "XP survived relog");

                Result back = RingService.perform(p2, RingSource.CURIOS, Action.RETURN, "");
                eq(Status.SUCCESS, back.status(), "return after relog");
                check(dist(p2, start) < 1.6, "player returned to departure point, at " + p2.position());
                check(RingData.getReturn(TestSupport.equipped(p2)).isEmpty(), "saved location cleared after successful return");
                eq(90, XpHelper.totalPoints(p2), "return is free");
                eq(Status.NOTHING_SAVED, RingService.perform(p2, RingSource.CURIOS, Action.RETURN, "").status(), "duplicate return request");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void delete_is_free_and_works_anywhere(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                ItemStack ring = TestSupport.ring(0);
                RingData.setReturn(ring, new ReturnPoint(Level.OVERWORLD.location(), 1, 2, 3, 0, 0));
                TestSupport.equip(p, ring);
                TestSupport.setXp(p, 50);
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.DELETE, "").status(), "delete");
                check(RingData.getReturn(TestSupport.equipped(p)).isEmpty(), "saved location deleted");
                eq(50, XpHelper.totalPoints(p), "delete is free");
                eq(Status.NOTHING_SAVED, RingService.perform(p, RingSource.CURIOS, Action.DELETE, "").status(), "delete again");

                // corrupt data can still be deleted
                ItemStack bad = TestSupport.ring(0);
                bad.getOrCreateTagElement(RingData.ROOT).putString(RingData.RETURN, "garbage");
                TestSupport.equip(p, bad);
                eq(Status.SAVED_UNREADABLE, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "return with unreadable data");
                eq(Status.ALREADY_SAVED, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY).status(), "teleport blocked while unreadable data present");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.DELETE, "").status(), "delete unreadable data");
                check(!RingData.hasRawReturn(TestSupport.equipped(p)), "unreadable data gone");
            } finally {
                TestSupport.cleanup(level.getServer(), id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void failed_attempts_change_nothing(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            BlockPos spawn = start.offset(3, 0, 3);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                stand(p, start);
                p.setRespawnPosition(Level.OVERWORLD, spawn, 0f, true, false);
                TestSupport.equip(p, TestSupport.ring(0));

                TestSupport.setXp(p, 9);
                Vec3 before = p.position();
                Result r = RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY);
                eq(Status.NOT_ENOUGH_XP, r.status(), "insufficient XP");
                eq(9, XpHelper.totalPoints(p), "XP untouched on failure");
                eq(before, p.position(), "player did not move");
                check(RingData.getReturn(TestSupport.equipped(p)).isEmpty(), "nothing saved on failure");

                TestSupport.setXp(p, 100);
                eq(Status.INVALID_TARGET, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, NETHER_KEY).status(), "base ring cannot target the Nether");
                eq(Status.INVALID_TARGET, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, "spawn:somemod:fake").status(), "unknown dimension key");
                eq(Status.INVALID_TARGET, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, "../../etc").status(), "garbage key");
                eq(Status.INVALID_TARGET, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, "").status(), "empty key");
                eq(Status.NO_RING, RingService.perform(p, RingSource.MAIN_HAND, Action.TELEPORT_SPAWN, OW_KEY).status(), "claiming a hand ring that isn't there");
                eq(Status.NOTHING_SAVED, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "return with nothing saved");
                eq(100, XpHelper.totalPoints(p), "XP untouched");
                eq(before, p.position(), "player did not move");

                TestSupport.equip(p, ItemStack.EMPTY);
                eq(Status.NO_RING, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY).status(), "no ring equipped");

                // dead players cannot act
                TestSupport.equip(p, TestSupport.ring(0));
                p.hurt(level.damageSources().genericKill(), 10000f);
                eq(Status.NOT_ALIVE, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY).status(), "dead player");
            } finally {
                TestSupport.cleanup(level.getServer(), id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void held_ring_works_and_stores_data_on_that_stack(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                stand(p, start);
                p.setRespawnPosition(Level.OVERWORLD, start.offset(3, 0, 3), 0f, true, false);
                p.setItemInHand(InteractionHand.OFF_HAND, TestSupport.ring(0));
                TestSupport.setXp(p, 30);
                eq(Status.SUCCESS, RingService.perform(p, RingSource.OFF_HAND, Action.TELEPORT_SPAWN, OW_KEY).status(), "teleport with held (offhand) ring");
                check(RingData.getReturn(p.getItemInHand(InteractionHand.OFF_HAND)).isPresent(), "return stored on the held stack");
                eq(20, XpHelper.totalPoints(p), "cost");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.OFF_HAND, Action.RETURN, "").status(), "return with held ring");
                check(dist(p, start) < 1.6, "back at start");
            } finally {
                TestSupport.cleanup(level.getServer(), id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void missing_bed_falls_back_to_world_spawn(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            BlockPos bedFoot = TestSupport.bed(level, start.offset(2, 0, 0), Direction.EAST);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                stand(p, start);
                p.setRespawnPosition(Level.OVERWORLD, bedFoot, 0f, false, false);
                TestSupport.equip(p, TestSupport.ring(0));
                TestSupport.setXp(p, 100);
                List<Targets.Target> t = Targets.compute(p, RingTier.BASE);
                eq(1, t.size(), "base ring offers one target");
                check(t.get(0).valid() && !t.get(0).fallback(), "bed present: real bed target");
                check(t.get(0).block().equals(bedFoot), "target is the bed");

                // bed broken: the entry falls back to the world spawn instead of failing or stranding the player
                TestSupport.place(level, bedFoot, Blocks.AIR);
                TestSupport.place(level, bedFoot.east(), Blocks.AIR);
                t = Targets.compute(p, RingTier.BASE);
                check(t.get(0).valid() && t.get(0).fallback(), "bed gone: falls back to world spawn");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY).status(), "teleport to world spawn fallback");
                BlockPos ws = level.getSharedSpawnPos();
                double h = Math.hypot(p.getX() - (ws.getX() + 0.5), p.getZ() - (ws.getZ() + 0.5));
                check(h < 8, "player is near the world spawn (" + ws + "), at " + p.position());
            } finally {
                TestSupport.cleanup(level.getServer(), id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void return_hazards_never_charge_or_clear(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos away = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, away, 2, Blocks.STONE, 4);
            BlockPos site = away.offset(20, 0, 0); // the old departure site
            TestSupport.platform(level, site, 6, Blocks.STONE, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                stand(p, away);
                ItemStack ring = TestSupport.ring(0);
                RingData.setReturn(ring, new ReturnPoint(Level.OVERWORLD.location(), site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0f, 0f));
                TestSupport.equip(p, ring);
                TestSupport.setXp(p, 40);

                // 1) exact spot filled with stone + lava around: should relocate to a nearby safe spot
                TestSupport.place(level, site, Blocks.STONE);
                TestSupport.place(level, site.above(), Blocks.STONE);
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "blocked exact spot -> nearby safe spot");
                check(p.position().distanceTo(Vec3.atBottomCenterOf(site)) <= 6, "landed near the old site, at " + p.position());
                check(level.noCollision(p), "player is not inside blocks");
                check(RingData.getReturn(TestSupport.equipped(p)).isEmpty(), "cleared after successful return");

                // 2) completely enclosed return site: must fail, keep the data, and not move or charge
                stand(p, away);
                ItemStack ring2 = TestSupport.ring(0);
                RingData.setReturn(ring2, new ReturnPoint(Level.OVERWORLD.location(), site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 0f, 0f));
                TestSupport.equip(p, ring2);
                for (int dx = -9; dx <= 9; dx++) {
                    for (int dy = -1; dy <= 8; dy++) {
                        for (int dz = -9; dz <= 9; dz++) {
                            TestSupport.place(level, site.offset(dx, dy, dz), Blocks.STONE);
                        }
                    }
                }
                Vec3 before = p.position();
                Result enclosed = RingService.perform(p, RingSource.CURIOS, Action.RETURN, "");
                eq(Status.DESTINATION_UNAVAILABLE, enclosed.status(), "enclosed return site");
                eq(before, p.position(), "player did not move");
                check(RingData.getReturn(TestSupport.equipped(p)).isPresent(), "saved location KEPT after a failed return");
                eq(40, XpHelper.totalPoints(p), "no charge");

                // 3) lava at the site and nothing safe nearby
                // 4) saved dimension no longer exists
                ItemStack ring3 = TestSupport.ring(0);
                RingData.setReturn(ring3, new ReturnPoint(new net.minecraft.resources.ResourceLocation("travelersring", "gone"), 0, 70, 0, 0, 0));
                TestSupport.equip(p, ring3);
                eq(Status.DESTINATION_UNAVAILABLE, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "missing dimension");
                check(RingData.getReturn(TestSupport.equipped(p)).isPresent(), "saved location kept for missing dimension");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.DELETE, "").status(), "can still delete it");

                // 5) return point far outside the world border is rejected, not teleported into the void
                ItemStack ring4 = TestSupport.ring(0);
                RingData.setReturn(ring4, new ReturnPoint(Level.OVERWORLD.location(), 1.0e9, 70, 0, 0, 0));
                TestSupport.equip(p, ring4);
                eq(Status.DESTINATION_UNAVAILABLE, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "outside world border");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty")
    public static void menu_data_reflects_server_state(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                stand(p, start);
                p.setRespawnPosition(Level.OVERWORLD, start.offset(3, 0, 3), 0f, true, false);
                TestSupport.equip(p, TestSupport.ring(0));
                TestSupport.setXp(p, 7);
                CostCalculator.Settings s = CostCalculator.Settings.fromConfig();
                RingLocator.RingHandle h = RingLocator.find(p, RingSource.CURIOS).orElseThrow();
                MenuData m = RingService.buildMenu(p, h, s);
                check(m.usableHere() && m.saved() == null && m.targets().size() == 1, "fresh ring: one target, usable");
                eq(10, m.targets().get(0).cost(), "displayed cost");
                eq(7, m.playerXp(), "displayed XP");
                check(!m.costsWaived(), "survival pays");

                TestSupport.setXp(p, 50);
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY).status(), "teleport");
                m = RingService.buildMenu(p, RingLocator.find(p, RingSource.CURIOS).orElseThrow(), s);
                check(m.saved() != null && m.targets().isEmpty(), "with a saved return the menu offers return/delete, not a second teleport");
            } finally {
                TestSupport.cleanup(level.getServer(), id);
            }
        });
    }

    // ---- Nether (first upgrade) ------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void netherite_ring_uses_charged_anchor_for_half_price(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel nether = TestSupport.nether(server);
            check(nether != null, "the Nether exists in the test world");
            BlockPos owStart = helper.absolutePos(new BlockPos(2, 1, 2));
            TestSupport.platform(level, owStart, 4, Blocks.STONE, 4);
            BlockPos owSpawn = owStart.offset(3, 0, 3);
            BlockPos nStart = new BlockPos(300, 90, 300);
            TestSupport.platform(nether, nStart, 5, Blocks.NETHERRACK, 6);
            BlockPos anchor = nStart.offset(3, 0, 3);
            nether.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 2), 3);

            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                stand(p, owStart);
                p.setRespawnPosition(Level.OVERWORLD, owSpawn, 0f, true, false);   // Overworld spawn
                p.setRespawnPosition(Level.NETHER, anchor, 0f, false, false);      // then Nether anchor (vanilla now forgets the Overworld one)
                TestSupport.equip(p, TestSupport.ring(1));
                TestSupport.setXp(p, 100);

                // In the Overworld the first upgrade offers only the Overworld spawn
                check(Targets.compute(p, RingTier.NETHERITE).size() == 1, "Overworld: only Overworld spawn");
                eq(Status.INVALID_TARGET, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, NETHER_KEY).status(), "cannot target the Nether anchor from the Overworld");

                // Go to the Nether (as a portal would) and use the ring there
                p.teleportTo(nether, nStart.getX() + 0.5, nStart.getY(), nStart.getZ() + 0.5, 0f, 0f);
                check(p.level() == nether, "player is in the Nether");
                List<Targets.Target> targets = Targets.compute(p, RingTier.NETHERITE);
                eq(2, targets.size(), "Nether: anchor + Overworld spawn");
                eq(NETHER_KEY, targets.get(0).key(), "anchor first");
                check(targets.get(0).valid(), "charged anchor is valid");
                check(targets.get(1).valid() && !targets.get(1).fallback(), "Overworld spawn (from per-dimension tracking) valid even though vanilla now points at the anchor");

                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, NETHER_KEY).status(), "teleport to the anchor");
                check(p.position().distanceTo(Vec3.atBottomCenterOf(anchor)) < 3, "next to the anchor, at " + p.position());
                eq(2, nether.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE), "ring teleport does NOT consume anchor charge");
                eq(95, XpHelper.totalPoints(p), "half price (5 of default 10)");
                eq("minecraft:the_nether", RingData.getReturn(TestSupport.equipped(p)).orElseThrow().dimension().toString(), "departure dimension saved");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "return within the Nether");
                check(p.position().distanceTo(Vec3.atBottomCenterOf(nStart)) < 1.6, "back at departure");

                // Nether -> Overworld spawn: cross-dimension, same flat cost
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY).status(), "teleport to Overworld spawn from the Nether");
                check(p.level() == level, "now in the Overworld");
                check(dist(p, owSpawn) < 1.6, "at the Overworld spawn");
                eq(90, XpHelper.totalPoints(p), "another 5 XP; no cross-dimension surcharge");
                eq("minecraft:the_nether", RingData.getReturn(TestSupport.equipped(p)).orElseThrow().dimension().toString(), "return point is in the Nether");

                // Return crosses back into the Nether (tier 1 allows both dimensions)
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "cross-dimension return");
                check(p.level() == nether && p.position().distanceTo(Vec3.atBottomCenterOf(nStart)) < 1.6, "back in the Nether at the departure point");
                eq(90, XpHelper.totalPoints(p), "return free");

                // Empty anchor: listed but unavailable; nothing changes
                nether.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 0), 3);
                targets = Targets.compute(p, RingTier.NETHERITE);
                check(!targets.get(0).valid(), "uncharged anchor is not valid");
                eq("anchor_empty", targets.get(0).resolution().failure().key, "reason");
                eq(Status.DESTINATION_UNAVAILABLE, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, NETHER_KEY).status(), "teleport to empty anchor refused");
                eq(90, XpHelper.totalPoints(p), "no charge");
                check(RingData.getReturn(TestSupport.equipped(p)).isEmpty(), "nothing saved");

                // Destroyed anchor
                nether.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
                targets = Targets.compute(p, RingTier.NETHERITE);
                check(!targets.get(0).valid(), "missing anchor is not valid");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void base_ring_is_overworld_only(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ServerLevel nether = TestSupport.nether(server);
            check(nether != null, "the Nether exists in the test world");
            BlockPos nStart = new BlockPos(500, 90, 500);
            TestSupport.platform(nether, nStart, 4, Blocks.NETHERRACK, 6);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                ItemStack ring = TestSupport.ring(0);
                RingData.setReturn(ring, new ReturnPoint(Level.OVERWORLD.location(), 0, 70, 0, 0, 0));
                TestSupport.equip(p, ring);
                TestSupport.setXp(p, 100);
                p.teleportTo(nether, nStart.getX() + 0.5, nStart.getY(), nStart.getZ() + 0.5, 0f, 0f);
                check(p.level() == nether, "in the Nether");
                eq(Status.NOT_ALLOWED_HERE, RingService.perform(p, RingSource.CURIOS, Action.TELEPORT_SPAWN, OW_KEY).status(), "base ring teleport in Nether");
                eq(Status.NOT_ALLOWED_HERE, RingService.perform(p, RingSource.CURIOS, Action.RETURN, "").status(), "base ring return in Nether");
                MenuData m = RingService.buildMenu(p, RingLocator.find(p, RingSource.CURIOS).orElseThrow(), CostCalculator.Settings.fromConfig());
                check(!m.usableHere(), "menu says not usable here");
                eq(100, XpHelper.totalPoints(p), "no charge");
                eq(Status.SUCCESS, RingService.perform(p, RingSource.CURIOS, Action.DELETE, "").status(), "delete still allowed so a ring is never stuck");
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }
}
