package com.relayops.scheduling.application.execution;

import com.relayops.scheduling.contract.ScheduledTaskHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Discovers every {@link ScheduledTaskHandler} in the application context. */
@Component
public class ScheduledTaskHandlerRegistry {

    private final Map<String, ScheduledTaskHandler> handlers = new LinkedHashMap<>();

    public ScheduledTaskHandlerRegistry(List<ScheduledTaskHandler> discovered) {
        for (ScheduledTaskHandler handler : discovered) {
            ScheduledTaskHandler previous = handlers.put(handler.taskType(), handler);
            if (previous != null) {
                throw new IllegalStateException("重复的任务类型: " + handler.taskType()
                        + " (" + previous.getClass().getName() + " / " + handler.getClass().getName() + ")");
            }
        }
    }

    public Optional<ScheduledTaskHandler> find(String taskType) {
        return Optional.ofNullable(handlers.get(taskType));
    }

    public boolean contains(String taskType) {
        return handlers.containsKey(taskType);
    }

    public List<ScheduledTaskHandler> all() {
        return List.copyOf(handlers.values());
    }
}
