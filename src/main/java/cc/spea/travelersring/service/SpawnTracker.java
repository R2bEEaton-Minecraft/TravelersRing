package cc.spea.travelersring.service;

import cc.spea.travelersring.data.PlayerRingData;
import cc.spea.travelersring.data.PlayerRingData.SpawnRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Tracks the LATEST successfully-set spawnpoint per dimension for each player.
 *
 * <p>Source of truth is Forge's {@link PlayerSetSpawnEvent}, which vanilla fires from
 * {@code ServerPlayer.setRespawnPosition} for real spawn-setting actions only: sleeping in a bed (even by day),
 * activating a charged respawn anchor, and {@code /spawnpoint}. Clicking a bed that cannot be slept in, or a dry
 * anchor, never reaches that method and is therefore never recorded.</p>
 *
 * <p>Historic spawnpoints from before this mod was installed cannot be recovered; the only thing we can adopt
 * is the player's CURRENT vanilla spawn (see {@link #syncWithVanilla}).</p>
 */
public final class SpawnTracker {
    private static boolean suppress;

    private SpawnTracker() {
    }

    /** Run {@code r} with spawn-event recording disabled (used when this mod itself moves a player's spawn). */
    public static void withoutRecording(Runnable r) {
        boolean prev = suppress;
        suppress = true;
        try {
            r.run();
        } finally {
            suppress = prev;
        }
    }

    public static void onSpawnSet(PlayerSetSpawnEvent event) {
        if (suppress || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getNewSpawn();
        if (pos == null) {
            return; // spawn being cleared; nothing new to record
        }
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.entry(player.getUUID());
        if (entry.swap != null) {
            return; // a respawn choice is in flight; vanilla's own re-set during respawn must not reorder records
        }
        record(player, event.getSpawnLevel().location(), pos, player.getYRot(), event.isForced());
    }

    public static void record(ServerPlayer player, ResourceLocation dim, BlockPos pos, float angle, boolean forced) {
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.entry(player.getUUID());
        entry.spawns.put(dim, new SpawnRecord(pos.immutable(), angle, forced, player.server.overworld().getGameTime()));
        data.touch();
    }

    /**
     * Adopt the player's current vanilla spawn if we have nothing (or something different) for its dimension.
     * The vanilla spawn is by definition the most recent spawn the player set, so it wins for its own dimension.
     */
    public static void syncWithVanilla(ServerPlayer player) {
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.entry(player.getUUID());
        if (entry.swap != null) {
            return; // vanilla spawn is temporarily the player's chosen one
        }
        BlockPos pos = player.getRespawnPosition();
        if (pos == null) {
            return;
        }
        ResourceLocation dim = player.getRespawnDimension().location();
        SpawnRecord existing = entry.spawns.get(dim);
        if (existing == null || !existing.pos().equals(pos)) {
            entry.spawns.put(dim, new SpawnRecord(pos.immutable(), player.getRespawnAngle(), player.isRespawnForced(), existing == null ? 0L : player.server.overworld().getGameTime()));
            data.touch();
        }
    }

    public static SpawnRecord get(ServerPlayer player, ResourceLocation dim) {
        syncWithVanilla(player);
        PlayerRingData.Entry entry = PlayerRingData.get(player.server).peek(player.getUUID());
        return entry == null ? null : entry.spawns.get(dim);
    }

    /** All tracked per-dimension spawns, newest first. */
    public static List<Map.Entry<ResourceLocation, SpawnRecord>> all(ServerPlayer player) {
        syncWithVanilla(player);
        PlayerRingData.Entry entry = PlayerRingData.get(player.server).peek(player.getUUID());
        List<Map.Entry<ResourceLocation, SpawnRecord>> out = new ArrayList<>();
        if (entry != null) {
            out.addAll(entry.spawns.entrySet());
            out.sort(Comparator.comparingLong((Map.Entry<ResourceLocation, SpawnRecord> e) -> e.getValue().time()).reversed());
        }
        return out;
    }
}
