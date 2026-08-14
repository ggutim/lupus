package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GamePhase;
import java.util.List;

/**
 * Full game state as seen by the master: current phase, round, every
 * player's role, and any pending/resolved selections for the current
 * night or vote. Only ever returned over an authenticated,
 * master-token-gated endpoint — never broadcast.
 */
public record MasterGameStateResponse(
        String code,
        GamePhase phase,
        int roundNumber,
        List<MasterPlayerView> players,
        Long pendingWerewolfVictimId,
        Long pendingPriestTargetId,
        Alignment priestCheckResult,
        Long lastNightVictimId,
        Long pendingVoteVictimId,
        Alignment winner
) {
}
