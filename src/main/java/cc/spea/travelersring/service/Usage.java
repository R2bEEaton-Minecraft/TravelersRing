package cc.spea.travelersring.service;

import cc.spea.travelersring.config.RingConfig;
import cc.spea.travelersring.ring.RingTier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Set;

/** Which dimensions each tier may be used in (all driven by config). */
public final class Usage {
    private Usage() {
    }

    public static boolean allowedIn(RingTier tier, ResourceKey<Level> dim) {
        ResourceLocation id = dim.location();
        if (RingConfig.parseDimensions(RingConfig.BLOCKED_DIMENSIONS.get()).contains(id)) {
            return false;
        }
        return switch (tier) {
            case BASE -> RingConfig.parseDimensions(RingConfig.BASE_DIMENSIONS.get()).contains(id);
            case NETHERITE -> RingConfig.parseDimensions(RingConfig.NETHERITE_DIMENSIONS.get()).contains(id);
            case NETHER_STAR -> RingConfig.NETHER_STAR_ANY_DIMENSION.get()
                    || RingConfig.parseDimensions(RingConfig.NETHER_STAR_DIMENSIONS.get()).contains(id);
        };
    }

    /** Dimensions a tier can use, for display only (null = unrestricted). */
    public static Set<ResourceLocation> listed(RingTier tier) {
        return switch (tier) {
            case BASE -> RingConfig.parseDimensions(RingConfig.BASE_DIMENSIONS.get());
            case NETHERITE -> RingConfig.parseDimensions(RingConfig.NETHERITE_DIMENSIONS.get());
            case NETHER_STAR -> RingConfig.NETHER_STAR_ANY_DIMENSION.get() ? null
                    : RingConfig.parseDimensions(RingConfig.NETHER_STAR_DIMENSIONS.get());
        };
    }
}
