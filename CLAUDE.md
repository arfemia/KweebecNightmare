# Kweebec Nightmare

Co-op horror minigame mod (Relight & Escape chase, one instance world per round). Zero MMO dependency. Per-package routers load lazily under `src/main/java/com/ziggfreed/kweebec/`. The family-wide rules apply here; this file adds only what is specific to this mod. Engine facts are verified against `shared-source/release`.

## Build and dependencies

- Build with `.\build.ps1` (`-Install:$false` builds only; `-ModsDir` overrides `HYTALE_MODS_DIR`). Release the plain `build.ps1` jar.
- `gradle/deprecation-gate.gradle` (run by `check`) is hyMMO's, copied byte for byte: it changes only by copying hyMMO's.
- Perfect Utils and `ziggfreed-common` are hard runtime deps compiled `compileOnly`: install both jars in `Mods/` or the load fails. `perfectUtilsJar` points at the Developer-Utils build output, which is the maintainer's own mod: when an engine update breaks it, rebuilding it is our work, never an external wait. `ziggfreedCommonJar` is a relative path that works from a worktree pair; `-PziggfreedCommonJar=` overrides it.
- The `api/` event POJOs are for third parties and are bundled into the jar minus `META-INF/services`; never bundle the MMO api jar.

## Ids other mods key on

- The MMO and mob-scaling key on kweebec ids; renaming any silently breaks the other mod: `InstanceRoundCompletedEvent` (mod id `kweebec`, mode and preset ids), the custom `DamageCause` ids (MMO Artillery XP), the `Chase_*` lootable ids (MMO `ContributesTo`), and the `KweebecNightmare_*` instance names (MMO and mob-scaling world rules).

## Scope

- Clash and Domination are in the build but unadvertised and not live-playtested; Survival is reserved. Never describe them in shipped voice.

## Gotchas

- Instance templates are plain JSON in `instance.bson` and must author `RemovalConditions` plus `DeleteOnRemove: true` or worlds leak. `SpawnPoint.Y` must match the worldgen floor (80).
- The 1 Hz round scheduler runs off-thread and hops via `world.execute` for every Store, Ref, HUD, sound or weather touch. Never `.join()` `spawnInstance` or `removeWorld` on a world thread.
- Register every vocabulary in `setup()` before assets load (`KweebecDialogue.init`, `KweebecDestinations`, `NpcActions.register`, `HunterWaveType`, `HunterFactors`); a lazy registration makes the files that name it fail to parse that boot.
- A cloned NPC role template must handle every state it sets or exports, or the builder fails and every Variant silently never spawns. Read the server log's `[NPC|P] FAIL` line first, and never add `UseCombatActionEvaluator` or `_CombatConfig` to a Razorleaf-based template.
- Logging: `util.SafeLog` on parse, validate and asset-load paths; `KweebecNightmarePlugin.LOGGER` directly in world-thread runtime code.
- Reach blocks only through ziggfreed-common's `BlockOps` (`blockItemIdAt`, then `BlockType.getAssetMap().getAsset(id)`, then `setInteractionState` or `setBlock`): `World`'s block and chunk accessors, and the block accessors of `WorldChunk` and `BlockChunk`, are gone on Update 7, where a call built on 0.6.8 fails inside its catch. `BlockAccessorScanTest` fails the build on one.
- A model's `Particles[].SystemId` names a particle system this mod ships under `Server/Particles/KweebecNightmare/`, never the base game's: a model naming a system the server cannot find fails, with every role that wears it, and ends the boot with exit code 6 (Update 7 renamed the base game's `Spectre_Void_Hands`). `ModelParticleSystemsTest` checks it; a shipped copy keeps the base game's bytes.
