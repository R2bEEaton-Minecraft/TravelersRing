package cc.spea.travelersring.ring;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Where the player departed from, saved on the individual ring stack. */
public record ReturnPoint(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {

    public static ReturnPoint of(ServerPlayer player) {
        return new ReturnPoint(player.level().dimension().location(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    public ResourceKey<Level> dimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, dimension);
    }
}
