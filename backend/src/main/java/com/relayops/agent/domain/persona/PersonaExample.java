package com.relayops.agent.domain.persona;

/** One few-shot exchange used to anchor tone. */
public record PersonaExample(String user, String assistant) {

    public boolean isComplete() {
        return user != null && !user.isBlank() && assistant != null && !assistant.isBlank();
    }

    public String describe() {
        return "用户：" + user.strip() + "\n你：" + assistant.strip();
    }
}
