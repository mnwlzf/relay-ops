package com.relayops.integration.sub2db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sub2API 外部只读库的共享访问网关。
 *
 * <p>该组件刻意不注册 {@code DataSource} Bean，避免被 MyBatis-Plus、Flyway
 * 或默认事务管理器误用。连接池在第一次查询时创建，外部库未启用或不可达时
 * 不影响应用启动。</p>
 */
@Component
public class Sub2ReadOnlyJdbc {

    private static final Logger log = LoggerFactory.getLogger(Sub2ReadOnlyJdbc.class);

    private static final String POOL_NAME = "sub2api-read-only";
    private static final long HIKARI_MIN_TIMEOUT_MILLIS = 250L;
    private static final long DEFAULT_UNAVAILABLE_COOLDOWN_MILLIS = 30_000L;

    private final Sub2ReadOnlyDataSourceProperties properties;
    private final AtomicLong unavailableUntilNanos = new AtomicLong();

    private volatile HikariDataSource dataSource;

    public Sub2ReadOnlyJdbc(Sub2ReadOnlyDataSourceProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean hasConnection() {
        return properties.hasConnection();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 是否已经创建过外部只读连接池。
     */
    public boolean isPoolInitialized() {
        return dataSource != null;
    }

    /**
     * 外部库是否处于失败后的快速失败窗口。
     */
    public boolean isInCooldown() {
        long until = unavailableUntilNanos.get();
        return until != 0L && System.nanoTime() < until;
    }

    /**
     * 标记外部库暂时不可用。传入空值或非正数时使用配置的冷却时间。
     */
    public void markUnavailable(Duration cooldown) {
        Duration effectiveCooldown = cooldown;
        if (effectiveCooldown == null || effectiveCooldown.isZero() || effectiveCooldown.isNegative()) {
            effectiveCooldown = properties.getUnavailableCooldown();
        }
        if (effectiveCooldown == null || effectiveCooldown.isZero() || effectiveCooldown.isNegative()) {
            effectiveCooldown = Duration.ofMillis(DEFAULT_UNAVAILABLE_COOLDOWN_MILLIS);
        }
        long until = System.nanoTime() + effectiveCooldown.toNanos();
        unavailableUntilNanos.updateAndGet(current -> Math.max(current, until));
    }

    /**
     * 外部库恢复后清除冷却状态。
     */
    public void markAvailable() {
        unavailableUntilNanos.set(0L);
    }

    /**
     * 默认查询入口，使用批量查询超时。
     */
    public JdbcTemplate template() {
        return createTemplate(properties.getQueryTimeout(), 0, 0);
    }

    /**
     * 批量查询入口，使用配置的单批大小作为 JDBC fetch size。
     */
    public JdbcTemplate batchTemplate() {
        return createTemplate(properties.getQueryTimeout(), Math.max(1, properties.getBatchSize()), 0);
    }

    /**
     * 实时查询入口，使用短超时和最大返回行数。
     */
    public JdbcTemplate realtimeTemplate() {
        return createTemplate(
                properties.getRealtimeQueryTimeout(),
                0,
                Math.max(1, properties.getRealtimeMaxRows())
        );
    }

    private JdbcTemplate createTemplate(Duration timeout, int fetchSize, int maxRows) {
        ensureUsable();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        jdbcTemplate.setQueryTimeout(toQueryTimeoutSeconds(timeout));
        if (fetchSize > 0) {
            jdbcTemplate.setFetchSize(fetchSize);
        }
        if (maxRows > 0) {
            jdbcTemplate.setMaxRows(maxRows);
        }
        return jdbcTemplate;
    }

    private void ensureUsable() {
        if (!properties.isEnabled()) {
            throw new Sub2ReadOnlyUnavailableException("Sub2API read-only data source is disabled");
        }
        if (!properties.hasConnection()) {
            throw new Sub2ReadOnlyUnavailableException("Sub2API read-only data source connection is not configured");
        }
        if (isInCooldown()) {
            throw new Sub2ReadOnlyUnavailableException("Sub2API read-only data source is cooling down");
        }
    }

    private HikariDataSource dataSource() {
        HikariDataSource current = dataSource;
        if (current == null) {
            synchronized (this) {
                current = dataSource;
                if (current == null) {
                    try {
                        current = createDataSource();
                        dataSource = current;
                    } catch (RuntimeException ex) {
                        markUnavailable(null);
                        throw new Sub2ReadOnlyUnavailableException(
                                "Sub2API read-only data source initialization failed", ex);
                    }
                }
            }
        }
        return current;
    }

    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setPoolName(POOL_NAME);
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());

        int maximumPoolSize = Math.max(1, properties.getMaximumPoolSize());
        int minimumIdle = Math.max(0, Math.min(properties.getMinimumIdle(), maximumPoolSize));
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setConnectionTimeout(toHikariTimeoutMillis(properties.getConnectTimeout()));
        config.setValidationTimeout(toHikariTimeoutMillis(properties.getConnectTimeout()));
        config.setInitializationFailTimeout(-1L);

        HikariDataSource created = new HikariDataSource(config);
        log.info("Sub2API read-only data source initialized: maximumPoolSize={}", maximumPoolSize);
        return created;
    }

    private int toQueryTimeoutSeconds(Duration timeout) {
        Duration effective = timeout;
        if (effective == null || effective.isZero() || effective.isNegative()) {
            effective = Duration.ofSeconds(1);
        }
        return Math.max(1, (int) Math.ceil(effective.toMillis() / 1000.0));
    }

    private long toHikariTimeoutMillis(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return HIKARI_MIN_TIMEOUT_MILLIS;
        }
        return Math.max(HIKARI_MIN_TIMEOUT_MILLIS, timeout.toMillis());
    }

    @PreDestroy
    public synchronized void close() {
        HikariDataSource current = dataSource;
        dataSource = null;
        unavailableUntilNanos.set(0L);
        if (current != null) {
            current.close();
            log.info("Sub2API read-only data source closed");
        }
    }
}