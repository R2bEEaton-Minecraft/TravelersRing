package cc.spea.travelersring.service;

import cc.spea.travelersring.data.PlayerRingData;
import cc.spea.travelersring.data.PlayerRingData.SpawnRecord;
import cc.spea.travelersring.config.RingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /** Anchor right-clicks waiting for the end of the tick, when we can see whether they added charge. */
    private record PendingAnchor(UUID player, ResourceKey<Level> dim, BlockPos pos, int chargeBefore) {
    }

    private static final List<PendingAnchor> PENDING_ANCHORS = new ArrayList<>();

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

    /**
     * The client's PDF asks for the "last interacted Respawn Anchor (as long as it's charged)". Charging an anchor with
     * glowstone is an interaction but does NOT set the player's spawn, so vanilla never tells us about it. Forge has no
     * "after use" event, so the click is noted here and checked once the tick ends ({@link #flushAnchorChecks}).
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        boolean vanillaAnchor = state.getBlock() instanceof RespawnAnchorBlock && RespawnAnchorBlock.canSetSpawn(level);
        boolean listed = !vanillaAnchor && RingConfig.CHARGE_COUNTS_BLOCKS.get().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (vanillaAnchor || listed) {
            SpawnResolver.chargeOf(state).ifPresent(charge -> PENDING_ANCHORS.add(
                    new PendingAnchor(player.getUUID(), level.dimension(), event.getPos().immutable(), charge)));
        }
    }

    /** Records every anchor clicked this tick that now holds more charge than before the click. */
    public static void flushAnchorChecks(MinecraftServer server) {
        if (PENDING_ANCHORS.isEmpty()) {
            return;
        }
        List<PendingAnchor> pending = new ArrayList<>(PENDING_ANCHORS);
        PENDING_ANCHORS.clear();
        for (PendingAnchor a : pending) {
            ServerPlayer player = server.getPlayerList().getPlayer(a.player());
            ServerLevel level = server.getLevel(a.dim());
            if (player == null || level == null) {
                continue;
            }
            BlockState state = level.getBlockState(a.pos());
            if (SpawnResolver.chargeOf(state).filter(c -> c > a.chargeBefore()).isPresent()) {
                recordAnchor(player, a.dim().location(), a.pos());
            }
        }
    }

    public static void recordAnchor(ServerPlayer player, ResourceLocation dim, BlockPos pos) {
        PlayerRingData data = PlayerRingData.get(player.server);
        data.entry(player.getUUID()).anchors.put(dim, new SpawnRecord(pos.immutable(), 0f, false, player.server.overworld().getGameTime(),
                blockAt(player.server, dim, pos)));
        data.touch();
    }

    /**
     * Where a ring teleport goes for {@code dim}: the newer of the latest spawnpoint and the latest charged anchor.
     * (Only the ring uses this. Death respawn choices stay limited to real spawnpoints.)
     */
    public static SpawnRecord getForRing(ServerPlayer player, ResourceLocation dim) {
        SpawnRecord spawn = get(player, dim);
        PlayerRingData.Entry entry = PlayerRingData.get(player.server).peek(player.getUUID());
        SpawnRecord anchor = entry == null ? null : entry.anchors.get(dim);
        if (anchor == null) {
            return spawn;
        }
        return spawn == null || anchor.time() > spawn.time() ? anchor : spawn;
    }

    /** Every dimension the ring knows a destination for (spawnpoints and charged anchors), newest first. */
    public static List<Map.Entry<ResourceLocation, SpawnRecord>> allForRing(ServerPlayer player) {
        syncWithVanilla(player);
        PlayerRingData.Entry entry = PlayerRingData.get(player.server).peek(player.getUUID());
        Map<ResourceLocation, SpawnRecord> merged = new LinkedHashMap<>();
        if (entry != null) {
            merged.putAll(entry.spawns);
            for (Map.Entry<ResourceLocation, SpawnRecord> a : entry.anchors.entrySet()) {
                merged.merge(a.getKey(), a.getValue(), (s, an) -> an.time() > s.time() ? an : s);
            }
        }
        List<Map.Entry<ResourceLocation, SpawnRecord>> out = new ArrayList<>(merged.entrySet());
        out.sort(Comparator.comparingLong((Map.Entry<ResourceLocation, SpawnRecord> e) -> e.getValue().time()).reversed());
        return out;
    }

    public static void record(ServerPlayer player, ResourceLocation dim, BlockPos pos, float angle, boolean forced) {
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.entry(player.getUUID());
        entry.spawns.put(dim, new SpawnRecord(pos.immutable(), angle, forced, player.server.overworld().getGameTime(),
                forced ? null : blockAt(player.server, dim, pos)));
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
        boolean forced = player.isRespawnForced();
        if (existing == null || !existing.pos().equals(pos)) {
            entry.spawns.put(dim, new SpawnRecord(pos.immutable(), player.getRespawnAngle(), forced,
                    existing == null ? 0L : player.server.overworld().getGameTime(), forced ? null : blockAt(player.server, dim, pos)));
            data.touch();
        } else if (existing.block() == null && !existing.forced()) {
            // Record from before 1.0.3: fill in which block the spawn is on, now that it's needed
            ResourceLocation block = blockAt(player.server, dim, pos);
            if (block != null) {
                entry.spawns.put(dim, new SpawnRecord(existing.pos(), existing.angle(), existing.forced(), existing.time(), block));
                data.touch();
            }
        }
    }

    /** Registry id of the block at {@code pos}, or null if the dimension is missing or the spot is air. */
    @Nullable
    static ResourceLocation blockAt(MinecraftServer server, ResourceLocation dim, BlockPos pos) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
