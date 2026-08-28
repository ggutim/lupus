package com.ggutim.lupus.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Payload for resolving a pending mayor succession: which living
 * player the dead mayor hands their card to.
 */
public record MayorSuccessionRequest(
        @NotNull(message = "successorPlayerId is required")
        Long successorPlayerId
) {
}
