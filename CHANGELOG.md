# Changelog

Developer changelog for Kweebec Nightmare. User-facing release notes live in `patch-notes/`.

## 0.2.0 (in development)

Chase MVP ("Relight & Escape"). The full co-op round runs end to end against the installed Hytale server; only the in-game behaviours below are unverified (no playtest yet), tracked as a handoff checklist.

- **Round engine.** A 1 Hz `RoundStateMachine` over a `RoundService`/`RoundRegistry`/`RoundInstance` model owns party/role/lifecycle state (the engine has no party API). Each round is its own Path A engine instance: `InstancesPlugin.spawnInstance` from a pack-authored `instance.bson`, players entered via `teleportPlayerToLoadingInstance`, left via `exitInstance`, with a `CleanupTicker` backstop over the authored `RemovalConditions`.
- **Configurable RuleSet + presets.** Immutable `RuleSet` knobs (rescue style, lives, bleed-out, hunter count/speed, shrine count, round length, corruption ramp) with shipped presets Amateur / Nightmare / Hardcore. This is the replayability pillar; an installed MMO Skill Tree can select or scale them.
- **Chase mode.** `2 + partySize` grove shrines with hold-to-relight (channelling pins the survivor); a corruption meter rising with time + per shrine drives the heartbeat tier and darkness; the final shrine opens the Heartwood Gate and locks the hunter onto the nearest survivor. Win = anyone reaches the exit; lose = all cocooned or the 15-minute cap.
- **Hunter.** A `HunterController` seam (AI now, human-driven later) spawning a hostile Blighted-Kweebec role (a `Template_Kweebec_Razorleaf` Variant, `DefaultPlayerAttitude:Hostile`, bumped speed/ranges) with per-tick marked-target re-assert on Adventure-mode survivors.
- **Death -> cocoon.** A custom `OnDeathSystem` (ordered before the vanilla respawn screen) holds a downed in-round player dead-in-place, rescuable by a teammate; players outside a round are untouched. Revive re-adds the player at the instance spawn (this build's engine has no `DeathComponent.respawn`).
- **Atmosphere + feedback.** Frozen dark midnight + forced Void weather; a custom round HUD (timer / objective / shrines / alive / corruption) with the native HUD stripped; `EventTitle` banners + Danger toasts; a server-pulsed 3-tier proximity heartbeat; a `ServerCameraService` top-down recipe for the future survival mode.
- **Integration is outbound native events only, zero MMO dependency.** `RoundStarted` / `RoundCompleted` / `PlayerCocooned` / `PlayerRescued` `IEvent<Void>` POJOs fired via `HytaleServer.get().getEventBus().dispatchFor(...)` behind a `hasListener()` guard.
- **Arena pack assets.** `instance.bson` (loaded as JSON) with `HytaleGenerator` + a vanilla flat WorldStructure; grove prefabs pasted at runtime via `PrefabUtil.paste`; the Blighted-Kweebec role; an en-US `.lang` (complete) and the round `.ui`.
- **Entry:** `/kweebec [start|exit|endall] [preset]` (the void-rift pad / shrine block / guide NPC triggers are designed-for, this is the first).

Technical: the build targets the **installed** `HytaleServer.jar`, which diverges from the public 0.5.3 decompile (math vectors are `com.hypixel.hytale.math.vector.*` not `org.joml`; `DeathComponent.respawn` and the 4-arg `resetPlayer` are absent; `HudManager.setCustomHud`; `exitInstance` returns void). Every signature was javap-verified. See the parent repo note `kweebec-nightmare-api-ledger`.

## 0.1.0 (in development)

Initial project scaffold.

- Standalone Hytale plugin stood up, mirroring the hyDungeons module layout: `java` + `api` Gradle modules, Java 25 toolchain, gradle wrapper, self-contained `build.ps1` that auto-installs to `HYTALE_MODS_DIR`.
- `manifest.json`: `ServerVersion ">=0.5.0-pre.0 <0.6.0"`, `IncludesAssetPack: true`, optional `Ziggfreed:MMOSkillTree` dependency, permission nodes.
- `KweebecNightmarePlugin` entry (setup/shutdown) and the `api` module's `RoundCompletedEvent` (a dungeon-completion-shaped signal for cross-jar consumers).
- A minimal `Server/Languages/en-US` asset pack so the combined plugin + asset-pack jar can be verified.
- Doc paradigm (CLAUDE.md, README, CURSEFORGE, this changelog, patch-notes).

Builds green. The round state machine, hunter AI, atmosphere, feedback stack, lobby trigger, and the MMOSkillTree bridge are the next phases (see the parent repo plan `utilize-a-new-git-snappy-moon`).
