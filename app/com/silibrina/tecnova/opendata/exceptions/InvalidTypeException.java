package com.silibrina.tecnova.opendata.exceptions;

/**
 * This is an exception to be thrown when an invalid type is given.
 */
public class InvalidTypeException extends RuntimeException {

    /**
     * Constructor of an invalid type exception without message.
     */
    public InvalidTypeException() {
        super();
    }

    /**
     * Constructor of an invalid type exception with the given message.
     * @param message
     */
    public InvalidTypeException(String message) {
        super(message);
    }
}
