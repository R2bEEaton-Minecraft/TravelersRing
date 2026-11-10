package cc.spea.travelersring.service;

import cc.spea.travelersring.config.RingConfig;
import cc.spea.travelersring.config.RingConfig.CostMode;
import cc.spea.travelersring.ring.RingTier;

/** Pure cost maths. Settings are passed in so tests can exercise every mode without touching the config file. */
public final class CostCalculator {
    private CostCalculator() {
    }

    public record Settings(CostMode mode, int flatCost, double perBlockCost, int perBlockMaxCost,
                           double netheriteMultiplier, double netherStarMultiplier) {
        public static Settings fromConfig() {
            return new Settings(RingConfig.COST_MODE.get(), RingConfig.FLAT_COST.get(), RingConfig.PER_BLOCK_COST.get(),
                    RingConfig.PER_BLOCK_MAX_COST.get(), RingConfig.NETHERITE_MULTIPLIER.get(), RingConfig.NETHER_STAR_MULTIPLIER.get());
        }
    }

    /**
     * XP points for a "teleport to spawn" action. Return and respawn-choice actions never call this: they are free.
     *
     * @param fromScale / toScale dimension coordinate scales (Overworld 1.0, Nether 8.0). Only used in PER_BLOCK mode.
     */
    public static int cost(Settings s, RingTier tier, double fromX, double fromZ, double fromScale,
                           double toX, double toZ, double toScale) {
        double base;
        if (s.mode() == CostMode.FLAT) {
            base = s.flatCost();
        } else {
            double dx = fromX * fromScale - toX * toScale;
            double dz = fromZ * fromScale - toZ * toScale;
            base = Math.sqrt(dx * dx + dz * dz) * s.perBlockCost();
            if (s.perBlockMaxCost() > 0) {
                base = Math.min(base, s.perBlockMaxCost());
            }
        }
        double mult = switch (tier) {
            case BASE -> 1.0;
            case NETHERITE -> s.netheriteMultiplier();
            case NETHER_STAR -> s.netherStarMultiplier();
        };
        double raw = base * mult;
        if (raw <= 1e-9) {
            return 0;
        }
        // ceil with a tiny epsilon so 0.1 * 30 style float noise cannot add a point
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) Math.ceil(raw - 1e-9)));
    }
}
