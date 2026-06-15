# Kweebec Nightmare

A standalone co-op horror chase minigame for Hytale. You and up to three friends are Kweebec saplings trapped in a Void-blighted Emerald Grove at perpetual midnight. Relight the corrupted grove-shrines to open the Heartwood Gate, but each shrine you light draws the **Blighted Kweebec** that hunts you closer, and lighting the last one wakes it for the final chase to the exit.

> **Status: in development (mod-jam build).** This is a v0.1.0 scaffold - the plugin loads and the asset pack is wired, but the round loop is still being built. See [the changelog](CHANGELOG.md) and [patch notes](patch-notes/) for what is actually live.

## The loop

1. **Nightfall** - spawn into a dim grove, scout, and claim a hiding spot (hollow logs and bushes hide tree-folk well).
2. **The ritual** - relight the grove-shrines. Each is a hold-in-place ritual that pins you in place and makes noise the hunter can hear. The longer the night runs and the more shrines you light, the faster and darker it gets.
3. **The escape** - the final shrine opens the Heartwood Gate and sounds an alarm that locks the hunter onto the nearest sapling. Run.

Caught saplings are cocooned in roots and can be cut free by a teammate. Anyone who reaches the exit wins the night. Plays solo (one survivor, one AI hunter) or co-op for up to four.

## Standalone, with optional MMO Skill Tree integration

Kweebec Nightmare needs nothing else installed to play. If [MMO Skill Tree](https://www.curseforge.com/hytale/mods/mmo-skill-tree) is also installed, surviving a night feeds your skill progression (quest, achievement, and reward hooks); without it, the minigame grants its own self-contained rewards. The dependency is declared **optional**, so the jar loads either way.

## Install

Drop `KweebecNightmare-<version>.jar` into your server `Mods/` folder. Requires a Hytale server in the Update 5 range (`>=0.5.0-pre.0 <0.6.0`).

## Build from source

```powershell
.\build.ps1                  # build the jar, install it if a Mods folder is known
.\build.ps1 -Install:$false  # build only
```
Java 25. The Hytale server jar path is configured in `gradle.properties`. Set `HYTALE_MODS_DIR` once to auto-install on build. See [CLAUDE.md](CLAUDE.md) for the developer guide.

## Sibling

A top-down survival variant (hold the grove until dawn against escalating waves) lives in `KweebecNightmare2D`.
