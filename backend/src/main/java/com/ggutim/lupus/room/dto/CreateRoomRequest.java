package com.ggutim.lupus.room.dto;

import com.ggutim.lupus.room.GameMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload sent by the master at the end of the room-creation wizard:
 * game mode, total player count, and the number of werewolves and priests.
 * The remaining players are assigned the villager role.
 */
public record CreateRoomRequest(
        @NotNull(message = "gameMode is required")
        GameMode gameMode,

        @NotNull(message = "playerCount is required")
        @Min(value = 4, message = "playerCount must be at least 4")
        @Max(value = 30, message = "playerCount must be at most 30")
        Integer playerCount,

        @NotNull(message = "werewolfCount is required")
        @Min(value = 1, message = "werewolfCount must be at least 1")
        Integer werewolfCount,

        @NotNull(message = "priestCount is required")
        @Min(value = 0, message = "priestCount must be at least 0")
        Integer priestCount
) {
}
