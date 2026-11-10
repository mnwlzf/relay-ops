package com.relayops.assistant.application.memory;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.memory.ConversationMessage;
import com.relayops.assistant.domain.memory.MemorySummary;
import com.relayops.assistant.domain.memory.MessageRole;
import com.relayops.shared.time.BusinessTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * L1/L2 memory: appends messages and maintains the rolling summary.
 *
 * <p>The summary prompt keeps the previous summary, so a long conversation is compressed
 * incrementally instead of being re-read from the start.</p>
 */
@Service
public class ConversationService {

    /** How many messages the summariser reads per run. */
    static final int SUMMARY_WINDOW = 20;

    private final ConversationStore store;
    private final TextSummarizer summarizer;
    private final BusinessTime time;

    public ConversationService(ConversationStore store, TextSummarizer summarizer, BusinessTime time) {
        this.store = store;
        this.summarizer = summarizer;
        this.time = time;
    }

    public long ensure(ScopeRef scope, String packId, Long personaId, Integer personaVersion) {
        return store.ensure(scope, packId, personaId, personaVersion, time.now());
    }

    public long appendUser(long conversationId, String speakerId, String speakerName, String content) {
        return store.append(conversationId, MessageRole.USER, speakerId, speakerName, content, false, time.now());
    }

    public long appendAssistant(long conversationId, String content) {
        return store.append(conversationId, MessageRole.ASSISTANT, null, null, content, false, time.now());
    }

    /** Broadcasts are remembered too, so a follow-up question can refer to them. */
    public long appendBroadcast(long conversationId, String content) {
        return store.append(conversationId, MessageRole.BROADCAST, null, null, content, false, time.now());
    }

    public List<ConversationMessage> recent(long conversationId, int limit) {
        return store.recent(conversationId, limit);
    }

    public Optional<MemorySummary> latestSummary(long conversationId) {
        return store.latestSummary(conversationId);
    }

    /**
     * Compresses the conversation into a new rolling summary when enough new messages accumulated.
     *
     * @return the stored summary, or empty when the window is not reached or no model is available
     */
    public Optional<MemorySummary> summarize(long conversationId, int minMessages) {
        if (!summarizer.available()) {
            return Optional.empty();
        }
        List<ConversationMessage> messages = store.recent(conversationId, SUMMARY_WINDOW);
        if (messages.size() < Math.max(1, minMessages)) {
            return Optional.empty();
        }
        String previous = store.latestSummary(conversationId).map(MemorySummary::summary).orElse("");
        String prompt = buildSummaryPrompt(previous, messages);
        return summarizer.summarize(prompt).map(summary -> {
            long upto = messages.get(messages.size() - 1).id() == null ? 0 : messages.get(messages.size() - 1).id();
            store.saveSummary(conversationId, upto, summary, null, null, time.now());
            return new MemorySummary(null, conversationId, upto, summary, null, null, time.now());
        });
    }

    /** Summarises every conversation that has un-summarised messages. */
    public int summarizePending(int minMessages, int limit) {
        int summarized = 0;
        for (Long conversationId : store.needingSummary(minMessages, limit)) {
            if (summarize(conversationId, minMessages).isPresent()) {
                summarized++;
            }
        }
        return summarized;
    }

    /** Prompt for the rolling summary; kept static so its shape can be asserted in tests. */
    static String buildSummaryPrompt(String previousSummary, List<ConversationMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请把下面的群聊/私聊记录压缩成不超过 300 字的中文摘要。\n")
                .append("保留：人物与称呼、做出的结论与约定、待办事项、情绪基调。\n")
                .append("不要编造记录里没有的信息，也不要保留密钥、邮箱或链接。\n");
        if (previousSummary != null && !previousSummary.isBlank()) {
            prompt.append("\n【已有摘要】\n").append(previousSummary.strip()).append('\n');
        }
        prompt.append("\n【新增对话】\n");
        for (ConversationMessage message : messages) {
            prompt.append(message.asPromptLine()).append('\n');
        }
        prompt.append("\n【输出】只输出摘要正文。");
        return prompt.toString();
    }
}
