package com.relayops.scheduling.application.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relayops.scheduling.domain.DayFilter;
import com.relayops.scheduling.domain.DayFilterType;
import com.relayops.scheduling.domain.HolidayPolicy;
import com.relayops.scheduling.domain.ScheduleKind;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Reads and writes {@link ScheduleSpec} JSON, and validates it against the rules of each kind.
 *
 * <p>Validation happens here (not in the record constructor) so every entry point — REST, import,
 * migration — fails with the same message.</p>
 */
@Component
public class ScheduleSpecCodec {

    private final ObjectMapper objectMapper;

    public ScheduleSpecCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScheduleSpec parse(JsonNode node) {
        return parse(node, BusinessTime.DEFAULT_ZONE);
    }

    public ScheduleSpec parse(JsonNode node, ZoneId defaultZone) {
        if (node == null || !node.isObject()) {
            throw invalid("spec 必须是 JSON 对象");
        }
        ScheduleKind kind = kind(node);
        ZoneId zone = zone(node, defaultZone);
        DayFilter dayFilter = dayFilter(node.get("dayFilter"));
        List<LocalTime> times = times(node.get("times"));
        HolidayPolicy holidayPolicy = holidayPolicy(node);
        ScheduleSpec.ActiveWindow activeWindow = activeWindow(node.get("activeWindow"));
        String cron = text(node, "cron");
        Duration interval = duration(node);
        Instant once = once(node, zone);
        String calendarCode = text(node, "calendarCode");

        ScheduleSpec spec = new ScheduleSpec(kind, zone, calendarCode, dayFilter, times,
                holidayPolicy, activeWindow, cron, interval, once);
        validate(spec);
        return spec;
    }

    /** Canonical JSON used for persistence, so stored specs never drift from the parsed form. */
    public JsonNode write(ScheduleSpec spec) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", spec.kind().name());
        root.put("timezone", spec.zone().getId());
        if (spec.calendarCode() != null) {
            root.put("calendarCode", spec.calendarCode());
        }
        ObjectNode filter = root.putObject("dayFilter");
        filter.put("type", spec.dayFilter().type().name());
        if (!spec.dayFilter().weekdays().isEmpty()) {
            ArrayNode weekdays = filter.putArray("weekdays");
            spec.dayFilter().weekdays().forEach(day -> weekdays.add(day.name()));
        }
        if (spec.dayFilter().nth() != null) {
            filter.put("nth", spec.dayFilter().nth());
        }
        if (!spec.dayFilter().daysOfMonth().isEmpty()) {
            ArrayNode days = filter.putArray("days");
            spec.dayFilter().daysOfMonth().stream().sorted().forEach(days::add);
        }
        if (!spec.dayFilter().dates().isEmpty()) {
            ArrayNode dates = filter.putArray("dates");
            spec.dayFilter().dates().forEach(date -> dates.add(date.toString()));
        }
        if (!spec.times().isEmpty()) {
            ArrayNode timeArray = root.putArray("times");
            spec.times().forEach(time -> timeArray.add(time.toString()));
        }
        root.put("holidayPolicy", spec.holidayPolicy().name());
        if (spec.activeWindow() != null) {
            ObjectNode window = root.putObject("activeWindow");
            window.put("from", spec.activeWindow().from().toString());
            window.put("to", spec.activeWindow().to().toString());
        }
        if (spec.cron() != null) {
            root.put("cron", spec.cron());
        }
        if (spec.interval() != null) {
            root.put("interval", spec.interval().toString());
        }
        if (spec.once() != null) {
            root.put("once", spec.once().toString());
        }
        return root;
    }

    public String writeString(ScheduleSpec spec) {
        try {
            return objectMapper.writeValueAsString(write(spec));
        } catch (Exception ex) {
            throw new IllegalStateException("无法序列化 spec", ex);
        }
    }

    /** Kind-specific validation; called by {@link #parse} and by the admin service before saving. */
    public void validate(ScheduleSpec spec) {
        spec.dayFilter().validate();
        switch (spec.kind()) {
            case CRON -> {
                if (spec.cron() == null) {
                    throw invalid("kind=CRON 需要 cron 表达式");
                }
                requireValidCron(spec.cron());
            }
            case INTERVAL -> {
                if (spec.interval() == null || spec.interval().isZero() || spec.interval().isNegative()) {
                    throw invalid("kind=INTERVAL 需要正数 interval，例如 PT10M");
                }
            }
            case ONCE -> {
                if (spec.once() == null) {
                    throw invalid("kind=ONCE 需要 once，例如 2027-01-04T10:00:00+08:00");
                }
            }
            case CALENDAR_TIMES -> {
                requireTimes(spec);
                requireCalendarForDayFilter(spec);
            }
            case MONTH_NTH_WORKDAY -> {
                requireTimes(spec);
                requireCalendarForDayFilter(spec);
                if (spec.dayFilter().type() != DayFilterType.MONTH_NTH_WORKDAY
                        && spec.dayFilter().type() != DayFilterType.MONTH_LAST_WORKDAY) {
                    throw invalid("kind=MONTH_NTH_WORKDAY 的 dayFilter 必须是 MONTH_NTH_WORKDAY 或 MONTH_LAST_WORKDAY");
                }
            }
        }
        if (spec.activeWindow() != null) {
            if (!spec.kind().equals(ScheduleKind.CALENDAR_TIMES)
                    && !spec.kind().equals(ScheduleKind.MONTH_NTH_WORKDAY)) {
                throw invalid("activeWindow 仅支持 CALENDAR_TIMES / MONTH_NTH_WORKDAY");
            }
            for (LocalTime time : spec.times()) {
                if (!spec.activeWindow().contains(time)) {
                    throw invalid("时刻 " + time + " 不在 activeWindow 内");
                }
            }
        }
    }

    private void requireTimes(ScheduleSpec spec) {
        if (spec.times().isEmpty()) {
            throw invalid("kind=" + spec.kind().name() + " 需要至少一个时刻，例如 [\"09:00\"]");
        }
    }

    private void requireCalendarForDayFilter(ScheduleSpec spec) {
        if (spec.holidayPolicy() == HolidayPolicy.FORCE) {
            return;
        }
        if (spec.dayFilter().needsCalendar() && spec.calendarCode() == null) {
            throw invalid("dayFilter=" + spec.dayFilter().type().name() + " 需要指定 calendarCode");
        }
    }

    private void requireValidCron(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            throw invalid("Cron 表达式不合法: " + cron);
        }
        // Quartz accepts out-of-range hour values (e.g. "0 0 99 * * ?" parses fine and then never
        // fires), so ranges are checked here to fail loudly at save time instead of silently.
        validateCronRanges(cron);
    }

    private void validateCronRanges(String cron) {
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 6 || parts.length > 7) {
            throw invalid("Cron 必须是 6 段或 7 段（秒 分 时 日 月 周 [年]）: " + cron);
        }
        checkField(parts[0], 0, 59, "秒", cron);
        checkField(parts[1], 0, 59, "分", cron);
        checkField(parts[2], 0, 23, "时", cron);
        checkField(parts[3], 1, 31, "日", cron);
        checkField(parts[4], 1, 12, "月", cron);
        checkField(parts[5], 1, 7, "周", cron);
        if (parts.length == 7) {
            checkField(parts[6], 1970, 9999, "年", cron);
        }
    }

    private void checkField(String field, int min, int max, String label, String cron) {
        for (String item : field.split(",")) {
            String token = item.trim();
            if (token.isEmpty() || "*".equals(token) || "?".equals(token)) {
                continue;
            }
            token = token.replaceAll("(?i)([lw].*)$", "");
            int stepIndex = token.indexOf('/');
            if (stepIndex >= 0) {
                token = token.substring(0, stepIndex);
            }
            if (token.isEmpty() || "*".equals(token) || "?".equals(token)) {
                continue;
            }
            int rangeIndex = token.indexOf('-');
            if (rangeIndex > 0) {
                checkNumber(token.substring(0, rangeIndex), min, max, label, cron);
                checkNumber(token.substring(rangeIndex + 1), min, max, label, cron);
            } else {
                checkNumber(token, min, max, label, cron);
            }
        }
    }

    private void checkNumber(String token, int min, int max, String label, String cron) {
        String value = token.trim();
        if (value.isEmpty() || value.chars().anyMatch(Character::isLetter)) {
            return; // month / weekday names are validated by Quartz itself
        }
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw invalid("Cron " + label + "字段不合法: " + token);
        }
        if (number < min || number > max) {
            throw invalid("Cron " + label + "字段超出范围 " + min + "-" + max + ": " + cron);
        }
    }

    private ScheduleKind kind(JsonNode node) {
        String value = text(node, "kind");
        if (value == null) {
            throw invalid("spec.kind 不能为空");
        }
        try {
            return ScheduleKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw invalid("未知的 spec.kind: " + value);
        }
    }

    private ZoneId zone(JsonNode node, ZoneId defaultZone) {
        String value = text(node, "timezone");
        if (value == null) {
            return defaultZone == null ? BusinessTime.DEFAULT_ZONE : defaultZone;
        }
        try {
            return ZoneId.of(value);
        } catch (RuntimeException ex) {
            throw invalid("时区不合法: " + value);
        }
    }

    private DayFilter dayFilter(JsonNode node) {
        if (node == null || node.isNull()) {
            return DayFilter.all();
        }
        if (!node.isObject()) {
            throw invalid("dayFilter 必须是对象");
        }
        DayFilterType type;
        String rawType = text(node, "type");
        if (rawType == null) {
            type = DayFilterType.ALL;
        } else {
            try {
                type = DayFilterType.valueOf(rawType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw invalid("未知的 dayFilter.type: " + rawType);
            }
        }
        List<DayOfWeek> weekdays = new ArrayList<>();
        JsonNode weekdayNode = node.get("weekdays");
        if (weekdayNode != null && weekdayNode.isArray()) {
            for (JsonNode item : weekdayNode) {
                weekdays.add(weekday(item.asText()));
            }
        }
        Integer nth = null;
        JsonNode nthNode = node.get("nth");
        if (nthNode != null && !nthNode.isNull()) {
            nth = nthNode.asInt();
        }
        List<Integer> daysOfMonth = new ArrayList<>();
        JsonNode daysNode = node.get("days") == null ? node.get("daysOfMonth") : node.get("days");
        if (daysNode != null && daysNode.isArray()) {
            for (JsonNode item : daysNode) {
                daysOfMonth.add(item.asInt());
            }
        }
        List<LocalDate> dates = new ArrayList<>();
        JsonNode dateNode = node.get("dates");
        if (dateNode != null && dateNode.isArray()) {
            for (JsonNode item : dateNode) {
                dates.add(parseDate(item.asText()));
            }
        }
        return DayFilter.of(type, weekdays, daysOfMonth, nth, dates);
    }

    private DayOfWeek weekday(String value) {
        String upper = value.trim().toUpperCase(Locale.ROOT);
        DayOfWeek shortName = switch (upper) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> null;
        };
        if (shortName != null) {
            return shortName;
        }
        try {
            int iso = Integer.parseInt(value.trim());
            if (iso >= 1 && iso <= 7) {
                return DayOfWeek.of(iso);
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw invalid("weekdays 只支持 MON..SUN 或 1..7: " + value);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            throw invalid("日期不合法: " + value);
        }
    }

    private List<LocalTime> times(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid("times 必须是数组");
        }
        List<LocalTime> times = new ArrayList<>();
        for (JsonNode item : node) {
            try {
                times.add(LocalTime.parse(item.asText().trim()));
            } catch (RuntimeException ex) {
                throw invalid("时刻不合法（HH:mm 或 HH:mm:ss）: " + item.asText());
            }
        }
        return times;
    }

    private HolidayPolicy holidayPolicy(JsonNode node) {
        String value = text(node, "holidayPolicy");
        if (value == null) {
            return HolidayPolicy.SKIP;
        }
        try {
            return HolidayPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw invalid("未知的 holidayPolicy: " + value);
        }
    }

    private ScheduleSpec.ActiveWindow activeWindow(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw invalid("activeWindow 必须是对象");
        }
        String from = text(node, "from");
        String to = text(node, "to");
        if (from == null || to == null) {
            throw invalid("activeWindow 需要 from/to");
        }
        return new ScheduleSpec.ActiveWindow(parseTime(from, "activeWindow.from"), parseTime(to, "activeWindow.to"));
    }

    private LocalTime parseTime(String value, String field) {
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException ex) {
            throw invalid(field + " 不是合法时间: " + value);
        }
    }

    private Duration duration(JsonNode node) {
        String value = text(node, "interval");
        if (value == null) {
            return null;
        }
        try {
            return Duration.parse(value);
        } catch (RuntimeException ex) {
            throw invalid("interval 必须是 ISO-8601 时长，例如 PT10M: " + value);
        }
    }

    private Instant once(JsonNode node, ZoneId zone) {
        String value = text(node, "once");
        if (value == null) {
            return null;
        }
        Instant parsed = com.relayops.scheduling.domain.ScheduleSpecJson.instantOrNull(value);
        if (parsed != null) {
            return parsed;
        }
        if (com.relayops.scheduling.domain.ScheduleSpecJson.looksLikeLocalDateTime(value)) {
            return LocalDateTime.parse(value).atZone(zone).toInstant();
        }
        throw invalid("once 不合法: " + value);
    }

    private String text(JsonNode node, String field) {
        return com.relayops.scheduling.domain.ScheduleSpecJson.text(node, field);
    }

    private BusinessException invalid(String message) {
        return new BusinessException("SCHEDULE_SPEC_INVALID", message);
    }
}
