package cc.spea.travelersring.client;

import cc.spea.travelersring.net.MenuData;
import cc.spea.travelersring.net.Network;
import cc.spea.travelersring.net.RingActionC2S;
import cc.spea.travelersring.service.RingService;
import cc.spea.travelersring.service.XpHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The ring menu. Purely a view: it displays what the server sent and sends back an action + target key. The server
 * re-validates everything, so disabling a button here is a convenience, not security.
 */
@OnlyIn(Dist.CLIENT)
public class RingScreen extends Screen {
    /** Vanilla's HUD sprite sheet, which holds the experience bar. */
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final int BAR_W = 182;
    private static final int BAR_H = 5;
    private static final int ROW_PLAIN = 24;
    private static final int ROW_WITH_BAR = 40;

    private final MenuData data;
    /** XP bars to draw (the cost under each teleport button), filled in by init(). */
    private final List<Bar> bars = new ArrayList<>();
    private int startY;
    private int headerH;

    private record Bar(int x, int y, int points, boolean affordable) {
    }

    public RingScreen(MenuData data) {
        super(Component.translatable("screen.travelersring.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        bars.clear();
        boolean hasSaved = data.saved() != null || data.savedUnreadable();
        List<MenuData.TargetView> targets = data.usableHere() && !hasSaved ? data.targets() : List.of();
        int otherRows = (data.usableHere() && hasSaved ? 1 : 0) + (hasSaved ? 1 : 0);

        // Wide enough for the longest label (long modded dimension names would otherwise be cut off)
        List<Component> labels = new ArrayList<>();
        int w = 220;
        for (MenuData.TargetView t : targets) {
            Component label = Component.translatable("screen.travelersring.teleport_to", describe(t),
                    data.costsWaived() || t.cost() == 0 ? Component.translatable("screen.travelersring.free")
                            : Component.translatable("screen.travelersring.xp", t.cost()));
            labels.add(label);
            w = Math.max(w, this.font.width(label) + 24);
        }
        w = Math.min(w, this.width - 20);
        int x = this.width / 2 - w / 2;

        // Cost bars only if everything still fits on screen; otherwise fall back to the compact layout
        boolean barsOn = !data.costsWaived();
        // title + status line, plus a "no XP cost" line in creative
        headerH = data.costsWaived() ? 44 : 32;
        int total = headerH + targets.size() * (barsOn ? ROW_WITH_BAR : ROW_PLAIN) + otherRows * ROW_PLAIN + 32;
        if (barsOn && total > this.height - 8) {
            barsOn = false;
            total = headerH + targets.size() * ROW_PLAIN + otherRows * ROW_PLAIN + 32;
        }
        startY = Math.max(4, (this.height - total) / 2);
        int y = startY + headerH;

        for (int i = 0; i < targets.size(); i++) {
            MenuData.TargetView t = targets.get(i);
            boolean affordable = data.costsWaived() || t.cost() <= data.playerXp();
            Button b = Button.builder(labels.get(i), btn -> send(RingService.Action.TELEPORT_SPAWN, t.key()))
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
            if (barsOn && t.valid() && t.cost() > 0) {
                bars.add(new Bar(this.width / 2 - BAR_W / 2, y + 30, t.cost(), affordable));
            }
            y += barsOn ? ROW_WITH_BAR : ROW_PLAIN;
        }
        if (data.usableHere() && hasSaved) {
            Button back = Button.builder(Component.translatable("screen.travelersring.return"),
                    btn -> send(RingService.Action.RETURN, "")).bounds(x, y, w, 20).build();
            back.active = !data.savedUnreadable();
            addRenderableWidget(back);
            y += ROW_PLAIN;
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
            y += ROW_PLAIN;
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

    /**
     * Draws a vanilla-style experience bar holding {@code points} XP: the level number centred above it, as on the HUD,
     * and the bar filled to the progress into that level, exactly as the points would look in the player's own XP bar.
     */
    private void drawXpBar(GuiGraphics g, int x, int y, int points, int numberColor) {
        int level = XpHelper.levelForPoints(points);
        float progress = (float) (points - XpHelper.pointsForLevel(level)) / XpHelper.xpNeededForNextLevel(level);
        int filled = Math.max(0, Math.min(BAR_W, (int) (progress * (BAR_W + 1))));
        g.blit(ICONS, x, y, 0, 64, BAR_W, BAR_H);
        if (filled > 0) {
            g.blit(ICONS, x, y, 0, 69, filled, BAR_H);
        }
        String text = Integer.toString(level);
        int tx = x + (BAR_W - this.font.width(text)) / 2;
        int ty = y - 6;
        // vanilla draws the level number with a black outline
        g.drawString(this.font, text, tx + 1, ty, 0, false);
        g.drawString(this.font, text, tx - 1, ty, 0, false);
        g.drawString(this.font, text, tx, ty + 1, 0, false);
        g.drawString(this.font, text, tx, ty - 1, 0, false);
        g.drawString(this.font, text, tx, ty, numberColor, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, Component.translatable("screen.travelersring.heading", tierName()).withStyle(ChatFormatting.BOLD), cx, startY, 0xFFFFFF);
        int statusY = startY + 14;
        if (data.costsWaived()) {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.xp_waived"), cx, statusY, 0xA0E0A0);
            statusY += 12;
        }
        if (data.saved() != null) {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.saved", RingText.dimension(data.saved().dimension()),
                    RingText.pos(data.saved().x(), data.saved().y(), data.saved().z())), cx, statusY, 0x80D0FF);
        } else if (data.savedUnreadable()) {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.saved_unreadable"), cx, statusY, 0xFF8080);
        } else if (!data.usableHere()) {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.not_here"), cx, statusY, 0xFF8080);
        } else {
            g.drawCenteredString(this.font, Component.translatable("screen.travelersring.no_saved"), cx, statusY, 0xB0B0B0);
        }
        for (Bar bar : bars) {
            drawXpBar(g, bar.x(), bar.y(), bar.points(), bar.affordable() ? 0x80FF20 : 0xFF5555);
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
