package com.ggutim.lupus.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload sent by a player wanting to join a room.
 */
public record JoinRoomRequest(
        @NotBlank(message = "nickname is required")
        @Size(max = 20, message = "nickname must be at most 20 characters")
        String nickname
) {
}
