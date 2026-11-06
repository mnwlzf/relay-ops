package com.relayops.integration.sub2db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class Sub2ReadOnlyJdbcTest {

    @Test
    void rejectsQueriesWhenDisabledWithoutCreatingAConnectionPool() {
        Sub2ReadOnlyDataSourceProperties properties = new Sub2ReadOnlyDataSourceProperties();
        Sub2ReadOnlyJdbc jdbc = new Sub2ReadOnlyJdbc(properties);

        assertThat(jdbc.isEnabled()).isFalse();
        assertThat(jdbc.hasConnection()).isFalse();
        assertThat(jdbc.isConfigured()).isFalse();
        assertThat(jdbc.isPoolInitialized()).isFalse();
        assertThatThrownBy(jdbc::template)
                .isInstanceOf(Sub2ReadOnlyUnavailableException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsQueriesWhenEnabledButConnectionInformationIsIncomplete() {
        Sub2ReadOnlyDataSourceProperties properties = new Sub2ReadOnlyDataSourceProperties();
        properties.setEnabled(true);

        Sub2ReadOnlyJdbc jdbc = new Sub2ReadOnlyJdbc(properties);

        assertThat(jdbc.hasConnection()).isFalse();
        assertThat(jdbc.isConfigured()).isFalse();
        assertThatThrownBy(jdbc::batchTemplate)
                .isInstanceOf(Sub2ReadOnlyUnavailableException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void entersAndClearsTheFailureCooldown() {
        Sub2ReadOnlyDataSourceProperties properties = configuredProperties();
        Sub2ReadOnlyJdbc jdbc = new Sub2ReadOnlyJdbc(properties);

        assertThat(jdbc.isInCooldown()).isFalse();
        jdbc.markUnavailable(Duration.ofSeconds(60));
        assertThat(jdbc.isInCooldown()).isTrue();
        assertThatThrownBy(jdbc::realtimeTemplate)
                .isInstanceOf(Sub2ReadOnlyUnavailableException.class)
                .hasMessageContaining("cooling down");

        jdbc.markAvailable();
        assertThat(jdbc.isInCooldown()).isFalse();
    }

    @Test
    void usesConfiguredCooldownWhenNoDurationIsProvided() {
        Sub2ReadOnlyDataSourceProperties properties = configuredProperties();
        properties.setUnavailableCooldown(Duration.ofSeconds(45));
        Sub2ReadOnlyJdbc jdbc = new Sub2ReadOnlyJdbc(properties);

        jdbc.markUnavailable(null);

        assertThat(jdbc.isInCooldown()).isTrue();
    }

    @Test
    void closeIsSafeWhenNoPoolWasInitialized() {
        Sub2ReadOnlyJdbc jdbc = new Sub2ReadOnlyJdbc(configuredProperties());

        jdbc.close();
        jdbc.close();

        assertThat(jdbc.isPoolInitialized()).isFalse();
        assertThat(jdbc.isInCooldown()).isFalse();
    }

    private Sub2ReadOnlyDataSourceProperties configuredProperties() {
        Sub2ReadOnlyDataSourceProperties properties = new Sub2ReadOnlyDataSourceProperties();
        properties.setEnabled(true);
        properties.setUrl("jdbc:postgresql://127.0.0.1:1/sub2api");
        properties.setUsername("relay_readonly");
        properties.setPassword("secret");
        return properties;
    }
}