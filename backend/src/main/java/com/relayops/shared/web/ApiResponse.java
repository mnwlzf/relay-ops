package com.relayops.shared.web;

/**
 * Stable success envelope used by HTTP APIs.
 *
 * @param data      response payload
 * @param requestId correlation identifier copied from the current request
 * @param <T>       payload type
 */
public record ApiResponse<T>(T data, String requestId) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, RequestContext.currentRequestId());
    }
}
