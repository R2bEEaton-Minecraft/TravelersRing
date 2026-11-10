package cc.spea.travelersring.service;

import cc.spea.travelersring.TravelersRing;
import cc.spea.travelersring.config.RingConfig;
import cc.spea.travelersring.data.PlayerRingData;
import cc.spea.travelersring.data.PlayerRingData.RetainedStack;
import cc.spea.travelersring.data.PlayerRingData.SlotKind;
import cc.spea.travelersring.ring.RingLocator;
import cc.spea.travelersring.ring.RingTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nether Star ring death retention: keep ONLY the held items (selected hotbar slot + offhand) and armor.
 *
 * <p>Design (chosen to make duplication/loss structurally impossible):</p>
 * <ol>
 *   <li>At {@link LivingDeathEvent} we note whether a Nether Star ring is EQUIPPED in Curios and take references to the
 *       six candidate stacks. Nothing is modified yet.</li>
 *   <li>At {@link LivingDropsEvent} (which only fires for a real death, after vanilla/Curios decided what drops) we
 *       look for the ItemEntities holding exactly those stacks and REMOVE them from the drop list. We only retain what we
 *       actually found in the drop list, so items another mod already took (or that were destroyed by Curse of
 *       Vanishing) can never be duplicated, and anything we take can never also drop.</li>
 *   <li>Retained stacks are written to {@link PlayerRingData} immediately (survives crash/disconnect) and restored on
 *       respawn into their original slots; if a slot is taken they go to the inventory, and if that is full they are
 *       dropped at the player's feet. They are never deleted.</li>
 * </ol>
 *
 * <p>The ring itself and every other Curios item are untouched here: Curios' own drop rules/config decide them. With
 * {@code keepInventory} on, vanilla keeps everything and this class does nothing.</p>
 */
public final class DeathRetention {
    private record Candidate(SlotKind kind, int index, ItemStack ref, ItemStack copy) {
    }

    private static final Map<UUID, List<Candidate>> DYING = new HashMap<>();

    private DeathRetention() {
    }

    public static void forget(UUID id) {
        DYING.remove(id);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DYING.remove(player.getUUID()); // always start clean; a cancelled earlier death must not leak
        if (!RingConfig.RETAIN_HELD_AND_ARMOR.get()
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                || !RingLocator.hasEquipped(player, RingTier.NETHER_STAR)) {
            return;
        }
        Inventory inv = player.getInventory();
        List<Candidate> list = new ArrayList<>();
        add(list, SlotKind.MAIN_HAND, inv.selected, inv.items.get(inv.selected));
        add(list, SlotKind.OFF_HAND, 0, inv.offhand.get(0));
        for (int i = 0; i < inv.armor.size(); i++) {
            add(list, SlotKind.ARMOR, i, inv.armor.get(i));
        }
        if (!list.isEmpty()) {
            DYING.put(player.getUUID(), list);
        }
    }

    private static void add(List<Candidate> list, SlotKind kind, int index, ItemStack stack) {
        if (!stack.isEmpty()) {
            list.add(new Candidate(kind, index, stack, stack.copy()));
        }
    }

    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<Candidate> candidates = DYING.remove(player.getUUID());
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        Collection<ItemEntity> drops = event.getDrops();
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.entry(player.getUUID());
        List<ItemEntity> taken = new ArrayList<>();
        List<Candidate> unmatched = new ArrayList<>();

        // Pass 1: exact stack-instance match (vanilla drops the very same ItemStack object).
        for (Candidate c : candidates) {
            ItemEntity hit = null;
            for (ItemEntity e : drops) {
                if (!taken.contains(e) && e.getItem() == c.ref()) {
                    hit = e;
                    break;
                }
            }
            if (hit != null) {
                taken.add(hit);
                entry.retained.add(new RetainedStack(c.kind(), c.index(), hit.getItem().copy()));
            } else {
                unmatched.add(c);
            }
        }
        // Pass 2: a mod swapped the instance for an equal copy. Match by value, each drop at most once.
        for (Candidate c : unmatched) {
            for (ItemEntity e : drops) {
                if (!taken.contains(e) && ItemStack.matches(e.getItem(), c.copy())) {
                    taken.add(e);
                    entry.retained.add(new RetainedStack(c.kind(), c.index(), e.getItem().copy()));
                    break;
                }
            }
        }
        if (!taken.isEmpty()) {
            drops.removeAll(taken);
            data.touch();
        }
    }

    /** Puts retained items back after respawn (or on login if a respawn event was somehow missed). */
    public static int restore(ServerPlayer player) {
        PlayerRingData data = PlayerRingData.get(player.server);
        PlayerRingData.Entry entry = data.peek(player.getUUID());
        if (entry == null || entry.retained.isEmpty()) {
            return 0;
        }
        List<RetainedStack> pending = new ArrayList<>(entry.retained);
        entry.retained.clear();
        data.touch();
        Inventory inv = player.getInventory();
        for (RetainedStack r : pending) {
            ItemStack stack = r.stack().copy();
            boolean placed = switch (r.kind()) {
                case MAIN_HAND -> {
                    int i = Math.max(0, Math.min(8, r.index()));
                    if (inv.items.get(i).isEmpty()) {
                        inv.items.set(i, stack);
                        inv.selected = i;
                        yield true;
                    }
                    yield false;
                }
                case OFF_HAND -> {
                    if (inv.offhand.get(0).isEmpty()) {
                        inv.offhand.set(0, stack);
                        yield true;
                    }
                    yield false;
                }
                case ARMOR -> {
                    int i = r.index();
                    if (i >= 0 && i < inv.armor.size() && inv.armor.get(i).isEmpty()) {
                        inv.armor.set(i, stack);
                        yield true;
                    }
                    yield false;
                }
            };
            if (!placed && !inv.add(stack)) {
                // never delete an item: drop it at the player's feet as a last resort
                player.drop(stack, false);
                TravelersRing.LOGGER.warn("Retained item {} for {} had no free slot; dropped at feet", stack, player.getGameProfile().getName());
            }
        }
        player.inventoryMenu.broadcastChanges();
        return pending.size();
    }
}
