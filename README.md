# Traveler's Ring

A Forge 1.20.1 mod (needs Curios), built for Applecar.
The ring teleports you to your spawn for some XP and remembers where you were, so you can come back for free.
Two smithing upgrades add the Nether, then every dimension, keeping your gear when you die, and choosing where you respawn.

Idea and design by Applecar. Code by R2bEEaton. Ring and template textures by [PorotoMz](https://x.com/PorotoMz).

Download: [Modrinth](https://modrinth.com/mod/travelers-ring)

> **Status:** 1.0.4. [VERIFICATION.md](VERIFICATION.md) says exactly what was and wasn't tested.

## Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.0 or newer 47.x (built and tested on 47.4.0) |
| Curios | **Required.** Built and tested against `5.14.1+1.20.1` (declared minimum `5.14.1`) |
| Java | 17 |

Install the JAR (`dist/travelersring-1.0.4.jar`) **and Curios** in `mods/` on the server **and** every client.
The mod is required on both sides (it has a network protocol and a client UI).

The mod itself adds the `ring` Curios slot to players (`data/travelersring/curios/entities/player.json`).
Curios alone defines slot *types* but gives no entity any; without this a bare Curios install would have no ring slot.
If a modpack already assigns a `ring` slot, the two simply coexist.

## The three rings

| Tier | Item | Works in | XP cost (default) | Respawn/death features |
|---|---|---|---|---|
| Base | Traveler's Ring | Overworld | 4 XP points per block | none |
| First upgrade | Netherite Traveler's Ring | Overworld + Nether | half (2 per block) | none |
| Second upgrade | Nether Star Traveler's Ring | **any** dimension | half (2 per block) *(provisional)* | keeps held items + armor on death; pick any valid spawn to respawn at, free |

* "Free return" and "delete saved location" are available and free on **all** tiers.
* There is **no cooldown**.
* XP is measured in **experience points**, not levels. The ring screen shows each cost as an XP bar plus the levels
  you would drop to, e.g. `Level 30 → 25 (-5)`.

## Controls

* **Equipped** (Curios `ring` slot): press the **"Use Traveler's Ring"** key. Default **`R`**, rebindable in
  *Options → Controls → Traveler's Ring*.
* **Held**: right-click with the ring in either hand. (Right-click does **not** auto-equip; equip it from the Curios
  inventory screen.)

The menu shows what the server says is currently possible:

* No saved location → one **"Teleport to ..."** button per destination with its XP cost (greyed out with the reason if not available).
  Under each button is an XP bar filled to that cost, with the level number centred above it, like the HUD, so you can see what 10 points actually looks like.
  The cost number turns red if you can't afford it. With several destinations, each one has its own cost, bar and
  level-drop line, each worked out from your current XP (they are alternatives, not added together). If there are too
  many to fit on screen, the bars are dropped and the level drop moves into each button's tooltip.
  An unavailable destination says "Unavailable" with the reason under it (e.g. no respawn anchor charged yet).
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
* **Netherite** – in the Overworld: as Base. In the Nether: the **last Respawn Anchor you charged or set your spawn
  at** (whichever was later; it must still be charged) **or** your Overworld spawn. Charging an anchor with glowstone is
  enough; you don't have to set your spawn there. Until you have used one, the Nether entry is greyed out and says why.
* **Nether Star** – the **latest recorded spawnpoint for every dimension** (including modded dimensions), the last
  charged anchor per dimension (as above), plus the Overworld world-spawn fallback.

**Modded spawn blocks** (e.g. Enigmatic Legacy's *Dimensional Anchor* in the End) work too. The ring remembers which
block you set your spawn on; as long as that same block is still there (and, if it has a charge, isn't empty), the ring
puts you on a safe spot next to it. The button shows the block's own name, e.g. "The End: Dimensional Anchor". Blocks in
`usage.charge_counts_blocks` (default: the Dimensional Anchor) also count when you only *charge* them, like the vanilla
Respawn Anchor. Tested against Enigmatic Legacy 2.30.1.

Ring teleports **never consume Respawn Anchor charges** and never change your real spawnpoint.

### Latest spawnpoint per dimension

Vanilla remembers one spawnpoint. The mod keeps a persistent record of the latest *successfully set* spawnpoint in
**each dimension** per player (`world/data/travelersring_players.dat`). It listens to Forge's `PlayerSetSpawnEvent`, which
vanilla fires only for real spawn-setting actions (sleeping in a bed – even by day –, activating a *charged* anchor,
`/spawnpoint`). Clicking an uncharged anchor or a bed that fails is not recorded. Separately, for **ring teleports only**, the last
anchor you *charged* in each dimension is remembered too (checked at the end of the tick: the click must really have
added charge). Charged-only anchors are not offered on the death screen, which lists real spawnpoints. Your *current* vanilla spawn is adopted
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
  own drop-rule API, matching the design doc's wording. Other mods' Curios overrides still win.
* **Respawn choice.** The death screen gets a **"Respawn at: ..."** button that cycles through *Default spawn*, every
  recorded spawnpoint, and *World spawn*. It is free. The server validates the choice and then lets vanilla's own respawn
  code re-validate it (so a bed/anchor destroyed on the death screen falls back to the world spawn, never strands you),
  then restores your real spawnpoint. Respawning at a charged anchor consumes a charge exactly as vanilla does.

## Recipes (all data-driven JSON; override with a datapack)

| Result | Recipe |
|---|---|
| Traveler's Ring | **Not craftable.** Found in loot (below); `/give` or creative tab for testing |
| Netherite Traveler's Ring | Smithing Table: **Netherite Upgrade Smithing Template** + Traveler's Ring + **Netherite Ingot** |
| Nether Star Upgrade Smithing Template | Found in **End City** chests (below). Copy it like vanilla's templates: **7 Diamonds** around the **template** (top middle) and **End Stone** (centre) makes **2** |
| Nether Star Traveler's Ring | Smithing Table: **Nether Star Upgrade Smithing Template** + Netherite Traveler's Ring + **Nether Star** |

Smithing preserves all NBT, so a saved return location survives upgrading (tested).
Files: `src/main/resources/data/travelersring/recipes/*.json`.

## Loot

A global loot modifier adds a base ring to configured **chest loot tables** with a low chance (default **5 %** per opened
target chest, Overworld only). Default targets are Overworld dungeon/structure chests: simple dungeon, abandoned mineshaft,
stronghold (corridor/crossing/library), desert pyramid, jungle temple, woodland mansion, pillager outpost, ancient city,
ruined portal, shipwreck treasure, big underwater ruin, rare trail ruins.

The **Nether Star Upgrade Smithing Template** is added to End City chests (`minecraft:chests/end_city_treasure`)
with a **20 %** chance per opened chest (`template_loot.*` in the config).

> **Loot-table ids are not structure ids.** `minecraft:desert_pyramid` is a *structure*; its chest is rolled from the
> *loot table* `minecraft:chests/desert_pyramid`. The config takes **loot-table ids** (the ones that look like
> `namespace:chests/...`). Wildcards work: `somemod:chests/*`. Nothing is promised for arbitrary modded structures; add
> their chest loot-table ids to opt in.

## Configuration

`config/travelersring-common.toml` (server side only matters; created on first launch). Highlights:

| Key | Default | Meaning |
|---|---|---|
| `xp.cost_type` | `PER_BLOCK` | `PER_BLOCK` or `FLAT` |
| `xp.xp_per_block` | `4.0` | XP points per horizontal block in `PER_BLOCK` mode (Base ring) |
| `xp.flat_cost` | `10` | XP points per teleport in `FLAT` mode (Base ring) |
| `xp.per_block_max_cost` | `0` | cap on the base cost before tier multipliers (0 = none) |
| `xp.netherite_multiplier` | `0.5` | first upgrade cost factor |
| `xp.nether_star_multiplier` | `0.5` | second upgrade cost factor (provisional) |
| `xp.creative_free` | `true` | creative players never pay |
| `usage.base_dimensions` / `netherite_dimensions` | Overworld / Overworld+Nether | where each tier works |
| `usage.nether_star_any_dimension` | `true` | else uses `nether_star_dimensions` |
| `usage.blocked_dimensions` | `[]` | dimensions where no ring works |
| `usage.charge_counts_blocks` | `[enigmaticlegacy:end_anchor]` | modded anchors where charging (not just setting your spawn) counts |
| `safety.search_radius` / `search_vertical` | `3` / `4` | how far to look for a safe spot if the return point is blocked |
| `death.retain_held_and_armor` | `true` | the Nether Star retention |
| `death.ring_keeps_itself` | `false` | ring asks Curios to keep it on death |
| `death.respawn_choice` | `true` | death-screen respawn picker |
| `loot.enabled` / `loot.chance` | `true` / `0.05` | loot injection |
| `loot.target_loot_tables` | see above | chest **loot-table** ids (wildcards allowed) |
| `loot.dimensions` | `[minecraft:overworld]` | only inject for chests in these dimensions (empty = any) |
| `template_loot.enabled` / `template_loot.chance` | `true` / `0.2` | Nether Star template in chests |
| `template_loot.target_loot_tables` | `[minecraft:chests/end_city_treasure]` | chest loot-table ids for the template |

These two keys were renamed in this version: `cost_mode` → `cost_type`, `per_block_cost` → `xp_per_block`. A config file
from an older build is updated to the new per-block default automatically; old values for those two keys are not carried over.

**Cost math.** `FLAT`: cost = `flat_cost`. `PER_BLOCK`: cost = `ceil(distance × xp_per_block)`, capped by
`per_block_max_cost`; distance is **horizontal** (X/Z) in real blocks when both points are in the same dimension (so a
100-block Nether trip costs the same as a 100-block Overworld trip). For cross-dimension trips both points are first
converted to Overworld-equivalent coordinates using each dimension's coordinate scale (Nether ×8). Then the tier multiplier is applied
and the result rounded **up** (never silently free). Return, delete and respawn choice never cost anything.

| Trip | Base ring (4/block) | Netherite / Nether Star (2/block) |
|---|---|---|
| 10 blocks | 40 points (≈ level 4 from zero) | 20 |
| 100 blocks | 400 (≈ level 17) | 200 (≈ level 11) |
| 200 blocks | 800 (≈ level 23) | 400 (≈ level 17) |
| 1,000 blocks | 4,000 (≈ level 44) | 2,000 (≈ level 34) |
| 5,000 blocks | 20,000 (≈ level 83) | 10,000 (≈ level 63) |

A player at level 30 drops 1 level for 40 points, 5 levels for 400 and 10 levels for 800. `xp.per_block_max_cost` caps
long trips if needed.

(Netherite and Nether Star rings pay half by default; each result is rounded up.)

## Building

```
./gradlew build            # -> build/libs/travelersring-1.0.4.jar (reobfuscated, distributable)
```
JDK 17 is used via Gradle's toolchain. The first build downloads Forge and Curios.
The released JAR is also copied to `dist/`. See [TESTING.md](TESTING.md) to run the tests.

## Art

The ring and template textures are by [PorotoMz](https://x.com/PorotoMz). Each ring has two icons:

* **off** (`travelers_ring*.png`): no saved location, the normal state.
* **on** (`travelers_ring*_active.png`, glowing): you've teleported to spawn and the ring holds your free return.
  It goes back to "off" when you return or delete the saved location.

The switch is the item property `travelersring:active` (registered in `client/ClientSetup`, read from the ring's NBT), used as a
model override in `models/item/travelers_ring*.json`, so it shows in the hand, inventory and Curios slot alike.
`empty_slot_ring.png` (the Curios slot background) is still the placeholder from `tools/make_placeholder_textures.py`.
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
