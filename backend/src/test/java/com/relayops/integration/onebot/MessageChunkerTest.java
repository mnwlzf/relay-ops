package com.relayops.integration.onebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessageChunkerTest {

    @Test
    void shortTextStaysWhole() {
        assertThat(MessageChunker.split("今天号池一切正常", 1000, 5))
                .containsExactly("今天号池一切正常");
    }

    @Test
    void blankTextProducesNoChunk() {
        assertThat(MessageChunker.split("   ", 1000, 5)).isEmpty();
        assertThat(MessageChunker.split(null, 1000, 5)).isEmpty();
    }

    @Test
    void splitsAtNewlineSoListsStayReadable() {
        String text = "第一段内容\n".repeat(40);

        List<String> chunks = MessageChunker.split(text, 100, 10);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(100));
        // every chunk ends on a complete line, never mid-word
        assertThat(chunks.get(0)).endsWith("第一段内容");
    }

    @Test
    void hardCutsWhenThereIsNoBoundary() {
        String text = "啊".repeat(300);

        List<String> chunks = MessageChunker.split(text, 100, 2);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(100);
        assertThat(chunks.get(1)).endsWith(MessageChunker.TRUNCATED_SUFFIX);
    }

    @Test
    void respectsTheSegmentCapAndMarksTruncation() {
        String text = "很长的日报内容。".repeat(200);

        List<String> chunks = MessageChunker.split(text, 200, 3);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(2)).contains("已截断");
    }

    @Test
    void enforcesSaneMinimums() {
        List<String> chunks = MessageChunker.split("短".repeat(120), 10, 0);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).length()).isLessThanOrEqualTo(50 + MessageChunker.TRUNCATED_SUFFIX.length());
    }
}
