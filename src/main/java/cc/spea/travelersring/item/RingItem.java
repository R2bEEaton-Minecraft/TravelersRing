package cc.spea.travelersring.item;

import cc.spea.travelersring.ring.RingData;
import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.service.RingService;
import net.minecraft.ChatFormatting;
import cc.spea.travelersring.config.RingConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

public class RingItem extends Item implements ICurioItem {
    private final RingTier tier;

    public RingItem(RingTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public RingTier tier() {
        return tier;
    }

    /** Right-click while held opens the ring menu. (Equip it in a Curios ring slot via the Curios inventory.) */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            RingService.openMenu(sp, hand == InteractionHand.MAIN_HAND ? RingSource.MAIN_HAND : RingSource.OFF_HAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Right-click is reserved for the ring menu, so do not let Curios auto-equip the ring on use. */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return false;
    }

    /**
     * Optional (config, default off): the Nether Star ring asks Curios to keep it on death. Expressed through Curios'
     * own drop-rule API so Curios settings / other mods' overrides keep working; nothing else is ever force-kept.
     */
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source, int lootingLevel, boolean recentlyHit, ItemStack stack) {
        if (tier == RingTier.NETHER_STAR && RingConfig.RING_KEEPS_ITSELF.get()) {
            return ICurio.DropRule.ALWAYS_KEEP;
        }
        return ICurio.DropRule.DEFAULT;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier == RingTier.NETHER_STAR || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.travelersring.tier." + tier.level()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.travelersring.how_to_use").withStyle(ChatFormatting.DARK_GRAY));
        RingData.getReturn(stack).ifPresentOrElse(rp -> tooltip.add(
                        Component.translatable("tooltip.travelersring.saved", rp.dimension().toString(),
                                Math.round(rp.x()), Math.round(rp.y()), Math.round(rp.z())).withStyle(ChatFormatting.AQUA)),
                () -> tooltip.add(Component.translatable("tooltip.travelersring.no_saved").withStyle(ChatFormatting.DARK_GRAY)));
    }
}
