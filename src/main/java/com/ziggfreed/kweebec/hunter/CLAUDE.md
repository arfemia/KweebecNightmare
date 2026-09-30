# hunter/

- The chase is the role's own Hostile AI (512 view, alert and hearing ranges, no leash). `AggroAPI` is override-only and never taunts a solo survivor.
- Corruption-scaled speed is live: `applySpeed` swaps pre-authored `KweebecNightmare_HunterPace_*` effects per hunter.
- Tune wave escalation in `EncounterManager/KweebecNightmare_Hunters.json` by adding or removing rungs, not in Java.
- `KweebecHunterWave` builder keys are flat with native `[min, max]` ranges (the NPC builder vocabulary), not nested codec groups; the action always answers finished.
- `HunterFactors` providers answer null wherever the round walk breaks (fail closed).
- The hunter template is a parameterized clone of `Template_Kweebec_Razorleaf` because a plain Variant `Modify` cannot set `DefaultPlayerAttitude` to Hostile.
