package cc.spea.travelersring.loot;

import cc.spea.travelersring.ModItems;
import cc.spea.travelersring.config.RingConfig;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Adds a Nether Star Upgrade Smithing Template to the configured chest loot tables (End City chests by default),
 * the way vanilla hands out its own upgrade template. Tables and chance come from {@link RingConfig}.
 */
public class AddTemplateLootModifier extends LootModifier {
    public static final Supplier<Codec<AddTemplateLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, AddTemplateLootModifier::new)));

    public AddTemplateLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!RingConfig.TEMPLATE_LOOT_ENABLED.get()) {
            return generatedLoot;
        }
        String table = context.getQueriedLootTableId().toString();
        if (AddRingLootModifier.matchesAny(table, RingConfig.templateLootPatterns())
                && AddRingLootModifier.roll(context.getRandom().nextDouble(), RingConfig.TEMPLATE_LOOT_CHANCE.get())) {
            generatedLoot.add(new ItemStack(ModItems.NETHER_STAR_UPGRADE_TEMPLATE.get()));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
