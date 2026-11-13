package cc.spea.travelersring.gametest;

import cc.spea.travelersring.ModItems;
import cc.spea.travelersring.config.RingConfig;
import cc.spea.travelersring.config.RingConfig.CostMode;
import cc.spea.travelersring.data.PlayerRingData;
import cc.spea.travelersring.loot.AddRingLootModifier;
import cc.spea.travelersring.net.MenuData;
import cc.spea.travelersring.ring.RingData;
import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.ring.ReturnPoint;
import cc.spea.travelersring.service.CostCalculator;
import cc.spea.travelersring.service.CostCalculator.Settings;
import cc.spea.travelersring.service.XpHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cc.spea.travelersring.gametest.TestSupport.check;
import static cc.spea.travelersring.gametest.TestSupport.eq;
import static cc.spea.travelersring.gametest.TestSupport.run;

@GameTestHolder("travelersring")
@PrefixGameTestTemplate(false)
public class LogicTests {

    @GameTest(template = "empty")
    public static void ring_data_roundtrips_through_nbt(GameTestHelper helper) {
        run(helper, () -> {
            ItemStack ring = TestSupport.ring(0);
            check(RingData.getReturn(ring).isEmpty(), "fresh ring has no return");
            ReturnPoint rp = new ReturnPoint(new ResourceLocation("minecraft:the_nether"), 12.5, 70.0, -33.25, 90f, 10f);
            RingData.setReturn(ring, rp);
            // simulate world save/load of the item (logout, chest, trading ...)
            ItemStack reloaded = ItemStack.of(ring.save(new CompoundTag()));
            eq(Optional.of(rp), RingData.getReturn(reloaded), "return point after NBT roundtrip");
            RingData.clearReturn(reloaded);
            check(RingData.getReturn(reloaded).isEmpty(), "cleared");
            check(reloaded.getTag() == null, "empty tag fully removed after clear");
            // corrupt data is not a return point but IS detectable so it can be deleted
            ItemStack bad = TestSupport.ring(0);
            bad.getOrCreateTagElement(RingData.ROOT).putString(RingData.RETURN, "garbage");
            check(RingData.getReturn(bad).isEmpty(), "corrupt data yields no return");
        });
    }

    @GameTest(template = "empty")
    public static void smithing_upgrades_preserve_saved_return_data(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            ReturnPoint rp = new ReturnPoint(new ResourceLocation("minecraft:overworld"), 1.5, 64, 2.5, 45f, 0f);

            // upgrade 1: vanilla netherite template + netherite ring base + netherite ingot
            ItemStack base = TestSupport.ring(0);
            RingData.setReturn(base, rp);
            Container c1 = new SimpleContainer(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), base, new ItemStack(Items.NETHERITE_INGOT));
            ItemStack up1 = assembleSmithing(level, c1);
            check(up1.is(ModItems.TRAVELERS_RING_NETHERITE.get()), "first upgrade yields the Netherite ring, got " + up1);
            eq(Optional.of(rp), RingData.getReturn(up1), "return preserved by upgrade 1");

            // upgrade 2: custom template + first-upgrade ring + nether star
            Container c2 = new SimpleContainer(new ItemStack(ModItems.NETHER_STAR_UPGRADE_TEMPLATE.get()), up1, new ItemStack(Items.NETHER_STAR));
            ItemStack up2 = assembleSmithing(level, c2);
            check(up2.is(ModItems.TRAVELERS_RING_NETHER_STAR.get()), "second upgrade yields the Nether Star ring, got " + up2);
            eq(Optional.of(rp), RingData.getReturn(up2), "return preserved by upgrade 2");

            // the base ring must NOT be accepted by the second upgrade (needs the first-upgrade ring)
            Container bad = new SimpleContainer(new ItemStack(ModItems.NETHER_STAR_UPGRADE_TEMPLATE.get()), TestSupport.ring(0), new ItemStack(Items.NETHER_STAR));
            check(findSmithing(level, bad) == null, "base ring must not skip straight to Nether Star tier");
            Container ingot = new SimpleContainer(new ItemStack(ModItems.NETHER_STAR_UPGRADE_TEMPLATE.get()), TestSupport.ring(1), new ItemStack(Items.NETHERITE_INGOT));
            check(findSmithing(level, ingot) == null, "second upgrade no longer takes a netherite ingot");
        });
    }

    private static Recipe<Container> findSmithing(ServerLevel level, Container c) {
        for (Recipe<Container> r : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            if (r.matches(c, level)) {
                return r;
            }
        }
        return null;
    }

    private static ItemStack assembleSmithing(ServerLevel level, Container c) {
        Recipe<Container> r = findSmithing(level, c);
        check(r != null, "a smithing recipe matches " + c.getItem(0) + " + " + c.getItem(1) + " + " + c.getItem(2));
        return r.assemble(c, level.registryAccess());
    }

    @GameTest(template = "empty")
    public static void template_duplication_recipe_exists(GameTestHelper helper) {
        run(helper, () -> {
            var rm = helper.getLevel().getRecipeManager();
            var recipe = rm.byKey(new ResourceLocation("travelersring", "nether_star_upgrade_smithing_template"));
            check(recipe.isPresent(), "template duplication recipe is registered");
            ItemStack result = recipe.get().getResultItem(helper.getLevel().registryAccess());
            check(result.is(ModItems.NETHER_STAR_UPGRADE_TEMPLATE.get()), "recipe result");
            eq(2, result.getCount(), "duplication makes 2 templates");
            List<String> ingredients = recipe.get().getIngredients().stream()
                    .map(i -> i.getItems()[0].getItem().toString()).sorted().toList();
            eq(List.of("diamond", "diamond", "diamond", "diamond", "diamond", "diamond", "diamond",
                    "end_stone", "nether_star_upgrade_smithing_template"), ingredients, "vanilla-style duplication: 7 diamonds + template + end stone");
        });
    }

    @GameTest(template = "empty")
    public static void config_defaults_match_brief(GameTestHelper helper) {
        run(helper, () -> {
            eq(CostMode.PER_BLOCK, RingConfig.COST_MODE.getDefault(), "default cost mode is PER_BLOCK (client request)");
            eq(4.0, RingConfig.PER_BLOCK_COST.getDefault(), "default = 4 XP points per block");
            eq(0.5, RingConfig.NETHERITE_MULTIPLIER.get(), "first upgrade halves cost");
            eq(0.5, RingConfig.NETHER_STAR_MULTIPLIER.get(), "second upgrade multiplier (provisional)");
            check(Set.of("minecraft:overworld").equals(Set.copyOf(RingConfig.BASE_DIMENSIONS.get())), "base ring is Overworld-only by default");
            check(RingConfig.NETHER_STAR_ANY_DIMENSION.get(), "nether star ring works in any dimension by default");
            check(RingConfig.LOOT_CHANCE.get() > 0 && RingConfig.LOOT_CHANCE.get() <= 0.1, "loot chance is low");
        });
    }

    @GameTest(template = "empty")
    public static void cost_flat_mode(GameTestHelper helper) {
        run(helper, () -> {
            Settings s = new Settings(CostMode.FLAT, 10, 10.0, 0, 0.5, 0.5);
            eq(10, CostCalculator.cost(s, RingTier.BASE, 0, 0, 1, 5000, 5000, 1), "base flat ignores distance");
            eq(5, CostCalculator.cost(s, RingTier.NETHERITE, 0, 0, 1, 5, 5, 1), "first upgrade halves");
            eq(5, CostCalculator.cost(s, RingTier.NETHER_STAR, 0, 0, 1, 5, 5, 1), "second upgrade provisional multiplier");
            eq(10, CostCalculator.cost(s, RingTier.BASE, 0, 0, 1, 5, 5, 8), "flat ignores dimension scale (no cross-dimension surcharge)");
            Settings odd = new Settings(CostMode.FLAT, 15, 0, 0, 0.5, 0.5);
            eq(8, CostCalculator.cost(odd, RingTier.NETHERITE, 0, 0, 1, 0, 0, 1), "halving rounds up, never silently free");
            Settings zero = new Settings(CostMode.FLAT, 0, 0, 0, 0.5, 0.5);
            eq(0, CostCalculator.cost(zero, RingTier.BASE, 0, 0, 1, 0, 0, 1), "zero cost allowed");
        });
    }

    @GameTest(template = "empty")
    public static void cost_per_block_mode_and_cross_dimension(GameTestHelper helper) {
        run(helper, () -> {
            Settings s = new Settings(CostMode.PER_BLOCK, 10, 2.0, 0, 0.5, 0.5);
            eq(200, CostCalculator.cost(s, RingTier.BASE, 0, 0, 1, 100, 0, 1), "100 blocks x 2 XP");
            eq(100, CostCalculator.cost(s, RingTier.NETHERITE, 0, 0, 1, 100, 0, 1), "first upgrade halves the computed cost");
            eq(100, CostCalculator.cost(s, RingTier.BASE, 30, 40, 1, 0, 0, 1), "3-4-5 triangle: 50 blocks x 2 XP; Y is ignored");
            // Nether (x8) point at (10,10) is Overworld-equivalent (80,80); Overworld spawn at (0,0)
            int expect = (int) Math.ceil(Math.sqrt(80.0 * 80 + 80.0 * 80) * 2.0);
            eq(expect, CostCalculator.cost(s, RingTier.BASE, 10, 10, 8, 0, 0, 1), "cross-dimension uses coordinate-scale-adjusted distance");
            Settings capped = new Settings(CostMode.PER_BLOCK, 10, 2.0, 300, 0.5, 0.5);
            eq(300, CostCalculator.cost(capped, RingTier.BASE, 0, 0, 1, 100000, 0, 1), "cap applies before tier multiplier");
            eq(150, CostCalculator.cost(capped, RingTier.NETHERITE, 0, 0, 1, 100000, 0, 1), "multiplier applies after cap");
            eq(0, CostCalculator.cost(s, RingTier.BASE, 5, 5, 1, 5, 5, 1), "no distance, no cost");
        });
    }

    @GameTest(template = "empty")
    public static void xp_points_math_is_exact(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer p = TestSupport.mockPlayer(level);
            try {
                // known vanilla totals: level 5 = 55 points, level 10 = 160, level 20 = 550, level 30 = 1395
                eq(55L, XpHelper.pointsForLevel(5), "points for level 5");
                eq(160L, XpHelper.pointsForLevel(10), "points for level 10");
                eq(550L, XpHelper.pointsForLevel(20), "points for level 20");
                eq(1395L, XpHelper.pointsForLevel(30), "points for level 30");
                for (int pts : new int[]{0, 1, 10, 54, 55, 56, 160, 549, 550, 1394, 1395, 5000, 20000}) {
                    TestSupport.setXp(p, pts);
                    eq(pts, XpHelper.totalPoints(p), "totalPoints after giving " + pts);
                }
                TestSupport.setXp(p, 100);
                check(XpHelper.trySpend(p, 10), "can spend 10 of 100");
                eq(90, XpHelper.totalPoints(p), "100 - 10");
                check(!XpHelper.trySpend(p, 91), "cannot overspend");
                eq(90, XpHelper.totalPoints(p), "failed spend changes nothing");
                check(XpHelper.trySpend(p, 90), "can spend exactly all");
                eq(0, XpHelper.totalPoints(p), "empty");
                eq(0, p.experienceLevel, "level 0 when empty");
                // 10 XP POINTS is not 10 levels
                TestSupport.setXp(p, XpHelper.pointsForLevel(10) > 0 ? 160 : 0);
                XpHelper.trySpend(p, 10);
                check(p.experienceLevel >= 8, "spending 10 points from level 10 must not drop 10 levels (level now " + p.experienceLevel + ")");
            } finally {
                TestSupport.cleanup(level.getServer(), p.getUUID());
            }
        });
    }

    @GameTest(template = "empty")
    public static void loot_pattern_matching(GameTestHelper helper) {
        run(helper, () -> {
            List<String> defaults = RingConfig.DEFAULT_LOOT_TABLES;
            ResourceLocation ow = new ResourceLocation("minecraft:overworld");
            check(AddRingLootModifier.shouldAdd(new ResourceLocation("minecraft:chests/simple_dungeon"), ow, defaults, Set.of(ow)), "dungeon chest is a target");
            check(!AddRingLootModifier.shouldAdd(new ResourceLocation("minecraft:chests/simple_dungeon"), new ResourceLocation("minecraft:the_nether"), defaults, Set.of(ow)), "nether chest excluded by dimension");
            check(!AddRingLootModifier.shouldAdd(new ResourceLocation("minecraft:chests/bastion_treasure"), ow, defaults, Set.of(ow)), "non-listed table is not a target");
            check(!AddRingLootModifier.shouldAdd(new ResourceLocation("minecraft:desert_pyramid"), ow, defaults, Set.of(ow)), "a STRUCTURE id is not a loot table id");
            check(AddRingLootModifier.matches("somemod:chests/crypt_big", "somemod:chests/*"), "glob suffix");
            check(AddRingLootModifier.matches("somemod:chests/crypt_big", "*:chests/crypt*"), "glob namespace + prefix");
            check(!AddRingLootModifier.matches("other:chests/crypt", "somemod:chests/*"), "glob namespace mismatch");
            check(AddRingLootModifier.shouldAdd(new ResourceLocation("a:b"), new ResourceLocation("x:y"), List.of("a:b"), Set.of()), "empty dimension set = any dimension");
            check(AddRingLootModifier.roll(0.01, 0.05) && !AddRingLootModifier.roll(0.5, 0.05), "chance roll");
        });
    }

    /** Rolls real loot tables through Forge's global loot modifier pipeline with the DEFAULT config (5%). */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void loot_modifier_injects_rings_at_configured_rate(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            int trials = 4000;
            int hitsTarget = countRings(level, "minecraft:chests/simple_dungeon", trials);
            int hitsOther = countRings(level, "minecraft:chests/bastion_treasure", trials);
            double expected = trials * RingConfig.LOOT_CHANCE.get();
            System.out.println("[travelersring-test] simple_dungeon rings=" + hitsTarget + "/" + trials + " (expected ~" + expected + "), bastion=" + hitsOther);
            check(hitsTarget > expected * 0.5 && hitsTarget < expected * 1.6, "target chest ring rate " + hitsTarget + "/" + trials + " should be near " + expected);
            eq(0, hitsOther, "rings in a non-target table");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void template_loot_in_end_city_chests(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            int trials = 2000;
            int hitsTarget = countItems(level, "minecraft:chests/end_city_treasure", trials, ModItems.NETHER_STAR_UPGRADE_TEMPLATE.get());
            int hitsOther = countItems(level, "minecraft:chests/simple_dungeon", trials, ModItems.NETHER_STAR_UPGRADE_TEMPLATE.get());
            double expected = trials * RingConfig.TEMPLATE_LOOT_CHANCE.get();
            System.out.println("[travelersring-test] end_city_treasure templates=" + hitsTarget + "/" + trials + " (expected ~" + expected + "), dungeon=" + hitsOther);
            check(hitsTarget > expected * 0.7 && hitsTarget < expected * 1.3, "end city template rate " + hitsTarget + "/" + trials + " should be near " + expected);
            eq(0, hitsOther, "templates in a non-target table");
        });
    }

    private static int countRings(ServerLevel level, String table, int trials) {
        return countItems(level, table, trials, ModItems.TRAVELERS_RING.get());
    }

    private static int countItems(ServerLevel level, String table, int trials, net.minecraft.world.item.Item item) {
        LootTable lt = level.getServer().getLootData().getLootTable(new ResourceLocation(table));
        int rings = 0;
        for (int i = 0; i < trials; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
            for (ItemStack s : lt.getRandomItems(params)) {
                if (s.is(item)) {
                    rings++;
                }
            }
        }
        return rings;
    }

    @GameTest(template = "empty")
    public static void saved_data_roundtrips(GameTestHelper helper) {
        run(helper, () -> {
            PlayerRingData d = new PlayerRingData();
            java.util.UUID id = java.util.UUID.randomUUID();
            PlayerRingData.Entry e = d.entry(id);
            e.spawns.put(new ResourceLocation("minecraft:overworld"), new PlayerRingData.SpawnRecord(new BlockPos(1, 2, 3), 90f, false, 77L));
            e.spawns.put(new ResourceLocation("mod:dim"), new PlayerRingData.SpawnRecord(new BlockPos(-5, 60, 8), 0f, true, 99L));
            e.retained.add(new PlayerRingData.RetainedStack(PlayerRingData.SlotKind.ARMOR, 2, new ItemStack(Items.IRON_CHESTPLATE)));
            e.retained.add(new PlayerRingData.RetainedStack(PlayerRingData.SlotKind.MAIN_HAND, 4, new ItemStack(Items.DIAMOND_SWORD)));
            e.swap = new PlayerRingData.SwapRecord(new ResourceLocation("minecraft:overworld"), new BlockPos(9, 9, 9), 12f, false);
            PlayerRingData back = PlayerRingData.load(d.save(new CompoundTag()));
            PlayerRingData.Entry b = back.peek(id);
            check(b != null, "entry reloaded");
            eq(e.spawns, b.spawns, "spawn records");
            eq(2, b.retained.size(), "retained stacks");
            check(ItemStack.matches(new ItemStack(Items.IRON_CHESTPLATE), b.retained.get(0).stack()), "retained stack contents");
            eq(PlayerRingData.SlotKind.MAIN_HAND, b.retained.get(1).kind(), "retained slot kind");
            eq(4, b.retained.get(1).index(), "retained slot index");
            eq(e.swap, b.swap, "swap record");
            // a no-spawn swap (pos == null) must roundtrip too
            e.swap = new PlayerRingData.SwapRecord(new ResourceLocation("minecraft:overworld"), null, 0f, false);
            eq(e.swap, PlayerRingData.load(d.save(new CompoundTag())).peek(id).swap, "null-pos swap record");
        });
    }

    @GameTest(template = "empty")
    public static void menu_packet_roundtrips(GameTestHelper helper) {
        run(helper, () -> {
            MenuData m = new MenuData(RingSource.MAIN_HAND, 2, true, new MenuData.SavedView(new ResourceLocation("minecraft:the_nether"), -10, 64, 300),
                    false, List.of(new MenuData.TargetView("spawn:minecraft:overworld", new ResourceLocation("minecraft:overworld"), 0, true, 1, 2, 3, true, "", 5, false),
                    new MenuData.TargetView("spawn:minecraft:the_end", new ResourceLocation("minecraft:the_end"), 1, false, 0, 0, 0, false, "no_spawn", 0, true)), 1234, false);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            m.write(buf);
            eq(m, MenuData.read(buf), "MenuData packet roundtrip");
            eq(0, buf.readableBytes(), "no trailing bytes");
        });
    }

    @GameTest(template = "empty")
    public static void every_packet_roundtrips_and_bad_values_are_harmless(GameTestHelper helper) {
        run(helper, () -> {
            FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
            cc.spea.travelersring.net.OpenMenuC2S.encode(new cc.spea.travelersring.net.OpenMenuC2S(RingSource.CURIOS), b);
            eq(RingSource.CURIOS, cc.spea.travelersring.net.OpenMenuC2S.decode(b).source(), "OpenMenuC2S");

            b = new FriendlyByteBuf(Unpooled.buffer());
            var action = new cc.spea.travelersring.net.RingActionC2S(RingSource.OFF_HAND, 2, "spawn:minecraft:the_nether");
            cc.spea.travelersring.net.RingActionC2S.encode(action, b);
            eq(action, cc.spea.travelersring.net.RingActionC2S.decode(b), "RingActionC2S");

            b = new FriendlyByteBuf(Unpooled.buffer());
            cc.spea.travelersring.net.ChooseRespawnC2S.encode(new cc.spea.travelersring.net.ChooseRespawnC2S("world"), b);
            eq("world", cc.spea.travelersring.net.ChooseRespawnC2S.decode(b).key(), "ChooseRespawnC2S");

            b = new FriendlyByteBuf(Unpooled.buffer());
            var offers = new cc.spea.travelersring.net.RespawnOffersS2C(List.of(
                    new cc.spea.travelersring.net.RespawnOptionView("default", new ResourceLocation("minecraft:overworld"), 6, false, 0, 0, 0, true, ""),
                    new cc.spea.travelersring.net.RespawnOptionView("spawn:minecraft:the_nether", new ResourceLocation("minecraft:the_nether"), 1, true, -4, 70, 99, false, "anchor_empty")));
            cc.spea.travelersring.net.RespawnOffersS2C.encode(offers, b);
            eq(offers, cc.spea.travelersring.net.RespawnOffersS2C.decode(b), "RespawnOffersS2C");

            b = new FriendlyByteBuf(Unpooled.buffer());
            cc.spea.travelersring.net.RespawnSelectedS2C.encode(new cc.spea.travelersring.net.RespawnSelectedS2C("default"), b);
            eq("default", cc.spea.travelersring.net.RespawnSelectedS2C.decode(b).key(), "RespawnSelectedS2C");

            // out-of-range enum values from a hostile client map to harmless defaults / null actions
            eq(RingSource.CURIOS, RingSource.byOrdinal(99), "bad source ordinal");
            eq(RingSource.CURIOS, RingSource.byOrdinal(-1), "negative source ordinal");
            check(cc.spea.travelersring.service.RingService.Action.byOrdinal(77) == null, "bad action ordinal is rejected");
            check(cc.spea.travelersring.service.RingService.Action.byOrdinal(-5) == null, "negative action ordinal is rejected");
        });
    }

    @GameTest(template = "empty")
    public static void rings_are_valid_for_the_curios_ring_slot(GameTestHelper helper) {
        run(helper, () -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer p = TestSupport.mockPlayer(level);
            try {
                var slot = new top.theillusivec4.curios.api.SlotContext("ring", p, 0, false, true);
                for (int tier = 0; tier <= 2; tier++) {
                    ItemStack ring = TestSupport.ring(tier);
                    check(top.theillusivec4.curios.api.CuriosApi.isStackValid(slot, ring), "tier " + tier + " ring is accepted by Curios' ring slot (curios:ring tag)");
                    check(!top.theillusivec4.curios.api.CuriosApi.isStackValid(new top.theillusivec4.curios.api.SlotContext("necklace", p, 0, false, true), ring), "rings are not valid in other slots");
                    var curio = top.theillusivec4.curios.api.CuriosApi.getCurio(ring);
                    check(curio.isPresent(), "tier " + tier + " ring exposes the Curios item capability");
                    var curioImpl = curio.resolve().orElseThrow();
                    check(!curioImpl.canEquipFromUse(slot), "right-click must not auto-equip (it opens the ring menu)");
                }
                check(!top.theillusivec4.curios.api.CuriosApi.isStackValid(slot, new ItemStack(Items.DIAMOND)), "a diamond is not a ring");
                // the player really has exactly the ring slot we assigned via data
                var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).resolve().orElseThrow();
                check(handler.getStacksHandler("ring").isPresent(), "player has a Curios ring slot");
                check(handler.getStacksHandler("ring").get().getSlots() >= 1, "at least one ring slot");
            } finally {
                TestSupport.cleanup(level.getServer(), p.getUUID());
            }
        });
    }

    @GameTest(template = "empty")
    public static void ring_tier_lookup(GameTestHelper helper) {
        run(helper, () -> {
            for (RingTier t : RingTier.values()) {
                eq(Optional.of(t), RingTier.fromStack(new ItemStack(t.item())), "tier lookup " + t);
            }
            check(RingTier.fromStack(new ItemStack(Items.DIAMOND)).isEmpty(), "non-ring");
            check(RingTier.fromStack(ItemStack.EMPTY).isEmpty(), "empty stack");
            check(Level.OVERWORLD.location().toString().equals("minecraft:overworld"), "sanity");
        });
    }
}
