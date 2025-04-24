package com.github.sinakarimi81.jdown.common;

import lombok.Getter;

@Getter
public enum HttpConstants {

    ACCEPT_RANGES_HEADER("Accept-Ranges"),
    RANGE("Range"),
    CONTENT_LENGTH_HEADER("Content-Length"),
    CONTENT_TYPE_HEADER("Content-Type"),
    CONTENT_DISPOSITION_HEADER("Content-Disposition"),

    HEAD_METHOD("HEAD"),
    GET_METHOD("GET"),

    FILENAME_TAG("filename");

    private final String value;

    HttpConstants(String headerVal) {
        this.value = headerVal;
    }

}
