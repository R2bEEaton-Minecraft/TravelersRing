package cc.spea.travelersring.client;

import cc.spea.travelersring.TravelersRing;
import cc.spea.travelersring.net.ChooseRespawnC2S;
import cc.spea.travelersring.net.Network;
import cc.spea.travelersring.net.RespawnOptionView;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Adds a "Respawn at: ..." cycle button to the vanilla death screen when the server offered a respawn choice
 * (Nether Star ring equipped at death). It only requests a choice; the server validates and confirms.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TravelersRing.MOD_ID, value = Dist.CLIENT)
public final class DeathPicker {
    private DeathPicker() {
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen) || ClientHooks.respawnOffers.isEmpty()) {
            return;
        }
        Button b = Button.builder(label(), btn -> cycle())
                .bounds(screen.width / 2 - 100, screen.height / 4 + 120, 200, 20).build();
        ClientHooks.respawnButton = b;
        event.addListener(b);
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof DeathScreen) {
            ClientHooks.clearRespawn();
        }
    }

    public static Component label() {
        for (RespawnOptionView o : ClientHooks.respawnOffers) {
            if (o.key().equals(ClientHooks.respawnSelected)) {
                return Component.translatable("screen.travelersring.respawn_at", describe(o));
            }
        }
        return Component.translatable("screen.travelersring.respawn_at", Component.translatable("screen.travelersring.respawn_default"));
    }

    private static Component describe(RespawnOptionView o) {
        if (o.key().equals("default")) {
            return Component.translatable("screen.travelersring.respawn_default");
        }
        if (o.key().equals("world")) {
            return Component.translatable("screen.travelersring.respawn_world");
        }
        return Component.translatable("screen.travelersring.respawn_entry", RingText.dimension(o.dimension()), RingText.kind(o.kind(), o.block()),
                RingText.pos(o.x(), o.y(), o.z()));
    }

    /** Advance to the next VALID option (wrapping) and ask the server to apply it. */
    private static void cycle() {
        List<RespawnOptionView> opts = ClientHooks.respawnOffers;
        if (opts.isEmpty()) {
            return;
        }
        int cur = 0;
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).key().equals(ClientHooks.respawnSelected)) {
                cur = i;
                break;
            }
        }
        for (int step = 1; step <= opts.size(); step++) {
            RespawnOptionView next = opts.get((cur + step) % opts.size());
            if (next.valid()) {
                Network.sendToServer(new ChooseRespawnC2S(next.key()));
                return;
            }
        }
    }
}
