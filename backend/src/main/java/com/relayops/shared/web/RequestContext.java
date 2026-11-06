package com.relayops.shared.web;

import java.util.UUID;
import org.slf4j.MDC;

public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private RequestContext() {
    }

    public static String currentRequestId() {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
