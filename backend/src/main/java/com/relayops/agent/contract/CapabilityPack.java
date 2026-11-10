package com.relayops.agent.contract;

import com.relayops.agent.domain.prompt.PromptSection;
import java.util.List;

/**
 * Pluggable bundle of prompt rules and tools.
 *
 * <p>Two implementations are expected: {@code general} (chat, weather, media) and {@code platform}
 * (monitoring queries). They never depend on each other and only meet here, in the kernel.</p>
 *
 * <p>Implementations are plain Spring beans; the kernel collects them and asks per request, because
 * both prompt and tools depend on who is asking.</p>
 */
public interface CapabilityPack {

    /** Stable id, e.g. {@code general} or {@code platform}. */
    String id();

    PackDescriptor describe();

    /** Prompt blocks of this pack, including its protected capability rules. */
    List<PromptSection> prompt(PromptContext context);

    /** Tools visible for this request; callers must still honour {@link ToolSpec#access()}. */
    List<ToolSpec> tools(ToolContext context);

    /** Whether this pack applies to the scope (e.g. platform pack only inside platform groups). */
    boolean supports(ScopeRef scope);
}
