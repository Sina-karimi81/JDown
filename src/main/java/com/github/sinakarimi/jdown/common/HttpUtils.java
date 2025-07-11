package com.github.sinakarimi.jdown.common;

import java.net.http.HttpResponse;

public class HttpUtils {

    public static <T> boolean isStatusCode4xx(HttpResponse<T> response) {
        return 400 <= response.statusCode() && response.statusCode() < 500;
    }

    public static <T> boolean isStatusCode5xx(HttpResponse<T> response) {
        return 500 <= response.statusCode();
    }

    public static <T> boolean isStatusCode2xx(HttpResponse<T> response) {
        return 200 <= response.statusCode() && response.statusCode() < 300;
    }

}
