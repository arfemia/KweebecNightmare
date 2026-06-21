# CLAUDE.md - Kweebec Nightmare

A **standalone Hytale horror minigame** for a mod jam, set in a Void-blighted Emerald Grove at perpetual midnight where the peaceful Kweebec tree-folk have been corrupted into something that hunts. It runs fully on its own and carries day-1 *optional* MMO Skill Tree elevation via **outbound native events** (no dependency), and is designed to later fold into [MMO Skill Tree](https://www.curseforge.com/hytale/mods/mmo-skill-tree) as the repeatable dungeon **capstone of the Emerald Wilds / Kweebec questline**.

This is ONE mod shipping **two modes as separate worlds/instances** (no lobby UI). It is a supplemental mod under the **hyMMO monorepo**'s `additional-mods/` (a git submodule; development is launched from hyMMO, like the content packs). **Status: v0.4.0 in dev** - the Chase MVP ("Relight & Escape") runs end-to-end, plus the 0.3.0 worldgen/hunter/scare pass and the 0.4.0 **Moonbloom loop**: a gathered glowing-mushroom resource you OFFER at interactable shrine FURNACES to cleanse them (press F to submit Moonbloom; the furnace lights with green fire when full, via `ShrineSubmitInteraction` + `ChaseMode.lightShrine`) or THROW to STUN a hunter (a reskinned vanilla Stun Bomb chain + Perfect Utils `StunMobAPI`, attributed via one `KweebecDamageSystem`), per-player SCORING (time / damage-avoided / stuns / shrine **devotion** - a per-shrine bonus + a full-cleanse bonus - asset-driven PER PRESET via `RoundPresetAsset` scoring knobs), a native `KweebecRoundScoredEvent`, and a persisted **per-difficulty x party-size** LEADERBOARD (the generalized `ziggfreed-common` board: total/best/time sort toggle + a lifetime Stats tab over stunned / moonbloom / shrines, via `MoonbloomCollectSystem` + per-player shrine/stun tracking). Asset-driven config (pack-authorable codecs + a runtime override API), natural-terrain worldgen, and the `ziggfreed-common` primitive lib (now incl. `InventoryUtil`) it consumes. Survival mode is still reserved.

> **0.5.0 difficulty pass (asset-driven, API-overridable; built on the `ziggfreed-common` ENCOUNTER framework).** Hunters now PUNISH a landed hit: a slow EntityEffect (`KweebecNightmare_HunterSlow_1..4`) + bonus damage, PROXIMITY-STACKING the debuff to a cap (cleared party-wide on a shrine-light), and DESPERATION ENRAGE (no hit in ~20s -> speed/damage burst + howl) - all per-archetype knobs on `HunterArchetypeAsset` with a `RuleSet` baseline, applied in `KweebecDamageSystem` (`damage.setAmount` + `EntityEffectService.applyTimed`) + `AiHunterController`. SPAWN is now asset-driven: a pack-authorable `SpawnRuleAsset` (trigger ROUND_START/SHRINE_LIT/CORRUPTION_TIER/TIME_ELAPSED/PLAYER_PROXIMITY x placement DEN/NEAR_RANDOM_PLAYER/RING_AROUND_PLAYERS/SCATTER + count/weight/cap/cooldown/maxPerRound), folded `defaults<pack<owner<runtime` (`KweebecNightmareAPI.overrideSpawnRules/scaleSpawnRules`), consuming common's `SpawnRoster`/`EncounterDirector`/`SpawnPlacement` (the old `planRoster`/`maybeEscalate` is now a zero-rules fallback; spawns floor-snap NEAR players on shrine-light + at corruption milestones). A multi-phase reskinned BOSS (the **Warden**, 600/450/300 HP, cloned from native `Goblin_Duke`; `asset/BossAsset`+`BossConfig` -> `boss/BossController` + `feedback/BossHud` health bar) spawns at gate-open when `RuleSet.bossEnabled()` (Hardcore preset). Code-complete; a `gradlew build` + the in-game spikes are still pending. Design + verified seams + the in-game spike list: [[kweebec-difficulty-spawn-design]]; plan + progress: [[kweebec-difficulty-encounter-framework-pass]]. **0.5.0 also adds a glow-throwable arsenal**: three Moonbloom-style thrown mushrooms (Gustbloom knockback / Mirebloom slow / Emberbloom damage, effects 100% asset-authored - `DamageEffects.Knockback`/`ApplyEffect`/`BaseDamage`), a pack-authorable [`GroveThrowableAsset`](src/main/java/com/ziggfreed/kweebec/asset/CLAUDE.md) grove-distribution type (Gust/Mire shipped `Enabled:false`), boss-phase Emberbloom clusters off the common `MultiPhaseBossAsset` throwable knobs, an Emberbloom friendly-fire guard, and the dungeon-crypt chest pool rebuilt around the three. Id authority [`moonbloom/GlowThrowables`](src/main/java/com/ziggfreed/kweebec/moonbloom/CLAUDE.md); design: [[need-to-author-additional-wobbly-hickey]].

> **Design + tech are LOCKED and verified.** Read these in the parent repo before building gameplay - they are the authority, this file is the local router:
> - Plan + LOCKED DESIGN: `../../.claude/plans/utilize-a-new-git-snappy-moon.md`
> - Verified call sequences + threading + the in-game test list: `../../.claude/research/kweebec-nightmare-techspec.md` (raw in `../../.claude/research/raw/kweebec-nightmare-techverify*.json`)
> - Original capability mine: `../../.claude/research/kweebec-nightmare-modjam.md`

## The two modes (design target)

Both are 1-4 player co-op with dead-player presence, played in their own per-round instance world. Stakes (rescue style, lives, bleed-out, hunter count/speed, shrine count, round length, corruption ramp) are a **configurable RuleSet** with presets (Amateur / Nightmare / Hardcore) - the replayability pillar; an installed MMO Skill Tree can select/scale them.

- **Chase - "Relight & Escape"** (~9 min, 15-min cap): relight `2 + partySize` grove-shrines (each a hold-in-place bar that pins you and emits noise the hunter hears; corruption rises with time + per shrine, ramping hunter speed + darkness + heartbeat tier); the final shrine opens the Heartwood Gate + a loud alert that locks the nearest survivor. Win = a CO-OP EXTRACTION HOLD: the required survivor group stands on the Heartwood platform TOGETHER for a per-difficulty `extractionHoldSeconds`, resetting if the group breaks (the required set is per-difficulty `extractionMode`: ALL_MOBILE = mobile survivors, EVERYONE = whole party rescue-first); lose = all cocooned or cap. Caught = cocooned in roots, freed by a teammate (revive-in-place). The continuous-hold timer is the reusable `ziggfreed-common` `instance/zone/ZoneHoldTimer` primitive. Build this FIRST.
- **Survival - "Last Light Till Dawn"** (~3.5 min to dawn): defend a heart-sapling + relight wards in a clearing against escalating corrupted-Kweebec lane waves to a pre-dawn surge, played from a **pure top-down locked camera**. Win = reach dawn; lose = heart falls or all down. Downed = teammate-revivable. The top-down read is the demo flex (Phase-0 camera-feel spike is the go/no-go gate; third-person is the fallback).

## Build

Gradle runs via PowerShell (Java 25). Self-contained `build.ps1` builds + installs:
```powershell
cd 'D:\dev\business\hyMMO\additional-mods\kweebec-nightmare'; .\build.ps1
.\build.ps1 -Install:$false     # build only
.\build.ps1 -ModsDir <path>     # explicit install target (else $env:HYTALE_MODS_DIR)
```
Produces `build/libs/KweebecNightmare-<version>.jar` and copies the runtime jar (never `-sources`/`-javadoc`) into the Hytale `Mods/` folder when `HYTALE_MODS_DIR` is set. `.\gradlew.bat build` works too.

**Perfect Utils is a HARD dependency.** `manifest.json` `Dependencies` lists `"narwhals:Perfect Utils": "*"`, and `build.gradle` adds it `compileOnly files(perfectUtilsJar)` (path in `gradle.properties` `perfectUtilsJar`, the installed jar) so `com.narwhals.perfectutils.api.AggroAPI` compiles. The server provides the jar at runtime; it is the override-only aggro layer used to steer the hunter. Its source is the **Developer-Utils** repo (`D:/dev/business/hytale-clients/Developer-Utils`).

**`ziggfreed-common` is ALSO a HARD dependency.** A shared, mod-agnostic primitive lib (sibling submodule `additional-mods/ziggfreed-common`, package `com.ziggfreed.common`): 3D sound, camera, asset-index cache, `SurfaceProbe` (floor-snap onto procedural terrain), notifications, HUD helpers. `manifest.json` `Dependencies` lists `"Ziggfreed:ZiggfreedCommon": "*"`; `build.gradle` adds it `compileOnly files(ziggfreedCommonJar)` (path in `gradle.properties`). **PARADIGM: a generic, reusable Hytale primitive belongs in `ziggfreed-common` and is consumed here - never reimplemented locally.** Install BOTH dependency jars to `Mods/` (a missing one fails the load / NoClassDefFounds mid-round).

## Layout

```
settings.gradle / gradle.properties / build.gradle    java + api Gradle modules
build.ps1                                              build + auto-install
api/  src/main/java/com/ziggfreed/kweebec/api/         outbound native event types (RoundCompletedEvent, ...) MMO listens to
src/main/resources/manifest.json                       Group:Ziggfreed, ServerVersion ">=0.5.0-pre.0 <0.6.0", IncludesAssetPack:true
src/main/resources/Server/                             the asset pack: HytaleGenerator/ (Biomes/WorldStructure/Density worldgen),
                                                        Instances/ (per-mode instance.bson), Models / NPC Roles / Weathers /
                                                        Environments / AmbienceFX / Particles / PortalTypes / Prefabs / Languages
src/main/java/com/ziggfreed/kweebec/         (nearly every package carries a nested CLAUDE.md router - see Architecture)
  KweebecNightmarePlugin.java                          JavaPlugin entry (setup/shutdown), wires the death + damage systems + command + round service + leaderboard
  round/                                               round state machine + RuleSet + instance lifecycle (shared); RuleSet is now SOURCED from asset/ (PresetConfig), not the old enum
  moonbloom/                                           the glowing-mushroom resource id authority (Moonbloom) - gather/cleanse/throw policy lives in arena/, mode/chase/, event/
  score/                                               configurable round scoring (ScoringConfig/ScoreCalculator) + the per-playercount persisted Leaderboard
  asset/                                               pack-authorable custom asset TYPES (RoundPreset + HunterArchetype + SpawnRule + Boss codecs) + *Config folds + KweebecAssetRegistrar + KweebecPackControlAsset
  boss/                                                BossController (spawn + per-tick HP/phase backstop + despawn) for the multi-phase Warden capstone (0.5.0)
  integration/                                         KweebecNightmareAPI - the runtime difficulty-override seam (overridePreset/scaleRuleSet) an installed MMO calls; resolveRuleSet composes defaults<pack<owner<runtime
  mode/chase/                                          the Chase loop (thin leaf over round/); survival mode reserved
  hunter/                                              HunterController seam (AI now, human-driven post-jam) + spawn + marked-target
  arena/                                               ArenaLayout anchors + ArenaBuilder prefab stamping
  death/                                               cocoon-on-death system + revive
  atmosphere/                                          frozen dark-midnight + forced weather
  camera/                                              ServerCameraService (top-down apply/reset; survival mode, reserved)
  feedback/                                            custom HUD + 3D-pulse heartbeat + title cards + toasts
  i18n/                                                Lang key registry (the .lang prefix contract)
  command/                                             /kweebec start|exit|endall|give|score|leaderboard (the first entry trigger; pad/block/NPC designed-for)
  event/                                               native event POJOs fired on the engine bus + KweebecDamageSystem (throw-stun attribution + damage-taken scoring)
```

## Architecture (per-package routers carry the working detail)

Every domain package under `src/main/java/com/ziggfreed/kweebec/` has a nested `CLAUDE.md` router that loads when you touch that subtree; the parent techspec (linked above) is the deep authority for exact call sequences + file:line. One line each:

- **[`round/`](src/main/java/com/ziggfreed/kweebec/round/CLAUDE.md)** - the round engine: `RoundService` (spawn/resolve/exit authority), `RoundStateMachine` (1 Hz off-thread -> `world.execute`), `InstanceLifecycle` (Path-A spawn/teleport/remove), `RuleSet`/presets, `RoundInstance`/`PlayerRoundState`.
- **[`mode/chase/`](src/main/java/com/ziggfreed/kweebec/mode/chase/CLAUDE.md)** - the PREP -> RITUAL -> ESCAPE loop, per-entry Adventure normalization, `ChaseState` corruption.
- **[`hunter/`](src/main/java/com/ziggfreed/kweebec/hunter/CLAUDE.md)** - the `HunterController` seam + per-tick marked-target re-assert (Adventure-only; no live speed setter in 0.5.3).
- **[`arena/`](src/main/java/com/ziggfreed/kweebec/arena/CLAUDE.md)** - `ArenaLayout` anchors, the `Default_Flat` floor-Y contract, `ArenaBuilder` prefab stamping.
- **[`death/`](src/main/java/com/ziggfreed/kweebec/death/CLAUDE.md)** - `CocoonOnDeathSystem` (before `PlayerDeathScreen`, `setShowDeathMenu(false)`) + `CocoonService` hold-in-place / `DeathComponent.respawn`.
- **[`feedback/`](src/main/java/com/ziggfreed/kweebec/feedback/CLAUDE.md)** - `RoundFeedback` fan-out, `NightmareHud` (element-id contract vs the `.ui`), 3D heartbeat.
- **[`atmosphere/`](src/main/java/com/ziggfreed/kweebec/atmosphere/CLAUDE.md)** - frozen midnight + validated forced weather.
- **[`i18n/`](src/main/java/com/ziggfreed/kweebec/i18n/CLAUDE.md)** - `Lang` key registry + the `.lang` filename-prefix contract.
- **[`camera/`](src/main/java/com/ziggfreed/kweebec/camera/CLAUDE.md)** - `ServerCameraService` (`SetServerCamera` packet 280, top-down locked preset; survival mode, reserved).
- **[`event/`](src/main/java/com/ziggfreed/kweebec/event/CLAUDE.md)** - `RoundEvents` outbound native `IEvent<Void>` POJOs (the entire MMO integration surface; see below).
- **[`command/`](src/main/java/com/ziggfreed/kweebec/command/CLAUDE.md)** - `/kweebec start|exit|endall [preset]` (a direct entry trigger).
- **`experience/` + `dialogue/`** (consumer wiring over the ziggfreed-common instance layer; no nested router) - the entry/matchmaking UX. The guide NPC dialogue's difficulty options now OPEN the reusable **Play screen** (`common.instance.play.PlayModePage`: a Public / Party / Solo chooser that morphs to a live roster whose launch timer **ticks down on screen**) via `dialogue/OpenPlayAction` (the `{ "Play": "<preset>" }` sugar) - they do NOT queue immediately (the old `StartRoundAction` is gone). `experience/KweebecPlayMode` maps the three cards to `KweebecLobby` (public join / `launchSolo` / open the party manager carrying the chosen difficulty); `KweebecExperience` builds the `PlayMode`/`Party`/`Results`/`Leaderboard` page deps. The mode cards + queue policy are **asset-driven** in `Server/KweebecNightmare/Instances/*.json` (`QueueModes`), never Java-baked. **End-game WIN rewards are score-tiered loot tables**: `KweebecExperience.stashResults` rolls the chase preset's `RewardTableId` (`Server/ZiggfreedCommon/LootTables/Chase_*.json`, the common `LootTable` primitive) by the player's `PlayerScore.total()` - better score = more rolls + premium unlocks - persisting the concrete rolled list to the durable claim store and stashing it for the results-page chips (delivered on the overworld Claim, full-inventory guarded). Win-only; the in-arena crypt chest stays native. See [[kweebec-playmode-public-party-solo]]; the next step (dialogues/leaderboard/party as pack assets) is [[ziggfreed-common-asset-driven-configs]].

**Instance + reskin authoring (asset JSON, no router):** each mode is a pack instance `Server/Instances/<mode>/instance.bson` shipped as plain JSON (the engine LOADS JSON via `WorldConfig.load` -> `RawJsonReader.readSync`; BSON is only what `/instances edit save` writes, never a load requirement). It MUST author `RemovalConditions` (`WorldEmpty`/timeout) + `DeleteOnRemove=true` or worlds leak, and `SpawnPoint.Y` MUST match the worldgen floor (see [`arena/`](src/main/java/com/ziggfreed/kweebec/arena/CLAUDE.md)). The hunter role clones `Template_Kweebec_Razorleaf` (`DefaultPlayerAttitude:Hostile`, drop the Target sensor's Attitude filter + Range). Reskin = extend `Kweebec_Sapling` (`Parent` + dark texture + Void-glow `ModelParticle[]` on bones `Chest`/`L-Eye-Attachment`/`R-Eye-Attachment`), no new mesh.

**Threading (load-bearing, every package):** off-thread scheduler -> `world.execute` hop -> all Store/Ref/Sound/HUD/camera/weather on the world thread; packet writes are thread-safe; `spawnInstance`/`removeWorld` never `.join()` on a world tick thread.

## MMO Skill Tree integration (optional, outbound events - NO dependency)

The mod has **zero compile/runtime dependency on MMO Skill Tree**. It fires its own native events on the shared engine bus: `HytaleServer.get().getEventBus().dispatchFor(E.class); if (d.hasListener()) d.dispatch(new E(...))` (sync `IEvent<Void>` POJOs in `api/`). MMO consumes them MMO-side via the reflective `registerGlobal` adapter (the `AnglersAlmanacAdapter` pattern: load the event class from THIS mod's classloader so `Class` identity matches, then `getEventRegistry().registerGlobal`). When MMO is absent, the mod self-contains its own rewards. MMO can also reflectively call back into a small `KweebecNightmareAPI` to scale difficulty / replayability. Fire from a world-thread context so listeners can hop to `world.execute`.

## Release notes (patch-notes paradigm)

Per-version public release notes in `patch-notes/<version>.md`, same paradigm as the parent mod repo: frontmatter (`version`, `title`, `type: patch-note`, `status: held|released`), a one-line summary, then `- **New/Fixed: ...**` bullets; `patch-notes/_INDEX.md` newest-first. `CHANGELOG.md` is the dev changelog, `CURSEFORGE.md` the public listing, `README.md` the GitHub front page. **No em-dashes.** **Describe shipped reality, not aspiration** - at scaffold stage say "scaffold", not "adds a horror minigame".

## Conventions

PascalCase asset filenames; codec JSON keys start upper-case. `@Nonnull`/`@Nullable` on params; `KweebecNightmarePlugin.LOGGER` for logging. Localize all player-facing text via `Message`/lang keys from day 1 (no raw display strings); en-US complete. Package root `com.ziggfreed.kweebec`. As a submodule, the order is fixed: commit + push HERE first, verify the SHA is on the remote, THEN bump the gitlink in the parent hyMMO repo.

## Gotchas (each was a shipped 0.2.0 bug; the full detail lives in the package router)

- **`.lang` keys are unprefixed - the FILENAME is the namespace.** Prefixing them doubles the key and shows raw keys on screen. See [`i18n/`](src/main/java/com/ziggfreed/kweebec/i18n/CLAUDE.md).
- **Spawn/arena Y must match the worldgen floor** (`Default_Flat` `Base`=80 -> Y=80; the old Y=65 buried players in terrain). See [`arena/`](src/main/java/com/ziggfreed/kweebec/arena/CLAUDE.md).
- **Survivor game mode is set to Adventure ONCE on entry, never per-tick** (per-tick forcing fought a Creative admin). See [`mode/chase/`](src/main/java/com/ziggfreed/kweebec/mode/chase/CLAUDE.md) + [`hunter/`](src/main/java/com/ziggfreed/kweebec/hunter/CLAUDE.md).
- **Perfect Utils (`narwhals:Perfect Utils`) is a HARD manifest dependency** - its `AggroAPI` is the override-only hunter aggro layer; missing the jar at runtime fails the load. Compile path lives in `gradle.properties` (`perfectUtilsJar`); source is the Developer-Utils repo (see Build).
- **A reskinned/cloned NPC role TEMPLATE must HANDLE every state it sets or exports, or the whole `Abstract` builder fails validation - and every `Variant` that `References` it then fails as `unknown builder`, so the entity silently NEVER spawns** (no exception at the spawn call site; the role just no-shows). The 0.5.0 Warden boss shipped this: cloned from the Blight but DROPPED the `Alerted` state handler while its `Component_Kweebec_Instruction_Search` still listed `Alerted` in `_ExportStates`, so the role failed validation (`State setter ... without accompanying state/setter: Alerted`), `BossController.spawn` returned false, and the escape gate opened with NO boss (the no-soft-lock fallback). Fix = a `{Sensor:{Type:State,State:X}}` handler for every set/exported state (the Warden's `Alerted` escalates straight to `Attack`; clone the FULL state machine from the proven base role, do not trim handlers). The SERVER LOAD LOG names the offending state + role (`[NPC|P] FAIL: <template>.json: ...`) - read it FIRST when a custom NPC will not spawn. Unknown top-level keys also bite: the old `UseCombatActionEvaluator`/`_CombatConfig` CAE pair REJECTS the builder; a stray `$Comment_*` (vs the tolerated `$Comment`) only logs `Unknown JSON attribute`.
