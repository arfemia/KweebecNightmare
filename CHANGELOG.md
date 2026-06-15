# Changelog

Developer changelog for Kweebec Nightmare. User-facing release notes live in `patch-notes/`.

## 0.1.0 (in development)

Initial project scaffold.

- Standalone Hytale plugin stood up, mirroring the hyDungeons module layout: `java` + `api` Gradle modules, Java 25 toolchain, gradle wrapper, self-contained `build.ps1` that auto-installs to `HYTALE_MODS_DIR`.
- `manifest.json`: `ServerVersion ">=0.5.0-pre.0 <0.6.0"`, `IncludesAssetPack: true`, optional `Ziggfreed:MMOSkillTree` dependency, permission nodes.
- `KweebecNightmarePlugin` entry (setup/shutdown) and the `api` module's `RoundCompletedEvent` (a dungeon-completion-shaped signal for cross-jar consumers).
- A minimal `Server/Languages/en-US` asset pack so the combined plugin + asset-pack jar can be verified.
- Doc paradigm (CLAUDE.md, README, CURSEFORGE, this changelog, patch-notes).

Builds green. The round state machine, hunter AI, atmosphere, feedback stack, lobby trigger, and the MMOSkillTree bridge are the next phases (see the parent repo plan `utilize-a-new-git-snappy-moon`).
