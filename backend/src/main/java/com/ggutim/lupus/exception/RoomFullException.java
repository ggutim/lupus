package com.ggutim.lupus.exception;

/**
 * Thrown when attempting to join a room that has already reached its
 * configured player count.
 */
public class RoomFullException extends RuntimeException {

    public RoomFullException(String code) {
        super("Room " + code + " is full");
    }
}
