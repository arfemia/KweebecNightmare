# moonbloom/

- `Moonbloom` and `GlowThrowables` are the id authority; Java and pack assets use these constants.
- A throwable item needs `MaxStack` and must consume through `{Type: ModifyInventory, AdjustHeldItemQuantity: -1}`; the vanilla Stun-Bomb chain lacks it.
- The burst deals a 1-HP tag with a custom `DamageCause`; the real damage comes from the preset's `ThrowableDamage` table.
- `RuleSet.throwMode` `CONE` is scaffolded and unwired; do not engage it without an explicit ask.
