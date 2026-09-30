# mode/chase/

- Set survivors to Adventure once on entry, never per tick (per-tick forcing fights an admin in Creative).
- The extraction hold marks every active survivor escaped at once when it completes, never per tick on contact.
- Shrines are counted, not pre-listed: `totalShrines` is known up front and furnaces register on first F-press; cleansing is gated to `RITUAL`.
- `ChaseState.loudestChanneller` and `ShrineState` `progress`, `channeller`, `feedbackStage` and `caveSurfaceTopY` are vestigial.
