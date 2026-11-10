package cc.spea.travelersring.net;

import cc.spea.travelersring.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server confirmation of which respawn option is now selected. */
public record RespawnSelectedS2C(String key) {

    public static void encode(RespawnSelectedS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.key, 128);
    }

    public static RespawnSelectedS2C decode(FriendlyByteBuf buf) {
        return new RespawnSelectedS2C(buf.readUtf(128));
    }

    public static void handle(RespawnSelectedS2C msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.setRespawnSelected(msg.key)));
        c.setPacketHandled(true);
    }
}
