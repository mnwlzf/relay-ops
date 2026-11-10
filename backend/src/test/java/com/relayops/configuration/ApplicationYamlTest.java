package com.relayops.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards the shape of {@code application.yml}.
 *
 * <p>Unit tests never boot Spring, so a broken YAML file (for example two keys accidentally joined
 * on one line) would only surface when someone starts the application. This test parses the file and
 * checks the keys that must stay split.</p>
 */
class ApplicationYamlTest {

    private static final Path APPLICATION_YML = Path.of("src/main/resources/application.yml");

    @Test
    void applicationYamlParsesAndKeepsCriticalKeys() throws IOException {
        String content = Files.readString(APPLICATION_YML, StandardCharsets.UTF_8);

        Map<String, Object> root = new Yaml().load(content);
        assertThat(root).containsKeys("server", "spring", "relay-ops");

        Map<String, Object> relayOps = map(root, "relay-ops");
        Map<String, Object> security = map(relayOps, "security");
        assertThat(security).containsKeys("dev-open", "cors-origins");
        assertThat(String.valueOf(security.get("dev-open")))
                .as("仓库默认必须是关闭的：本地联调请在 .env 里打开")
                .contains("RELAY_OPS_DEV_OPEN_API:false");

        Map<String, Object> scheduling = map(relayOps, "scheduling");
        assertThat(scheduling)
                .as("scheduling.enabled 与 scheduling.zone 必须是两个独立的键")
                .containsKeys("enabled", "zone", "default-calendar", "preview-count");
        assertThat(String.valueOf(scheduling.get("enabled"))).contains("RELAY_OPS_SCHEDULING_ENABLED");

        assertThat(map(relayOps, "upstream")).containsKey("credential-key");
        assertThat(map(map(relayOps, "ai"), "chat")).containsKeys("enabled", "primary", "fallback");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        assertThat(value).as("缺少配置段: %s", key).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
