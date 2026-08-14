package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Player;

/**
 * Returned to a player right after joining. Includes the player's own
 * secret token, exactly once — the client is responsible for storing it
 * to later fetch their own role via {@code GET
 * /api/rooms/{code}/players/{playerId}/role}.
 */
public record JoinRoomResponse(Long id, String nickname, String playerToken) {

    public static JoinRoomResponse from(Player player, String playerToken) {
        return new JoinRoomResponse(player.getId(), player.getNickname(), playerToken);
    }
}
