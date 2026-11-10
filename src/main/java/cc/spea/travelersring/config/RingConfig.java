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
                "XP cost of the 'teleport to spawn' action. XP is measured in experience POINTS (not levels).",
                "Returning to the saved location, deleting it, and choosing a respawn point are ALWAYS free.",
                "",
                "PROVISIONAL DEFAULT: the client first confirmed a flat configurable cost, but their latest message says",
                "'XP points ... try 10 per block to start'. That wording is unresolved, so the default is FLAT = 10 points.",
                "Switch cost_mode to PER_BLOCK to try the per-block reading."
        ).push("xp");
        COST_MODE = b.comment("FLAT = same cost every time (default). PER_BLOCK = cost scales with horizontal distance.")
                .defineEnum("cost_mode", CostMode.FLAT);
        FLAT_COST = b.comment("FLAT mode: XP points charged per teleport for the BASE ring. (10 points is only about 1 level at low levels.)")
                .defineInRange("flat_cost", 10, 0, 1_000_000);
        PER_BLOCK_COST = b.comment(
                        "PER_BLOCK mode: XP points per block of horizontal distance. 10.0 is the client's literal wording and is VERY steep",
                        "(1000 blocks = 10,000 points, roughly level 100). Lower it (e.g. 0.05) for a playable curve.",
                        "Cross-dimension distance: both points are converted to Overworld-equivalent coordinates using each",
                        "dimension's coordinate scale (Nether = x8), then the horizontal (X/Z) distance is measured. Y is ignored.")
                .defineInRange("per_block_cost", 10.0, 0.0, 1_000_000.0);
        PER_BLOCK_MAX_COST = b.comment("PER_BLOCK mode: cap on the base cost before tier multipliers. 0 = no cap.")
                .defineInRange("per_block_max_cost", 0, 0, 1_000_000_000);
        NETHERITE_MULTIPLIER = b.comment("Cost multiplier for the first upgrade (Netherite Traveler's Ring). The brief says XP costs are halved.")
                .defineInRange("netherite_multiplier", 0.5, 0.0, 100.0);
        NETHER_STAR_MULTIPLIER = b.comment(
                        "Cost multiplier for the second upgrade (Nether Star Traveler's Ring).",
                        "PROVISIONAL: the client did not specify this. Default matches the first upgrade (halved).")
                .defineInRange("nether_star_multiplier", 0.5, 0.0, 100.0);
        CREATIVE_FREE = b.comment("Creative-mode players never pay XP.").define("creative_free", true);
        b.pop();

        b.comment("Where each ring tier may be used. Dimension ids look like 'minecraft:overworld'.").push("usage");
        BASE_DIMENSIONS = b.comment("Dimensions the base ring works in (default: Overworld only).")
                .defineListAllowEmpty("base_dimensions", List.of("minecraft:overworld"), RingConfig::isResourceLocation);
        NETHERITE_DIMENSIONS = b.comment("Dimensions the Netherite ring (first upgrade) works in.")
                .defineListAllowEmpty("netherite_dimensions", List.of("minecraft:overworld", "minecraft:the_nether"), RingConfig::isResourceLocation);
        NETHER_STAR_ANY_DIMENSION = b.comment("Nether Star ring (second upgrade) works in every dimension, including modded ones.")
                .define("nether_star_any_dimension", true);
        NETHER_STAR_DIMENSIONS = b.comment("Only used when nether_star_any_dimension = false.")
                .defineListAllowEmpty("nether_star_dimensions", List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), RingConfig::isResourceLocation);
        BLOCKED_DIMENSIONS = b.comment("Dimensions where NO ring tier works (e.g. minigame or dungeon dimensions). Overrides everything above.")
                .defineListAllowEmpty("blocked_dimensions", List.of(), RingConfig::isResourceLocation);
        b.pop();

        b.comment("Teleport safety. Return points are re-checked at use time and may have become unsafe.").push("safety");
        SAFE_SEARCH_RADIUS = b.comment("If the exact saved return point is blocked or hazardous, search this many blocks horizontally for a safe spot. 0 = exact point only.")
                .defineInRange("search_radius", 3, 0, 16);
        SAFE_SEARCH_VERTICAL = b.comment("...and this many blocks up/down.")
                .defineInRange("search_vertical", 4, 0, 32);
        b.pop();

        b.comment("Nether Star ring death/respawn features.").push("death");
        RETAIN_HELD_AND_ARMOR = b.comment(
                        "If a Nether Star ring is equipped in a Curios slot when the player dies, keep ONLY the held items",
                        "(selected hotbar slot + offhand) and the 4 armor slots. Everything else follows normal rules.",
                        "The ring itself and other Curios follow Curios' own drop rules/settings, not this option.")
                .define("retain_held_and_armor", true);
        RING_KEEPS_ITSELF = b.comment(
                        "OFF (default, follows the project brief): Curios' own drop settings decide whether the equipped ring (and any other",
                        "Curio) stays after death - by default Curios drops them unless keepInventory is on.",
                        "ON: the Nether Star ring declares itself 'always keep' through Curios' own drop-rule API, which matches the client's",
                        "original PDF wording ('retain ... the ring itself'). Other mods/datapacks that override Curios drop rules still win.",
                        "Only the ring is affected; no other Curio is ever retained by this mod.")
                .define("ring_keeps_itself", false);
        RESPAWN_CHOICE = b.comment("A player who dies with a Nether Star ring equipped may pick any valid recorded spawnpoint on the death screen (free).")
                .define("respawn_choice", true);
        b.pop();

        b.comment(
                "Loot injection for the BASE ring.",
                "IMPORTANT: these are chest LOOT TABLE ids (e.g. 'minecraft:chests/simple_dungeon'), NOT structure ids",
                "(e.g. 'minecraft:desert_pyramid' is a structure; its chest uses the loot table 'minecraft:chests/desert_pyramid').",
                "The ids are not interchangeable. Wildcards are allowed: '*' matches any characters, e.g. 'somemod:chests/*'.",
                "No compatibility with arbitrary modded structures is promised; add their chest loot-table ids here to opt in."
        ).push("loot");
        LOOT_ENABLED = b.comment("Inject the ring into the loot tables below.").define("enabled", true);
        LOOT_CHANCE = b.comment("Chance (0.0 - 1.0) that each opened target chest receives a ring. Default 5%.")
                .defineInRange("chance", 0.05, 0.0, 1.0);
        LOOT_TABLES = b.comment("Chest loot-table ids (wildcards allowed) that can roll the ring.")
                .defineListAllowEmpty("target_loot_tables", DEFAULT_LOOT_TABLES, RingConfig::isLootPattern);
        LOOT_DIMENSIONS = b.comment("Only inject when the chest is in one of these dimensions (default: Overworld only). Empty list = any dimension.")
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
