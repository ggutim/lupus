package com.ggutim.lupus.room.dto;

/**
 * Lightweight broadcast sent over {@code /topic/rooms/{code}} whenever
 * the game state changes (phase advances, a selection is made). Carries
 * no secret information (no roles, no pending selections) — it only
 * tells the master's own client "something changed, go re-fetch the
 * authenticated game state". Kept separate from {@link RoomStateMessage}
 * which covers the pre-game lobby.
 */
public record GameUpdatedMessage(String code) {
}
