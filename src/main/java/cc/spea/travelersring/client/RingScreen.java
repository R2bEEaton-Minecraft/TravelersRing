package cc.spea.travelersring.client;

import cc.spea.travelersring.net.MenuData;
import cc.spea.travelersring.net.Network;
import cc.spea.travelersring.net.RingActionC2S;
import cc.spea.travelersring.service.RingService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The ring menu. Purely a view: it displays what the server sent and sends back an action + target key. The server
 * re-validates everything, so disabling a button here is a convenience, not security.
 */
@OnlyIn(Dist.CLIENT)
public class RingScreen extends Screen {
    private final MenuData data;

    public RingScreen(MenuData data) {
        super(Component.translatable("screen.travelersring.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int w = 220;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 40;
        boolean hasSaved = data.saved() != null || data.savedUnreadable();

        if (data.usableHere() && !hasSaved) {
            for (MenuData.TargetView t : data.targets()) {
                Component label = Component.translatable("screen.travelersring.teleport_to", describe(t),
                        data.costsWaived() || t.cost() == 0 ? Component.translatable("screen.travelersring.free")
                                : Component.translatable("screen.travelersring.xp", t.cost()));
                boolean affordable = data.costsWaived() || t.cost() <= data.playerXp();
                Button b = Button.builder(label, btn -> send(RingService.Action.TELEPORT_SPAWN, t.key()))
                        .bounds(x, y, w, 20).build();
                b.active = t.valid() && affordable;
                if (!t.valid()) {
                    b.setTooltip(Tooltip.create(RingText.failure(t.reason())));
                } else if (!affordable) {
                    b.setTooltip(Tooltip.create(Component.translatable("screen.travelersring.need_xp", t.cost(), data.playerXp())));
                } else if (t.fallback()) {
                    b.setTooltip(Tooltip.create(Component.translatable("screen.travelersring.fallback_world_spawn")));
                }
                addRenderableWidget(b);
                y += 24;
            }
        }
        if (data.usableHere() && hasSaved) {
            Button back = Button.builder(Component.translatable("screen.travelersring.return"),
                    btn -> send(RingService.Action.RETURN, "")).bounds(x, y, w, 20).build();
            back.active = !data.savedUnreadable();
            addRenderableWidget(back);
            y += 24;
        }
        if (hasSaved) {
            addRenderableWidget(Button.builder(Component.translatable("screen.travelersring.delete"),
                            btn -> this.minecraft.setScreen(new ConfirmScreen(yes -> {
                                if (yes) {
                                    send(RingService.Action.DELETE, "");
                                } else {
                                    this.minecraft.setScreen(this);
                                }
                            }, Component.translatable("screen.travelersring.delete_confirm_title"),
                                    Component.translatable("screen.travelersring.delete_confirm_body"))))
                    .bounds(x, y, w, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose()).bounds(x, y + 6, w, 20).build());
    }

    private Component describe(MenuData.TargetView t) {
        Component where = RingText.dimension(t.dimension());
        Component what = RingText.kind(t.kind());
        return Component.translatable("screen.travelersring.target", where, what);
    }

    private void send(RingService.Action action, String key) {
        Network.sendToServer(new RingActionC2S(data.source(), action.ordinal(), key));
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int top = this.height / 2 - 40;
        int lines = 0;
        g.drawCenteredString(this.font, Component.translatable("screen.travelersring.heading", tierName()).withStyle(ChatFormatting.BOLD), cx, top - 44, 0xFFFFFF);
        g.drawCenteredString(this.font, data.costsWaived()
                ? Component.translatable("screen.travelersring.xp_waived")
                : Component.translatable("screen.travelersring.xp_have", data.playerXp()), cx, top - 32, 0xA0E0A0);
        if (data.saved() != null) {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.saved", RingText.dimension(data.saved().dimension()),
                    RingText.pos(data.saved().x(), data.saved().y(), data.saved().z())), cx, top - 20, 0x80D0FF);
        } else if (data.savedUnreadable()) {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.saved_unreadable"), cx, top - 20, 0xFF8080);
        } else if (!data.usableHere()) {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.not_here"), cx, top - 20, 0xFF8080);
        } else {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.no_saved"), cx, top - 20, 0xB0B0B0);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private Component tierName() {
        return Component.translatable("screen.travelersring.tier." + data.tier());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
