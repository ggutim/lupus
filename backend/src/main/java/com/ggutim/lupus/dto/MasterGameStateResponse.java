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
 * current round.
 */
public record MasterGameStateResponse(
        String code,
        GamePhase phase,
        int roundNumber,
        List<MasterPlayerView> players,
        Role currentNightRole,
        NightStepKind currentNightStepKind,
        Long pendingNightActionTargetId,
        Alignment nightActionResult,
        Long lastNightVictimId,
        Long pendingVoteVictimId,
        Alignment winner
) {

    /**
     * @param currentAction the resolved {@link NightAction} for {@code room}'s
     *                      current night role, if any (null outside NIGHT_ACTIONS
     *                      or before that role's selection resolves)
     * @param lastNightVictimId the werewolves' recorded target this round, if any
     */
    public static MasterGameStateResponse from(Room room, List<MasterPlayerView> players,
            NightAction currentAction, Long lastNightVictimId) {
        return new MasterGameStateResponse(
                room.getCode(),
                room.getPhase(),
                room.getRoundNumber(),
                players,
                room.getCurrentNightRole(),
                room.getCurrentNightStepKind(),
                currentAction == null ? null : currentAction.getTargetPlayerId(),
                currentAction == null ? null : currentAction.getResultAlignment(),
                lastNightVictimId,
                room.getPendingVoteVictimId(),
                room.getWinner());
    }
}
