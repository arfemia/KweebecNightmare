# CLAUDE.md - Kweebec Nightmare

A **standalone Hytale horror minigame** for a mod jam, set in a Void-blighted Emerald Grove at perpetual midnight where the peaceful Kweebec tree-folk have been corrupted into something that hunts. It runs fully on its own and carries day-1 *optional* MMO Skill Tree elevation via **outbound native events** (no dependency), and is designed to later fold into [MMO Skill Tree](https://www.curseforge.com/hytale/mods/mmo-skill-tree) as the repeatable dungeon **capstone of the Emerald Wilds / Kweebec questline**.

This is ONE mod shipping **two modes as separate worlds/instances** (no lobby UI). It is a supplemental mod under the parent hyMMO repo's `additional-mods/` (a git submodule; development is launched from hyMMO, like the content packs). **Status: v0.1.0 scaffold** - the plugin stands up and the asset pack loads; the round loop is not built yet.

> **Design + tech are LOCKED and verified.** Read these in the parent repo before building gameplay - they are the authority, this file is the local router:
> - Plan + LOCKED DESIGN: `../../.claude/plans/utilize-a-new-git-snappy-moon.md`
> - Verified call sequences + threading + the in-game test list: `../../.claude/research/kweebec-nightmare-techspec.md` (raw in `../../.claude/research/raw/kweebec-nightmare-techverify*.json`)
> - Original capability mine: `../../.claude/research/kweebec-nightmare-modjam.md`

## The two modes (design target)

Both are 1-4 player co-op with dead-player presence, played in their own per-round instance world. Stakes (rescue style, lives, bleed-out, hunter count/speed, shrine count, round length, corruption ramp) are a **configurable RuleSet** with presets (Amateur / Nightmare / Hardcore) - the replayability pillar; an installed MMO Skill Tree can select/scale them.

- **Chase - "Relight & Escape"** (~9 min, 15-min cap): relight `2 + partySize` grove-shrines (each a hold-in-place bar that pins you and emits noise the hunter hears; corruption rises with time + per shrine, ramping hunter speed + darkness + heartbeat tier); the final shrine opens the Heartwood Gate + a loud alert that locks the nearest survivor for the chase to the exit. Win = anyone escapes; lose = all cocooned or cap. Caught = cocooned in roots, freed by a teammate (revive-in-place). Build this FIRST.
- **Survival - "Last Light Till Dawn"** (~3.5 min to dawn): defend a heart-sapling + relight wards in a clearing against escalating corrupted-Kweebec lane waves to a pre-dawn surge, played from a **pure top-down locked camera**. Win = reach dawn; lose = heart falls or all down. Downed = teammate-revivable. The top-down read is the demo flex (Phase-0 camera-feel spike is the go/no-go gate; third-person is the fallback).

## Build

Gradle runs via PowerShell (Java 25). Self-contained `build.ps1` builds + installs:
```powershell
cd 'D:\dev\business\hyMMO\additional-mods\kweebec-nightmare'; .\build.ps1
.\build.ps1 -Install:$false     # build only
.\build.ps1 -ModsDir <path>     # explicit install target (else $env:HYTALE_MODS_DIR)
```
Produces `build/libs/KweebecNightmare-<version>.jar` and copies the runtime jar (never `-sources`/`-javadoc`) into the Hytale `Mods/` folder when `HYTALE_MODS_DIR` is set. `.\gradlew.bat build` works too.

## Layout

```
settings.gradle / gradle.properties / build.gradle    java + api Gradle modules
build.ps1                                              build + auto-install
api/  src/main/java/com/ziggfreed/kweebec/api/         outbound native event types (RoundCompletedEvent, ...) MMO listens to
src/main/resources/manifest.json                       Group:Ziggfreed, ServerVersion ">=0.5.0-pre.0 <0.6.0", IncludesAssetPack:true
src/main/resources/Server/                             the asset pack: HytaleGenerator/ (Biomes/WorldStructure/Density worldgen),
                                                        Instances/ (per-mode instance.bson), Models / NPC Roles / Weathers /
                                                        Environments / AmbienceFX / Particles / PortalTypes / Prefabs / Languages
src/main/java/com/ziggfreed/kweebec/
  KweebecNightmarePlugin.java                          JavaPlugin entry (setup/shutdown), native-event dispatch
  round/                                               round state machine + RuleSet + instance lifecycle + cocoon/revive (shared)
  mode/                                                chase + survival mode rules (thin leaves over round/)
  hunter/                                              HunterController seam (AI now, human-driven post-jam) + spawn + marked-target + speed ramp
  camera/                                              ServerCameraService (top-down apply/reset; survival mode)
  feedback/                                            camera shake, 3D-pulse heartbeat, title cards, custom HUD
  ui/                                                  entry triggers (command now; shrine block / void-rift pad / guide NPC designed-for)
  event/                                               native event POJOs fired on the engine bus
```

## Architecture (verified - see the parent techspec for exact call sequences + file:line)

- **Arena = Path A engine instances + WorldGen V2 (`HytaleGenerator`), hybrid.** Each mode is its own pack-authored instance (`Server/Instances/<mode>/instance.bson`) referencing a pack-authored `HytaleGenerator` WorldStructure/Biome/Density (node-editor authored, `Server/HytaleGenerator/**`). Spawn per round via `InstancesPlugin.get().spawnInstance(name, world, returnPoint)` (engine gives free isolation, return-point restore, off-thread cleanup, zero-Java entry/exit). The procedural grove is the base; authored beats (shrines, gate, hiding spots, lanes) are runtime `PrefabUtil.paste` (on `world.execute`) + code mob spawns. **The `instance.bson` MUST author `RemovalConditions:[WorldEmpty,Timeout]` + `DeleteOnRemove=true`** or worlds leak. Ship `instance.bson` as plain JSON - the engine LOADS it as JSON (`WorldConfig.load` -> `RawJsonReader.readSync` -> `codec.decodeJson`); BSON is only what the in-game `/instances edit save` writes, NOT a load requirement. So the instance config + the `HytaleGenerator` worldgen are hand-authorable JSON in the pack; the in-game node editor / `/instances edit` are conveniences. (Confirm live that a JSON-content `instance.bson` loads + generates.)
- **Hunter:** `NPCPlugin.spawnNPC` + a pack hostile Kweebec role (clone `Template_Kweebec_Razorleaf`, `DefaultPlayerAttitude:Hostile`, **drop the Target sensor's Attitude filter + Range**); `((NPCEntity)pair.second()).getRole().setMarkedTarget("LockedTarget", survivorRef)` re-asserted each tick (works only for ADVENTURE-mode survivors); recover via `BodyMotion:Teleport`. Multiple hunters, noise/proximity targeting. Behind a `HunterController` seam.
- **Death -> cocoon:** a custom `OnDeathSystem` (`RefChangeSystem` on `DeathComponent`, query-matched to Player, ordered BEFORE `PlayerDeathScreen`, `setShowDeathMenu(false)`); engine never auto-respawns, so hold-dead-in-place is free; rescue via `DeathComponent.respawn` at an in-arena point. NOT `DrainPlayerFromWorldEvent`.
- **Atmosphere:** `WorldTimeResource.setDayTime(0.0, world, store)` + `setGameTimePaused(true)`; whole-world `WeatherResource.setForcedWeather(validatedId)` (pack dark `Weather`, no custom `ScreenEffect` strings - they fail validation).
- **Camera (survival):** `camera/ServerCameraService` sends `SetServerCamera` (packet 280) via `writeNoCache`; top-down preset (Custom, isLocked, dist 20, pitch -PI/2); reset `SetServerCamera(Custom,false,null)`. Always reset on death/disconnect/round-end. No FOV/orthographic.
- **Feedback:** `HudManager.addCustomHud` + strip native HUD; `EventTitleUtil` titles; `NotificationUtil` Danger toasts; server-pulsed one-shot 3D `SoundUtil.playSoundEvent3d` heartbeat (no `StopSound` exists - loop bed is a data-driven `Looping` asset).
- **Reskin:** extend `Kweebec_Sapling` (`"Parent"` + dark texture + Void-glow `ModelParticle[]` on real bones `Chest` / `L-Eye-Attachment` / `R-Eye-Attachment`); no new mesh.
- **Threading (load-bearing):** off-thread scheduler -> `world.execute` hop -> all Store/Ref/Sound/HUD/camera/weather on the world thread; packet writes are thread-safe; `spawnInstance`/`removeWorld` never `.join()` on a world tick thread.

## MMO Skill Tree integration (optional, outbound events - NO dependency)

The mod has **zero compile/runtime dependency on MMO Skill Tree**. It fires its own native events on the shared engine bus: `HytaleServer.get().getEventBus().dispatchFor(E.class); if (d.hasListener()) d.dispatch(new E(...))` (sync `IEvent<Void>` POJOs in `api/`). MMO consumes them MMO-side via the reflective `registerGlobal` adapter (the `AnglersAlmanacAdapter` pattern: load the event class from THIS mod's classloader so `Class` identity matches, then `getEventRegistry().registerGlobal`). When MMO is absent, the mod self-contains its own rewards. MMO can also reflectively call back into a small `KweebecNightmareAPI` to scale difficulty / replayability. Fire from a world-thread context so listeners can hop to `world.execute`.

## Release notes (patch-notes paradigm)

Per-version public release notes in `patch-notes/<version>.md`, same paradigm as the parent mod repo: frontmatter (`version`, `title`, `type: patch-note`, `status: held|released`), a one-line summary, then `- **New/Fixed: ...**` bullets; `patch-notes/_INDEX.md` newest-first. `CHANGELOG.md` is the dev changelog, `CURSEFORGE.md` the public listing, `README.md` the GitHub front page. **No em-dashes.** **Describe shipped reality, not aspiration** - at scaffold stage say "scaffold", not "adds a horror minigame".

## Conventions

PascalCase asset filenames; codec JSON keys start upper-case. `@Nonnull`/`@Nullable` on params; `KweebecNightmarePlugin.LOGGER` for logging. Localize all player-facing text via `Message`/lang keys from day 1 (no raw display strings); en-US complete. Package root `com.ziggfreed.kweebec`. As a submodule, the order is fixed: commit + push HERE first, verify the SHA is on the remote, THEN bump the gitlink in the parent hyMMO repo.
