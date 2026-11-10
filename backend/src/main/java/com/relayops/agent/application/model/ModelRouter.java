package com.relayops.agent.application.model;

import com.relayops.agent.config.ChatProperties;
import com.relayops.agent.domain.model.ModelProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Picks which model answers.
 *
 * <p>Order is primary then fallback; a slot without endpoint/api key/model is skipped instead of
 * failing, and when nothing is usable the assistant degrades to command-only mode. Callers must treat
 * {@link #resolve()} being empty as a valid state, not an error.</p>
 */
@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ChatProperties properties;

    public ModelRouter(ChatProperties properties) {
        this.properties = properties;
        if (!properties.chatEnabled()) {
            log.info("聊天模型未启用，助手运行在仅命令模式");
        } else if (resolve().isEmpty()) {
            log.warn("聊天模型已启用但没有可用配置（缺少 base-url / api-key / model），仍按仅命令模式运行");
        }
    }

    public boolean chatEnabled() {
        return properties.chatEnabled();
    }

    /** Usable profiles in preference order. */
    public List<ModelProfile> chain() {
        List<ModelProfile> profiles = new ArrayList<>(2);
        if (!properties.chatEnabled()) {
            return List.of();
        }
        if (properties.primary().usable()) {
            profiles.add(ModelProfile.from(ModelProfile.PRIMARY, properties.primary()));
        }
        if (properties.fallback().usable()) {
            profiles.add(ModelProfile.from(ModelProfile.FALLBACK, properties.fallback()));
        }
        return List.copyOf(profiles);
    }

    public Optional<ModelProfile> resolve() {
        return chain().stream().findFirst();
    }

    /** Whether a model call is possible at all. */
    public boolean available() {
        return resolve().isPresent();
    }

    public ModelStatus status() {
        List<ModelProfile> profiles = chain();
        String reason;
        if (!properties.chatEnabled()) {
            reason = "聊天模型已禁用（relay-ops.ai.chat.enabled=false）";
        } else if (profiles.isEmpty()) {
            reason = "未配置可用模型：需要 base-url、api-key 与 model";
        } else {
            reason = "就绪";
        }
        return new ModelStatus(properties.chatEnabled(), !profiles.isEmpty(), reason,
                profiles.stream().map(profile -> new ModelStatus.Slot(
                        profile.id(), profile.model(), profile.baseUrl(), profile.masked())).toList());
    }

    /** Status snapshot for the admin page and health checks. */
    public record ModelStatus(boolean enabled, boolean available, String reason, List<Slot> profiles) {

        /** One configured slot without the secret. */
        public record Slot(String id, String model, String baseUrl, String maskedApiKey) {
        }
    }
}
