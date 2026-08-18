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
        List<Long> lastNightVictimIds,
        Long pendingVoteVictimId,
        Alignment winner,
        Role winningRole,
        boolean remoteJoin
) {

    /**
     * @param currentAction the resolved {@link NightAction} for {@code room}'s
     *                      current night role, if any (null outside NIGHT_ACTIONS
     *                      or before that role's selection resolves)
     * @param lastNightVictimIds every deferred-kill role's recorded target this
     *                           round (werewolves', and the corrupted judge's
     *                           when active) — empty when nobody died
     */
    public static MasterGameStateResponse from(Room room, List<MasterPlayerView> players,
            NightAction currentAction, List<Long> lastNightVictimIds) {
        return new MasterGameStateResponse(
                room.getCode(),
                room.getPhase(),
                room.getRoundNumber(),
                players,
                room.getCurrentNightRole(),
                room.getCurrentNightStepKind(),
                currentAction == null ? null : currentAction.getTargetPlayerId(),
                currentAction == null ? null : currentAction.getResultAlignment(),
                lastNightVictimIds,
                room.getPendingVoteVictimId(),
                room.getWinner(),
                room.getWinningRole(),
                room.isRemoteJoin());
    }
}
