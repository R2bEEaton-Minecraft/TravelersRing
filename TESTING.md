# Testing

Prerequisites: JDK 17 available to Gradle (the toolchain finds it), internet on first run (Forge/Curios download).

## 1. Automated in-world tests (41 GameTests)

```
./gradlew runGameTestServer
```
Starts a headless Forge server with Curios and this mod, builds mock players that go through the real
`PlayerList.placeNewPlayer` / `respawn` code paths, and runs the suite. Success ends with
`All 41 required tests passed :)`; failures are listed by name with the assertion message. (~25 s.)
Tests live in `src/gametest/` and are **not** shipped in the JAR.

What they cover: ring NBT persistence (including a real logout/login), smithing upgrades preserving data, cost modes,
XP-point maths, destination validation (missing bed, empty/missing anchor, absent dimension, enclosed/blocked/lava return
site, world border), per-dimension spawn tracking through real vanilla paths (sleeping, `/spawnpoint`, anchor use, cancelled
events), tier/dimension rules, duplicate and forged requests, death retention (slots, keepInventory, vanishing, no loss/dup,
full inventory), the ring's Curios drop rules in both config modes, the death-screen respawn choice, loot-modifier rate,
Curios slot validity, packet round-trips, the template's End City loot rate and duplication recipe.
The teleport tests pin `FLAT` 10 in memory so their exact XP checks stay readable; per-block maths has its own tests.

## 2. Dedicated server with the real, distributable JAR

```
./gradlew build
```
Then install a Forge 1.20.1-47.4.0 server, put `build/libs/travelersring-1.0.0.jar` and Curios `5.14.1+1.20.1`
in `mods/`, accept the EULA, start it. Expect: `Loaded 10 curio slots`, `Loaded 1 curio entities`,
`[mod:travelersring]` in `datapack list`, `Done`, no errors, `config/travelersring-common.toml` created, clean `stop`.

## 3. Manual in-game checklist (client UI — not automated; see VERIFICATION.md)

Dev client: `./gradlew runClient` (or `-PquickPlay=host:port` to join a server). In a survival world:

1. `/give @s travelersring:travelers_ring`; `/curios replace ring 0 @s with travelersring:travelers_ring`;
   `/xp add @s 100 points`. Set a spawn (sleep in a bed or `/spawnpoint`).
2. Walk ~100 blocks from your spawn. Press **R** (rebindable). The ring screen lists "Go to Overworld: Bed (~400 XP)"
   with an XP bar and a line like `Level 30 → 25 (-5)`. Click it: you move to the bed and drop to that level.
3. Press **R** again: the screen offers **Return** and **Delete** only. Return: back where you started, nothing charged, return cleared.
4. Hover the ring: tooltip shows the saved return while one exists. Right-click it while held: same menu.
5. Put the ring + Netherite template + ingot in a Smithing Table, upgrade, confirm the saved return survives (hover).
6. Get a Nether Star template (End City chest, or `/give @s travelersring:nether_star_upgrade_smithing_template`), copy it
   (7 diamonds + template + end stone = 2), then upgrade to the Nether Star ring (template + netherite ring + Nether Star).
7. Nether Star ring: set a bed spawn, then charge+click a Respawn Anchor in the Nether, then another bed. Press **R**: entries for each dimension.
8. Fill hotbar/armor/offhand/other items, `/kill`. On respawn only held slot, offhand, armor return; the rest is on the ground.
   Death screen shows **Respawn at: ...**; click to cycle, then Respawn.
9. Try a second client to confirm both see their own rings' data.
10. Check `/give` rings are in the creative tab "Traveler's Ring"; the textures are placeholders.

## 4. Playtest setup (JEI + Curios)

* **Dev client:** `./gradlew runClient` already has Curios and JEI 15.56.0.205 on its classpath (`jei_version` in `gradle.properties`; dev-only, not a dependency of the shipped mod). World saves live in `run/saves`.
* **Local server:** `server-test/` is a Forge 1.20.1-47.4.0 server whose `mods/` has Curios 5.14.1, JEI 15.56.0.205 and the mod JAR. Start it with `server-test/run.bat` (add `--nogui` if you like), then join `localhost` from a client that has the same three mods, or from the dev client with `./gradlew runClient -PquickPlay=localhost:25565`.
* After rebuilding, copy `build/libs/travelersring-1.0.0.jar` over the one in `server-test/mods/`.

## 5. Regenerating assets
`python -I tools/make_placeholder_textures.py` (placeholder PNGs), `python -I tools/make_empty_structure.py` (GameTest template).
