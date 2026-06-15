# CLAUDE.md - KweebecNightmare (the chase)

A **standalone Hytale horror minigame** for a mod jam: a co-op chase set in a Void-blighted Emerald Grove at perpetual midnight, where corrupted Kweebec tree-folk hunt you. It runs fully on its own, carries day-1 *optional* soft-dependency hooks so [MMO Skill Tree](https://www.curseforge.com/hytale/mods/mmo-skill-tree) elevates it when present, and is designed to later fold into MMO Skill Tree as the repeatable dungeon **capstone of the Emerald Wilds / Kweebec questline**. The sibling top-down survival variant is `additional-mods/kweebec-nightmare-2d`.

This is one of two supplemental mods under the parent hyMMO repo's `additional-mods/` (git submodules; development is launched from the hyMMO repo, like the content packs). **Status: v0.1.0 scaffold** - the plugin stands up and the asset pack loads; the round loop is not built yet. The design + capability research live in the parent repo: plan `.claude/plans/utilize-a-new-git-snappy-moon.md`, research `.claude/research/kweebec-nightmare-modjam.md` (+ `raw/`). Read those before building gameplay.

## The experience (design target)

**Relight & Escape** (1-4 player co-op, 8-12 min, 15 min hard cap):
1. **Prep / Nightfall** - saplings spawn in a dim grove, the hunter is contained, players scout and claim hiding spots.
2. **Ritual** - relight N = 2 + partySize grove-shrines; each is a hold-in-place bar that pins the player and emits noise the sound-driven hunter hears; a corruption meter rises with time and per shrine, ramping hunter speed, darkness, and the proximity-audio tier.
3. **Escape** - the final shrine opens the Heartwood Gate and fires a loud alert that locks the hunter onto the nearest player for the chase to the exit.

Caught players are cocooned in roots (death intercepted, not vanilla respawn), freeable by a teammate. Win = a player reaches the exit; lose = all cocooned or the night timer expires. Solo plays the same loop with one AI hunter. Perspective is default third-person, with an optional locked overhead/fixed cam as a dread lever.

## Build

Gradle runs via PowerShell (Java 25 toolchain). Self-contained `build.ps1` builds and installs:
```powershell
cd 'D:\dev\business\hyMMO\additional-mods\kweebec-nightmare'; .\build.ps1
.\build.ps1 -Install:$false     # build only, no copy
.\build.ps1 -ModsDir <path>     # explicit install target (else $env:HYTALE_MODS_DIR)
```
Produces `build/libs/KweebecNightmare-<version>.jar` and copies the runtime jar (never the `-sources`/`-javadoc` siblings) into the Hytale `Mods/` folder when `HYTALE_MODS_DIR` is set. The server jar path comes from `gradle.properties` (`hytaleHome`/`patchline`/`game_build`). `.\gradlew.bat build` works too.

## Layout

```
settings.gradle / gradle.properties / build.gradle    java + api Gradle modules (mirrors hyDungeons)
build.ps1                                              build + auto-install
api/                                                   the api module: the one type a consumer compiles against
  src/main/java/com/ziggfreed/kweebec/api/RoundCompletedEvent.java   dungeon-completion-shaped signal
src/main/resources/manifest.json                       Group:Ziggfreed, ServerVersion ">=0.5.0-pre.0 <0.6.0",
                                                        IncludesAssetPack:true, OptionalDependencies Ziggfreed:MMOSkillTree
src/main/resources/Server/                             the asset pack (Models / NPC Roles / Weathers / Environments /
                                                        AmbienceFX / Particles / PortalTypes / Prefabs / Languages)
src/main/java/com/ziggfreed/kweebec/
  KweebecNightmarePlugin.java                          JavaPlugin entry (setup/shutdown)
  round/                                               round state machine, instance lifecycle, cleanup, death/cocoon
  hunter/                                              spawn + per-tick marked-target lock + speed ramp + teleport
  feedback/                                            camera shake, 3D-pulse heartbeat, title cards, custom HUD
  ui/                                                  lobby/queue page
  integration/                                         KweebecMmoBridge - reflective optional MMOSkillTree soft-dep
```

## Architecture (decompile-verified capabilities)

All pillars are confirmed against the build-matched 0.5.3 decompile (anchors in the parent research note). Build only on confirmed capabilities; everything else needs an in-game spike first.
- **Per-round instanced world:** `Universe.makeWorld` + `resetPlayer` in/out + own `removeWorld` cleanup ticker. `hyDungeons` (`D:/dev/business/hyDungeons`) exercises this but is **UNTESTED and slated for a 1.5.0 rework** - lift its orchestration *shape* as a draft to re-verify, never trust its code blind.
- **Hunter:** `NPCPlugin.spawnNPC` + a pack-authored hostile Kweebec role (Razorleaf variant, `DefaultPlayerAttitude=Hostile`); `Role.setMarkedTarget("LockedTarget", playerRef)`; speed via role `MaxWalkSpeed`; recover via `BodyMotionTeleport`. Native attitude aggro is the proven baseline.
- **Atmosphere:** `WorldTimeResource.setDayTime(0.0)` + paused time; a pack-authored dark Weather/Environment; per-player `WeatherTracker` override (cleared on teleport - re-apply after instance entry).
- **Feedback:** custom `HudManager.addCustomHud`; `EventTitleUtil` cinematic titles; `NotificationUtil` toasts; camera shake (reuse the MMO `CameraShakeService` shape); heartbeat = **server-pulsed one-shot 3D sound** at a tightening interval (0.5.3 has no StopSound for a true loop).
- **Death -> custom state:** a `DeathSystems.OnDeathSystem` on `DeathComponent`; classify DEATH / DISCONNECT / EVICT via `DrainPlayerFromWorldEvent`.
- **Reskin:** extend vanilla `Kweebec_Sapling` (`"Parent"` + dark texture + glow `ModelParticle[]`); no new mesh.

## MMOSkillTree integration (optional, never required)

The manifest declares `OptionalDependencies` (NOT a hard `Dependencies` entry - that would fail the load when the mod is absent). All MMO calls sit behind one reflective `integration/KweebecMmoBridge`, guarded by `PluginManager.hasPlugin(...)`, with a NoOp fallback so the minigame self-contains every reward and feedback path when the mod is absent. **`compileOnly` the `mmoskilltree-api` jar, NEVER bundle it** (bundling double-loads its event types under two classloaders -> ClassCastException). On a win, push `content.objective.ProgressEvents.fire(...)` (advances quest + achievement + stat) and grant via `content.reward.RewardGrantService.grantAll(...)`; bind lazily at PlayerReady.

## Release notes (patch-notes paradigm)

Per-version public release notes live in `patch-notes/<version>.md`, the same paradigm as the parent mod repo: YAML frontmatter (`version`, `title`, `type: patch-note`, `status: held|released`), a one-line summary, then user-facing `- **New/Fixed: ...**` bullets. `patch-notes/_INDEX.md` lists them newest-first. `CHANGELOG.md` is the developer changelog; `CURSEFORGE.md` is the public listing copy; `README.md` is the GitHub front page. **No em-dashes anywhere.** **Describe shipped reality, not aspiration** - at scaffold stage, say "scaffold" not "adds a horror chase"; a feature is "New: X" only in the release that actually ships it.

## Conventions

PascalCase asset filenames; codec JSON keys start upper-case. `@Nonnull`/`@Nullable` on params; `KweebecNightmarePlugin.LOGGER` for logging. Localize all player-facing text via `Message`/lang keys from day 1 (no raw display strings); en-US complete. Package root `com.ziggfreed.kweebec` (distinct from the 2D mod's `com.ziggfreed.kweebec2d` so both can load together). Submodule order is fixed: commit + push HERE first, verify the SHA is on the remote, THEN bump the gitlink in the parent repo.
