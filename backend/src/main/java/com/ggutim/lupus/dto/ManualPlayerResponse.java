package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;

/**
 * Master-only view of a manually-added player, including their role.
 * Unlike {@link PlayerResponse}, this must never be sent to a player
 * device — only to a verified master (see {@code MasterRoomStateResponse}
 * and {@code PlayerController#addPlayerManually}).
 */
public record ManualPlayerResponse(Long id, String nickname, Role role) {

    public static ManualPlayerResponse from(Player player) {
        return new ManualPlayerResponse(player.getId(), player.getNickname(), player.getRole());
    }
}
