package com.ggutim.lupus.room.exception;

/**
 * Thrown when a request to a master-only endpoint is missing the
 * {@code X-Master-Token} header or provides one that does not match the
 * room's master token.
 */
public class MasterTokenMismatchException extends RuntimeException {

    public MasterTokenMismatchException(String code) {
        super("Invalid or missing master token for room " + code);
    }
}
