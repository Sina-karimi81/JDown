package com.github.sinakarimi.jdown.exception;

public class FileDataRequestFailedException extends Exception {

    public FileDataRequestFailedException() {
    }

    public FileDataRequestFailedException(String message) {
        super(message);
    }

    public FileDataRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileDataRequestFailedException(Throwable cause) {
        super(cause);
    }

}
