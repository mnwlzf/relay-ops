-- ============================================================
-- V6: 定时消息蓝图（M6 消息编排）
-- ============================================================
-- 蓝图 = 分段编排：文本段引用变量、工具段携带只读工具结果、点评段交给模型。
-- 只读约束（sideEffect=READ_ONLY 且 schedulable=true）由应用层校验，这里不重复建模。

CREATE TABLE assistant_blueprint
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(80)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    pack_id    VARCHAR(30)  NOT NULL,
    persona_id BIGINT       REFERENCES assistant_persona (id) ON DELETE SET NULL,
    segments   JSONB        NOT NULL,
    options    JSONB        NOT NULL DEFAULT '{}',
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    version    INTEGER      NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT assistant_blueprint_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT assistant_blueprint_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT assistant_blueprint_segments_ck CHECK (jsonb_typeof(segments) = 'array')
);

COMMENT ON TABLE assistant_blueprint IS '定时消息蓝图：分段编排，可携带只读工具的实时结果';
COMMENT ON COLUMN assistant_blueprint.segments IS '段数组：text / tool / persona_note，见 docs 3.6';
COMMENT ON COLUMN assistant_blueprint.options IS '执行选项：maxToolCalls、onToolError、fallback 等';

-- 演示用蓝图：让前端在没有 copilot 工具前也能看到完整的 dry-run 效果。
-- general.clock 是内置的只读演示工具，替换成真实平台工具即可。
INSERT INTO assistant_blueprint (code, name, pack_id, segments, options, enabled)
VALUES ('demo.daily-note', '每日播报（演示）', 'general',
        '[
           {"type":"text","template":"【{{date}} 值班播报】现在是 {{now}}"},
           {"type":"tool","ref":"general.clock","as":"clock","args":{},"title":"系统时间","onError":"DEGRADE"},
           {"type":"tool","ref":"general.echo","as":"memo","args":{"text":"{{args.memo}}"},"title":"备注","onEmpty":"HIDE"},
           {"type":"text","template":"如需调整，回复「改播报」即可。"}
         ]'::jsonb,
        '{"maxToolCalls":8,"onToolError":"DEGRADE","fallback":"TEXT"}'::jsonb,
        TRUE);

-- 演示用定时任务：默认停用，页面里启用或"立即执行"即可验证整条链路。
INSERT INTO scheduling_task (task_type, name, kind, spec, timezone, calendar_code, params,
                             enabled, misfire_policy, concurrency_policy, timeout_seconds,
                             retry_max, retry_backoff_seconds, version)
VALUES ('assistant.broadcast', '每日播报（演示）', 'CALENDAR_TIMES',
        '{"kind":"CALENDAR_TIMES","timezone":"Asia/Shanghai","calendarCode":"cn-official",
          "dayFilter":{"type":"WORKDAY"},"times":["09:00"]}'::jsonb,
        'Asia/Shanghai', 'cn-official',
        '{"blueprintCode":"demo.daily-note","scopeType":"GLOBAL","scopeKey":"*",
          "args":{"memo":"这是演示备注，可在任务参数里改"}}'::jsonb,
        FALSE, 'FIRE_ONCE', 'SKIP', 300, 0, 60, 0);
