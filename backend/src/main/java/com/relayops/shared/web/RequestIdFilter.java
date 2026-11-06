package com.relayops.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final int MAX_REQUEST_ID_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalize(request.getHeader(RequestContext.REQUEST_ID_HEADER));
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        }
    }

    private String normalize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_REQUEST_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }
        return candidate.strip();
    }
}
