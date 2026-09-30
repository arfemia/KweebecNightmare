# asset/

- New tunable content is a pack-authorable asset type registered through `ziggfreed-common`'s `AssetStoreRegistrar` in `KweebecAssetRegistrar`. Preset tuning is data in `Presets/*.json`, never a Java defaults class.
- `ScareBeat` and `Structure` are `ziggfreed-common` types (`BandedEffect`, `WeightedPrefabPlacement`, content under `Server/ZiggfreedCommon/`). The boss and the hunter waves are native encounter scripts, not asset types.
- `BossId` names the encounter script id and is carried verbatim and case-sensitive (`RoundPresetBossIdTest`).
- `ThrowableDamage` keys on the `DamageCause` id; `GatherHealthRestore` keys on the item id.
- Asset filenames are PascalCase; a lowercase file logs "Asset key ... has incorrect format!".
