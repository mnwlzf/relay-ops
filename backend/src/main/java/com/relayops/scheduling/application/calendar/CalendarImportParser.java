package com.relayops.scheduling.application.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.scheduling.domain.day.DayType;
import com.relayops.shared.error.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses calendar data from the formats operators actually have at hand.
 *
 * <ul>
 *   <li>{@code JSON} — an array of {@code {date,type,name}} or the {@code holiday-cn} shape
 *       ({@code {days:[{date,name,isOffDay}]}}); {@code isOffDay=false} means a make-up workday.</li>
 *   <li>{@code ICS} — {@code VEVENT}s with {@code DTSTART;VALUE=DATE} and {@code SUMMARY}.</li>
 *   <li>{@code TEXT} — one line per date: {@code 2026-10-01 HOLIDAY 国庆}.</li>
 * </ul>
 */
public final class CalendarImportParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private CalendarImportParser() {
    }

    public enum Format {
        AUTO, JSON, ICS, TEXT
    }

    /** One imported date. */
    public record ImportedDay(LocalDate day, DayType dayType, String name) {
    }

    public static List<ImportedDay> parse(String content, Format format) {
        if (content == null || content.isBlank()) {
            throw invalid("导入内容为空");
        }
        Format resolved = format == null || format == Format.AUTO ? detect(content) : format;
        List<ImportedDay> parsed = switch (resolved) {
            case JSON -> parseJson(content);
            case ICS -> parseIcs(content);
            case TEXT -> parseText(content);
            case AUTO -> throw invalid("无法识别导入格式");
        };
        if (parsed.isEmpty()) {
            throw invalid("没有从导入内容中解析出任何日期");
        }
        Map<LocalDate, ImportedDay> deduped = new LinkedHashMap<>();
        for (ImportedDay day : parsed) {
            deduped.put(day.day(), day);
        }
        List<ImportedDay> ordered = new ArrayList<>(deduped.values());
        ordered.sort((left, right) -> left.day().compareTo(right.day()));
        return List.copyOf(ordered);
    }

    private static Format detect(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return Format.JSON;
        }
        if (trimmed.toUpperCase(Locale.ROOT).contains("BEGIN:VCALENDAR")) {
            return Format.ICS;
        }
        return Format.TEXT;
    }

    private static List<ImportedDay> parseJson(String content) {
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (Exception ex) {
            throw invalid("JSON 解析失败: " + ex.getMessage());
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else if (root.path("days").isArray()) {
            root.get("days").forEach(nodes::add);
        } else if (root.isObject()) {
            nodes.add(root);
        }
        List<ImportedDay> days = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            LocalDate day = parseDate(firstText(node, "date", "day", "dateString", "dtstart"));
            DayType type = resolveType(node);
            if (day == null || type == null) {
                continue;
            }
            days.add(new ImportedDay(day, type, firstText(node, "name", "summary", "title")));
        }
        return days;
    }

    private static List<ImportedDay> parseIcs(String content) {
        List<String> lines = unfold(content);
        List<ImportedDay> days = new ArrayList<>();
        LocalDate current = null;
        String summary = null;
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("BEGIN:VEVENT")) {
                current = null;
                summary = null;
            } else if (upper.startsWith("DTSTART")) {
                current = parseIcsDate(line);
            } else if (upper.startsWith("SUMMARY")) {
                summary = afterColon(line);
            } else if (upper.startsWith("END:VEVENT")) {
                if (current != null) {
                    days.add(new ImportedDay(current, typeFromSummary(summary), summary));
                }
                current = null;
            }
        }
        return days;
    }

    private static List<ImportedDay> parseText(String content) {
        List<ImportedDay> days = new ArrayList<>();
        for (String raw : content.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 3);
            LocalDate day = parseDate(parts[0]);
            if (day == null) {
                throw invalid("无法解析日期: " + line);
            }
            DayType type = DayType.HOLIDAY;
            String name = null;
            if (parts.length > 1) {
                DayType parsed = typeFromToken(parts[1]);
                if (parsed != null) {
                    type = parsed;
                    name = parts.length > 2 ? parts[2] : null;
                } else {
                    name = parts[1] + (parts.length > 2 ? " " + parts[2] : "");
                }
            }
            days.add(new ImportedDay(day, type, name));
        }
        return days;
    }

    private static DayType resolveType(JsonNode node) {
        String raw = firstText(node, "type", "dayType");
        if (raw != null) {
            DayType parsed = typeFromToken(raw);
            if (parsed != null) {
                return parsed;
            }
        }
        if (node.has("isOffDay")) {
            return node.get("isOffDay").asBoolean(true) ? DayType.HOLIDAY : DayType.MAKEUP_WORKDAY;
        }
        if (node.has("isWorkday")) {
            return node.get("isWorkday").asBoolean(false) ? DayType.MAKEUP_WORKDAY : DayType.HOLIDAY;
        }
        return DayType.HOLIDAY;
    }

    private static DayType typeFromToken(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "HOLIDAY", "OFF", "REST", "放假", "假期", "休息" -> DayType.HOLIDAY;
            case "MAKEUP_WORKDAY", "MAKEUP", "WORK_DAY", "补班", "上班", "调休上班" -> DayType.MAKEUP_WORKDAY;
            case "WORKDAY", "WORKING", "工作日" -> DayType.WORKDAY;
            case "WEEKEND", "周末" -> DayType.WEEKEND;
            default -> null;
        };
    }

    private static DayType typeFromSummary(String summary) {
        if (summary != null) {
            DayType parsed = typeFromToken(summary);
            if (parsed != null) {
                return parsed;
            }
            if (summary.contains("补班") || summary.contains("上班")) {
                return DayType.MAKEUP_WORKDAY;
            }
        }
        return DayType.HOLIDAY;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException ignored) {
            // try basic format below
        }
        try {
            return LocalDate.parse(text, BASIC_DATE);
        } catch (RuntimeException ex) {
            throw invalid("无法解析日期: " + value);
        }
    }

    private static LocalDate parseIcsDate(String line) {
        String value = afterColon(line);
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            throw invalid("ICS 日期不合法: " + line);
        }
        try {
            return LocalDate.parse(digits.substring(0, 8), BASIC_DATE);
        } catch (RuntimeException ex) {
            throw invalid("ICS 日期不合法: " + line);
        }
    }

    private static String afterColon(String line) {
        int index = line.indexOf(':');
        return index < 0 ? null : line.substring(index + 1).trim();
    }

    private static List<String> unfold(String content) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = null;
        for (String raw : content.split("\\R")) {
            if (raw.startsWith(" ") || raw.startsWith("\t")) {
                if (current != null) {
                    current.append(raw.substring(1));
                }
                continue;
            }
            if (current != null) {
                lines.add(current.toString());
            }
            current = new StringBuilder(raw.trim());
        }
        if (current != null) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("SCHEDULING_CALENDAR_INVALID", message);
    }
}
