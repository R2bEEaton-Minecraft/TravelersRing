# Traveler's Ring

A Forge 1.20.1 mod (needs Curios), built for Applecar.
The ring teleports you to your spawn for some XP and remembers where you were, so you can come back for free.
Two smithing upgrades add the Nether, then every dimension, keeping your gear when you die, and choosing where you respawn.

> **Status:** everything in `BRIEF.md` is built. The art is placeholder until the client sends textures, and "Traveler's Ring"
> is a working name the client hasn't confirmed. [ASSUMPTIONS.md](ASSUMPTIONS.md) lists the decisions made along the way and
> [VERIFICATION.md](VERIFICATION.md) says exactly what was and wasn't tested.

## Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.0 or newer 47.x (built and tested on 47.4.0) |
| Curios | **Required.** Built and tested against `5.14.1+1.20.1` (declared minimum `5.14.1`) |
| Java | 17 |

Install the JAR (`dist/travelersring-1.0.0.jar`) **and Curios** in `mods/` on the server **and** every client.
The mod is required on both sides (it has a network protocol and a client UI).

The mod itself adds the `ring` Curios slot to players (`data/travelersring/curios/entities/player.json`).
Curios alone defines slot *types* but gives no entity any; without this a bare Curios install would have no ring slot.
If a modpack already assigns a `ring` slot, the two simply coexist.

## The three rings

| Tier | Item | Works in | XP cost (default) | Respawn/death features |
|---|---|---|---|---|
| Base | Traveler's Ring | Overworld | 10 XP points (flat) | none |
| First upgrade | Netherite Traveler's Ring | Overworld + Nether | half (5) | none |
| Second upgrade | Nether Star Traveler's Ring | **any** dimension | half (5) *(provisional)* | keeps held items + armor on death; pick any valid spawn to respawn at, free |

* "Free return" and "delete saved location" are available and free on **all** tiers.
* There is **no cooldown**.
* XP is measured in **experience points**, not levels (10 points is a fraction of one level at level 7+).

## Controls

* **Equipped** (Curios `ring` slot): press the **"Use Traveler's Ring"** key. Default **`R`**, rebindable in
  *Options → Controls → Traveler's Ring*.
* **Held**: right-click with the ring in either hand. (Right-click does **not** auto-equip; equip it from the Curios
  inventory screen.)

The menu shows what the server says is currently possible:

* No saved location → one **"Go to ..."** button per destination, with its XP cost (greyed out with the reason if not available).
* Saved location → **"Return to saved location (free)"** and **"Delete saved location"** (asks to confirm).
  A new teleport is not offered until the saved location is returned to or deleted.

If a keybind is pressed and several rings are equipped, the highest tier is used.

## How it works

1. **Teleport to spawn** (costs XP): your departure position, rotation **and dimension** are written into *that ring's* item
   NBT, then you are moved. The saved return lives on the ring, so it survives logout, restarts, chests, trading and
   smithing upgrades, and is not a single global per-player value.
2. **Return** (free): moves you back to the saved point (re-checked for safety) and clears it.
3. **Delete** (free, works in any dimension): clears it.

A failed teleport never charges XP and never overwrites or clears a saved location.

### Destinations per tier

* **Base** – your Overworld spawnpoint (bed / `/spawnpoint`); falls back to the Overworld **world spawn** if that bed is
  missing, obstructed, etc.
* **Netherite** – in the Overworld: as Base. In the Nether: your **latest recorded Nether spawn** (normally the
  last Respawn Anchor you charged and activated; it must still be charged) **or** your Overworld spawn.
* **Nether Star** – the **latest recorded spawnpoint for every dimension** (including modded dimensions) plus the
  Overworld world-spawn fallback.

Ring teleports **never consume Respawn Anchor charges** and never change your real spawnpoint.

### Latest spawnpoint per dimension

Vanilla remembers one spawnpoint. The mod keeps a persistent record of the latest *successfully set* spawnpoint in
**each dimension** per player (`world/data/travelersring_players.dat`). It listens to Forge's `PlayerSetSpawnEvent`, which
vanilla fires only for real spawn-setting actions (sleeping in a bed – even by day –, activating a *charged* anchor,
`/spawnpoint`). Clicking an uncharged anchor or a bed that fails is not recorded. Your *current* vanilla spawn is adopted
on login. Spawnpoints from before the mod was installed cannot be recovered.

### Validation (all server-side)

Missing/obstructed beds, empty/missing anchors, absent dimensions, unsafe or enclosed return spots (the exact point is tried,
then a small safe-spot search, otherwise the action is refused and the saved point kept), world border, insufficient XP,
wrong dimension for the tier, duplicate or replayed requests (second "teleport" is refused while a location is saved;
second "return" finds nothing saved), a dead player, a forged target key. See `VERIFICATION.md` for the tests.

### Nether Star ring: death and respawn

If a Nether Star ring is **equipped in a Curios slot when you die**:

* **Retention.** Only the **selected hotbar item, offhand, and the four armor slots** are kept (put back in their original
  slots on respawn). Everything else drops normally. Curse of Vanishing items are still destroyed. With `keepInventory`
  on, vanilla already keeps everything and the mod does nothing extra. Retained stacks are persisted immediately, only
  ever taken from items that were really about to drop (no duplication), and are dropped at your feet rather than deleted if
  a slot is somehow taken.
* **The ring itself and other Curios** are decided by **Curios' own drop rules/settings**, not by this mod. By default
  Curios drops them on death unless `keepInventory`/Curios config says otherwise. A config switch
  (`death.ring_keeps_itself`, default **off**) makes the Nether Star ring declare itself "always keep" through Curios'
  own drop-rule API, matching the design doc's wording. Other mods' Curios overrides still win. See ASSUMPTIONS.md.
* **Respawn choice.** The death screen gets a **"Respawn at: ..."** button that cycles through *Default spawn*, every
  recorded spawnpoint, and *World spawn*. It is free. The server validates the choice and then lets vanilla's own respawn
  code re-validate it (so a bed/anchor destroyed on the death screen falls back to the world spawn, never strands you),
  then restores your real spawnpoint. Respawning at a charged anchor consumes a charge exactly as vanilla does.

## Recipes (all data-driven JSON; override with a datapack)

| Result | Recipe |
|---|---|
| Traveler's Ring | **Not craftable.** Found in loot (below); `/give` or creative tab for testing |
| Netherite Traveler's Ring | Smithing Table: **Netherite Upgrade Smithing Template** + Traveler's Ring + **Netherite Ingot** |
| Nether Star Upgrade Smithing Template | Crafting (shapeless): **Netherite Upgrade Smithing Template** + **Nether Star** *(provisional, see ASSUMPTIONS)* |
| Nether Star Traveler's Ring | Smithing Table: **Nether Star Upgrade Smithing Template** + Netherite Traveler's Ring + **Netherite Ingot** *(addition provisional)* |

Smithing preserves all NBT, so a saved return location survives upgrading (tested).
Files: `src/main/resources/data/travelersring/recipes/*.json`.

## Loot

A global loot modifier adds a base ring to configured **chest loot tables** with a low chance (default **5 %** per opened
target chest, Overworld only). Default targets are Overworld dungeon/structure chests: simple dungeon, abandoned mineshaft,
stronghold (corridor/crossing/library), desert pyramid, jungle temple, woodland mansion, pillager outpost, ancient city,
ruined portal, shipwreck treasure, big underwater ruin, rare trail ruins.

> **Loot-table ids are not structure ids.** `minecraft:desert_pyramid` is a *structure*; its chest is rolled from the
> *loot table* `minecraft:chests/desert_pyramid`. The config takes **loot-table ids** (the ones that look like
> `namespace:chests/...`). Wildcards work: `somemod:chests/*`. Nothing is promised for arbitrary modded structures; add
> their chest loot-table ids to opt in.

## Configuration

`config/travelersring-common.toml` (server side only matters; created on first launch). Highlights:

| Key | Default | Meaning |
|---|---|---|
| `xp.cost_mode` | `FLAT` | `FLAT` or `PER_BLOCK` |
| `xp.flat_cost` | `10` | XP points per teleport (Base ring) |
| `xp.per_block_cost` | `10.0` | XP points per horizontal block in `PER_BLOCK` mode. **10.0 is very steep** (client's literal words, ≈ level 63 for 1,000 blocks); try `0.05` |
| `xp.per_block_max_cost` | `0` | cap on the base cost before tier multipliers (0 = none) |
| `xp.netherite_multiplier` | `0.5` | first upgrade cost factor |
| `xp.nether_star_multiplier` | `0.5` | second upgrade cost factor (provisional) |
| `xp.creative_free` | `true` | creative players never pay |
| `usage.base_dimensions` / `netherite_dimensions` | Overworld / Overworld+Nether | where each tier works |
| `usage.nether_star_any_dimension` | `true` | else uses `nether_star_dimensions` |
| `usage.blocked_dimensions` | `[]` | dimensions where no ring works |
| `safety.search_radius` / `search_vertical` | `3` / `4` | how far to look for a safe spot if the return point is blocked |
| `death.retain_held_and_armor` | `true` | the Nether Star retention |
| `death.ring_keeps_itself` | `false` | ring asks Curios to keep it on death |
| `death.respawn_choice` | `true` | death-screen respawn picker |
| `loot.enabled` / `loot.chance` | `true` / `0.05` | loot injection |
| `loot.target_loot_tables` | see above | chest **loot-table** ids (wildcards allowed) |
| `loot.dimensions` | `[minecraft:overworld]` | only inject for chests in these dimensions (empty = any) |

**Cost math.** `FLAT`: cost = `flat_cost`. `PER_BLOCK`: cost = `ceil(distance × per_block_cost)`, capped by
`per_block_max_cost`; distance is **horizontal** (X/Z), and for cross-dimension trips both points are first converted to
Overworld-equivalent coordinates using each dimension's coordinate scale (Nether ×8). Then the tier multiplier is applied
and the result rounded **up** (never silently free). Return, delete and respawn choice never cost anything.

| Trip (Base ring) | `FLAT` 10 | `PER_BLOCK` 10.0/block (literal) | `PER_BLOCK` 0.05/block |
|---|---|---|---|
| 10 blocks | 10 | 100 | 1 |
| 200 blocks | 10 | 2,000 (≈ level 35) | 10 |
| 1,000 blocks | 10 | 10,000 (≈ level 63) | 50 |
| 5,000 blocks | 10 | 50,000 | 250 |

(Netherite and Nether Star rings pay half by default; each result is rounded up.)

## Building

```
./gradlew build            # -> build/libs/travelersring-1.0.0.jar (reobfuscated, distributable)
```
JDK 17 is used via Gradle's toolchain. The first build downloads Forge and Curios.
The released JAR is also copied to `dist/`. See [TESTING.md](TESTING.md) to run the tests.

## Placeholder art

All textures are **temporary** and carry a magenta 2×2 marker in the top-left so they are obvious in-game:
`assets/travelersring/textures/item/{travelers_ring, travelers_ring_netherite, travelers_ring_nether_star,
nether_star_upgrade_smithing_template, empty_slot_ring}.png`. They are generated by `tools/make_placeholder_textures.py`.
Replace the five PNGs (keep names and 16×16 size) when the client's art arrives; no code changes are needed.
(Vanilla names every smithing template "Smithing Template"; the custom upgrade name is shown in its tooltip.)

## Project layout

```
src/main/java/cc/spea/travelersring/
  ring/      tiers, per-ring NBT (RingData), locating the equipped/held ring
  service/   RingService (all actions), Targets, SpawnResolver, SafeSpot, CostCalculator, XpHelper,
             SpawnTracker, DeathRetention, RespawnChoice, Teleporter
  data/      PlayerRingData (world SavedData: per-dimension spawns, retained items, spawn swap)
  net/       SimpleChannel packets (client sends intent only; server decides)
  client/    keybind, ring screen, death-screen picker
  loot/      global loot modifier
src/gametest/   in-world GameTests (dev only, never in the JAR)
```
