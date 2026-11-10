package cc.spea.travelersring.net;

import cc.spea.travelersring.service.RespawnChoice;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Death-screen pick. Ignored by the server unless that player is dead AND has a server-created offer containing the key. */
public record ChooseRespawnC2S(String key) {

    public static void encode(ChooseRespawnC2S msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.key, 128);
    }

    public static ChooseRespawnC2S decode(FriendlyByteBuf buf) {
        return new ChooseRespawnC2S(buf.readUtf(128));
    }

    public static void handle(ChooseRespawnC2S msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) {
                RespawnChoice.choose(player, msg.key);
            }
        });
        c.setPacketHandled(true);
    }
}
