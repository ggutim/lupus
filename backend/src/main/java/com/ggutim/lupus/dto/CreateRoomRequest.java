package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.GameMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload sent by the master at the end of the room-creation wizard:
 * game mode, total player count, and the number of werewolves, priests,
 * gravediggers, idiots, corrupted judges and survivors. The remaining
 * players are assigned the villager role.
 *
 * <p>The allowed range for {@code playerCount} is configurable (see
 * {@link com.ggutim.lupus.config.GameRules}) and is therefore validated in
 * {@link com.ggutim.lupus.service.RoomService} rather than with a
 * compile-time bean validation constraint here.
 */
public record CreateRoomRequest(
        @NotNull(message = "gameMode is required")
        GameMode gameMode,

        @NotNull(message = "playerCount is required")
        Integer playerCount,

        @NotNull(message = "werewolfCount is required")
        @Min(value = 1, message = "werewolfCount must be at least 1")
        Integer werewolfCount,

        @NotNull(message = "priestCount is required")
        @Min(value = 0, message = "priestCount must be at least 0")
        Integer priestCount,

        @NotNull(message = "gravediggerCount is required")
        @Min(value = 0, message = "gravediggerCount must be at least 0")
        Integer gravediggerCount,

        @NotNull(message = "idiotCount is required")
        @Min(value = 0, message = "idiotCount must be at least 0")
        Integer idiotCount,

        @NotNull(message = "corruptedJudgeCount is required")
        @Min(value = 0, message = "corruptedJudgeCount must be at least 0")
        @Max(value = 1, message = "corruptedJudgeCount cannot be more than 1")
        Integer corruptedJudgeCount,

        @NotNull(message = "survivorCount is required")
        @Min(value = 0, message = "survivorCount must be at least 0")
        Integer survivorCount,

        @NotNull(message = "remoteJoin is required")
        Boolean remoteJoin,

        @NotNull(message = "manualRoles is required")
        Boolean manualRoles
) {
}
