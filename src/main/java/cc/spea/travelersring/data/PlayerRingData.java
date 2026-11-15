package cc.spea.travelersring.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-level persistent per-player data (stored in the Overworld's data storage, file
 * {@code data/travelersring_players.dat}). Server-authoritative; clients never see or write this directly.
 *
 * <ul>
 *   <li><b>spawns</b>: the latest successfully-set spawnpoint PER dimension (vanilla only remembers one).</li>
 *   <li><b>anchors</b>: the latest respawn anchor the player CHARGED per dimension. Ring teleports only (the PDF's
 *       "last interacted Respawn Anchor"); never offered as a death respawn and never changes the real spawn.</li>
 *   <li><b>retained</b>: held/armor items pulled out of a death drop list for a Nether Star ring wearer,
 *       persisted so a crash/disconnect between death and respawn cannot lose them.</li>
 *   <li><b>swap</b>: the player's real spawnpoint while a death-screen respawn choice temporarily overrides it,
 *       persisted so it can always be restored.</li>
 * </ul>
 */
public final class PlayerRingData extends SavedData {
    public static final String NAME = "travelersring_players";

    /**
     * @param block the block the spawn was set on (e.g. {@code minecraft:red_bed}, {@code enigmaticlegacy:end_anchor}),
     *              or null if unknown (records from before 1.0.3). Lets modded spawn blocks that don't implement Forge's
     *              respawn-position hook still be recognised later.
     */
    public record SpawnRecord(BlockPos pos, float angle, boolean forced, long time, @Nullable ResourceLocation block) {
        public SpawnRecord(BlockPos pos, float angle, boolean forced, long time) {
            this(pos, angle, forced, time, null);
        }
    }

    public enum SlotKind {
        MAIN_HAND("main"), OFF_HAND("off"), ARMOR("armor");

        public final String id;

        SlotKind(String id) {
            this.id = id;
        }

        static SlotKind byId(String id) {
            for (SlotKind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            return null;
        }
    }

    /** index: hotbar slot for MAIN_HAND; armor index (0=feet..3=head) for ARMOR; unused for OFF_HAND. */
    public record RetainedStack(SlotKind kind, int index, ItemStack stack) {
    }

    /** The player's real vanilla spawn, saved while a respawn choice is overriding it. pos == null means "no spawn". */
    public record SwapRecord(ResourceLocation dimension, @Nullable BlockPos pos, float angle, boolean forced) {
    }

    public static final class Entry {
        public final Map<ResourceLocation, SpawnRecord> spawns = new LinkedHashMap<>();
        public final Map<ResourceLocation, SpawnRecord> anchors = new LinkedHashMap<>();
        public final List<RetainedStack> retained = new ArrayList<>();
        @Nullable
        public SwapRecord swap;

        boolean isEmpty() {
            return spawns.isEmpty() && anchors.isEmpty() && retained.isEmpty() && swap == null;
        }
    }

    private final Map<UUID, Entry> players = new LinkedHashMap<>();

    public PlayerRingData() {
    }

    public static PlayerRingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlayerRingData::load, PlayerRingData::new, NAME);
    }

    public Entry entry(UUID id) {
        return players.computeIfAbsent(id, k -> new Entry());
    }

    @Nullable
    public Entry peek(UUID id) {
        return players.get(id);
    }

    public void touch() {
        setDirty();
    }

    // ---- persistence ------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> e : players.entrySet()) {
            Entry en = e.getValue();
            if (en.isEmpty()) {
                continue;
            }
            CompoundTag p = new CompoundTag();
            p.put("Id", NbtUtils.createUUID(e.getKey()));

            p.put("Spawns", saveSpawns(en.spawns));
            if (!en.anchors.isEmpty()) {
                p.put("Anchors", saveSpawns(en.anchors));
            }

            ListTag retained = new ListTag();
            for (RetainedStack r : en.retained) {
                CompoundTag t = new CompoundTag();
                t.putString("Slot", r.kind().id);
                t.putInt("Index", r.index());
                t.put("Stack", r.stack().save(new CompoundTag()));
                retained.add(t);
            }
            p.put("Retained", retained);

            if (en.swap != null) {
                CompoundTag t = new CompoundTag();
                t.putString("Dim", en.swap.dimension().toString());
                if (en.swap.pos() != null) {
                    t.put("Pos", NbtUtils.writeBlockPos(en.swap.pos()));
                }
                t.putFloat("Angle", en.swap.angle());
                t.putBoolean("Forced", en.swap.forced());
                p.put("Swap", t);
            }
            list.add(p);
        }
        root.put("Players", list);
        return root;
    }

    public static PlayerRingData load(CompoundTag root) {
        PlayerRingData data = new PlayerRingData();
        ListTag list = root.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            if (!p.contains("Id")) {
                continue;
            }
            UUID id = NbtUtils.loadUUID(p.get("Id"));
            Entry en = new Entry();

            loadSpawns(p.getList("Spawns", Tag.TAG_COMPOUND), en.spawns);
            loadSpawns(p.getList("Anchors", Tag.TAG_COMPOUND), en.anchors);

            ListTag retained = p.getList("Retained", Tag.TAG_COMPOUND);
            for (int j = 0; j < retained.size(); j++) {
                CompoundTag t = retained.getCompound(j);
                SlotKind kind = SlotKind.byId(t.getString("Slot"));
                ItemStack stack = ItemStack.of(t.getCompound("Stack"));
                if (kind != null && !stack.isEmpty()) {
                    en.retained.add(new RetainedStack(kind, t.getInt("Index"), stack));
                }
            }

            if (p.contains("Swap", Tag.TAG_COMPOUND)) {
                CompoundTag t = p.getCompound("Swap");
                ResourceLocation dim = ResourceLocation.tryParse(t.getString("Dim"));
                if (dim != null) {
                    BlockPos pos = t.contains("Pos") ? NbtUtils.readBlockPos(t.getCompound("Pos")) : null;
                    en.swap = new SwapRecord(dim, pos, t.getFloat("Angle"), t.getBoolean("Forced"));
                }
            }
            data.players.put(id, en);
        }
        return data;
    }

    private static ListTag saveSpawns(Map<ResourceLocation, SpawnRecord> map) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, SpawnRecord> s : map.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", s.getKey().toString());
            t.put("Pos", NbtUtils.writeBlockPos(s.getValue().pos()));
            t.putFloat("Angle", s.getValue().angle());
            t.putBoolean("Forced", s.getValue().forced());
            t.putLong("Time", s.getValue().time());
            if (s.getValue().block() != null) {
                t.putString("Block", s.getValue().block().toString());
            }
            list.add(t);
        }
        return list;
    }

    private static void loadSpawns(ListTag list, Map<ResourceLocation, SpawnRecord> into) {
        for (int j = 0; j < list.size(); j++) {
            CompoundTag t = list.getCompound(j);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("Dim"));
            if (dim == null || !t.contains("Pos")) {
                continue;
            }
            ResourceLocation block = t.contains("Block") ? ResourceLocation.tryParse(t.getString("Block")) : null;
            into.put(dim, new SpawnRecord(NbtUtils.readBlockPos(t.getCompound("Pos")), t.getFloat("Angle"),
                    t.getBoolean("Forced"), t.getLong("Time"), block));
        }
    }
}
