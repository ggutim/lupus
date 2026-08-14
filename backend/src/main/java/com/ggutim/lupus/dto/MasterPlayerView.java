package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;

/**
 * A player as seen by the master: includes the assigned role, unlike
 * {@link PlayerResponse} which is safe to broadcast to everyone.
 */
public record MasterPlayerView(Long id, String nickname, boolean alive, Role role) {

    public static MasterPlayerView from(Player player) {
        return new MasterPlayerView(player.getId(), player.getNickname(), player.isAlive(), player.getRole());
    }
}
