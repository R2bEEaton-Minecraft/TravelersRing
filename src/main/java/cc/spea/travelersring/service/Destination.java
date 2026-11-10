package cc.spea.travelersring.service;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** A fully validated place to put a player. */
public record Destination(ServerLevel level, double x, double y, double z, float yaw, float pitch, Kind kind) {

    public enum Kind {
        BED, ANCHOR, FORCED_SPAWN, WORLD_SPAWN, RETURN_POINT, DEFAULT
    }

    public enum Failure {
        DIMENSION_MISSING("dimension_missing"),
        NO_SPAWN("no_spawn"),
        SPAWN_BLOCK_MISSING("spawn_block_missing"),
        BED_UNUSABLE("bed_unusable"),
        BED_OBSTRUCTED("bed_obstructed"),
        ANCHOR_EMPTY("anchor_empty"),
        ANCHOR_UNUSABLE("anchor_unusable"),
        ANCHOR_OBSTRUCTED("anchor_obstructed"),
        UNSAFE("unsafe");

        public final String key;

        Failure(String key) {
            this.key = key;
        }

        public static Failure byKey(String key) {
            for (Failure f : values()) {
                if (f.key.equals(key)) {
                    return f;
                }
            }
            return UNSAFE;
        }
    }

    /** Either a destination or the reason there is none. */
    public record Resolution(@Nullable Destination destination, @Nullable Failure failure, Kind kind) {
        public static Resolution ok(Destination d) {
            return new Resolution(d, null, d.kind());
        }

        public static Resolution fail(Failure f, Kind kind) {
            return new Resolution(null, f, kind);
        }

        public boolean isOk() {
            return destination != null;
        }
    }
}
