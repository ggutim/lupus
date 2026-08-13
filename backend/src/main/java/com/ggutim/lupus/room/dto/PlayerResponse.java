package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.Player;

public record PlayerResponse(String nickname) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getNickname());
    }
}
