package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for the killer's once-per-game power: reveal themselves and
 * guess {@code targetPlayerId}'s exact role in one shot.
 */
public record KillerGuessRequest(
        @NotNull(message = "targetPlayerId is required")
        Long targetPlayerId,

        @NotNull(message = "guessedRole is required")
        Role guessedRole
) {
}
