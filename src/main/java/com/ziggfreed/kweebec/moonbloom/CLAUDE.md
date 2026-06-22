# moonbloom/ - the glowing-mushroom resource (gather / cleanse / throw)

Router for `moonbloom/`. The Moonbloom is ONE shared resource with two competing sinks
(the cleanse-vs-defend tension): spend at a shrine to cleanse, or throw to stun a hunter.
Real INVENTORY items, not an abstract counter.

- **[`Moonbloom`](Moonbloom.java)** - the id authority: `CHARGE_ITEM` (`KweebecNightmare_Moonbloom`,
  the throwable + spendable charge) and `PLANT_BLOCK` (`KweebecNightmare_Moonbloom_Plant`, the
  harvestable grove block that drops the charge). One place so the Java and the pack assets never
  drift.
- **[`GlowThrowables`](GlowThrowables.java)** (0.6.0) - the id authority for Moonbloom's SIBLING glow-throwable
  family: Gustbloom (knockback), Mirebloom (slow), Emberbloom (boss-phase damage). Each is a reskinned vanilla
  glowing mushroom whose on-burst effect is authored 100% in its pack burst JSON (no Java) - knockback via
  `DamageEffects.Knockback` (Type Point), slow via an `ApplyEffect` step + a slow EntityEffect, damage via a
  real `BaseDamage`. The same gather loop as Moonbloom (item / `_Plant` block / `_Drop` droplist / `/Prefab`
  cluster / throw chain). Distribution: Gust/Mire are DATA-DRIVEN via [`../asset/GroveThrowableConfig`](../asset/CLAUDE.md)
  (shipped `Enabled:false`); Emberbloom clusters are placed by [`../boss/BossController`](../boss/BossController.java)
  during the Warden phases. The only Java touch beyond ids is the Emberbloom friendly-fire guard in
  [`../event/KweebecDamageSystem`](../event/CLAUDE.md) (`EMBER_DAMAGE_CAUSE` nulled on a survivor victim).
- **Gather** is asset-only: the `Moonbloom_Plant` block's `Gathering.Harvest.DropList`
  (`Server/Drops/KweebecNightmare/KweebecNightmare_Moonbloom_Drop.json`) yields the charge item.
  Placement (a guaranteed cluster at each unlit surface shrine + a seed-deterministic grove scatter +
  mid-match respawn waves) lives in [`../arena/ArenaBuilder`](../arena/ArenaBuilder.java)`.plantMoonbloom`,
  driven at build (initial) and from [`../mode/chase/ChaseMode`](../mode/chase/ChaseMode.java)`.maybeRespawnMoonbloom`.
- **Cleanse** is an interactable shrine FURNACE (the 0.4.0 rework): a survivor presses F on a
  `KweebecNightmare_Shrine` block to OFFER Moonbloom. The registered
  [`../interaction/ShrineSubmitInteraction`](../interaction/CLAUDE.md) deposits up to the remaining need
  (`RuleSet.cleanseCost()` total, authored per preset - 3 on Amateur) via `ziggfreed-common`
  `InventoryUtil.take`, and once full `ChaseMode.lightShrine` lights the furnace with green fire. `cleanseCost`
  is a pack/runtime-configurable rule-set knob (0 = light on the first press). `ChaseMode.handleShrines` is
  now only a per-tick lit reconciler; the old proximity-spend (`InventoryUtil.spend` within `INTERACT_RADIUS`)
  is gone. Cleansing is gated to the RITUAL phase.
- **Throw-to-stun** is mostly assets (a reskin of the vanilla `Weapon_Bomb_Stun` chain: the item left-click
  `Moonbloom_Throw` -> `Projectile_Config_KweebecNightmare_Moonbloom` -> `Moonbloom_Burst`). The item needs
  `MaxStack` (vanilla bombs default non-stacking) and the throw must consume via
  `{Type:ModifyInventory, AdjustHeldItemQuantity:-1}` (the proven `Item_Throw` step; the Stun-Bomb chain
  lacks it). The burst deals a 1-HP TAG carrying a **custom DamageCause** `KweebecNightmare_Moonbloom`
  (authored at `Server/Entity/Damage/`) - that cause is how [`../event/KweebecDamageSystem`](../event/CLAUDE.md)
  identifies a Moonbloom hit (source- and world-agnostic: see that router for WHY the source is the
  shooter, not the projectile). On a Moonbloom-caused hit to a non-player mob ANYWHERE it applies the
  Perfect Utils `StunMobAPI` freeze + the vanilla `Stun` effect (the visible Stunned stars; the Perfect
  Utils `DU_Entity_Stunned` has no particle). `RuleSet.throwMode` selects PROJECTILE (default) or the
  code-only CONE fallback (scaffolded, not wired - DO NOT engage cone without an explicit ask).
- **Per-difficulty throwable damage (generic, asset-driven).** The burst's `BaseDamage` is a fixed 1-HP TAG;
  the ACTUAL damage a thrown throwable deals to a mob in a round is the per-preset `ThrowableDamage` table
  on [`../round/RuleSet`](../round/CLAUDE.md) - a `Map<DamageCauseId, amount>` (authored in the preset JSON,
  e.g. `"ThrowableDamage": {"KweebecNightmare_Moonbloom": 25}`; Amateur 25 / Nightmare 12 / Hardcore 1).
  [`../event/KweebecDamageSystem`](../event/CLAUDE.md) reverse-resolves the hit's `DamageCause` id and, if the
  preset tunes it, `setAmount`s the override (covers Moonbloom, Emberbloom, and ANY future authored throwable
  with NO per-item Java; a cause absent from the table keeps the burst's authored damage). Outside a round the
  authored 1 stands.
- **Per-difficulty GATHER HEAL (generic, asset-driven).** Picking up a glow-mushroom can RESTORE HP, tuned per
  preset by the `GatherHealthRestore` knob on [`../round/RuleSet`](../round/CLAUDE.md) - a `Map<ItemId, hp>` keyed
  by the gathered ITEM id (e.g. `{"KweebecNightmare_Moonbloom": 8, "KweebecNightmare_Emberbloom": 8}`; Amateur
  8/8, Nightmare 5/5, Hardcore 2/3). [`../event/MoonbloomCollectSystem`](../event/CLAUDE.md) (the in-round pickup
  observer) reads it on each gather and tops the gatherer off by `perShroom * quantity` via ziggfreed-common
  `HealthUtil.heal` (a stack of N heals N times; an item absent from the table heals nothing; no per-item Java,
  so any future authored shroom is covered the moment a preset lists it). Note the heal table keys on the ITEM
  id (`Moonbloom.CHARGE_ITEM` / `GlowThrowables.EMBER_ITEM`), distinct from the throwable-damage table which keys
  on the DamageCause id (`KweebecNightmare_EmberHit`).
