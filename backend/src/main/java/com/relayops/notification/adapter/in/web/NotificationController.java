package com.relayops.notification.adapter.in.web;

import com.relayops.notification.application.alert.AlertStore;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import com.relayops.shared.web.ApiResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alert history and silence rules. */
@RestController
@RequestMapping("/api/v1/notification")
public class NotificationController {

    private final AlertStore store;
    private final BusinessTime time;

    public NotificationController(AlertStore store, BusinessTime time) {
        this.store = store;
        this.time = time;
    }

    @GetMapping("/alerts")
    public ApiResponse<List<AlertStore.AlertHistoryRow>> alerts(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(store.history(limit));
    }

    @GetMapping("/silences")
    public ApiResponse<List<AlertStore.SilenceRule>> silences() {
        return ApiResponse.of(store.silences());
    }

    @PostMapping("/silences")
    public ApiResponse<AlertStore.SilenceRule> addSilence(@RequestBody SilenceRequest request) {
        Instant now = time.now();
        Instant until = request.untilHours() == null || request.untilHours() <= 0
                ? now.plus(24, ChronoUnit.HOURS)
                : now.plus(request.untilHours(), ChronoUnit.HOURS);
        String scopeType = request.scopeType() == null || request.scopeType().isBlank()
                ? "GLOBAL" : request.scopeType().trim().toUpperCase(Locale.ROOT);
        if (!"GLOBAL".equals(scopeType) && !"ACCOUNT".equals(scopeType)) {
            throw new BusinessException("SILENCE_SCOPE_INVALID", "未知的静默作用域: " + request.scopeType());
        }
        String scopeKey = "GLOBAL".equals(scopeType) ? "*"
                : (request.scopeKey() == null || request.scopeKey().isBlank() ? "*" : request.scopeKey().trim());
        String alertType = request.alertType() == null || request.alertType().isBlank()
                ? null : request.alertType().trim().toUpperCase(Locale.ROOT);
        long id = store.addSilence(scopeType, scopeKey, alertType, until, request.reason());
        return ApiResponse.of(store.silences().stream()
                .filter(rule -> rule.id() == id)
                .findFirst()
                .orElseThrow());
    }

    @DeleteMapping("/silences/{id}")
    public ApiResponse<Void> deleteSilence(@PathVariable long id) {
        store.deleteSilence(id);
        return ApiResponse.of(null);
    }

    /** Silence payload; {@code untilHours} defaults to 24. */
    public record SilenceRequest(String scopeType, String scopeKey, String alertType,
                                 Integer untilHours, String reason) {
    }
}
