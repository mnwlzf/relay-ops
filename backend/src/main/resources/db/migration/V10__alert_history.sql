-- ============================================================
-- V10: 告警历史与静默规则
-- ============================================================
-- 历史：每次真正发出的告警记一行，回答"谁在什么时候被告警过"。
-- 静默：按账号或全局在指定时间前不发送（维护窗口、已知问题）。

CREATE TABLE notification_alert_history
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   BIGINT       NOT NULL,
    account_name VARCHAR(120) NOT NULL,
    alert_type   VARCHAR(30)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    channels     VARCHAR(60)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE notification_alert_history IS '告警发送历史；channels 记录本次投递尝试的通道';
CREATE INDEX notification_alert_history_recent_idx
    ON notification_alert_history (created_at DESC);

CREATE TABLE notification_silence_rule
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type VARCHAR(20)  NOT NULL,
    scope_key  VARCHAR(120) NOT NULL,
    alert_type VARCHAR(30),
    until      TIMESTAMPTZ  NOT NULL,
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT notification_silence_scope_ck CHECK (scope_type IN ('GLOBAL', 'ACCOUNT')),
    CONSTRAINT notification_silence_key_ck CHECK (btrim(scope_key) <> ''),
    CONSTRAINT notification_silence_type_ck
        CHECK (alert_type IS NULL OR alert_type IN ('BALANCE_LOW', 'COLLECT_FAILED', 'COLLECT_STALE'))
);

COMMENT ON TABLE notification_silence_rule IS '告警静默规则：到 until 之前不再发送匹配的告警';
COMMENT ON COLUMN notification_silence_rule.alert_type IS '为空表示该作用域下所有告警类型';

CREATE INDEX notification_silence_until_idx ON notification_silence_rule (until);
