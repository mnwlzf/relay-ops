package com.relayops.agent.application.tool;

import com.relayops.agent.contract.CapabilityPack;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ToolContext;
import com.relayops.agent.contract.ToolSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only place tools are looked up.
 *
 * <p>Filters by scope, then by the tool's own {@code access} predicate, and finally exposes which
 * tools a scheduled message may reference. Duplicate ids across packs keep the first occurrence and
 * log a warning instead of failing the request.</p>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final List<CapabilityPack> packs;

    public ToolRegistry(List<CapabilityPack> packs) {
        this.packs = List.copyOf(packs);
    }

    public List<CapabilityPack> packs() {
        return packs;
    }

    /** Tools available for this request, deduplicated by id and already permission-filtered. */
    public List<ToolSpec> available(ScopeRef scope, ToolContext context) {
        if (context == null) {
            return List.of();
        }
        Map<String, ToolSpec> byId = new LinkedHashMap<>();
        for (CapabilityPack pack : packsFor(scope)) {
            for (ToolSpec tool : safeTools(pack, context)) {
                ToolSpec existing = byId.putIfAbsent(tool.id(), tool);
                if (existing != null) {
                    log.warn("工具 id 重复，保留先注册的实现: id={}, pack={}", tool.id(), pack.id());
                    continue;
                }
                if (!tool.access().allows(context)) {
                    byId.remove(tool.id());
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    public Optional<ToolSpec> find(String toolId, ScopeRef scope, ToolContext context) {
        if (toolId == null || toolId.isBlank()) {
            return Optional.empty();
        }
        return available(scope, context).stream()
                .filter(tool -> tool.id().equals(toolId))
                .findFirst();
    }

    /** Tools a scheduled message may call: read-only, explicitly allowed, permission-filtered. */
    public List<ToolSpec> schedulable(ScopeRef scope, ToolContext context) {
        return available(scope, context).stream()
                .filter(ToolSpec::allowsScheduling)
                .toList();
    }

    private List<CapabilityPack> packsFor(ScopeRef scope) {
        ScopeRef resolved = scope == null ? ScopeRef.global() : scope;
        return packs.stream().filter(pack -> pack.supports(resolved)).toList();
    }

    private List<ToolSpec> safeTools(CapabilityPack pack, ToolContext context) {
        try {
            List<ToolSpec> tools = pack.tools(context);
            return tools == null ? List.of() : tools;
        } catch (RuntimeException ex) {
            log.warn("能力包贡献工具失败，已跳过: pack={}", pack.id(), ex);
            return List.of();
        }
    }
}
