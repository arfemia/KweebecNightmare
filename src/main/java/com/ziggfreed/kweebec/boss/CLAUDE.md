# boss/

- The fight is the native script `EncounterManager/KweebecNightmare_Warden_Encounter.json` plus zc's binding row. This package only raises it at the gate (`EncounterSpawner.spawnWhenLoaded`), keeps the per-preset map marker and the Emberbloom rings, and opens the gate on defeat, the `zc:kweebec:no_show` signal or a reset.
- An encounter script shares the NPC role id namespace: a script named after a role replaces the role at load, so a script id never matches a role id.
- Re-resolve the Warden on every read (`EncounterSubjects.resolve`): an in-place role change reissues its Ref.
- Encounter events fire synchronously inside engine systems; every reaction hops through `world.execute`.
- The gate bars on "a boss was requested", and every failure path of the raise opens it.
- Chase instances need `IsSpawnMarkersEnabled: true` with `IsSpawningNPC: false` so the manual-trigger markers fire only from the script.
