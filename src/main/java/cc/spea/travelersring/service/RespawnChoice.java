package cc.spea.travelersring.service;

import cc.spea.travelersring.config.RingConfig;
import cc.spea.travelersring.data.PlayerRingData;
import cc.spea.travelersring.data.PlayerRingData.SpawnRecord;
import cc.spea.travelersring.data.PlayerRingData.SwapRecord;
import cc.spea.travelersring.net.Network;
import cc.spea.travelersring.net.RespawnOffersS2C;
import cc.spea.travelersring.net.RespawnOptionView;
import cc.spea.travelersring.net.RespawnSelectedS2C;
import cc.spea.travelersring.ring.RingLocator;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.service.Destination.Kind;
import cc.spea.travelersring.service.Destination.Resolution;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nether Star ring: pick any valid recorded spawnpoint on the death screen (free).
 *
 * <p>Safety model:</p>
 * <ul>
 *   <li>The offer exists only if a Nether Star ring was equipped at death (decided server-side). The client can only
 *       send a key; the server maps it to its own recorded spawn, re-validates it, and ignores anything else.</li>
 *   <li>The choice never bypasses vanilla: we temporarily point the player's vanilla spawn at the chosen record, then
 *       vanilla's own respawn code validates it again (bed present, stand-up space, anchor charge, dimension exists)
 *       and falls back to the world spawn if it fails. A bad choice therefore cannot strand a player.</li>
 *   <li>The player's REAL spawn is saved in {@link PlayerRingData} (persisted) and restored right after respawn - or on
 *       next login - so a choice never silently overwrites their bed.</li>
 *   <li>Respawning at a chosen respawn anchor uses a charge exactly as vanilla respawning does. Ring teleports never do.</li>
 * </ul>
 */
public final class RespawnChoice {
    public static final String KEY_DEFAULT = "default";
    public static final String KEY_WORLD = "world";

    private record Offer(Map<String, RespawnOptionView> options, String[] selected) {
    }

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    private RespawnChoice() {
    }

    public static void forget(UUID id) {
        OFFERS.remove(id);
    }

    public static boolean hasOffer(ServerPlayer player) {
        return OFFERS.containsKey(player.getUUID());
    }

    // ---- offer ------------------------------------------------------------------------------------------------

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        OFFERS.remove(player.getUUID());
        if (!RingConfig.RESPAWN_CHOICE.get() || player.server.isHardcore() || !RingLocator.hasEquipped(player, RingTier.NETHER_STAR)) {
            return;
        }
        List<RespawnOptionView> options = buildOptions(player);
        Map<String, RespawnOptionView> map = new java.util.LinkedHashMap<>();
        for (RespawnOptionView o : options) {
            map.put(o.key(), o);
        }
        OFFERS.put(player.getUUID(), new Offer(map, new String[]{KEY_DEFAULT}));
        Network.sendToPlayer(player, new RespawnOffersS2C(options));
    }

    public static List<RespawnOptionView> buildOptions(ServerPlayer player) {
        List<RespawnOptionView> out = new ArrayList<>();
        out.add(new RespawnOptionView(KEY_DEFAULT, Level.OVERWORLD.location(), Kind.DEFAULT.ordinal(), false, 0, 0, 0, true, ""));
        for (Map.Entry<ResourceLocation, SpawnRecord> e : SpawnTracker.all(player)) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, e.getKey());
            // Mirror vanilla's respawn rules (forced anchors respawn uncharged).
            Resolution r = SpawnResolver.resolveRecorded(player.server, dim, e.getValue(), false);
            BlockPos p = e.getValue().pos();
            out.add(new RespawnOptionView(Targets.keyFor(e.getKey()), e.getKey(), r.kind().ordinal(), true,
                    p.getX(), p.getY(), p.getZ(), r.isOk(), r.isOk() ? "" : r.failure().key));
        }
        Resolution world = SpawnResolver.resolveWorldSpawn(player.server);
        BlockPos ws = player.server.overworld().getSharedSpawnPos();
        out.add(new RespawnOptionView(KEY_WORLD, Level.OVERWORLD.location(), Kind.WORLD_SPAWN.ordinal(), true,
                ws.getX(), ws.getY(), ws.getZ(), world.isOk(), world.isOk() ? "" : world.failure().key));
        return out;
    }

    // ---- choose -----------------------------------------------------------------------------------------------

    /** @return true if the choice was applied. Silently ignores anything that is not a legitimate pending offer. */
    public static boolean choose(ServerPlayer player, String key) {
        if (player.isAlive() || key == null) {
            return false; // only a dead player with a server-created offer may choose
        }
        Offer offer = OFFERS.get(player.getUUID());
        if (offer == null || !offer.options().containsKey(key)) {
            return false;
        }
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.entry(player.getUUID());

        if (key.equals(KEY_DEFAULT)) {
            restoreOriginal(player, entry, data);
            offer.selected()[0] = KEY_DEFAULT;
            Network.sendToPlayer(player, new RespawnSelectedS2C(KEY_DEFAULT));
            return true;
        }

        ResourceKey<Level> dim;
        BlockPos pos = null;
        float angle = 0f;
        boolean forced = false;
        if (key.equals(KEY_WORLD)) {
            Resolution r = SpawnResolver.resolveWorldSpawn(player.server);
            if (!r.isOk()) {
                return reject(player);
            }
            dim = Level.OVERWORLD;
        } else {
            if (!key.startsWith(Targets.KEY_PREFIX)) {
                return false;
            }
            ResourceLocation id = ResourceLocation.tryParse(key.substring(Targets.KEY_PREFIX.length()));
            SpawnRecord rec = id == null ? null : entry.spawns.get(id);
            if (rec == null) {
                return reject(player);
            }
            dim = ResourceKey.create(Registries.DIMENSION, id);
            Resolution r = SpawnResolver.resolveRecorded(player.server, dim, rec, false);
            if (!r.isOk()) {
                return reject(player);
            }
            pos = rec.pos();
            angle = rec.angle();
            forced = rec.forced();
        }

        if (entry.swap == null) {
            entry.swap = new SwapRecord(player.getRespawnDimension().location(), player.getRespawnPosition(),
                    player.getRespawnAngle(), player.isRespawnForced());
            data.touch();
        }
        final ResourceKey<Level> fdim = dim;
        final BlockPos fpos = pos;
        final float fangle = angle;
        final boolean fforced = forced;
        SpawnTracker.withoutRecording(() -> player.setRespawnPosition(fdim, fpos, fangle, fforced, false));
        offer.selected()[0] = key;
        Network.sendToPlayer(player, new RespawnSelectedS2C(key));
        return true;
    }

    private static boolean reject(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.travelersring.respawn_invalid").withStyle(ChatFormatting.RED), false);
        // refresh so the player sees current validity
        Offer offer = OFFERS.get(player.getUUID());
        List<RespawnOptionView> options = buildOptions(player);
        if (offer != null) {
            offer.options().clear();
            for (RespawnOptionView o : options) {
                offer.options().put(o.key(), o);
            }
        }
        Network.sendToPlayer(player, new RespawnOffersS2C(options));
        return false;
    }

    // ---- respawn / login cleanup ------------------------------------------------------------------------------

    /** Called for the NEW player object after vanilla has finished respawning (and validating) them. */
    public static void onRespawn(ServerPlayer player) {
        OFFERS.remove(player.getUUID());
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.peek(player.getUUID());
        if (entry != null && entry.swap != null) {
            restoreOriginal(player, entry, data);
        }
        Network.sendToPlayer(player, new RespawnOffersS2C(List.of()));
    }

    /** A living player must never be left with a swapped spawn (e.g. they disconnected on the death screen). */
    public static void onLogin(ServerPlayer player) {
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.peek(player.getUUID());
        if (entry != null && entry.swap != null && player.isAlive()) {
            restoreOriginal(player, entry, data);
        }
    }

    private static void restoreOriginal(ServerPlayer player, PlayerRingData.Entry entry, PlayerRingData data) {
        SwapRecord s = entry.swap;
        if (s == null) {
            return;
        }
        entry.swap = null;
        data.touch();
        SpawnTracker.withoutRecording(() -> {
            if (s.pos() == null) {
                player.setRespawnPosition(Level.OVERWORLD, null, 0f, false, false);
            } else {
                player.setRespawnPosition(ResourceKey.create(Registries.DIMENSION, s.dimension()), s.pos(), s.angle(), s.forced(), false);
            }
        });
    }
}
