package com.ggutim.lupus.dto;

import com.ggutim.lupus.config.GameRules;

/**
 * Public snapshot of {@link GameRules}, served to the frontend so the
 * room-creation wizard can enforce the same constraints the backend
 * validates against, without duplicating them client-side.
 */
public record GameRulesResponse(
        int minPlayers,
        int maxPlayers
) {

    public static GameRulesResponse from(GameRules gameRules) {
        return new GameRulesResponse(gameRules.getMinPlayers(), gameRules.getMaxPlayers());
    }
}
