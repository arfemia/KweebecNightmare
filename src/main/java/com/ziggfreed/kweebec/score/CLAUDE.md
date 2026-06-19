# score/ - round scoring + the persisted leaderboard

Router for `score/`. Turns a round into a measurable per-player result, emits it as a
native event for other mods, and records a durable leaderboard.

- **[`ScoringConfig`](ScoringConfig.java)** - the configurable scoring weights (immutable, defaults
  pre-seeded): a baseline, a par time + points-per-second-under-par (speed), a damage budget +
  points-per-HP-avoided (caution), a flat per-stun bonus (aggression), and **devotion** - a per-shrine
  bonus (`shrineBonusPer`) plus a flat `allShrinesBonus` when the round lit EVERY discovered shrine.
  Scoring is **asset-driven PER PRESET**: authored on [`../asset/RoundPresetAsset`](../asset/RoundPresetAsset.java)
  and carried on [`../round/RuleSet`](../round/RuleSet.java)`.scoring()`, so each difficulty can score
  differently. The runtime tier ([`../integration/KweebecNightmareAPI`](../integration/KweebecNightmareAPI.java)`.overrideScoring`/`scaleScoring`/`resolveScoring(base)`)
  still lets an installed MMO override/scale the preset base without an asset edit.
- **[`ScoreCalculator`](ScoreCalculator.java)** - pure + deterministic `compute(duration, damageTaken,
  mobsStunned, moonbloomCollected, shrinesLit, allShrinesLit, win, cfg)` -> [`PlayerScore`](../../../../../api/src/main/java/com/ziggfreed/kweebec/api/PlayerScore.java)
  (the value object lives in the `api` module so the scored event can carry it across the jar boundary).
  `shrinesLit` is weighted into total via `shrineBonus`; `allShrinesLit` (from the shrine-discovery total,
  `ChaseState.allShrinesLit()`) gates the flat `allShrinesBonus`; `moonbloomCollected` stays a carried
  lifetime-stat input only (not weighted).
- **The leaderboard is the generalized `ziggfreed-common` primitive** (`instance/leaderboard/`), wired in
  [`../experience/KweebecExperience`](../experience/KweebecExperience.java), NOT a local class here. One
  board PER game-mode (`"leaderboard-chase"`); buckets are `"<difficulty>_<partySize>"` (see
  `KweebecExperience.bucketKey`). Each record keeps best score + best winning time + plays + cumulative
  total points + the per-stat counters (`stunned`/`moonbloom`/`shrines`). The page shows a difficulty x
  size two-axis tab set, a best-score/total/time sort toggle, and a global-aggregate Stats tab. Per-player
  stat sources: `mobsStunned` (KweebecDamageSystem), `shrinesLit` (ShrineSubmitInteraction),
  `moonbloomCollected` ([`../event/MoonbloomCollectSystem`](../event/MoonbloomCollectSystem.java)).
- **Wiring:** `RoundService.resolve` computes the per-player scores, fires `RoundEvents.fireRoundScored`
  ([`api/KweebecRoundScoredEvent`](../../../../../api/src/main/java/com/ziggfreed/kweebec/api/KweebecRoundScoredEvent.java),
  AFTER `RoundCompletedEvent`), and records each present player. A player's "win" = the round won AND
  they personally escaped, so a cocooned-but-team-won player scores lower than the survivor who ran.
