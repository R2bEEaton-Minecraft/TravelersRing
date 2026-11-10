package cc.spea.travelersring.net;

import cc.spea.travelersring.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The death-screen respawn picker contents. An empty list clears the picker. */
public record RespawnOffersS2C(List<RespawnOptionView> options) {
    private static final int MAX = 64;

    public static void encode(RespawnOffersS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.options.size());
        for (RespawnOptionView o : msg.options) {
            o.write(buf);
        }
    }

    public static RespawnOffersS2C decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<RespawnOptionView> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(RespawnOptionView.read(buf));
        }
        return new RespawnOffersS2C(list);
    }

    public static void handle(RespawnOffersS2C msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.setRespawnOffers(msg.options)));
        c.setPacketHandled(true);
    }
}
