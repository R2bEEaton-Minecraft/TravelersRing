package cc.spea.travelersring;

import cc.spea.travelersring.item.RingItem;
import cc.spea.travelersring.ring.RingTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TravelersRing.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TravelersRing.MOD_ID);

    public static final RegistryObject<RingItem> TRAVELERS_RING = ITEMS.register("travelers_ring",
            () -> new RingItem(RingTier.BASE, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<RingItem> TRAVELERS_RING_NETHERITE = ITEMS.register("travelers_ring_netherite",
            () -> new RingItem(RingTier.NETHERITE, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<RingItem> TRAVELERS_RING_NETHER_STAR = ITEMS.register("travelers_ring_nether_star",
            () -> new RingItem(RingTier.NETHER_STAR, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> NETHER_STAR_UPGRADE_TEMPLATE = ITEMS.register("nether_star_upgrade_smithing_template",
            ModItems::createNetherStarTemplate);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.travelersring"))
            .icon(() -> TRAVELERS_RING.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(TRAVELERS_RING.get());
                output.accept(TRAVELERS_RING_NETHERITE.get());
                output.accept(TRAVELERS_RING_NETHER_STAR.get());
                output.accept(NETHER_STAR_UPGRADE_TEMPLATE.get());
            })
            .build());

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

    private static Item createNetherStarTemplate() {
        // Same construction vanilla uses for its Netherite Upgrade template. Slot icons: the ring placeholder
        // (base slot) and vanilla's ingot silhouette (additions slot).
        return new SmithingTemplateItem(
                Component.translatable("item.travelersring.smithing_template.nether_star_upgrade.applies_to").withStyle(ChatFormatting.BLUE),
                Component.translatable("item.travelersring.smithing_template.nether_star_upgrade.ingredients").withStyle(ChatFormatting.BLUE),
                Component.translatable("upgrade.travelersring.nether_star_upgrade").withStyle(ChatFormatting.GRAY),
                Component.translatable("item.travelersring.smithing_template.nether_star_upgrade.base_slot_description"),
                Component.translatable("item.travelersring.smithing_template.nether_star_upgrade.additions_slot_description"),
                List.of(new ResourceLocation(TravelersRing.MOD_ID, "item/empty_slot_ring")),
                List.of(new ResourceLocation("item/empty_slot_ingot")));
    }
}
