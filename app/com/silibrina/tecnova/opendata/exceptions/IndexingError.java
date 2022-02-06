package com.silibrina.tecnova.opendata.exceptions;

import javax.annotation.Nonnull;

import static com.silibrina.tecnova.commons.utils.Preconditions.checkValidString;

public class IndexingError<T> {
    private final T exception;
    private final String msg;

    public IndexingError(@Nonnull String msg, T exception) {
        checkValidString("message can not be null", msg);

        this.msg = msg;
        this.exception = exception;
    }

    public String getMessage() {
        return msg;
    }

    public T getException() {
        return exception;
    }
}
