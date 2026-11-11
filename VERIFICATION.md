# Verification results

Run on 2026-11-10, Windows 11, JDK 17 (Temurin 17.0.16), Gradle 8.8, ForgeGradle 6, Forge 47.4.0, Curios 5.14.1+1.20.1.
Deliverable: `dist/travelersring-1.0.0.jar`, SHA-256 `bf31423a71e9e525ee21860c46198d911847c6abbb938323f7ab74edccd67387`
(135,789 bytes; reobfuscated; contains no test classes; `META-INF/mods.toml` declares Forge ≥47.4.0, Minecraft 1.20.1, Curios ≥5.14.1).

## What was actually run and what happened

| # | Check | Result |
|---|---|---|
| 1 | `./gradlew clean build` | **Passed.** Only Forge-API deprecation warnings. |
| 2 | `./gradlew runGameTestServer` (headless Forge server + Curios + the mod, real `PlayerList` login/respawn code paths) | **40 / 40 GameTests passed** (final run, after clean build). |
| 3 | Mutation check: deliberately removed (a) the drop-list removal of retained items, (b) the spawn-swap restore, (c) the "already saved" guard | The suite **failed 10 tests, in exactly the tests designed to catch each bug**; the code was then restored and re-verified green. This shows the tests are not vacuous. |
| 4 | **Real Forge 1.20.1-47.4.0 dedicated server** with Curios and the final distributable JAR (reobfuscated, from `dist/`, SHA verified identical) | **Booted cleanly**: `Done (9.5s)`, datapack `mod:travelersring` enabled, `Loaded 10 curio slots / 1 curio entities`, `reload` clean, `stop` exit code 0, no errors from the mod. Config file generated. |
| 5 | Dev client to main menu (`runClient`) | Reached the sound-engine/main-menu stage with no mod errors (all models/textures/lang/keybind registration loaded). |
| 6 | Dev client joined that real dedicated server (`-PquickPlay`) | **Forge handshake and login succeeded** (`Dev joined the game`); server console `xp add` and `/curios replace ring 0 Dev with travelersring:travelers_ring` worked; the client showed the resulting XP level. |
| 7 | After the wording/texture/config-comment pass: `./gradlew clean build runGameTestServer`, then the new JAR (hash above) on the real server **with JEI 15.56.0.205 added** | Build OK, **40 / 40 GameTests passed**. Server: `Done (2.2s)`, datapacks `mod:forge, mod:curios, mod:travelersring, mod:jei`, 10 curio slots, no errors from the mod (the only ERROR was the console reader failing on my piped stdin, unrelated). |
| 8 | Dev client (now with JEI in its runtime classpath) joined that server | Joined. JEI started (`Starting JEI took 652.9 ms`, `Registering recipes ... 284 ms`) with no JEI or `travelersring` warnings/errors in the client log. I did **not** open JEI's screen to look at the ring recipes (no working GUI input, see below). |
| 9 | Template and ring textures redrawn (rings: banded, gem-set style), rebuilt: `clean build runGameTestServer` | Build OK, 40 / 40 passed. The server was not re-booted for this texture-only change. The new sprites were only compared visually as a PNG against the vanilla one, not in-game. |

## GameTest coverage (40)

* **Persistence:** ring NBT round-trip; saved return survives a real logout/login (player data written to disk and reloaded, Curios slot included); `PlayerRingData` (spawns/retained/swap) NBT round-trip.
* **Upgrades:** vanilla smithing keeps saved return through both upgrades; base ring cannot skip to the Nether Star tier; template recipe present with the documented ingredients.
* **Cost:** FLAT, PER_BLOCK, cap, tier multipliers, cross-dimension scale, round-up; exact XP-point maths incl. "10 points ≠ 10 levels".
* **Teleport flow:** teleport→saved→duplicate refused (no 2nd charge, no overwrite)→return→duplicate return refused; held-ring path; delete (free, anywhere, also for corrupt data); failures change nothing (no XP, no movement, no data): not enough XP, wrong dimension, unknown/forged key, no ring, dead player; return hazards: blocked exact spot → nearby safe spot, fully enclosed → refused and kept, missing dimension → kept, outside world border → refused.
* **Destinations:** missing bed → world-spawn fallback; Netherite ring uses a charged anchor from the Nether at half price **without consuming charge**, cross-dimension teleport and return, empty/destroyed anchor refused; Overworld-only base ring; Nether Star ring across Overworld/Nether/End and a nonexistent dimension.
* **Tracking:** latest-per-dimension through real vanilla paths (`setRespawnPosition`, `startSleepInBed` by day, `/spawnpoint`, `RespawnAnchorBlock.use` charged vs dry), cancelled events not recorded, adoption of current vanilla spawn without inventing history.
* **Death retention:** held slot kept in its original hotbar index, offhand, 4 armor; loose items drop once; each item exists exactly once (no loss/dup); no retention for lower tier / ring only in inventory / ring only held / no ring; `keepInventory` untouched; Curse of Vanishing still destroys; empty offhand; restore with every slot occupied drops at feet and never deletes; ring itself follows Curios (dropped by default, kept when `ring_keeps_itself`).
* **Respawn choice:** offer list; forged/unknown/absent keys ignored; living player ignored; choosing Overworld/anchor/world/default; real spawn restored; anchor consumes a vanilla charge only on actual respawn; anchor destroyed after choosing → falls back to world spawn, not stranded; invalid option refused; no offer without an equipped Nether Star ring; leftover swap restored at login.
* **Other:** loot modifier ≈5 % in a target table and 0 in a non-target table over 4,000 rolls each; Curios `ring` slot validity for all three rings (and not other slots, not a diamond; right-click does not auto-equip); all six packets round-trip; hostile enum values harmless.

## NOT verified (do not assume these work)

* **In-game client UI was not exercised.** The ring screen, delete-confirm dialog, death-screen "Respawn at" button, keybind press, tooltips, and smithing-table UI were *compiled and loaded without errors* but never seen or clicked. I tried to drive the dev client with desktop automation; synthetic keyboard input did not reach the Minecraft window in this session (even Escape), so that attempt produced no UI evidence.
* The **client→server packet path from a real key press / button click** was not exercised end-to-end (handshake and login were). The server logic behind each packet is tested directly, and each packet's encode/decode is round-trip tested.
* Placeholder textures/models were loaded without error but not visually inspected in-game.
* Loot injection was verified by rolling the loot tables, not by opening chests in generated structures.
* Real multiplayer with several clients, other mods, other Forge/Curios versions, long-running servers, and performance: untested.
* A real "disconnect on the death screen" was simulated by calling the login handler, not by disconnecting a real client.
* Sleeping at night / real bed-leaving flows were not played through (the vanilla sleep method was called by day).

## Environment notes
* Test servers/clients ran from git-ignored `server-test/` and `run*/` directories.
* Rows 1-6 ran on the first build. Row 7 re-ran the build and tests after the text, config-comment and texture changes; the shipped JAR is the row 7 build.
* A Windows Firewall prompt for Java appeared when the test server started; it was cancelled (loopback needs no rule).
