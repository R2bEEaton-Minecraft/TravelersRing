package cc.spea.travelersring.net;

import cc.spea.travelersring.ring.RingSource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the client needs to draw the ring menu. Pure display data: the server recomputes and re-validates
 * everything when an action arrives, so a forged or stale MenuData cannot change what the server does.
 */
public record MenuData(RingSource source, int tier, boolean usableHere, @Nullable SavedView saved, boolean savedUnreadable,
                       List<TargetView> targets, int playerXp, boolean costsWaived) {

    public record SavedView(ResourceLocation dimension, int x, int y, int z) {
    }

    /**
     * kind = ordinal of {@code Destination.Kind}. reason = {@code Destination.Failure.key} when invalid.
     * block = a modded spawn block's id, so its own name can be shown (null otherwise).
     */
    public record TargetView(String key, ResourceLocation dimension, int kind, boolean hasPos, int x, int y, int z,
                             boolean valid, String reason, int cost, boolean fallback, @Nullable ResourceLocation block) {
        public TargetView(String key, ResourceLocation dimension, int kind, boolean hasPos, int x, int y, int z,
                          boolean valid, String reason, int cost, boolean fallback) {
            this(key, dimension, kind, hasPos, x, y, z, valid, reason, cost, fallback, null);
        }
    }

    static void writeBlock(FriendlyByteBuf buf, @Nullable ResourceLocation block) {
        buf.writeBoolean(block != null);
        if (block != null) {
            buf.writeResourceLocation(block);
        }
    }

    @Nullable
    static ResourceLocation readBlock(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    private static final int MAX_TARGETS = 64;

    public void write(FriendlyByteBuf buf) {
        buf.writeByte(source.ordinal());
        buf.writeByte(tier);
        buf.writeBoolean(usableHere);
        buf.writeBoolean(saved != null);
        if (saved != null) {
            buf.writeResourceLocation(saved.dimension());
            buf.writeVarInt(saved.x());
            buf.writeVarInt(saved.y());
            buf.writeVarInt(saved.z());
        }
        buf.writeBoolean(savedUnreadable);
        buf.writeVarInt(targets.size());
        for (TargetView t : targets) {
            buf.writeUtf(t.key(), 128);
            buf.writeResourceLocation(t.dimension());
            buf.writeByte(t.kind());
            buf.writeBoolean(t.hasPos());
            buf.writeVarInt(t.x());
            buf.writeVarInt(t.y());
            buf.writeVarInt(t.z());
            buf.writeBoolean(t.valid());
            buf.writeUtf(t.reason(), 64);
            buf.writeVarInt(t.cost());
            buf.writeBoolean(t.fallback());
            writeBlock(buf, t.block());
        }
        buf.writeVarInt(playerXp);
        buf.writeBoolean(costsWaived);
    }

    public static MenuData read(FriendlyByteBuf buf) {
        RingSource source = RingSource.byOrdinal(buf.readByte());
        int tier = buf.readByte();
        boolean usable = buf.readBoolean();
        SavedView saved = null;
        if (buf.readBoolean()) {
            saved = new SavedView(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
        boolean unreadable = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), MAX_TARGETS);
        List<TargetView> targets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            targets.add(new TargetView(buf.readUtf(128), buf.readResourceLocation(), buf.readByte(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(64),
                    buf.readVarInt(), buf.readBoolean(), readBlock(buf)));
        }
        int xp = buf.readVarInt();
        boolean waived = buf.readBoolean();
        return new MenuData(source, tier, usable, saved, unreadable, targets, xp, waived);
    }
}
