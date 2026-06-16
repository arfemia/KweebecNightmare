# atmosphere/ - frozen dark-midnight + forced weather

Router for `atmosphere/`. One file; world-thread-only (self-hops via `world.execute`), fully try-guarded.

- **[`AtmosphereService.lock`](AtmosphereService.java)** freezes the world into a dark midnight: `WorldTimeResource.setDayTime` to midnight (`dayTime` is a day-fraction from hour 0, where 0.0 = midnight / darkest) + `WorldConfig.setGameTimePaused(true)` so night never advances, then forces the first dark weather that VALIDATES (`Weather.getAssetMap().getIndex != Integer.MIN_VALUE`) - the pack weather first, then vanilla Void/Blood-Moon/Terror fallbacks. `markChanged()` after.
- **Validate every asset id before applying.** A `getIndex == Integer.MIN_VALUE` means the asset is missing - skip it, never set a bad id (an unknown weather blanks the sky). The same validate-then-use rule governs sounds in [`../feedback/`](../feedback/CLAUDE.md) and the cocoon effect in [`../death/`](../death/CLAUDE.md).
