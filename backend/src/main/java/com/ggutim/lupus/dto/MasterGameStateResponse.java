package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.NightAction;
import com.ggutim.lupus.model.NightStepKind;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import java.util.List;

/**
 * Full game state as seen by the master: current phase, round, every
 * player's role, and any pending/resolved selections for the current
 * night or vote. Only ever returned over an authenticated,
 * master-token-gated endpoint — never broadcast.
 *
 * <p>{@code currentNightRole}/{@code currentNightStepKind} identify
 * which role's night turn is active while {@code phase} is
 * {@code NIGHT_ACTIONS}; {@code pendingNightActionTargetId} and
 * {@code nightActionResult} are that role's selection and any
 * immediate result (e.g. the priest's alignment reveal) for the
 * current round. {@code secondPendingNightActionTargetId} is used only
 * by the ghosts' two-target curse in afterlife mode — every other role
 * leaves it null. {@code nightActionResult} is already the value the
 * master should relay at the table — see {@code GameService}'s
 * assembly, which flips it (and sets {@code nightActionResultCursed})
 * when the target is currently cursed; the true alignment never
 * reaches this DTO in that case. {@code guardianBlockedPlayerId} is
 * set only during the guardian's turn — the player they protected last
 * round, who can't be selected again this round. {@code
 * pendingMayorSuccessionPlayerId} is set once the current mayor has
 * died and at least one other player is still alive to inherit the
 * card — while non-null, the master must resolve it before advancing
 * any further (see {@code GameService#assignMayorSuccessor}).
 */
public record MasterGameStateResponse(
        String code,
        GamePhase phase,
        int roundNumber,
        List<MasterPlayerView> players,
        Role currentNightRole,
        NightStepKind currentNightStepKind,
        Long pendingNightActionTargetId,
        Long secondPendingNightActionTargetId,
        Alignment nightActionResult,
        boolean nightActionResultCursed,
        Long guardianBlockedPlayerId,
        List<Long> lastNightVictimIds,
        Long pendingVoteVictimId,
        Alignment winner,
        Role winningRole,
        boolean remoteJoin,
        Long pendingMayorSuccessionPlayerId
) {

    /**
     * @param currentAction the resolved {@link NightAction} for {@code room}'s
     *                      current night role, if any (null outside NIGHT_ACTIONS
     *                      or before that role's selection resolves)
     * @param lastNightVictimIds every deferred-kill role's recorded target this
     *                           round (werewolves', and the corrupted judge's
     *                           when active) — empty when nobody died
     * @param nightActionResult the alignment to actually show the master —
     *                          already flipped by the caller if the target is
     *                          currently cursed
     * @param nightActionResultCursed whether that flip happened, so the master
     *                                sees a note explaining the lie
     * @param guardianBlockedPlayerId the player the guardian can't select this
     *                                round, or null when it isn't the guardian's turn
     */
    public static MasterGameStateResponse from(Room room, List<MasterPlayerView> players,
            NightAction currentAction, List<Long> lastNightVictimIds,
            Alignment nightActionResult, boolean nightActionResultCursed, Long guardianBlockedPlayerId) {
        return new MasterGameStateResponse(
                room.getCode(),
                room.getPhase(),
                room.getRoundNumber(),
                players,
                room.getCurrentNightRole(),
                room.getCurrentNightStepKind(),
                currentAction == null ? null : currentAction.getTargetPlayerId(),
                currentAction == null ? null : currentAction.getSecondTargetPlayerId(),
                nightActionResult,
                nightActionResultCursed,
                guardianBlockedPlayerId,
                lastNightVictimIds,
                room.getPendingVoteVictimId(),
                room.getWinner(),
                room.getWinningRole(),
                room.isRemoteJoin(),
                room.getPendingMayorSuccessionPlayerId());
    }
}
