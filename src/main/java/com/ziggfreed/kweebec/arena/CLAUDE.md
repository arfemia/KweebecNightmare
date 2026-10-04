# arena/

- Gameplay runs off `ArenaLayout` anchors, never pasted geometry; a round stays playable when a paste fails.
- Surface shrines are worldgen-scattered host prefabs (`KN_ShrineHosts`, `KN_ShrineLarge`, `KN_ShrineDungeon`; `KN_ShrineHouses` is `Skip: true`). `ShrinePlacement` force-loads the r70 core because ECS queries see only loaded chunks, then detects hosts by the `kn_shrine_marker` `PersistentDisplayName` sentinel every marker-bearing `Shrine_*` prefab must carry (`Shrine_Crypt_Large` carries none).
- A force-load takes its chunk columns from `ChunkColumns` (`around` a point, `covering` an area), keyed by `ChunkUtil.chunkCoordinate`: a Hytale chunk is 32 blocks wide, so never shift a block coordinate by 4. Ziggfreed Common 2.2.0's `SurfaceProbe` reads only loaded chunks, so a paste where no player stands force-loads its columns first.
- The top-up places only the small `Shrine_Crypt_01`, so the large crypt and dungeon rarely land inside the core; a guaranteed big structure needs deterministic placement.
- A V2 `Prefab` prop `WeightedPrefabPaths.Path` must name a folder or spell the `.prefab.json` extension; an extension-less file name silently loads nothing (`PrefabLoader.isPrefabFile`). A malformed `Prefab` prop airs out the whole biome. The biome JSON is not compiled: boot-check and play-test it.
- Runtime pastes floor-snap with `SurfaceProbe`, skipping surface decoration via `ArenaBuilder.surfaceDecorationKeys`. The exit and cave shafts re-paste at +4s and +9s to beat the worldgen race, idempotent through `ChaseState.caveCarveY`.
- A prefab paste carries no entities, so spawn markers ride the arena as blocks (`Warden_Spawners`).
- Vanilla prefab keys do not resolve at runtime: copy the prefab into the pack (`tools/build_extraction_pad.py` regenerates `Extraction_Pad`). The exit pad takes no portal-surface block because it teleports.
