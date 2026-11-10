package cc.spea.travelersring.client;

import cc.spea.travelersring.net.MenuData;
import cc.spea.travelersring.net.RespawnOptionView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Client-only state and entry points called from network handlers (always via DistExecutor). */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
    public static List<RespawnOptionView> respawnOffers = List.of();
    public static String respawnSelected = "default";
    public static Button respawnButton;

    private ClientHooks() {
    }

    public static void openMenu(MenuData data) {
        Minecraft.getInstance().setScreen(new RingScreen(data));
    }

    public static void setRespawnOffers(List<RespawnOptionView> options) {
        respawnOffers = List.copyOf(options);
        respawnSelected = "default";
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DeathScreen ds) {
            ds.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()); // re-init so the picker appears/refreshes
        }
    }

    public static void setRespawnSelected(String key) {
        respawnSelected = key;
        if (respawnButton != null) {
            respawnButton.setMessage(DeathPicker.label());
        }
    }

    public static void clearRespawn() {
        respawnOffers = List.of();
        respawnSelected = "default";
        respawnButton = null;
    }
}
