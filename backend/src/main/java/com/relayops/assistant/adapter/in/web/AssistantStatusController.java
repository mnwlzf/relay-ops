package com.relayops.assistant.adapter.in.web;

import com.relayops.agent.application.model.ModelRouter;
import com.relayops.shared.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Assistant runtime status: which model slots are configured and whether chat is available. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantStatusController {

    private final ModelRouter modelRouter;

    public AssistantStatusController(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @GetMapping("/models")
    public ApiResponse<ModelRouter.ModelStatus> models() {
        return ApiResponse.of(modelRouter.status());
    }
}
