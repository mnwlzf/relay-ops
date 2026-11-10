package com.relayops.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the configuration contract: every placeholder used by the application or by
 * {@code compose.yaml} must be documented in {@code .env.example}.
 *
 * <p>Without this, a new {@code ${VAR}} silently becomes an undocumented operational knob that only
 * the original author knows about.</p>
 */
class ConfigurationReferenceTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)(?::[^}]*)?}");
    private static final Pattern COMPOSE_VARIABLE =
            Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]*)(?::-[^}]*)?}");

    @Test
    void everyApplicationPlaceholderIsDocumented() throws IOException {
        Path root = repositoryRoot();
        String applicationYml = Files.readString(
                root.resolve("backend/src/main/resources/application.yml"), StandardCharsets.UTF_8);
        Set<String> placeholders = placeholders(applicationYml, PLACEHOLDER);

        assertThat(placeholders).isNotEmpty();
        Set<String> documented = documentedVariables(root.resolve(".env.example"));
        assertThat(placeholders)
                .as("application.yml 中使用的环境变量必须在 .env.example 中声明")
                .allMatch(documented::contains);
    }

    @Test
    void everyComposeVariableIsDocumented() throws IOException {
        Path root = repositoryRoot();
        String compose = Files.readString(root.resolve("compose.yaml"), StandardCharsets.UTF_8);
        Set<String> placeholders = placeholders(compose, COMPOSE_VARIABLE);

        assertThat(placeholders).isNotEmpty();
        Set<String> documented = documentedVariables(root.resolve(".env.example"));
        assertThat(placeholders)
                .as("compose.yaml 中使用的环境变量必须在 .env.example 中声明")
                .allMatch(documented::contains);
    }

    @Test
    void envExampleHasNoDuplicateKeys() throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        for (String line : Files.readAllLines(repositoryRoot().resolve(".env.example"))) {
            if (!isKeyLine(line)) {
                continue;
            }
            String key = line.substring(0, line.indexOf('='));
            assertThat(seen.add(key))
                    .as("重复的配置项: %s", key)
                    .isTrue();
        }
        assertThat(seen).isNotEmpty();
    }

    private Set<String> placeholders(String content, Pattern pattern) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private Set<String> documentedVariables(Path envExample) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(envExample, StandardCharsets.UTF_8)) {
            if (isKeyLine(line)) {
                keys.add(line.substring(0, line.indexOf('=')));
            }
        }
        return keys;
    }

    private boolean isKeyLine(String rawLine) {
        String line = rawLine.trim();
        return !line.isEmpty() && !line.startsWith("#") && line.indexOf('=') > 0 && !line.startsWith(" ");
    }

    /** Walks up from the working directory until the repository marker is found. */
    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (Files.exists(current.resolve(".env.example"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到包含 .env.example 的仓库根目录");
    }
}
