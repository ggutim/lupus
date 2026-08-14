package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.RoomStatus;
import java.util.List;

/**
 * Broadcast over WebSocket to everyone watching a room (primarily the
 * master), whenever the player list changes or the room starts.
 */
public record RoomStateMessage(
        String code,
        RoomStatus status,
        int playerCount,
        List<PlayerResponse> players
) {
}
