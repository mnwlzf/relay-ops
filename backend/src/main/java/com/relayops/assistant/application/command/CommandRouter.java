package com.relayops.assistant.application.command;

import com.relayops.agent.application.model.ModelRouter;
import com.relayops.assistant.application.chat.ChatService;
import com.relayops.assistant.application.memory.MemoryService;
import com.relayops.assistant.application.message.BlueprintRenderer;
import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The deterministic half of the assistant.
 *
 * <p>Supported: {@code /help}, {@code /status}, {@code /remember <text>}, {@code /report <code>}.
 * Anything else that starts with {@code /} is reported as unknown so operators are not left guessing
 * while a model is unavailable.</p>
 */
@Component
public class CommandRouter implements CommandDispatcher {

    private static final String PREFIX = "/";

    private final MemoryService memory;
    private final BlueprintRenderer blueprints;
    private final ModelRouter models;
    private final BusinessTime time;

    public CommandRouter(MemoryService memory, BlueprintRenderer blueprints, ModelRouter models, BusinessTime time) {
        this.memory = memory;
        this.blueprints = blueprints;
        this.models = models;
        this.time = time;
    }

    @Override
    public Optional<String> dispatch(ChatService.ChatRequest request) {
        String text = request.text().strip();
        if (!text.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = text.substring(PREFIX.length()).split("\\s+", 2);
        String name = parts[0].toLowerCase(java.util.Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].strip() : "";
        return switch (name) {
            case "help", "帮助" -> Optional.of(help(request.admin()));
            case "status", "状态" -> Optional.of(status());
            case "remember", "记住" -> Optional.of(remember(request, rest));
            case "report", "播报" -> Optional.of(report(request, rest));
            default -> Optional.of("未知命令：/" + name + "\n\n" + help(request.admin()));
        };
    }

    private String help(boolean admin) {
        StringBuilder help = new StringBuilder("可用命令：\n")
                .append("/help — 显示本帮助\n")
                .append("/status — 模型与记忆状态\n")
                .append("/remember <内容> — 记入长期记忆（作用域内共享）\n")
                .append("/report <蓝图 code> — 用蓝图渲染一条消息（不投递）");
        if (admin) {
            help.append("\n\n管理员：/report 需要蓝图已启用；投递请在「定时任务」里立即执行。");
        }
        return help.toString();
    }

    private String status() {
        ModelRouter.ModelStatus status = models.status();
        String modelLine = status.available()
                ? "模型：就绪（" + status.profiles().get(0).id() + " / " + status.profiles().get(0).model() + "）"
                : "模型：" + status.reason();
        return modelLine + "\n记忆：按作用域隔离，平台事实只对 platform 包可见";
    }

    private String remember(ChatService.ChatRequest request, String content) {
        if (content.isBlank()) {
            return "用法：/remember 你希望我记住的内容";
        }
        try {
            MemoryFact saved = memory.remember(new MemoryFact(
                    null, MemoryNamespace.GENERAL, request.scope(), "convention", null, content,
                    0.9, FactSource.EXPLICIT, Map.of("source", "command"), null, null, time.now()));
            return "记下了（#" + saved.id() + "）：" + saved.value();
        } catch (BusinessException ex) {
            return "没法记住：" + ex.getMessage();
        }
    }

    private String report(ChatService.ChatRequest request, String code) {
        if (code.isBlank()) {
            return "用法：/report <蓝图 code>，例如 /report demo.daily-note";
        }
        try {
            String rendered = blueprints.render(code, request.scope(), null);
            return rendered.isBlank() ? "蓝图 " + code + " 渲染结果为空" : rendered;
        } catch (BusinessException ex) {
            return "渲染失败：" + ex.getMessage();
        }
    }
}
