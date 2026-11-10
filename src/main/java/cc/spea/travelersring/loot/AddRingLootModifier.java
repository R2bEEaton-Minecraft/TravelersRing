package cc.spea.travelersring.loot;

import cc.spea.travelersring.ModItems;
import cc.spea.travelersring.config.RingConfig;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Global loot modifier that adds a BASE Traveler's Ring to configured chest loot tables with a configured chance.
 * Matching is done on the chest's LOOT TABLE id (not a structure id) and the dimension the chest is in; both come from
 * {@link RingConfig}. The modifier is registered once in data ({@code data/travelersring/loot_modifiers/add_ring.json})
 * and has no hard-coded targets.
 */
public class AddRingLootModifier extends LootModifier {
    public static final Supplier<Codec<AddRingLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, AddRingLootModifier::new)));

    public AddRingLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!RingConfig.LOOT_ENABLED.get()) {
            return generatedLoot;
        }
        ResourceLocation table = context.getQueriedLootTableId();
        ResourceLocation dim = context.getLevel().dimension().location();
        if (shouldAdd(table, dim, RingConfig.lootPatterns(), RingConfig.parseDimensions(RingConfig.LOOT_DIMENSIONS.get()))
                && roll(context.getRandom().nextDouble(), RingConfig.LOOT_CHANCE.get())) {
            generatedLoot.add(new ItemStack(ModItems.TRAVELERS_RING.get()));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    // ---- pure helpers (unit-testable) -------------------------------------------------------------------------

    public static boolean roll(double random, double chance) {
        return random < chance;
    }

    /** @param dimensions allowed dimensions; an empty set means "any dimension". */
    public static boolean shouldAdd(ResourceLocation table, ResourceLocation dimension, List<String> patterns, Set<ResourceLocation> dimensions) {
        if (!dimensions.isEmpty() && !dimensions.contains(dimension)) {
            return false;
        }
        return matchesAny(table.toString(), patterns);
    }

    public static boolean matchesAny(String tableId, List<String> patterns) {
        for (String p : patterns) {
            if (matches(tableId, p)) {
                return true;
            }
        }
        return false;
    }

    /** Exact id match, or glob match when the pattern contains '*'. */
    public static boolean matches(String tableId, String pattern) {
        if (!pattern.contains("*")) {
            return tableId.equals(pattern);
        }
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.matches(regex.toString(), tableId);
    }
}
