package com.ggutim.lupus.room.exception;

/**
 * Thrown when the ruleset submitted to create a room is internally
 * inconsistent (e.g. role counts exceeding the total player count).
 */
public class InvalidRulesetException extends RuntimeException {

    public InvalidRulesetException(String message) {
        super(message);
    }
}
