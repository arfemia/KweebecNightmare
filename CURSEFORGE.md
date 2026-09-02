# Kweebec Nightmare

**A standalone co-op horror minigame. Relight the grove together, or be hunted in the dark.**

The Void has crept into the Emerald Grove, and the gentle Kweebec tree-folk did not survive it whole. Now they wear one another's faces and hunt through a perpetual midnight that never breaks. You are one of the few still warm, still awake - alone, or with up to three others - and the only way out is to wake the grove: relight its corrupted shrines with the last of its clean light. But lighting a shrine is loud, and the thing in the dark has very good ears. Relight them all to open the Heartwood Gate, then gather on the platform beyond and hold it together until the grove lets you go, all while the hunt grows faster and hungrier with every shrine you burn bright.

> v1.1.2. Rebuilt for Hytale Update 6 and the current Ziggfreed's CommonLib: more Emberbloom stocked for the Warden fight, chase rewards that pay out reliably, Artillery training on MMO Skill Tree servers, and round wins announced to the shared progression engine so other mods can count them. The co-op chase ("Relight & Escape") runs end to end. If something feels off in your server, please report it in my discord so it can be fixed.

## Features

- **Co-op horror, 1 to 4 players.** Work together to relight the grove, or get cocooned in roots and hope a friend cuts you free. Plays solo against an AI hunter too.
- The Blighted Kweebec tracks the noise of your rituals and grows faster and more relentless as the grove darkens, hunting differently depending on the breed that stalks you.
- Every shrine you light brings you closer to escape and closer to the hunt. The final shrine is the loudest of all, and it wakes something worse.
- **A capstone boss.** On the harder nights the corrupted Warden rises when the gate opens, tougher the larger your party and the higher the difficulty. Keep it killable by gathering and hurling glow-mushrooms, and track it on your map as it hunts.
- Gather corrupted glowcaps and throw them. Each bursts differently: stun the hunter, knock it flying, slow it to a slog, or blast for real damage. Stocking your kit is half of staying alive.
- Opening the Heartwood Gate is not the end. The survivors have to gather on the extraction platform and hold it together until everyone gets out, and the hold resets if the group breaks.
- A new instanced grove per round, with shifting shrine layouts, caves, and corrupted ruins. Win well and you earn better loot.
- **Built for dread.** Perpetual midnight, thickening fog, a proximity heartbeat that tightens as the hunter nears, whispers in the trees, cinematic title cards.

## Install

Place `KweebecNightmare-<version>.jar` in your server `Mods/` folder. Requires a Hytale Update 6 (0.6.x) server, plus the **Ziggfreed's CommonLib** (2.0.0 or newer) and **Perfect Utils** dependencies (the server loads them first).

## Versions

| Version | Notes                                                                                                                                                                                                                                                                                                                                                                          |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1.1.2   | The Update 6 (0.6.x) build, on Ziggfreed's CommonLib 2.0.0+. More Emberbloom stocked in the dungeon crypts and their chests, and the mod now ships as a standalone jar requiring the CommonLib and Perfect Utils companions (previously bundled in). Chase rewards pay out reliably on finishing a round; Amateur, Hardcore, and Nightmare also grant a reward on a loss. Round wins are announced on the shared progression bus, so quests and achievements from other mods can count them, and thrown glow-mushrooms still train Artillery on MMO Skill Tree servers. On servers also running MMO Skill Tree the Grove Warden no longer stands at spawn (its own guide is already there); where the Warden stands is an asset now, with an on/off switch in `mods/ziggfreedcommon/npc-placements.json`. One-time reset on upgrade: unclaimed pending rewards and the leaderboard start over. |
| 1.0.0   | First public release. The co-op chase ("Relight & Escape"): an instanced grove round with shifting layouts, difficulty presets, the Blighted hunter and its breeds, gather-and-throw glow-mushrooms, the multi-phase Warden capstone (scaling with party size and difficulty, with a world-map marker), score-tiered win loot, and a co-op extraction hold to escape together. |
| 0.2.0   | Chase round built ("Relight & Escape"): round engine, difficulty presets, the Blighted hunter, cocoon and rescue, dark atmosphere and round HUD.                                                                                                                                                                                                                               |
| 0.1.0   | Initial project scaffold (not yet playable).                                                                                                                                                                                                                                                                                                                                   |
