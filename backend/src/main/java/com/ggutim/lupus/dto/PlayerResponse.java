package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.Player;

/**
 * Public view of a player, safe to broadcast to everyone in a room.
 * Never includes the player's role.
 */
public record PlayerResponse(Long id, String nickname, boolean alive) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getNickname(), player.isAlive());
    }

    /**
     * Like {@link #from}, but delays revealing an overnight kill until
     * the room moves past {@link GamePhase#MORNING_REVEAL}, so a player
     * checking the village overview can't spoil the master's reveal.
     */
    public static PlayerResponse visibleDuring(Player player, GamePhase phase) {
        boolean visiblyAlive = player.isAlive() || phase == GamePhase.MORNING_REVEAL;
        return new PlayerResponse(player.getId(), player.getNickname(), visiblyAlive);
    }
}
