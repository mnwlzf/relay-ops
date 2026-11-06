package com.relayops.integration.sub2db;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class Sub2ReadOnlyDataSourcePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void usesSafeDefaultsWhenNoConfigurationIsProvided() {
        contextRunner.run(context -> {
            Sub2ReadOnlyDataSourceProperties properties =
                    context.getBean(Sub2ReadOnlyDataSourceProperties.class);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.hasConnection()).isFalse();
            assertThat(properties.isConfigured()).isFalse();
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getQueryTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getRealtimeQueryTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.getMaximumPoolSize()).isEqualTo(2);
            assertThat(properties.getMinimumIdle()).isEqualTo(1);
            assertThat(properties.getBatchSize()).isEqualTo(2000);
            assertThat(properties.getMaxBatchesPerRun()).isEqualTo(20);
            assertThat(properties.getUnavailableCooldown()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getRealtimeMaxRows()).isEqualTo(500);
        });
    }

    @Test
    void detectsConnectionInformationWithoutEnablingTheSource() {
        contextRunner
                .withPropertyValues(
                        "relay-ops.datasource.sub2-read-only.url=jdbc:postgresql://external:5432/sub2api",
                        "relay-ops.datasource.sub2-read-only.username=relay_readonly"
                )
                .run(context -> {
                    Sub2ReadOnlyDataSourceProperties properties =
                            context.getBean(Sub2ReadOnlyDataSourceProperties.class);

                    assertThat(properties.hasConnection()).isTrue();
                    assertThat(properties.isConfigured()).isFalse();
                });
    }

    @Test
    void reportsConfiguredWhenEnabledAndConnectionInformationExists() {
        contextRunner
                .withPropertyValues(
                        "relay-ops.datasource.sub2-read-only.enabled=true",
                        "relay-ops.datasource.sub2-read-only.url=jdbc:postgresql://external:5432/sub2api",
                        "relay-ops.datasource.sub2-read-only.username=relay_readonly"
                )
                .run(context -> {
                    Sub2ReadOnlyDataSourceProperties properties =
                            context.getBean(Sub2ReadOnlyDataSourceProperties.class);

                    assertThat(properties.hasConnection()).isTrue();
                    assertThat(properties.isConfigured()).isTrue();
                });
    }

    @Test
    void bindsAllExternalReadOnlyConfigurationValues() {
        contextRunner
                .withPropertyValues(
                        "relay-ops.datasource.sub2-read-only.enabled=true",
                        "relay-ops.datasource.sub2-read-only.url=jdbc:postgresql://external:5432/sub2api",
                        "relay-ops.datasource.sub2-read-only.username=relay_readonly",
                        "relay-ops.datasource.sub2-read-only.password=secret",
                        "relay-ops.datasource.sub2-read-only.connect-timeout=7s",
                        "relay-ops.datasource.sub2-read-only.query-timeout=45s",
                        "relay-ops.datasource.sub2-read-only.realtime-query-timeout=4s",
                        "relay-ops.datasource.sub2-read-only.maximum-pool-size=4",
                        "relay-ops.datasource.sub2-read-only.minimum-idle=2",
                        "relay-ops.datasource.sub2-read-only.batch-size=1000",
                        "relay-ops.datasource.sub2-read-only.max-batches-per-run=10",
                        "relay-ops.datasource.sub2-read-only.unavailable-cooldown=60s",
                        "relay-ops.datasource.sub2-read-only.realtime-max-rows=250"
                )
                .run(context -> {
                    Sub2ReadOnlyDataSourceProperties properties =
                            context.getBean(Sub2ReadOnlyDataSourceProperties.class);

                    assertThat(properties.isConfigured()).isTrue();
                    assertThat(properties.getUrl()).isEqualTo("jdbc:postgresql://external:5432/sub2api");
                    assertThat(properties.getUsername()).isEqualTo("relay_readonly");
                    assertThat(properties.getPassword()).isEqualTo("secret");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(properties.getQueryTimeout()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(properties.getRealtimeQueryTimeout()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(properties.getMaximumPoolSize()).isEqualTo(4);
                    assertThat(properties.getMinimumIdle()).isEqualTo(2);
                    assertThat(properties.getBatchSize()).isEqualTo(1000);
                    assertThat(properties.getMaxBatchesPerRun()).isEqualTo(10);
                    assertThat(properties.getUnavailableCooldown()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(properties.getRealtimeMaxRows()).isEqualTo(250);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(Sub2ReadOnlyDataSourceProperties.class)
    static class TestConfiguration {
    }
}
