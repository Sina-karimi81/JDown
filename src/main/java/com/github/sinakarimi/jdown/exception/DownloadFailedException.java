package com.github.sinakarimi.jdown.exception;

public class DownloadFailedException extends RuntimeException {

    public DownloadFailedException(String message) {
        super(message);
    }

    public DownloadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public DownloadFailedException(Throwable throwable) {
        super("Download Failed!!", throwable);
    }
}
