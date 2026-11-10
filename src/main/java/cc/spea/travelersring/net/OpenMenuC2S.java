package cc.spea.travelersring.net;

import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.service.RingService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Keybind pressed: ask the server to open the menu for the best EQUIPPED ring. */
public record OpenMenuC2S(RingSource source) {

    public static void encode(OpenMenuC2S msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.source.ordinal());
    }

    public static OpenMenuC2S decode(FriendlyByteBuf buf) {
        return new OpenMenuC2S(RingSource.byOrdinal(buf.readByte()));
    }

    public static void handle(OpenMenuC2S msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) {
                // The keybind only ever means "equipped ring"; hand sources are opened by the item's own right-click.
                RingService.openMenu(player, RingSource.CURIOS);
            }
        });
        c.setPacketHandled(true);
    }
}
