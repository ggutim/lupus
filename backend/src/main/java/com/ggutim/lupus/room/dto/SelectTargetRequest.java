package com.ggutim.lupus.room.dto;

/**
 * Payload for the master selecting a player during a night or vote
 * phase. {@code playerId} may be {@code null} only for the vote phase,
 * meaning nobody was voted out.
 */
public record SelectTargetRequest(Long playerId) {
}
