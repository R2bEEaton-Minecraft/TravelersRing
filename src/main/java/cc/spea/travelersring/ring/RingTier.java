package cc.spea.travelersring.ring;

import cc.spea.travelersring.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Supplier;

/** The three ring tiers. Each is its own item so upgrades are plain data-driven smithing recipes. */
public enum RingTier {
    BASE(0, () -> ModItems.TRAVELERS_RING.get()),
    NETHERITE(1, () -> ModItems.TRAVELERS_RING_NETHERITE.get()),
    NETHER_STAR(2, () -> ModItems.TRAVELERS_RING_NETHER_STAR.get());

    private final int level;
    private final Supplier<Item> item;

    RingTier(int level, Supplier<Item> item) {
        this.level = level;
        this.item = item;
    }

    public int level() {
        return level;
    }

    public Item item() {
        return item.get();
    }

    public static Optional<RingTier> fromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Item i = stack.getItem();
        for (RingTier t : values()) {
            if (t.item() == i) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public static Optional<RingTier> byLevel(int level) {
        for (RingTier t : values()) {
            if (t.level == level) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
