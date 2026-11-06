package com.relayops.integration.sub2db.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.integration.sub2db.Sub2ReadOnlyDataSourceProperties;
import com.relayops.integration.sub2db.Sub2ReadOnlyJdbc;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 使用真实 Sub2API 只读库验证 SQL、权限和连接配置。
 *
 * <p>仅在设置 SUB2API_DB_ENABLED=true 且提供连接信息时运行。</p>
 */
@Tag("contract")
@EnabledIfEnvironmentVariable(named = "SUB2API_DB_ENABLED", matches = "(?i)true")
class Sub2UsageLogReaderContractTest {

    @Test
    void readsCurrentMaxIdFromRealDatabase() {
        Map<String, String> environment = System.getenv();
        Sub2ReadOnlyDataSourceProperties properties = new Sub2ReadOnlyDataSourceProperties();
        properties.setEnabled(true);
        properties.setUrl(environment.get("SUB2API_DB_URL"));
        properties.setUsername(environment.get("SUB2API_DB_USERNAME"));
        properties.setPassword(environment.get("SUB2API_DB_PASSWORD"));

        assertThat(properties.isConfigured()).isTrue();

        Sub2ReadOnlyJdbc jdbc = new Sub2ReadOnlyJdbc(properties);
        try {
            Sub2UsageLogReader reader = new Sub2UsageLogReader(jdbc);
            assertThat(reader.isAvailable()).isTrue();
            assertThat(reader.currentMaxId()).isGreaterThanOrEqualTo(0L);
        } finally {
            jdbc.close();
        }
    }
}