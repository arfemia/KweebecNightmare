# feedback/

- Combat-text and 3D-sound mechanics are in the root `src/main/java/com/ziggfreed/mmoskilltree/feedback/CLAUDE.md` and `ziggfreed-common`'s `zc-presentation`.
- `NightmareHud` element ids (`#Timer`, `#Objective`, `#Shrines`, `#Alive`, `#Corruption`, `#Extraction`) must match `Common/UI/Custom/Hud/KweebecNightmareHud.ui`.
- The Warden's bar is the engine boss bar the encounter script raises and its notices are `FeedbackMoment` assets; there is no custom boss HUD.
- `ScareDirector`'s vignette swap deliberately does not use `EntityEffectService.applyBand`: that API is tier-agnostic and rung-indexed, so adopting it shifts every vignette one band and drops `minTier`.
- `ChaseRoundMode.onTeardown` calls `ScareDirector.clear` last so no dread effect rides out on an ejected player.
