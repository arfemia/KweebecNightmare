# pages/ - custom UI pages

Router for `pages/`. Mod-specific full-screen `InteractiveCustomUIPage`s (distinct from the
runtime HUD overlay in [`../feedback/`](../feedback/CLAUDE.md), which is a `CustomUIHud`).

- **[`KweebecLeaderboardPage`](KweebecLeaderboardPage.java)** - the in-game leaderboard the hub NPC's
  `{ "Open": "leaderboard" }` dialogue option launches (routed in [`../dialogue/KweebecDialogue`](../dialogue/KweebecDialogue.java)
  via the engine's `.router(...)` seam; no custom action class). One decorated panel: four party-size
  tabs (Solo/Duo/Trio/Squad), a column header, and a scrollable ranked list of
  [`../score/Leaderboard`](../score/Leaderboard.java)`.forPartySize(n)` sorted by best score (top 3
  gold/silver/bronze, the viewer's row highlighted + summarized in a footer). Names resolve live
  (`Universe.get().getPlayer(uuid).getUsername()`) then the persisted `Entry.name` then a short UUID.
- **[`LeaderboardEventData`](LeaderboardEventData.java)** - the page's click POJO + codec (`Action` + `Party`).

**Conventions (these were load-bearing elsewhere; keep them):**
- **DRY + the ziggfreed-common paradigm.** The page's `.ui` imports the SHARED styles cross-jar from
  ziggfreed-common (`$F = "../Common/ZigFrames.ui"` -> `@ZigDecoratedFrame`; `$Z = "../Common/ZigButtons.ui"`
  -> `@ZigTabBtnStyle`) and uses common's `util/NumberFormatter`. Do NOT redefine a frame/button style
  locally; if a needed generic style is missing, ADD it to ziggfreed-common's `ZigButtons.ui`/`ZigFrames.ui`
  (one owner, reusable) and rebuild+repin the common jar. `.ui` imports resolve client-side across the
  merged asset tree, so a Kweebec-shipped page can import common's `../Common/Zig*.ui`.
- **Every `handleDataEvent` exit path sends a response** (`openCustomPage` or `setPage(Page.None)`); the
  page manager does not wrap build/handleDataEvent and the client spins forever otherwise.
- **The active tab is painted from Java** by overwriting `#Tab*.Style.{Default,Hovered,Pressed}.Background.Color`
  (the @ZigTabBtnStyle stays the one neutral style); a per-row IMAGE glyph would have to be a pre-authored
  hidden child (a `../Common/...` texture set from Java renders a red X), but the rows are colour-only Labels.
- **`.ui` is not gradle-validated** - smoke-test in-game.
