package com.ggutim.lupus.room.exception;

/**
 * Thrown when the master tries to start a game with fewer than the
 * minimum required number of joined players.
 */
public class NotEnoughPlayersException extends RuntimeException {

    public NotEnoughPlayersException(String code, int required) {
        super("Room " + code + " needs at least " + required + " players to start");
    }
}
