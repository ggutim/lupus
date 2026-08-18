package com.ggutim.lupus.dto;

import com.ggutim.lupus.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload sent by the master to add a player by hand, in a narrate-only
 * room. {@code role} is required when the room's roles are assigned
 * manually, and must be absent otherwise — see {@code PlayerService#addPlayerManually}.
 */
public record AddPlayerManualRequest(
        @NotBlank(message = "nickname is required")
        @Size(max = 20, message = "nickname must be at most 20 characters")
        String nickname,

        Role role
) {
}
