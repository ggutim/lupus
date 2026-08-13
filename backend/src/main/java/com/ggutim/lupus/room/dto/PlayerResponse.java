package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.Player;

public record PlayerResponse(Long id, String nickname) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getNickname());
    }
}
