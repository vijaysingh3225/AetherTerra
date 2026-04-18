package com.aetherterra.common;

public record ApiResponse<T>(T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> message(String message) {
        return new ApiResponse<>(null, message);
    }
}
