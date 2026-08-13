package com.ggutim.lupus.room.exception;

/**
 * Thrown when a player id does not match any player in the given room.
 */
public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(Long playerId) {
        super("No player found with id " + playerId);
    }
}
