# moonbloom/

- `Moonbloom` and `GlowThrowables` are the id authority; Java and pack assets use these constants.
- A throwable item needs `MaxStack` and must consume through `{Type: ModifyInventory, AdjustHeldItemQuantity: -1}`; the vanilla Stun-Bomb chain lacks it.
- A burst deals a Hytale damage type (Moonbloom a 1-HP Physical hit) and carries `"Tags": {"Hit": ["<item id>"]}` at its file's root; the real damage comes from the preset's `ThrowableDamage` table, keyed by that tag.
- `RuleSet.throwMode` `CONE` is scaffolded and unwired; do not engage it without an explicit ask.
