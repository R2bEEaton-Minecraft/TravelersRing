package cc.spea.travelersring.client;

import cc.spea.travelersring.TravelersRing;
import cc.spea.travelersring.net.Network;
import cc.spea.travelersring.net.OpenMenuC2S;
import cc.spea.travelersring.ring.RingSource;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client wiring: the (rebindable) "Use Traveler's Ring" key. */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static final KeyMapping USE_RING = new KeyMapping("key.travelersring.use", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.travelersring");

    @Mod.EventBusSubscriber(modid = TravelersRing.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(USE_RING);
        }
    }

    @Mod.EventBusSubscriber(modid = TravelersRing.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (USE_RING.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    Network.sendToServer(new OpenMenuC2S(RingSource.CURIOS));
                }
            }
        }
    }
}
