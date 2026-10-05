# hunter/

- The chase is the role's own Hostile AI (512 view, alert and hearing ranges, no leash). `AggroAPI` is override-only and never taunts a solo survivor.
- Corruption-scaled speed is live: `applySpeed` swaps pre-authored `KweebecNightmare_HunterPace_*` effects per hunter.
- Tune wave escalation in `EncounterManager/KweebecNightmare_Hunters.json` by adding or removing rungs, not in Java.
- `KweebecHunterWave` builder keys are flat with native `[min, max]` ranges (the NPC builder vocabulary), not nested codec groups; the action always answers finished.
- `HunterFactors` providers answer null wherever the round walk breaks (fail closed).
- The hunter template is a parameterized clone of `Template_Kweebec_Razorleaf` because a plain Variant `Modify` cannot set `DefaultPlayerAttitude` to Hostile.
- A hunter goes down only into a ticking chunk section (`AiHunterController.spawnWhenTicking` over ziggfreed-common's `TickingSections.ensureTicking`): on Update 7 one added into a sleeping section is parked untracked, and nothing guarantees a survivor's hot sphere covers the den or a wave hunter's spot when it goes down. A section the guard had to wake logs one INFO line (`woke the chunk section under hunter role`); a hunter whose section does not tick after the wake (not in memory, or not awake yet) is skipped with a WARNING, never parked.
