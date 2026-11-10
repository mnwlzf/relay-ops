package com.relayops.agent.domain.persona;

/**
 * Structured expression style. Every field is optional; {@link #describe()} turns the set values into
 * one prompt line so the model does not have to infer style from prose.
 */
public record PersonaStyle(Integer maxSentences, Integer emojiPerTurn, Boolean asksQuestions) {

    public static PersonaStyle empty() {
        return new PersonaStyle(null, null, null);
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        if (maxSentences != null && maxSentences > 0) {
            builder.append("每轮最多 ").append(maxSentences).append(" 句");
        }
        if (emojiPerTurn != null) {
            append(builder, emojiPerTurn <= 0 ? "不要使用表情" : "每轮最多 " + emojiPerTurn + " 个表情");
        }
        if (Boolean.FALSE.equals(asksQuestions)) {
            append(builder, "不要每轮都用问句结尾");
        } else if (Boolean.TRUE.equals(asksQuestions)) {
            append(builder, "需要澄清时最多追问一次");
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        if (!builder.isEmpty()) {
            builder.append("；");
        }
        builder.append(part);
    }
}
