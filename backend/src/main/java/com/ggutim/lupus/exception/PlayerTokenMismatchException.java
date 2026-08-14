package com.ggutim.lupus.exception;

/**
 * Thrown when a request for a player's own data is missing the
 * {@code X-Player-Token} header or provides one that does not match
 * that player's token.
 */
public class PlayerTokenMismatchException extends RuntimeException {

    public PlayerTokenMismatchException(Long playerId) {
        super("Invalid or missing player token for player " + playerId);
    }
}
