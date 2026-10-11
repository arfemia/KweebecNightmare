# asset/

- New tunable content is a pack-authorable asset type registered through `ziggfreed-common`'s `AssetStoreRegistrar` in `KweebecAssetRegistrar`. Preset tuning is data in `Presets/*.json`, never a Java defaults class.
- Scare beats and arena structures are `ziggfreed-common` asset types, `BandedEffectAsset` (zc-effects) and `WeightedPrefabPlacementAsset` (zc-world), with content under `Server/ZiggfreedCommon/`. The boss and the hunter waves are native encounter scripts, not asset types.
- `BossId` names the encounter script id and is carried verbatim and case-sensitive (`RoundPresetBossIdTest`).
- `ThrowableDamage` keys on the burst's hit tag and `GatherHealthRestore` on the item id, the same id for each bloom.
- Asset filenames are PascalCase; a lowercase file logs "Asset key ... has incorrect format!".
