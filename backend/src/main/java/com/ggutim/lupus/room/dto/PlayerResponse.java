package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.Player;

/**
 * Public view of a player, safe to broadcast to everyone in a room.
 * Never includes the player's role.
 */
public record PlayerResponse(Long id, String nickname, boolean alive) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getNickname(), player.isAlive());
    }
}
