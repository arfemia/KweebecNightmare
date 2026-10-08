# npc/

- The Grove Keeper (the guide, once called the Grove Warden) is the `ziggfreed-common` placement asset `Kweebec_Grove_Warden.json`; this mod has no spawner.
- `npc-placements.json` carries only `enabled` and cannot un-gate the asset's `Requires`. A server wanting the Keeper beside the MMO ships a same-id asset without `Requires`.
- Press-F lives in the role (`ZigOpenDialogue`) so a hand-spawned Warden works. `NpcActions.register()` runs in `setup()` before role assets load.
- A Warden saved by an older build carries no `PlacedNpcComponent` and is never swept.
