package cc.spea.travelersring.net;

import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.service.RingService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A button press in the ring menu. The server re-derives everything; only the action and target key are used. */
public record RingActionC2S(RingSource source, int action, String key) {

    public static void encode(RingActionC2S msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.source.ordinal());
        buf.writeByte(msg.action);
        buf.writeUtf(msg.key, 128);
    }

    public static RingActionC2S decode(FriendlyByteBuf buf) {
        return new RingActionC2S(RingSource.byOrdinal(buf.readByte()), buf.readByte(), buf.readUtf(128));
    }

    public static void handle(RingActionC2S msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            RingService.Action action = RingService.Action.byOrdinal(msg.action);
            if (player != null && action != null) {
                RingService.perform(player, msg.source, action, msg.key);
            }
        });
        c.setPacketHandled(true);
    }
}
