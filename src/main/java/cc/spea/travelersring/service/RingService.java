package cc.spea.travelersring.service;

import cc.spea.travelersring.TravelersRing;
import cc.spea.travelersring.config.RingConfig;
import cc.spea.travelersring.net.MenuData;
import cc.spea.travelersring.net.Network;
import cc.spea.travelersring.net.OpenMenuS2C;
import cc.spea.travelersring.ring.RingData;
import cc.spea.travelersring.ring.RingLocator;
import cc.spea.travelersring.ring.RingLocator.RingHandle;
import cc.spea.travelersring.ring.RingSource;
import cc.spea.travelersring.ring.RingTier;
import cc.spea.travelersring.ring.ReturnPoint;
import cc.spea.travelersring.service.Destination.Resolution;
import cc.spea.travelersring.service.Targets.Target;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative ring logic. Every client request funnels through {@link #perform}, which re-derives the ring,
 * the allowed targets, the destination and the cost from server state. Nothing the client says is trusted beyond
 * "which action" and "which target key".
 *
 * <p>All of this runs synchronously on the server thread: there is no warm-up/queue, so two identical packets can
 * never both succeed (the second sees the state the first left behind).</p>
 */
public final class RingService {
    public enum Action {
        TELEPORT_SPAWN, RETURN, DELETE;

        public static Action byOrdinal(int i) {
            Action[] v = values();
            return i >= 0 && i < v.length ? v[i] : null;
        }
    }

    public enum Status {
        SUCCESS, NO_RING, NOT_ALLOWED_HERE, ALREADY_SAVED, NOTHING_SAVED, SAVED_UNREADABLE, INVALID_TARGET,
        DESTINATION_UNAVAILABLE, NOT_ENOUGH_XP, TELEPORT_FAILED, NOT_ALIVE
    }

    public record Result(Status status, Component message) {
        public boolean ok() {
            return status == Status.SUCCESS;
        }
    }

    /** Menu-open spam guard (NOT a gameplay cooldown: actions are never throttled). */
    private static final int OPEN_MIN_TICKS = 5;
    private static final Map<UUID, Integer> LAST_OPEN = new HashMap<>();

    private RingService() {
    }

    public static void forget(UUID player) {
        LAST_OPEN.remove(player);
    }

    // ---- menu -------------------------------------------------------------------------------------------------

    public static void openMenu(ServerPlayer player, RingSource source) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        int now = player.server.getTickCount();
        Integer last = LAST_OPEN.get(player.getUUID());
        if (last != null && now >= last && now - last < OPEN_MIN_TICKS) {
            return;
        }
        LAST_OPEN.put(player.getUUID(), now);

        Optional<RingHandle> handle = RingLocator.find(player, source);
        if (handle.isEmpty()) {
            player.displayClientMessage(Component.translatable(source == RingSource.CURIOS
                    ? "message.travelersring.no_ring_equipped" : "message.travelersring.no_ring").withStyle(ChatFormatting.RED), true);
            return;
        }
        Network.sendToPlayer(player, new OpenMenuS2C(buildMenu(player, handle.get(), CostCalculator.Settings.fromConfig())));
    }

    public static MenuData buildMenu(ServerPlayer player, RingHandle handle, CostCalculator.Settings settings) {
        RingTier tier = handle.tier();
        ItemStack stack = handle.stack();
        boolean allowed = Usage.allowedIn(tier, player.level().dimension());
        Optional<ReturnPoint> saved = RingData.getReturn(stack);
        boolean unreadable = saved.isEmpty() && RingData.hasRawReturn(stack);
        MenuData.SavedView savedView = saved.map(rp -> new MenuData.SavedView(rp.dimension(), (int) Math.floor(rp.x()),
                (int) Math.floor(rp.y()), (int) Math.floor(rp.z()))).orElse(null);

        List<MenuData.TargetView> views = new ArrayList<>();
        boolean waived = isFree(player);
        if (allowed && saved.isEmpty() && !unreadable) {
            for (Target t : Targets.compute(player, tier)) {
                int cost = 0;
                boolean valid = t.valid();
                int kind = t.resolution().kind().ordinal();
                int x = 0, y = 0, z = 0;
                boolean hasPos = false;
                if (valid) {
                    Destination d = t.resolution().destination();
                    cost = waived ? 0 : costTo(player, tier, d, settings);
                    x = (int) Math.floor(d.x());
                    y = (int) Math.floor(d.y());
                    z = (int) Math.floor(d.z());
                    hasPos = true;
                    kind = d.kind().ordinal();
                } else if (t.block() != null) {
                    x = t.block().getX();
                    y = t.block().getY();
                    z = t.block().getZ();
                    hasPos = true;
                }
                views.add(new MenuData.TargetView(t.key(), t.dimension(), kind, hasPos, x, y, z, valid,
                        valid ? "" : t.resolution().failure().key, cost, t.fallback()));
            }
        }
        return new MenuData(handle.source(), tier.level(), allowed, savedView, unreadable, views,
                XpHelper.totalPoints(player), waived);
    }

    // ---- actions ----------------------------------------------------------------------------------------------

    public static Result perform(ServerPlayer player, RingSource source, Action action, String targetKey) {
        return perform(player, source, action, targetKey, CostCalculator.Settings.fromConfig());
    }

    public static Result perform(ServerPlayer player, RingSource source, Action action, String targetKey, CostCalculator.Settings settings) {
        if (!player.isAlive() || player.isSpectator() || action == null) {
            return fail(player, Status.NOT_ALIVE, Component.translatable("message.travelersring.not_alive"));
        }
        Optional<RingHandle> found = RingLocator.find(player, source);
        if (found.isEmpty()) {
            return fail(player, Status.NO_RING, Component.translatable(source == RingSource.CURIOS
                    ? "message.travelersring.no_ring_equipped" : "message.travelersring.no_ring"));
        }
        RingHandle handle = found.get();
        return switch (action) {
            case DELETE -> doDelete(player, handle);
            case RETURN -> doReturn(player, handle);
            case TELEPORT_SPAWN -> doTeleportToSpawn(player, handle, targetKey == null ? "" : targetKey, settings);
        };
    }

    private static Result doDelete(ServerPlayer player, RingHandle handle) {
        // Deleting is free and allowed in any dimension (it moves nobody), so a ring can never be stuck with stale data.
        if (!RingData.hasRawReturn(handle.stack())) {
            return fail(player, Status.NOTHING_SAVED, Component.translatable("message.travelersring.nothing_saved"));
        }
        ItemStack updated = handle.stack().copy();
        RingData.clearReturn(updated);
        RingLocator.write(player, handle, updated);
        return succeed(player, Component.translatable("message.travelersring.deleted"));
    }

    private static Result doReturn(ServerPlayer player, RingHandle handle) {
        RingTier tier = handle.tier();
        if (!Usage.allowedIn(tier, player.level().dimension())) {
            return fail(player, Status.NOT_ALLOWED_HERE, Component.translatable("message.travelersring.not_allowed_here"));
        }
        Optional<ReturnPoint> saved = RingData.getReturn(handle.stack());
        if (saved.isEmpty()) {
            if (RingData.hasRawReturn(handle.stack())) {
                return fail(player, Status.SAVED_UNREADABLE, Component.translatable("message.travelersring.saved_unreadable"));
            }
            return fail(player, Status.NOTHING_SAVED, Component.translatable("message.travelersring.nothing_saved"));
        }
        Resolution res = SpawnResolver.resolveReturn(player.server, saved.get());
        if (!res.isOk()) {
            // The saved location is KEPT so the player can try again (e.g. after clearing the blockage) or delete it.
            return fail(player, Status.DESTINATION_UNAVAILABLE, Component.translatable("message.travelersring.return_unavailable",
                    Component.translatable("failure.travelersring." + res.failure().key)));
        }
        if (!Teleporter.teleport(player, res.destination())) {
            return fail(player, Status.TELEPORT_FAILED, Component.translatable("message.travelersring.teleport_failed"));
        }
        ItemStack updated = handle.stack().copy();
        RingData.clearReturn(updated);
        RingLocator.write(player, handle, updated);
        return succeed(player, Component.translatable("message.travelersring.returned"));
    }

    private static Result doTeleportToSpawn(ServerPlayer player, RingHandle handle, String key, CostCalculator.Settings settings) {
        RingTier tier = handle.tier();
        if (!Usage.allowedIn(tier, player.level().dimension())) {
            return fail(player, Status.NOT_ALLOWED_HERE, Component.translatable("message.travelersring.not_allowed_here"));
        }
        // A saved return must be used or deleted first. This also makes duplicate/replayed "teleport" requests harmless.
        if (RingData.getReturn(handle.stack()).isPresent() || RingData.hasRawReturn(handle.stack())) {
            return fail(player, Status.ALREADY_SAVED, Component.translatable("message.travelersring.already_saved"));
        }
        Optional<Target> target = Targets.find(player, tier, key);
        if (target.isEmpty()) {
            return fail(player, Status.INVALID_TARGET, Component.translatable("message.travelersring.invalid_target"));
        }
        Target t = target.get();
        if (!t.valid()) {
            return fail(player, Status.DESTINATION_UNAVAILABLE, Component.translatable("message.travelersring.target_unavailable",
                    Component.translatable("failure.travelersring." + t.resolution().failure().key)));
        }
        Destination dest = t.resolution().destination();
        int cost = isFree(player) ? 0 : costTo(player, tier, dest, settings);
        int have = XpHelper.totalPoints(player);
        if (cost > 0 && have < cost) {
            return fail(player, Status.NOT_ENOUGH_XP, Component.translatable("message.travelersring.not_enough_xp", cost, have));
        }

        // Commit: write the departure point, move, and only then charge. If the move fails, restore the ring untouched.
        ItemStack original = handle.stack().copy();
        ItemStack updated = handle.stack().copy();
        RingData.setReturn(updated, ReturnPoint.of(player));
        RingLocator.write(player, handle, updated);
        if (!Teleporter.teleport(player, dest)) {
            RingLocator.write(player, handle, original);
            return fail(player, Status.TELEPORT_FAILED, Component.translatable("message.travelersring.teleport_failed"));
        }
        if (!XpHelper.trySpend(player, cost)) {
            TravelersRing.LOGGER.warn("Pre-checked XP spend failed for {} (cost {}); teleport already done", player.getGameProfile().getName(), cost);
        }
        return succeed(player, Component.translatable("message.travelersring.teleported", cost));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    public static boolean isFree(ServerPlayer player) {
        return RingConfig.CREATIVE_FREE.get() && player.getAbilities().instabuild;
    }

    public static int costTo(ServerPlayer player, RingTier tier, Destination dest, CostCalculator.Settings settings) {
        double fromScale = player.level().dimensionType().coordinateScale();
        double toScale = dest.level().dimensionType().coordinateScale();
        return CostCalculator.cost(settings, tier, player.getX(), player.getZ(), fromScale, dest.x(), dest.z(), toScale);
    }

    private static Result fail(ServerPlayer player, Status status, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), false);
        return new Result(status, message);
    }

    private static Result succeed(ServerPlayer player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GREEN), true);
        return new Result(Status.SUCCESS, message);
    }
}
