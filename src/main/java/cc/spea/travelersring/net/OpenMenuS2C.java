package cc.spea.travelersring.net;

import cc.spea.travelersring.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record OpenMenuS2C(MenuData data) {

    public static void encode(OpenMenuS2C msg, FriendlyByteBuf buf) {
        msg.data.write(buf);
    }

    public static OpenMenuS2C decode(FriendlyByteBuf buf) {
        return new OpenMenuS2C(MenuData.read(buf));
    }

    public static void handle(OpenMenuS2C msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openMenu(msg.data)));
        c.setPacketHandled(true);
    }
}
