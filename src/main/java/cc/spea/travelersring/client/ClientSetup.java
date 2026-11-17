package cc.spea.travelersring.client;

import cc.spea.travelersring.ModItems;
import cc.spea.travelersring.TravelersRing;
import cc.spea.travelersring.net.Network;
import cc.spea.travelersring.net.OpenMenuC2S;
import cc.spea.travelersring.ring.RingData;
import cc.spea.travelersring.ring.RingSource;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Client wiring: the (rebindable) "Use Traveler's Ring" key and the rings' on/off icon. */
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

        /**
         * {@code travelersring:active} is 1 while the ring holds a saved location (you've gone to spawn and can
         * return for free), so its model switches to the glowing "_active" texture; 0 after you return or delete it.
         * Read straight from the stack's NBT, so it is correct in the hand, inventory and Curios slot alike.
         */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ResourceLocation active = new ResourceLocation(TravelersRing.MOD_ID, "active");
                for (Item ring : new Item[]{ModItems.TRAVELERS_RING.get(), ModItems.TRAVELERS_RING_NETHERITE.get(),
                        ModItems.TRAVELERS_RING_NETHER_STAR.get()}) {
                    ItemProperties.register(ring, active,
                            (stack, level, entity, seed) -> RingData.hasRawReturn(stack) ? 1.0F : 0.0F);
                }
            });
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
