package com.ggutim.lupus.dto;

/**
 * Result of the killer's reveal-and-guess, alongside the full updated
 * game state (who died — the guessed player if {@code correct}, the
 * killer himself otherwise — and whether that ended the game).
 */
public record KillerGuessResponse(boolean correct, MasterGameStateResponse gameState) {
}
