package cc.spea.travelersring.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side tuning. File: {@code config/travelersring-common.toml}. Only the server's copy matters for gameplay;
 * the client is told costs/targets by the server, so clients never need matching values.
 */
public final class RingConfig {
    public static final ForgeConfigSpec SPEC;

    public enum CostMode {
        /** Every teleport costs {@code flat_cost} XP points regardless of distance or dimension. */
        FLAT,
        /** Cost scales with horizontal distance (see README for cross-dimension rules). */
        PER_BLOCK
    }

    // [xp]
    public static final ForgeConfigSpec.EnumValue<CostMode> COST_MODE;
    public static final ForgeConfigSpec.IntValue FLAT_COST;
    public static final ForgeConfigSpec.DoubleValue PER_BLOCK_COST;
    public static final ForgeConfigSpec.IntValue PER_BLOCK_MAX_COST;
    public static final ForgeConfigSpec.DoubleValue NETHERITE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue NETHER_STAR_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_FREE;

    // [usage]
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BASE_DIMENSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NETHERITE_DIMENSIONS;
    public static final ForgeConfigSpec.BooleanValue NETHER_STAR_ANY_DIMENSION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NETHER_STAR_DIMENSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED_DIMENSIONS;

    // [safety]
    public static final ForgeConfigSpec.IntValue SAFE_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue SAFE_SEARCH_VERTICAL;

    // [death]
    public static final ForgeConfigSpec.BooleanValue RETAIN_HELD_AND_ARMOR;
    public static final ForgeConfigSpec.BooleanValue RESPAWN_CHOICE;
    public static final ForgeConfigSpec.BooleanValue RING_KEEPS_ITSELF;

    // [loot]
    public static final ForgeConfigSpec.BooleanValue LOOT_ENABLED;
    public static final ForgeConfigSpec.DoubleValue LOOT_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOOT_TABLES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOOT_DIMENSIONS;

    public static final List<String> DEFAULT_LOOT_TABLES = List.of(
            "minecraft:chests/simple_dungeon",
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/stronghold_corridor",
            "minecraft:chests/stronghold_crossing",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/desert_pyramid",
            "minecraft:chests/jungle_temple",
            "minecraft:chests/woodland_mansion",
            "minecraft:chests/pillager_outpost",
            "minecraft:chests/ancient_city",
            "minecraft:chests/ruined_portal",
            "minecraft:chests/shipwreck_treasure",
            "minecraft:chests/underwater_ruin_big",
            "minecraft:chests/trail_ruins_rare"
    );

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment(
                "What a teleport to spawn costs. Costs are in experience points, not levels.",
                "Returning to the saved spot, forgetting it, and picking a respawn point are always free."
        ).push("xp");
        COST_MODE = b.comment("FLAT: every teleport costs flat_cost. PER_BLOCK: cost grows with distance.")
                .defineEnum("cost_mode", CostMode.FLAT);
        FLAT_COST = b.comment("FLAT mode: XP points per teleport with the base ring.")
                .defineInRange("flat_cost", 10, 0, 1_000_000);
        PER_BLOCK_COST = b.comment(
                        "PER_BLOCK mode: XP points per block of distance (X/Z only, height is ignored).",
                        "10.0 is very expensive: 1000 blocks costs 10,000 points. Something like 0.05 is more playable.",
                        "Between dimensions, both points are converted to Overworld coordinates first (Nether = x8).")
                .defineInRange("per_block_cost", 10.0, 0.0, 1_000_000.0);
        PER_BLOCK_MAX_COST = b.comment("PER_BLOCK mode: most a teleport can cost before the ring multiplier. 0 = no limit.")
                .defineInRange("per_block_max_cost", 0, 0, 1_000_000_000);
        NETHERITE_MULTIPLIER = b.comment("Cost multiplier for the Netherite ring.")
                .defineInRange("netherite_multiplier", 0.5, 0.0, 100.0);
        NETHER_STAR_MULTIPLIER = b.comment("Cost multiplier for the Nether Star ring.")
                .defineInRange("nether_star_multiplier", 0.5, 0.0, 100.0);
        CREATIVE_FREE = b.comment("Players in creative mode don't pay XP.").define("creative_free", true);
        b.pop();

        b.comment("Where each ring works. Dimensions are written like minecraft:overworld.").push("usage");
        BASE_DIMENSIONS = b.comment("Dimensions the base ring works in.")
                .defineListAllowEmpty("base_dimensions", List.of("minecraft:overworld"), RingConfig::isResourceLocation);
        NETHERITE_DIMENSIONS = b.comment("Dimensions the Netherite ring works in.")
                .defineListAllowEmpty("netherite_dimensions", List.of("minecraft:overworld", "minecraft:the_nether"), RingConfig::isResourceLocation);
        NETHER_STAR_ANY_DIMENSION = b.comment("The Nether Star ring works in every dimension, modded ones included.")
                .define("nether_star_any_dimension", true);
        NETHER_STAR_DIMENSIONS = b.comment("Dimensions the Nether Star ring works in. Only used if nether_star_any_dimension is false.")
                .defineListAllowEmpty("nether_star_dimensions", List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), RingConfig::isResourceLocation);
        BLOCKED_DIMENSIONS = b.comment("Dimensions where no ring works. Overrides everything above.")
                .defineListAllowEmpty("blocked_dimensions", List.of(), RingConfig::isResourceLocation);
        b.pop();

        b.comment("Return points are checked again when you use them, in case the spot became unsafe.").push("safety");
        SAFE_SEARCH_RADIUS = b.comment("If the return point is blocked or dangerous, look this many blocks sideways for a safe spot. 0 = exact spot only.")
                .defineInRange("search_radius", 3, 0, 16);
        SAFE_SEARCH_VERTICAL = b.comment("How many blocks up and down to look.")
                .defineInRange("search_vertical", 4, 0, 32);
        b.pop();

        b.comment("What the Nether Star ring does when you die.").push("death");
        RETAIN_HELD_AND_ARMOR = b.comment(
                        "Wearing the ring when you die keeps the item in your selected hotbar slot, your offhand and your armor.",
                        "Everything else drops as usual.")
                .define("retain_held_and_armor", true);
        RING_KEEPS_ITSELF = b.comment(
                        "false: Curios decides what happens to the ring on death (it normally drops).",
                        "true: the ring is kept too. Other mods that change Curios drop rules still win.")
                .define("ring_keeps_itself", false);
        RESPAWN_CHOICE = b.comment("Wearing the ring when you die lets you pick any of your spawn points on the death screen, for free.")
                .define("respawn_choice", true);
        b.pop();

        b.comment(
                "Where the base ring turns up in chests.",
                "These are chest loot table ids like minecraft:chests/simple_dungeon, not structure ids.",
                "(The desert pyramid structure uses the loot table minecraft:chests/desert_pyramid.)",
                "You can use * as a wildcard, e.g. somemod:chests/*. Add a modded structure's chest table here to include it."
        ).push("loot");
        LOOT_ENABLED = b.comment("Add the ring to the loot tables below.").define("enabled", true);
        LOOT_CHANCE = b.comment("Chance (0.0 to 1.0) that an opened chest in the list holds a ring.")
                .defineInRange("chance", 0.05, 0.0, 1.0);
        LOOT_TABLES = b.comment("Loot tables that can contain the ring.")
                .defineListAllowEmpty("target_loot_tables", DEFAULT_LOOT_TABLES, RingConfig::isLootPattern);
        LOOT_DIMENSIONS = b.comment("Only add the ring to chests in these dimensions. Empty = any dimension.")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), RingConfig::isResourceLocation);
        b.pop();

        SPEC = b.build();
    }

    private RingConfig() {
    }

    private static boolean isResourceLocation(Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private static boolean isLootPattern(Object o) {
        if (!(o instanceof String s) || s.isBlank()) {
            return false;
        }
        return s.contains("*") || ResourceLocation.tryParse(s) != null;
    }

    public static Set<ResourceLocation> parseDimensions(List<? extends String> raw) {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : raw) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) {
                out.add(rl);
            }
        }
        return out;
    }

    public static List<String> lootPatterns() {
        return new ArrayList<>(LOOT_TABLES.get());
    }
}
