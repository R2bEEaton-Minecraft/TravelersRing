package cc.spea.travelersring.net;

import cc.spea.travelersring.TravelersRing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Network {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TravelersRing.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {
    }

    public static void register() {
        int id = 0;
        // client -> server
        CHANNEL.registerMessage(id++, OpenMenuC2S.class, OpenMenuC2S::encode, OpenMenuC2S::decode, OpenMenuC2S::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, RingActionC2S.class, RingActionC2S::encode, RingActionC2S::decode, RingActionC2S::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ChooseRespawnC2S.class, ChooseRespawnC2S::encode, ChooseRespawnC2S::decode, ChooseRespawnC2S::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // server -> client
        CHANNEL.registerMessage(id++, OpenMenuS2C.class, OpenMenuS2C::encode, OpenMenuS2C::decode, OpenMenuS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RespawnOffersS2C.class, RespawnOffersS2C::encode, RespawnOffersS2C::decode, RespawnOffersS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RespawnSelectedS2C.class, RespawnSelectedS2C::encode, RespawnSelectedS2C::decode, RespawnSelectedS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        if (player.connection == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
