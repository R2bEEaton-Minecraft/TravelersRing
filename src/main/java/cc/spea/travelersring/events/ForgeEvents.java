package cc.spea.travelersring.events;

import cc.spea.travelersring.TravelersRing;
import cc.spea.travelersring.service.DeathRetention;
import cc.spea.travelersring.service.RespawnChoice;
import cc.spea.travelersring.service.RingService;
import cc.spea.travelersring.service.SpawnTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side event wiring. Ordering notes are on each handler. */
@Mod.EventBusSubscriber(modid = TravelersRing.MOD_ID)
public final class ForgeEvents {
    private ForgeEvents() {
    }

    /**
     * Record real spawn-setting events. LOWEST so every other mod has had its chance to cancel; cancelled events are
     * skipped (receiveCanceled defaults to false).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnSet(PlayerSetSpawnEvent event) {
        SpawnTracker.onSpawnSet(event);
    }

    /** HIGHEST so we look at the player (and their equipped ring) before anything else reacts to the death. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        DeathRetention.onDeath(event);
        RespawnChoice.onDeath(event);
    }

    /**
     * HIGHEST so we remove retained stacks from the drop list before Curios or other mods add/process their own
     * drops. We only ever remove stacks we matched against the player's own held/armor items.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(LivingDropsEvent event) {
        DeathRetention.onDrops(event);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RespawnChoice.onRespawn(player);
            DeathRetention.restore(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpawnTracker.syncWithVanilla(player);
            if (player.isAlive()) {
                RespawnChoice.onLogin(player);
                DeathRetention.restore(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RingService.forget(player.getUUID());
            DeathRetention.forget(player.getUUID());
            RespawnChoice.forget(player.getUUID());
        }
    }
}
