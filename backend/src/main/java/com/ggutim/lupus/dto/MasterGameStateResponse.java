package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.NightStepKind;
import com.ggutim.lupus.model.Role;
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
}
