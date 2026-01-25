package com.stability.martrix.exception;

/**
 * Exception thrown when tombstone data is invalid or missing required information
 */
public class InvalidTombstoneException extends RuntimeException {
    public InvalidTombstoneException(String message) {
        super(message);
    }

    public InvalidTombstoneException(String message, Throwable cause) {
        super(message, cause);
    }
}
