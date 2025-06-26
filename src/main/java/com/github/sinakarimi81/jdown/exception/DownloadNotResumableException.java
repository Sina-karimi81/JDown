package com.github.sinakarimi81.jdown.exception;

public class DownloadNotResumableException extends RuntimeException {
    public DownloadNotResumableException(String message) {
        super(message);
    }
}
