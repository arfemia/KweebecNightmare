# moonbloom/ - the glowing-mushroom resource (gather / cleanse / throw)

Router for `moonbloom/`. The Moonbloom is ONE shared resource with two competing sinks
(the cleanse-vs-defend tension): spend at a shrine to cleanse, or throw to stun a hunter.
Real INVENTORY items, not an abstract counter.

- **[`Moonbloom`](Moonbloom.java)** - the id authority: `CHARGE_ITEM` (`KweebecNightmare_Moonbloom`,
  the throwable + spendable charge) and `PLANT_BLOCK` (`KweebecNightmare_Moonbloom_Plant`, the
  harvestable grove block that drops the charge). One place so the Java and the pack assets never
  drift.
- **Gather** is asset-only: the `Moonbloom_Plant` block's `Gathering.Harvest.DropList`
  (`Server/Drops/KweebecNightmare/KweebecNightmare_Moonbloom_Drop.json`) yields the charge item.
  Placement (a guaranteed cluster at each unlit surface shrine + a seed-deterministic grove scatter +
  mid-match respawn waves) lives in [`../arena/ArenaBuilder`](../arena/ArenaBuilder.java)`.plantMoonbloom`,
  driven at build (initial) and from [`../mode/chase/ChaseMode`](../mode/chase/ChaseMode.java)`.maybeRespawnMoonbloom`.
- **Cleanse** (pure swap) lives in `ChaseMode.handleShrines`: an active survivor within
  `INTERACT_RADIUS` of an unlit shrine who holds `RuleSet.cleanseCost()` charges SPENDS them (via
  `ziggfreed-common` `InventoryUtil.spend`) and the shrine lights instantly. `cleanseCost` is a
  pack/runtime-configurable rule-set knob (0 = cleanse on proximity alone).
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
