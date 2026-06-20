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
