-- ============================================================
-- V1: 调度域基础表（任务、执行历史、工作日历）
-- ============================================================
-- 约定：
--   * spec 为结构化 JSON，语义见 docs/refactor-scheduling-and-assistant.md 2.2；
--   * 只有日历例外存表，未命中回落"周一至周五 = 工作日"；
--   * 执行历史以 (task_id, planned_at) 为幂等键，MANUAL 除外。

CREATE TABLE scheduling_calendar
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    timezone    VARCHAR(60)  NOT NULL DEFAULT 'Asia/Shanghai',
    source      VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    covered_to  DATE,
    description VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT scheduling_calendar_source_ck CHECK (source IN ('MANUAL', 'IMPORT'))
);

COMMENT ON TABLE scheduling_calendar IS '工作日历：法定节假日、调休补班与自定义休息日';
COMMENT ON COLUMN scheduling_calendar.source IS '数据来源：MANUAL 手工维护，IMPORT 年度导入';
COMMENT ON COLUMN scheduling_calendar.covered_to IS '数据覆盖到的最后一天，用于提示"明年数据未导入"';

CREATE TABLE scheduling_calendar_day
(
    calendar_id BIGINT      NOT NULL REFERENCES scheduling_calendar (id) ON DELETE CASCADE,
    day         DATE        NOT NULL,
    day_type    VARCHAR(20) NOT NULL,
    name        VARCHAR(80),
    origin      VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    PRIMARY KEY (calendar_id, day),
    CONSTRAINT scheduling_calendar_day_type_ck
        CHECK (day_type IN ('WORKDAY', 'MAKEUP_WORKDAY', 'WEEKEND', 'HOLIDAY')),
    CONSTRAINT scheduling_calendar_day_origin_ck CHECK (origin IN ('MANUAL', 'IMPORT'))
);

COMMENT ON TABLE scheduling_calendar_day IS '只存例外：法定节假日、调休补班、被强制标为上班的休息日';
COMMENT ON COLUMN scheduling_calendar_day.day_type IS 'WORKDAY 工作日 / MAKEUP_WORKDAY 调休补班 / WEEKEND 周末 / HOLIDAY 法定节假日';

CREATE TABLE scheduling_task
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_type           VARCHAR(100) NOT NULL,
    name                VARCHAR(120) NOT NULL,
    kind                VARCHAR(30)  NOT NULL,
    spec                JSONB        NOT NULL,
    timezone            VARCHAR(60)  NOT NULL DEFAULT 'Asia/Shanghai',
    calendar_code       VARCHAR(60),
    params              JSONB,
    misfire_policy      VARCHAR(20)  NOT NULL DEFAULT 'FIRE_ONCE',
    max_catch_up        SMALLINT     NOT NULL DEFAULT 1,
    concurrency_policy  VARCHAR(20)  NOT NULL DEFAULT 'SKIP',
    timeout_seconds     INTEGER      NOT NULL DEFAULT 300,
    retry_max           SMALLINT     NOT NULL DEFAULT 0,
    retry_backoff_seconds INTEGER    NOT NULL DEFAULT 60,
    quiet_hours         JSONB,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    next_fire_at        TIMESTAMPTZ,
    last_run_at         TIMESTAMPTZ,
    last_run_status     VARCHAR(20),
    last_run_message    TEXT,
    version             INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT scheduling_task_kind_ck
        CHECK (kind IN ('CRON', 'INTERVAL', 'CALENDAR_TIMES', 'ONCE', 'MONTH_NTH_WORKDAY')),
    CONSTRAINT scheduling_task_misfire_ck
        CHECK (misfire_policy IN ('SKIP', 'FIRE_ONCE', 'CATCH_UP')),
    CONSTRAINT scheduling_task_concurrency_ck
        CHECK (concurrency_policy IN ('SKIP', 'QUEUE')),
    CONSTRAINT scheduling_task_timeout_ck CHECK (timeout_seconds > 0),
    CONSTRAINT scheduling_task_retry_ck CHECK (retry_max >= 0)
);

COMMENT ON TABLE scheduling_task IS '可配置的定时任务；执行由 Quartz 触发，业务逻辑在处理器（ScheduledTaskHandler）里';
COMMENT ON COLUMN scheduling_task.task_type IS '处理器编码，由各业务模块实现 ScheduledTaskHandler';
COMMENT ON COLUMN scheduling_task.spec IS '结构化时间定义，见计划 2.2 节';
COMMENT ON COLUMN scheduling_task.calendar_code IS '关联的工作日历；为空表示纯自然日';
COMMENT ON COLUMN scheduling_task.params IS '处理器参数（含消息蓝图引用与工具参数）';
COMMENT ON COLUMN scheduling_task.quiet_hours IS '静默时段，例如 ["23:00","07:00"]，命中则跳过本次';

CREATE UNIQUE INDEX scheduling_task_name_uniq ON scheduling_task (name);
CREATE INDEX scheduling_task_due_idx ON scheduling_task (next_fire_at) WHERE enabled;
CREATE INDEX scheduling_task_type_idx ON scheduling_task (task_type);

CREATE TABLE scheduling_task_run
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id        BIGINT      NOT NULL REFERENCES scheduling_task (id) ON DELETE CASCADE,
    planned_at     TIMESTAMPTZ NOT NULL,
    trigger_source VARCHAR(20) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL,
    duration_ms    INTEGER,
    message        TEXT,
    detail         JSONB,
    CONSTRAINT scheduling_task_run_source_ck
        CHECK (trigger_source IN ('SCHEDULE', 'MANUAL', 'CATCH_UP', 'RETRY', 'HOLIDAY_SHIFT')),
    CONSTRAINT scheduling_task_run_status_ck
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT', 'SKIPPED'))
);

COMMENT ON TABLE scheduling_task_run IS '任务执行历史；planned_at 是计划触发点，用于错过补偿与幂等';
COMMENT ON COLUMN scheduling_task_run.detail IS '各工具段耗时/失败原因等结构化明细';

CREATE UNIQUE INDEX scheduling_task_run_idem
    ON scheduling_task_run (task_id, planned_at)
    WHERE trigger_source <> 'MANUAL';
CREATE INDEX scheduling_task_run_task_idx ON scheduling_task_run (task_id, started_at DESC);
CREATE INDEX scheduling_task_run_status_idx ON scheduling_task_run (status, started_at DESC);

INSERT INTO scheduling_calendar (code, name, timezone, source, description)
VALUES ('cn-official', '中国大陆法定节假日（含调休）', 'Asia/Shanghai', 'MANUAL',
        '未导入日期按周一至周五为工作日计算；每年国务院办公厅通知后导入当年数据');
