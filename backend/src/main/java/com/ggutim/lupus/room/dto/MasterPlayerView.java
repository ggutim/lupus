package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.Player;
import com.ggutim.lupus.room.Role;

/**
 * A player as seen by the master: includes the assigned role, unlike
 * {@link PlayerResponse} which is safe to broadcast to everyone.
 */
public record MasterPlayerView(Long id, String nickname, boolean alive, Role role) {

    public static MasterPlayerView from(Player player) {
        return new MasterPlayerView(player.getId(), player.getNickname(), player.isAlive(), player.getRole());
    }
}
