# command/ - the /kweebec round-entry command

Router for `command/`. The first of the designed-for entry triggers (the void-rift pad / shrine block / guide NPC route through the same [`../round/RoundService`](../round/RoundService.java) entry and land later).

- **[`KweebecCommand`](KweebecCommand.java)** is `/kweebec [start|exit|endall] [preset]` (alias `/kn`): `start` spawns a Chase round for the caller PLUS every other player in the caller's current world (the lobby co-ops in together), `exit` leaves your round, `endall` force-ends every live round (admin/testing). Preset = amateur | nightmare | hardcore (default nightmare via `RoundPreset.DEFAULT`).
- **Every user-facing string + the command/arg descriptions resolve through [`../i18n/Lang`](../i18n/Lang.java)** (the engine resolves the `super(name, descKey)` + `withOptionalArg(name, descKey, ...)` strings as lang keys, not literals). `setPermissionGroup(GameMode.Adventure)` is deprecated-but-functional in 0.5.3; revisit when the engine ships a replacement.
