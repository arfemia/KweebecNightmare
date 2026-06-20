package com.ziggfreed.kweebec.round;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * The leave-no-gaps config-surface guard for a PvP (Clash / Domination) {@link RuleSet}: a PURE
 * static check (no engine calls) the lobby owner runs before a PvP round starts to surface
 * contradictory or unsafe preset combinations that would make a match hang, never resolve, or
 * silently clamp.
 *
 * <p>It returns a list of human-readable {@code [Kweebec]} warning strings (one line each); an
 * empty list means the preset is internally consistent. The check is advisory - the runtime still
 * clamps the values it warns about (e.g. {@code teamSize} via {@link RuleSet#teamSize()}) - so a
 * warning explains the fallback rather than blocking the round.
 *
 * <p>Checked combinations:
 * <ul>
 *   <li>{@code teamSize} outside the supported 1 (1v1) / 2 (2v2) set (clamped to a legal value);</li>
 *   <li>{@link RespawnPolicy#INFINITE} with an elimination win ({@link WinCondition#LAST_TEAM_STANDING}):
 *       nobody can ever be eliminated, so the match only resolves at the timer;</li>
 *   <li>a score-capped win condition ({@link WinCondition#MOST_KILLS} / {@link WinCondition#TDM_SCORE_TO_KILLS})
 *       with {@code scoreToWin <= 0}: there is no early cap, so the match falls back to the timer;</li>
 *   <li>{@code roundCapSeconds <= 0} with no elimination armed: no timer and no team-wipe end, so the
 *       match would hang;</li>
 *   <li>a negative {@code suddenDeathSeconds} (a malformed overtime window).</li>
 * </ul>
 */
public final class ClashPresetValidator {

    private ClashPresetValidator() {
    }

    /**
     * Validate the PvP-relevant fields of {@code ruleSet} and return any warnings.
     *
     * @param ruleSet the resolved rule-set for the round (never null)
     * @return an immutable-style list of {@code [Kweebec]} warning lines; empty when the preset is consistent
     */
    @Nonnull
    public static List<String> validate(@Nonnull RuleSet ruleSet) {
        List<String> warnings = new ArrayList<>();

        // Read the raw authored teamSize (RuleSet#teamSize() already clamps to >= 1, hiding a 0/negative);
        // validate against the supported 1v1 / 2v2 set so an out-of-range author hears about the clamp.
        int teamSize = ruleSet.teamSize();
        if (teamSize != 1 && teamSize != 2) {
            warnings.add("[Kweebec] teamSize=" + teamSize
                    + " is unsupported (only 1 for 1v1 or 2 for 2v2); it will be clamped to a legal value.");
        }

        ClashConfig clash = ruleSet.clash();
        WinCondition winCondition = clash.winCondition();
        RespawnPolicy respawnPolicy = clash.respawnPolicy();
        int scoreToWin = clash.scoreToWin();
        int suddenDeathSeconds = clash.suddenDeathSeconds();
        int roundCapSeconds = ruleSet.roundCapSeconds();

        // INFINITE respawn + an elimination win is contradictory: no death is ever permanent, so a team
        // can never be wiped and the elimination gate never fires.
        if (respawnPolicy == RespawnPolicy.INFINITE && winCondition == WinCondition.LAST_TEAM_STANDING) {
            warnings.add("[Kweebec] RespawnPolicy.INFINITE with WinCondition.LAST_TEAM_STANDING is contradictory: "
                    + "nobody can be eliminated, so the match will only resolve at the timer.");
        }

        // A score-capped win condition with no positive cap has no early end; it silently falls back to
        // the timer (and its tiebreak).
        if ((winCondition == WinCondition.MOST_KILLS || winCondition == WinCondition.TDM_SCORE_TO_KILLS)
                && scoreToWin <= 0) {
            warnings.add("[Kweebec] WinCondition." + winCondition.name() + " with scoreToWin=" + scoreToWin
                    + " (<= 0) has no cap; the match will fall back to the timer instead of ending early.");
        }

        // No timer AND no elimination would never resolve - the match would hang. (An elimination win can
        // still end a timer-less round by wiping a team, so it is only a hang when neither path exists.)
        if (roundCapSeconds <= 0 && !winCondition.isElimination()) {
            warnings.add("[Kweebec] roundCapSeconds=" + roundCapSeconds
                    + " (<= 0) with a non-elimination win condition (" + winCondition.name()
                    + ") leaves no way to end the match; it would hang.");
        }

        // A malformed overtime window.
        if (suddenDeathSeconds < 0) {
            warnings.add("[Kweebec] suddenDeathSeconds=" + suddenDeathSeconds
                    + " is negative; an overtime window must be 0 (resolve to a draw) or positive.");
        }

        return warnings;
    }
}
