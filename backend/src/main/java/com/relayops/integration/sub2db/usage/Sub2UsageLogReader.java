package com.relayops.integration.sub2db.usage;

import com.relayops.integration.sub2db.Sub2ReadOnlyJdbc;
import com.relayops.integration.sub2db.Sub2ReadOnlyUnavailableException;
import org.springframework.stereotype.Component;

/**
 * Sub2API usage_logs 只读查询。
 *
 * <p>当前仅提供同步初始化所需的最大 ID 查询；增量拉取和本地写入在后续阶段实现。</p>
 */
@Component
public class Sub2UsageLogReader {

    static final String CURRENT_MAX_ID_SQL =
            "SELECT COALESCE(MAX(id), 0) FROM usage_logs";

    private final Sub2ReadOnlyJdbc jdbc;

    public Sub2UsageLogReader(Sub2ReadOnlyJdbc jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 外部只读库是否已配置且当前未处于冷却窗口。
     */
    public boolean isAvailable() {
        return jdbc.isConfigured() && !jdbc.isInCooldown();
    }

    /**
     * 读取 usage_logs 当前最大 ID，用于首次启用同步时初始化游标。
     */
    public long currentMaxId() {
        try {
            Long maxId = jdbc.template().queryForObject(CURRENT_MAX_ID_SQL, Long.class);
            jdbc.markAvailable();
            return maxId == null ? 0L : maxId;
        } catch (Sub2ReadOnlyUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            jdbc.markUnavailable(null);
            throw new Sub2ReadOnlyUnavailableException(
                    "Failed to query current max id from Sub2API usage_logs", ex);
        }
    }
}