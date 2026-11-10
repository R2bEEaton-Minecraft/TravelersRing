package cc.spea.travelersring.ring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Finds the ring being used, and writes changed ring stacks back to wherever they live. */
public final class RingLocator {
    private RingLocator() {
    }

    /** A located ring. {@code stack} is a snapshot reference; use {@link #write} to change it. */
    public record RingHandle(RingSource source, String slotId, int slotIndex, ItemStack stack, RingTier tier) {
    }

    public static Optional<RingHandle> find(ServerPlayer player, RingSource source) {
        return switch (source) {
            case MAIN_HAND -> inHand(player, InteractionHand.MAIN_HAND, RingSource.MAIN_HAND);
            case OFF_HAND -> inHand(player, InteractionHand.OFF_HAND, RingSource.OFF_HAND);
            case CURIOS -> equipped(player);
        };
    }

    private static Optional<RingHandle> inHand(ServerPlayer player, InteractionHand hand, RingSource source) {
        ItemStack stack = player.getItemInHand(hand);
        return RingTier.fromStack(stack).map(t -> new RingHandle(source, "", 0, stack, t));
    }

    /** The best-tier ring currently equipped in any Curios slot (ties: first in slot order). */
    public static Optional<RingHandle> equipped(LivingEntity entity) {
        Optional<ICuriosItemHandler> inv = CuriosApi.getCuriosInventory(entity).resolve();
        if (inv.isEmpty()) {
            return Optional.empty();
        }
        List<SlotResult> found = inv.get().findCurios(s -> RingTier.fromStack(s).isPresent());
        return found.stream()
                .max(Comparator.comparingInt((SlotResult r) -> RingTier.fromStack(r.stack()).map(RingTier::level).orElse(-1)))
                .map(r -> new RingHandle(RingSource.CURIOS, r.slotContext().identifier(), r.slotContext().index(), r.stack(),
                        RingTier.fromStack(r.stack()).orElseThrow()));
    }

    public static boolean hasEquipped(LivingEntity entity, RingTier tier) {
        Optional<ICuriosItemHandler> inv = CuriosApi.getCuriosInventory(entity).resolve();
        return inv.isPresent() && !inv.get().findCurios(s -> RingTier.fromStack(s).filter(t -> t == tier).isPresent()).isEmpty();
    }

    /** Replace the ring stack at its slot with {@code updated} (also triggers Curios/inventory sync). */
    public static void write(ServerPlayer player, RingHandle handle, ItemStack updated) {
        switch (handle.source()) {
            case MAIN_HAND -> player.setItemInHand(InteractionHand.MAIN_HAND, updated);
            case OFF_HAND -> player.setItemInHand(InteractionHand.OFF_HAND, updated);
            case CURIOS -> CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(inv -> inv.getStacksHandler(handle.slotId()))
                    .ifPresent(h -> h.getStacks().setStackInSlot(handle.slotIndex(), updated));
        }
    }
}
