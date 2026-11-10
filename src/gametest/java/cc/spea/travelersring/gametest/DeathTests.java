package cc.spea.travelersring.gametest;

import cc.spea.travelersring.data.PlayerRingData;
import cc.spea.travelersring.service.DeathRetention;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cc.spea.travelersring.gametest.TestSupport.check;
import static cc.spea.travelersring.gametest.TestSupport.eq;
import static cc.spea.travelersring.gametest.TestSupport.run;

@GameTestHolder("travelersring")
@PrefixGameTestTemplate(false)
public class DeathTests {

    private static ItemStack named(net.minecraft.world.item.Item item, String name, int count) {
        ItemStack s = new ItemStack(item, count);
        s.setHoverName(Component.literal(name));
        return s;
    }

    /** Fills a recognisable kit: selected hotbar slot, offhand, 4 armor pieces, plus loose inventory items. */
    private static void kit(ServerPlayer p, int selectedSlot) {
        Inventory inv = p.getInventory();
        inv.items.set(selectedSlot, named(Items.DIAMOND_SWORD, "kit-sword", 1));
        inv.selected = selectedSlot;
        inv.items.set((selectedSlot + 1) % 9, named(Items.GOLDEN_AXE, "kit-unselected-hotbar", 1));
        inv.offhand.set(0, named(Items.SHIELD, "kit-shield", 1));
        inv.armor.set(0, named(Items.IRON_BOOTS, "kit-boots", 1));
        inv.armor.set(1, named(Items.IRON_LEGGINGS, "kit-legs", 1));
        inv.armor.set(2, named(Items.IRON_CHESTPLATE, "kit-chest", 1));
        inv.armor.set(3, named(Items.IRON_HELMET, "kit-helmet", 1));
        inv.items.set(20, named(Items.COBBLESTONE, "kit-cobble", 64));
        inv.items.set(21, named(Items.APPLE, "kit-apple", 5));
    }

    private static final List<String> HELD_AND_ARMOR = List.of("kit-sword", "kit-shield", "kit-boots", "kit-legs", "kit-chest", "kit-helmet");
    private static final List<String> LOOSE = List.of("kit-unselected-hotbar", "kit-cobble", "kit-apple");

    /** Total count of items with this name across the player's inventory, Curios, and item entities in the world. */
    private static int count(ServerPlayer p, List<ItemEntity> world, String name) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (List<ItemStack> list : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack s : list) {
                if (s.hasCustomHoverName() && s.getHoverName().getString().equals(name)) {
                    n += s.getCount();
                }
            }
        }
        for (ItemEntity e : world) {
            ItemStack s = e.getItem();
            if (s.hasCustomHoverName() && s.getHoverName().getString().equals(name)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int countRings(ServerPlayer p, List<ItemEntity> world, net.minecraft.world.item.Item ring) {
        int n = 0;
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().armor, p.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (s.is(ring)) {
                    n++;
                }
            }
        }
        for (ItemEntity e : world) {
            if (e.getItem().is(ring)) {
                n++;
            }
        }
        var inv = CuriosApi.getCuriosInventory(p).resolve();
        if (inv.isPresent()) {
            n += inv.get().findCurios(s -> s.is(ring)).size();
        }
        return n;
    }

    private record Outcome(ServerPlayer player, List<ItemEntity> worldItems) {
    }

    /** Kill the player and respawn them through vanilla's PlayerList.respawn (the real code path). */
    private static Outcome dieAndRespawn(ServerLevel level, ServerPlayer p, BlockPos where) {
        MinecraftServer server = level.getServer();
        p.hurt(level.damageSources().genericKill(), 10000f); // the real damage -> death path
        check(!p.isAlive(), "player died");
        List<ItemEntity> world = TestSupport.itemsAround(level, where, 12);
        ServerPlayer np = server.getPlayerList().respawn(p, false);
        return new Outcome(np, world);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nether_star_ring_keeps_only_held_items_and_armor(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            Outcome out = null;
            try {
                p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                TestSupport.equip(p, TestSupport.ring(2));
                kit(p, 3);
                Outcome o = out = dieAndRespawn(level, p, start);
                ServerPlayer np = o.player();
                Inventory inv = np.getInventory();

                for (String n : HELD_AND_ARMOR) {
                    eq(1, count(np, o.worldItems(), n), "'" + n + "' exists exactly once (no loss, no duplication)");
                }
                for (String n : HELD_AND_ARMOR) {
                    check(inventoryHas(np, n), "'" + n + "' retained in the new inventory");
                    check(o.worldItems().stream().noneMatch(e -> e.getItem().hasCustomHoverName() && e.getItem().getHoverName().getString().equals(n)),
                            "'" + n + "' not on the ground");
                }
                eq("kit-sword", inv.items.get(3).getHoverName().getString(), "held item back in its ORIGINAL hotbar slot");
                eq(3, inv.selected, "selected hotbar slot restored");
                eq("kit-shield", inv.offhand.get(0).getHoverName().getString(), "offhand restored");
                eq("kit-boots", inv.armor.get(0).getHoverName().getString(), "boots slot");
                eq("kit-legs", inv.armor.get(1).getHoverName().getString(), "leggings slot");
                eq("kit-chest", inv.armor.get(2).getHoverName().getString(), "chestplate slot");
                eq("kit-helmet", inv.armor.get(3).getHoverName().getString(), "helmet slot");
                for (String n : LOOSE) {
                    check(!inventoryHas(np, n), "'" + n + "' (not held/armor) was NOT retained");
                    check(count(np, o.worldItems(), n) >= 1, "'" + n + "' dropped normally");
                }
                eq(5, count(np, o.worldItems(), "kit-apple"), "dropped stack intact (no duplication)");
                eq(64, count(np, o.worldItems(), "kit-cobble"), "dropped stack intact (no duplication)");
                // The ring itself follows Curios' own rules: it must exist exactly once, wherever Curios put it
                eq(1, countRings(np, o.worldItems(), TestSupport.ring(2).getItem()), "ring neither lost nor duplicated");
                PlayerRingData.Entry e = PlayerRingData.get(server).peek(id);
                check(e == null || e.retained.isEmpty(), "retained list emptied after restore");
            } finally {
                if (out != null) {
                    TestSupport.discardAll(out.worldItems());
                }
                TestSupport.cleanup(server, id);
            }
        });
    }

    private static boolean inventoryHas(ServerPlayer p, String name) {
        Inventory inv = p.getInventory();
        for (List<ItemStack> list : List.of(inv.items, inv.armor, inv.offhand)) {
            for (ItemStack s : list) {
                if (s.hasCustomHoverName() && s.getHoverName().getString().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The ring itself is NOT handled by this mod's retention: Curios decides (default: drop; optional config: always keep). */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void ring_itself_follows_curios_drop_rules(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            var ringItem = TestSupport.ring(2).getItem();
            boolean old = cc.spea.travelersring.config.RingConfig.RING_KEEPS_ITSELF.get();
            try {
                for (boolean keeps : new boolean[]{false, true}) {
                    cc.spea.travelersring.config.RingConfig.RING_KEEPS_ITSELF.set(keeps);
                    UUID id = UUID.randomUUID();
                    ServerPlayer p = TestSupport.mockPlayer(level, id);
                    Outcome o = null;
                    try {
                        p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                        TestSupport.equip(p, TestSupport.ring(2));
                        kit(p, 1);
                        o = dieAndRespawn(level, p, start);
                        boolean stillEquipped = cc.spea.travelersring.ring.RingLocator.equipped(o.player()).isPresent();
                        boolean onGround = o.worldItems().stream().anyMatch(e -> e.getItem().is(ringItem));
                        eq(keeps, stillEquipped, "ring_keeps_itself=" + keeps + ": ring still equipped after death");
                        eq(!keeps, onGround, "ring_keeps_itself=" + keeps + ": ring dropped on the ground");
                        eq(1, countRings(o.player(), o.worldItems(), ringItem), "ring exactly once");
                        // either way, held items + armor are retained by this mod and nothing else is
                        for (String n : HELD_AND_ARMOR) {
                            check(inventoryHas(o.player(), n), n + " retained");
                        }
                        for (String n : LOOSE) {
                            check(!inventoryHas(o.player(), n), n + " not retained");
                        }
                    } finally {
                        if (o != null) {
                            TestSupport.discardAll(o.worldItems());
                        }
                        TestSupport.cleanup(server, id);
                    }
                }
            } finally {
                cc.spea.travelersring.config.RingConfig.RING_KEEPS_ITSELF.set(old);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void no_retention_unless_nether_star_ring_is_equipped(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            List<ItemEntity> cleanup = new ArrayList<>();
            // case: lower tier equipped / case: Nether Star ring only in the inventory / case: only held / case: none
            for (int scenario = 0; scenario < 4; scenario++) {
                UUID id = UUID.randomUUID();
                ServerPlayer p = TestSupport.mockPlayer(level, id);
                try {
                    p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                    switch (scenario) {
                        case 0 -> TestSupport.equip(p, TestSupport.ring(1));
                        case 1 -> p.getInventory().items.set(30, TestSupport.ring(2));
                        case 2 -> {
                            TestSupport.equip(p, ItemStack.EMPTY);
                            p.getInventory().items.set(0, TestSupport.ring(2)); // held in main hand, NOT equipped
                        }
                        default -> {
                        }
                    }
                    kit(p, 5);
                    Outcome o = dieAndRespawn(level, p, start);
                    cleanup.addAll(o.worldItems());
                    for (String n : HELD_AND_ARMOR) {
                        check(!inventoryHas(o.player(), n), "scenario " + scenario + ": '" + n + "' must NOT be retained");
                        eq(1, count(o.player(), o.worldItems(), n), "scenario " + scenario + ": '" + n + "' dropped exactly once");
                    }
                    PlayerRingData.Entry e = PlayerRingData.get(server).peek(id);
                    check(e == null || e.retained.isEmpty(), "scenario " + scenario + ": nothing retained");
                } finally {
                    TestSupport.cleanup(server, id);
                    TestSupport.discardAll(TestSupport.itemsAround(level, start, 12));
                }
            }
            TestSupport.discardAll(cleanup);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void keep_inventory_gamerule_is_unaffected_and_never_duplicates(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            Outcome out = null;
            var rule = level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
            boolean old = rule.get();
            try {
                rule.set(true, server);
                p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                TestSupport.equip(p, TestSupport.ring(2));
                kit(p, 0);
                Outcome o = out = dieAndRespawn(level, p, start);
                for (String n : HELD_AND_ARMOR) {
                    eq(1, count(o.player(), o.worldItems(), n), "'" + n + "' exactly once with keepInventory");
                }
                for (String n : LOOSE) {
                    check(inventoryHas(o.player(), n), "keepInventory keeps loose items too (vanilla behaviour): " + n);
                }
                check(o.worldItems().stream().noneMatch(e -> e.getItem().hasCustomHoverName()), "nothing dropped on the ground");
                PlayerRingData.Entry e = PlayerRingData.get(server).peek(id);
                check(e == null || e.retained.isEmpty(), "this mod retained nothing (vanilla already kept it all)");
                eq(1, countRings(o.player(), o.worldItems(), TestSupport.ring(2).getItem()), "ring exactly once");
            } finally {
                rule.set(old, server);
                if (out != null) {
                    TestSupport.discardAll(out.worldItems());
                }
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void vanishing_curse_still_destroys_and_empty_hands_are_fine(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            Outcome out = null;
            try {
                p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                TestSupport.equip(p, TestSupport.ring(2));
                kit(p, 2);
                p.getInventory().armor.get(3).enchant(Enchantments.VANISHING_CURSE, 1);   // helmet is cursed
                p.getInventory().offhand.set(0, ItemStack.EMPTY);                           // empty offhand
                Outcome o = out = dieAndRespawn(level, p, start);
                eq(0, count(o.player(), o.worldItems(), "kit-helmet"), "vanishing helmet is destroyed, not retained and not dropped");
                for (String n : List.of("kit-sword", "kit-boots", "kit-legs", "kit-chest")) {
                    check(inventoryHas(o.player(), n), n + " retained");
                    eq(1, count(o.player(), o.worldItems(), n), n + " exactly once");
                }
                check(o.player().getInventory().offhand.get(0).isEmpty(), "empty offhand stays empty (no phantom items)");
            } finally {
                if (out != null) {
                    TestSupport.discardAll(out.worldItems());
                }
                TestSupport.cleanup(server, id);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void restore_never_deletes_items_when_slots_are_taken(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
            TestSupport.platform(level, start, 4, Blocks.STONE, 4);
            UUID id = UUID.randomUUID();
            ServerPlayer p = TestSupport.mockPlayer(level, id);
            try {
                p.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0f, 0f);
                Inventory inv = p.getInventory();
                // Another mod already filled every slot (and the armor/offhand slots) before restore runs.
                for (int i = 0; i < inv.items.size(); i++) {
                    inv.items.set(i, new ItemStack(Items.DIRT, 64));
                }
                inv.offhand.set(0, new ItemStack(Items.DIRT));
                for (int i = 0; i < 4; i++) {
                    inv.armor.set(i, new ItemStack(Items.LEATHER_BOOTS));
                }
                PlayerRingData data = PlayerRingData.get(server);
                PlayerRingData.Entry e = data.entry(id);
                e.retained.add(new PlayerRingData.RetainedStack(PlayerRingData.SlotKind.MAIN_HAND, 4, named(Items.DIAMOND_SWORD, "kit-sword", 1)));
                e.retained.add(new PlayerRingData.RetainedStack(PlayerRingData.SlotKind.ARMOR, 2, named(Items.IRON_CHESTPLATE, "kit-chest", 1)));
                e.retained.add(new PlayerRingData.RetainedStack(PlayerRingData.SlotKind.OFF_HAND, 0, named(Items.SHIELD, "kit-shield", 1)));
                int restored = DeathRetention.restore(p);
                eq(3, restored, "all three entries processed");
                List<ItemEntity> world = TestSupport.itemsAround(level, start, 6);
                for (String n : List.of("kit-sword", "kit-chest", "kit-shield")) {
                    eq(1, count(p, world, n), n + " exists exactly once (dropped at the player's feet, never deleted)");
                }
                check(e.retained.isEmpty(), "retained list emptied");
                eq(0, DeathRetention.restore(p), "second restore is a no-op (no duplication)");
                TestSupport.discardAll(world);
            } finally {
                TestSupport.cleanup(server, id);
            }
        });
    }
}
