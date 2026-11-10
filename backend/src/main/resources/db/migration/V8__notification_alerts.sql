-- ============================================================
-- V8: 告警去重状态与平台播报蓝图
-- ============================================================
-- 告警状态用于冷却：同账号同类型在冷却窗口内只发一次，避免采集每 10 分钟就催一次。

CREATE TABLE notification_alert_state
(
    account_id    BIGINT      NOT NULL,
    alert_type    VARCHAR(30) NOT NULL,
    last_sent_at  TIMESTAMPTZ NOT NULL,
    last_value    VARCHAR(120),
    PRIMARY KEY (account_id, alert_type),
    CONSTRAINT notification_alert_type_ck
        CHECK (alert_type IN ('BALANCE_LOW', 'COLLECT_FAILED', 'COLLECT_STALE'))
);

COMMENT ON TABLE notification_alert_state IS '告警发送状态：按 账号+类型 记录上次发送时间，用于冷却';

-- 每 15 分钟检查一次告警，纯技术周期。
INSERT INTO scheduling_task (task_type, name, kind, spec, timezone, params, enabled,
                             misfire_policy, concurrency_policy, timeout_seconds,
                             retry_max, retry_backoff_seconds, version)
VALUES ('notification.alerts', '采集告警检查', 'CRON',
        '{"kind":"CRON","cron":"0 */15 * * * ?","timezone":"Asia/Shanghai"}'::jsonb,
        'Asia/Shanghai', '{}'::jsonb, TRUE, 'SKIP', 'SKIP', 60, 0, 60, 0);

-- 平台播报蓝图：真实数据来自 copilot 的 platform.overview / platform.abnormal 工具。
INSERT INTO assistant_blueprint (code, name, pack_id, segments, options, enabled)
VALUES ('platform.daily', '平台日报', 'platform',
        '[
           {"type":"text","template":"【{{date}} 平台日报】"},
           {"type":"tool","ref":"platform.overview","as":"overview","args":{},"title":"总览","onError":"DEGRADE"},
           {"type":"tool","ref":"platform.abnormal","as":"abnormal","args":{},"title":"需要关注","onError":"DEGRADE","onEmpty":"HIDE"},
           {"type":"text","template":"回复「/report platform.daily」可再次查看。"}
         ]'::jsonb,
        '{"maxToolCalls":4,"onToolError":"DEGRADE","fallback":"TEXT"}'::jsonb,
        TRUE);
