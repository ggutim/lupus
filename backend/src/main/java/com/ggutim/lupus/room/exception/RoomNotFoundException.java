package com.ggutim.lupus.room.exception;

/**
 * Thrown when a room code does not match any existing room.
 */
public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(String code) {
        super("No room found with code " + code);
    }
}
