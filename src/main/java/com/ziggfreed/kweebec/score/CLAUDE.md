# score/ - round scoring + the persisted leaderboard

Router for `score/`. Turns a round into a measurable per-player result, emits it as a
native event for other mods, and records a durable leaderboard.

- **[`ScoringConfig`](ScoringConfig.java)** - the configurable scoring weights (immutable, defaults
  pre-seeded): a baseline, a par time + points-per-second-under-par (speed), a damage budget +
  points-per-HP-avoided (caution), and a flat per-stun bonus (aggression). The single configurable
  scoring surface; the runtime tier ([`../integration/KweebecNightmareAPI`](../integration/KweebecNightmareAPI.java)`.overrideScoring`/`scaleScoring`/`resolveScoring`)
  lets an installed MMO tune it without an asset edit.
- **[`ScoreCalculator`](ScoreCalculator.java)** - pure + deterministic `compute(duration, damageTaken,
  mobsStunned, win, cfg)` -> [`PlayerScore`](../../../../../api/src/main/java/com/ziggfreed/kweebec/api/PlayerScore.java)
  (the value object lives in the `api` module so the scored event can carry it across the jar boundary).
- **[`Leaderboard`](Leaderboard.java)** - per PARTY SIZE bucket, UUID-keyed (best score + best winning
  time + plays). Loaded once at setup from the plugin data dir (`getDataDirectory()/leaderboard.json`);
  `record` mutates the in-memory `ConcurrentHashMap` (safe from the world-thread resolve) and schedules
  a DEBOUNCED off-thread atomic flush (`FileUtil.writeStringAtomic`, Gson, `.bak` fallback). No UI;
  `forPartySize(int)` is the query seam (the `/kweebec leaderboard` chat dump + a future board).
- **Wiring:** `RoundService.resolve` computes the per-player scores, fires `RoundEvents.fireRoundScored`
  ([`api/KweebecRoundScoredEvent`](../../../../../api/src/main/java/com/ziggfreed/kweebec/api/KweebecRoundScoredEvent.java),
  AFTER `RoundCompletedEvent`), and records each present player. A player's "win" = the round won AND
  they personally escaped, so a cocooned-but-team-won player scores lower than the survivor who ran.
