package com.relayops.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security switches.
 *
 * <p>{@code devOpen} exists only because the identity module is not implemented yet: it opens
 * {@code /api/**} and enables CORS so the local admin UI can be tried out. It must stay off outside
 * local development; the application logs a warning when it is on.</p>
 *
 * @param corsOrigins comma separated origins allowed when {@code devOpen} is true
 */
@ConfigurationProperties(prefix = "relay-ops.security")
public record SecurityProperties(Boolean devOpen, String corsOrigins) {

    public SecurityProperties {
        devOpen = devOpen != null && devOpen;
        corsOrigins = corsOrigins == null || corsOrigins.isBlank()
                ? "http://localhost:5173" : corsOrigins.strip();
    }

    public List<String> allowedOrigins() {
        return List.of(corsOrigins.split(",")).stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
