package cc.spea.travelersring.client;

import cc.spea.travelersring.service.Destination;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/** Client-side display helpers shared by the ring menu and the death-screen picker. */
@OnlyIn(Dist.CLIENT)
public final class RingText {
    private RingText() {
    }

    public static Component dimension(ResourceLocation dim) {
        String key = "dimension.travelersring." + dim.getNamespace() + "." + dim.getPath();
        if (net.minecraft.client.resources.language.I18n.exists(key)) {
            return Component.translatable(key);
        }
        // Unknown (modded) dimension: prettify the path, e.g. "twilight_forest" -> "Twilight Forest"
        StringBuilder sb = new StringBuilder();
        for (String part : dim.getPath().split("[_/]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return Component.literal(sb.toString());
    }

    public static Component kind(int ordinal) {
        Destination.Kind[] kinds = Destination.Kind.values();
        Destination.Kind k = ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : Destination.Kind.BED;
        return Component.translatable("kind.travelersring." + k.name().toLowerCase(Locale.ROOT));
    }

    public static Component pos(int x, int y, int z) {
        return Component.literal(x + ", " + y + ", " + z);
    }

    public static Component failure(String reasonKey) {
        return Component.translatable("failure.travelersring." + reasonKey);
    }
}
