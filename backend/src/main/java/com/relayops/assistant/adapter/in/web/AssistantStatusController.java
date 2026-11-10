package com.relayops.assistant.adapter.in.web;

import com.relayops.agent.application.model.ModelRouter;
import com.relayops.assistant.adapter.out.onebot.LoggingMessageSender;
import com.relayops.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Assistant runtime status: model slots and the last broadcasted message. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantStatusController {

    private final ModelRouter modelRouter;
    private final LoggingMessageSender messageSender;

    public AssistantStatusController(ModelRouter modelRouter, LoggingMessageSender messageSender) {
        this.modelRouter = modelRouter;
        this.messageSender = messageSender;
    }

    @GetMapping("/models")
    public ApiResponse<ModelRouter.ModelStatus> models() {
        return ApiResponse.of(modelRouter.status());
    }

    /** Latest message that went through the sender; lets the admin page verify rendering. */
    @GetMapping("/messages/last")
    public ApiResponse<LoggingMessageSender.Delivered> lastMessage() {
        return ApiResponse.of(messageSender.last().orElse(null));
    }
}
