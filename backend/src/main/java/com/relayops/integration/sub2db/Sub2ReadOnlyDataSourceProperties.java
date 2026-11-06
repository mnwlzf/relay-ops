package com.relayops.integration.sub2db;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sub2API 外部只读库配置。
 *
 * <p>该类只负责绑定配置，不创建连接池或 JDBC 组件。外部库默认关闭，
 * 未配置时不得影响本地主库、MyBatis-Plus 或 Flyway。</p>
 */
@ConfigurationProperties(prefix = "relay-ops.datasource.sub2-read-only")
public class Sub2ReadOnlyDataSourceProperties {

    /** 是否启用 Sub2API 外部只读库。 */
    private boolean enabled;

    /** PostgreSQL JDBC URL。 */
    private String url;

    /** 只读账号用户名。 */
    private String username;

    /** 只读账号密码。 */
    private String password;

    /** 建立外部库连接的超时时间。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 批量查询的默认超时时间。 */
    private Duration queryTimeout = Duration.ofSeconds(30);

    /** 页面或实时查询使用的短超时时间。 */
    private Duration realtimeQueryTimeout = Duration.ofSeconds(3);

    /** 外部只读连接池最大连接数。 */
    private int maximumPoolSize = 2;

    /** 外部只读连接池最小空闲连接数。 */
    private int minimumIdle = 1;

    /** 游标增量同步的单批条数。 */
    private int batchSize = 2000;

    /** 单轮同步最多处理的批数。 */
    private int maxBatchesPerRun = 20;

    /** 外部库查询失败后的冷却时间。 */
    private Duration unavailableCooldown = Duration.ofSeconds(30);

    /** 实时查询允许返回的最大行数。 */
    private int realtimeMaxRows = 500;

    /**
     * 是否具备基本连接信息，不检查启用开关，也不连接数据库。
     */
    public boolean hasConnection() {
        return hasText(url) && hasText(username);
    }

    /**
     * 外部库是否已启用且连接信息完整。
     */
    public boolean isConfigured() {
        return enabled && hasConnection();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public Duration getRealtimeQueryTimeout() {
        return realtimeQueryTimeout;
    }

    public void setRealtimeQueryTimeout(Duration realtimeQueryTimeout) {
        this.realtimeQueryTimeout = realtimeQueryTimeout;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxBatchesPerRun() {
        return maxBatchesPerRun;
    }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) {
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    public Duration getUnavailableCooldown() {
        return unavailableCooldown;
    }

    public void setUnavailableCooldown(Duration unavailableCooldown) {
        this.unavailableCooldown = unavailableCooldown;
    }

    public int getRealtimeMaxRows() {
        return realtimeMaxRows;
    }

    public void setRealtimeMaxRows(int realtimeMaxRows) {
        this.realtimeMaxRows = realtimeMaxRows;
    }
}
