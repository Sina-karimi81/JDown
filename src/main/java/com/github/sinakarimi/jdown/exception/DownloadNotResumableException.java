package com.github.sinakarimi.jdown.exception;

public class DownloadNotResumableException extends RuntimeException {
    public DownloadNotResumableException(String message) {
        super(message);
    }
}
