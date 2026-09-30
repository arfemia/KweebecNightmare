# round/

- `RoundService` is the single mutating authority; `resolve` is idempotent through `RoundInstance.claimResolution`.
- `RoundStateMachine` polls at 1 Hz off-thread and hops through `world.execute`; five consecutive throwing ticks force-abort the round.
- Consume `spawnInstance` through `whenComplete`, never `.join()` on a world thread. `removeWorld` self-joins, so it runs on a dedicated daemon (`removeWorldOffThread`).
- Each member captures their own return transform; a win full-heals after the overworld inventory restore.
- `RoundInventoryGuard` strips on entry unless `InventoryMode.KEEP`, restores on exit and re-applies a leftover snapshot on `PlayerReady`.
- A new mode registers a `RoundMode` in `ModeRegistry` at setup; `startRound` refuses an unregistered mode.
