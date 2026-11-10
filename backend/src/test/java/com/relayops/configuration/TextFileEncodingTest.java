package com.relayops.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Repository text files must be UTF-8.
 *
 * <p>Two failure modes are covered:</p>
 * <ol>
 *   <li>bytes that are not valid UTF-8 at all (file saved as GBK/ANSI);</li>
 *   <li>valid UTF-8 that is already garbled — the classic "UTF-8 bytes decoded as GBK, then saved
 *       again" result. Such files decode cleanly, so they need a marker check on top.</li>
 * </ol>
 *
 * <p>The marker set only contains character pairs that do not occur in normal Chinese prose, which
 * keeps false positives out while still catching the double-encoding pattern.</p>
 */
class TextFileEncodingTest {

    private static final List<String> GARBAGE_MARKERS = List.of(
            "锛", "鐨", "鎵", "銆", "鏂", "鍐", "閿", "鍚", "绱", "鍒");

    @Test
    void repositoryTextFilesAreCleanUtf8() throws IOException {
        Path root = repositoryRoot();
        List<Path> files = new ArrayList<>();
        files.add(root.resolve("README.md"));
        files.add(root.resolve(".env.example"));
        files.add(root.resolve("compose.yaml"));
        collect(files, root.resolve("docs"), ".md");
        collect(files, root.resolve("backend/src/main/resources"), ".yml", ".sql");
        collect(files, root.resolve("backend/src/main/java"), ".java");

        assertThat(files).as("应当扫描到文本文件").isNotEmpty();

        for (Path file : files) {
            String content = decodeStrict(file);
            assertThat(content)
                    .as("文件含替换字符（疑似编码损坏）: %s", file)
                    .doesNotContain("\uFFFD");
            assertThat(GARBAGE_MARKERS.stream().filter(content::contains).toList())
                    .as("文件疑似双重编码产生的乱码: %s", file)
                    .isEmpty();
        }
    }

    private static String decodeStrict(Path file) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file)))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new AssertionError("文件不是合法 UTF-8: " + file, ex);
        }
    }

    private static void collect(List<Path> target, Path directory, String... extensions) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> hasExtension(path, extensions))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(target::add);
        }
    }

    private static boolean hasExtension(Path path, String... extensions) {
        String name = path.getFileName().toString();
        for (String extension : extensions) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** Walks up from the working directory until the repository marker is found. */
    private static Path repositoryRoot() {
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
