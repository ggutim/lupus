package com.ggutim.lupus.exception;

/**
 * Thrown when a player tries to join a room with a nickname already used
 * by another player in the same room.
 */
public class NicknameTakenException extends RuntimeException {

    public NicknameTakenException(String nickname) {
        super("Nickname \"" + nickname + "\" is already taken in this room");
    }
}
