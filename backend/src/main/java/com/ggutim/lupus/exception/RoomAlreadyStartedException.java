package com.ggutim.lupus.exception;

/**
 * Thrown when attempting to join a room whose game has already started.
 */
public class RoomAlreadyStartedException extends RuntimeException {

    public RoomAlreadyStartedException(String code) {
        super("Room " + code + " has already started");
    }
}
