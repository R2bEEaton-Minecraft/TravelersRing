package cc.spea.travelersring.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** One row of the death-screen respawn picker (display only; the server re-validates on choose AND at respawn). */
public record RespawnOptionView(String key, ResourceLocation dimension, int kind, boolean hasPos, int x, int y, int z,
                                boolean valid, String reason, @Nullable ResourceLocation block) {

    public RespawnOptionView(String key, ResourceLocation dimension, int kind, boolean hasPos, int x, int y, int z,
                             boolean valid, String reason) {
        this(key, dimension, kind, hasPos, x, y, z, valid, reason, null);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(key, 128);
        buf.writeResourceLocation(dimension);
        buf.writeByte(kind);
        buf.writeBoolean(hasPos);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        buf.writeBoolean(valid);
        buf.writeUtf(reason, 64);
        MenuData.writeBlock(buf, block);
    }

    public static RespawnOptionView read(FriendlyByteBuf buf) {
        return new RespawnOptionView(buf.readUtf(128), buf.readResourceLocation(), buf.readByte(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(64), MenuData.readBlock(buf));
    }
}
