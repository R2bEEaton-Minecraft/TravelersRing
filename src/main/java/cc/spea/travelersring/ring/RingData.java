package cc.spea.travelersring.ring;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Persistent per-ring data, stored in the ItemStack's NBT so it survives logout, death-retention, trading,
 * Curios moves and vanilla smithing upgrades (the vanilla smithing recipe copies the base item's tag).
 *
 * <pre>
 * {TravelersRing: {Return: {Dim:"minecraft:overworld", X:1.5d, Y:64.0d, Z:-3.5d, Yaw:90.0f, Pitch:0.0f}}}
 * </pre>
 */
public final class RingData {
    public static final String ROOT = "TravelersRing";
    public static final String RETURN = "Return";

    private RingData() {
    }

    public static Optional<ReturnPoint> getReturn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag root = tag.getCompound(ROOT);
        if (!root.contains(RETURN, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag r = root.getCompound(RETURN);
        if (!r.contains("Dim", Tag.TAG_STRING) || !r.contains("X", Tag.TAG_DOUBLE) || !r.contains("Y", Tag.TAG_DOUBLE)
                || !r.contains("Z", Tag.TAG_DOUBLE)) {
            return Optional.empty();
        }
        ResourceLocation dim = ResourceLocation.tryParse(r.getString("Dim"));
        if (dim == null) {
            return Optional.empty();
        }
        double x = r.getDouble("X");
        double y = r.getDouble("Y");
        double z = r.getDouble("Z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Optional.empty();
        }
        return Optional.of(new ReturnPoint(dim, x, y, z, r.getFloat("Yaw"), r.getFloat("Pitch")));
    }

    public static void setReturn(ItemStack stack, ReturnPoint point) {
        CompoundTag r = new CompoundTag();
        r.putString("Dim", point.dimension().toString());
        r.putDouble("X", point.x());
        r.putDouble("Y", point.y());
        r.putDouble("Z", point.z());
        r.putFloat("Yaw", point.yaw());
        r.putFloat("Pitch", point.pitch());
        CompoundTag root = stack.getOrCreateTagElement(ROOT);
        root.put(RETURN, r);
    }

    public static void clearReturn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag root = tag.getCompound(ROOT);
        root.remove(RETURN);
        if (root.isEmpty()) {
            tag.remove(ROOT);
        }
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    /** True when any (possibly corrupt) return data is present. Lets us offer deletion of unreadable data. */
    public static boolean hasRawReturn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ROOT, Tag.TAG_COMPOUND) && tag.getCompound(ROOT).contains(RETURN);
    }
}
