# Kweebec Nightmare

Co-op horror minigame mod (Relight & Escape chase, one instance world per round). Zero MMO dependency. Per-package routers load lazily under `src/main/java/com/ziggfreed/kweebec/`. The family-wide rules apply here; this file adds only what is specific to this mod. Engine facts are verified against `reference/shared-source/release`.

## Build and dependencies

- Build with `.\build.ps1` (`-Install:$false` builds only). It installs into the Mods folder of the client the workspace's `family.properties` `patchline` plays, through the workspace's `tools\mods-dir.ps1` (R205); `-ModsDir` overrides it, and `HYTALE_MODS_DIR` applies only when no Hytale install is found or in a lone clone. Release the plain `build.ps1` jar.
- `gradle/deprecation-gate.gradle` (run by `check`) is the MMO's (`mmo-family/mmo-skills`), copied byte for byte: it changes only by copying the MMO's.
- Perfect Utils and `ziggfreed-common` are hard runtime deps compiled `compileOnly`: install both jars in `Mods/` or the load fails. Both resolve through the workspace's `family.properties` (`repo.Developer-Utils`, `repo.ziggfreed-common`, each `build/libs`) at the pinned `perfectUtilsVersion` and `ziggfreedCommonVersion`, so a tree builds against its own copies; `-PperfectUtilsJar=` and `-PziggfreedCommonJar=` override them. Developer-Utils (Perfect Utils) is the maintainer's own mod: when an engine update breaks it, rebuilding it is our work, never an external wait. `ziggfreedCommonVersion` equals the manifest's `Ziggfreed:ZiggfreedCommon` floor: move the two together. `ManifestTargetTest` fails when they differ, and checks the manifest's server range against the server jar the build compiles with.
- The `api/` event POJOs are for third parties and are bundled into the jar minus `META-INF/services`; never bundle the MMO api jar.

## Ids other mods key on

- The MMO and mob-scaling key on kweebec ids; renaming any silently breaks the other mod: `InstanceRoundCompletedEvent` (mod id `kweebec`, mode and preset ids), the custom `DamageCause` ids (MMO Artillery XP), the `Chase_*` lootable ids (MMO `ContributesTo`), and the `KweebecNightmare_*` instance names (MMO and mob-scaling world rules).

## Scope

- Clash and Domination are in the build but unadvertised and not live-playtested; Survival is reserved. Never describe them in shipped voice.

## Gotchas

- Instance templates are plain JSON in `instance.bson` and must author `RemovalConditions` plus `DeleteOnRemove: true` or worlds leak. `SpawnPoint.Y` must match the worldgen floor (80). `Discovery` names its title look with `Style`, spelled exactly as the engine's codec spells it (`Default`, `Major`, `GoblinBreach`, `VoidEviction`; any other spelling fails the template), never the deprecated `Major` flag base-game instances still carry. `InstanceDiscoveryStyleTest` checks it.
- The 1 Hz round scheduler runs off-thread and hops via `world.execute` for every Store, Ref, HUD, sound or weather touch. Never `.join()` `spawnInstance` or `removeWorld` on a world thread.
- Register every vocabulary in `setup()` before assets load (`KweebecDialogue.init`, `KweebecDestinations`, `NpcActions.register`, `HunterWaveType`, `HunterFactors`); a lazy registration makes the files that name it fail to parse that boot.
- A cloned NPC role template must handle every state it sets or exports, or the builder fails and every Variant silently never spawns. Read the server log's `[NPC|P] FAIL` line first, and never add `UseCombatActionEvaluator` or `_CombatConfig` to a Razorleaf-based template.
- Logging: `util.SafeLog` on parse, validate and asset-load paths; `KweebecNightmarePlugin.LOGGER` directly in world-thread runtime code.
- Reach blocks only through ziggfreed-common's `BlockOps` (`blockItemIdAt`, then `BlockType.getAssetMap().getAsset(id)`, then `setInteractionState` or `setBlock`): `World`'s block and chunk accessors, and the block accessors of `WorldChunk` and `BlockChunk`, are gone on Update 7, where a call built on 0.6.8 fails inside its catch. `BlockAccessorScanTest` fails the build on one.
- A model's `Particles[].SystemId` names a particle system this mod ships under `Server/Particles/KweebecNightmare/`, never the base game's: a model naming a system the server cannot find fails, with every role that wears it, and ends the boot with exit code 6 (Update 7 renamed the base game's `Spectre_Void_Hands`). `ModelParticleSystemsTest` checks it; a shipped copy keeps the base game's bytes.
