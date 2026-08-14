package com.ggutim.lupus.room.exception;

/**
 * Thrown when a game action is attempted from a phase that does not
 * support it (e.g. selecting a werewolf victim while not in the
 * werewolf-selection phase).
 */
public class InvalidGamePhaseException extends RuntimeException {

    public InvalidGamePhaseException(String message) {
        super(message);
    }
}
