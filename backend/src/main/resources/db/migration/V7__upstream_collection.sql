-- ============================================================
-- V7: 上游平台、账号与采集快照
-- ============================================================
-- 最基础的采集闭环：平台 → 账号（凭证加密） → 每次采集写一条快照 + 一条运行记录。
-- 凭证只存密文；解密只发生在采集调用的一瞬间。

CREATE TABLE upstream_platform
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(60)  NOT NULL UNIQUE,
    name       VARCHAR(120) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    base_url   VARCHAR(300) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT upstream_platform_type_ck CHECK (type IN ('NEWAPI', 'SUB2API')),
    CONSTRAINT upstream_platform_code_nonblank CHECK (btrim(code) <> '')
);

COMMENT ON TABLE upstream_platform IS '上游中转平台；type 决定用哪个采集客户端';
COMMENT ON COLUMN upstream_platform.base_url IS '平台根地址，采集时拼接具体 API 路径';

CREATE TABLE upstream_account
(
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform_id          BIGINT       NOT NULL REFERENCES upstream_platform (id) ON DELETE CASCADE,
    name                 VARCHAR(120) NOT NULL,
    login                VARCHAR(160) NOT NULL,
    credential_cipher    TEXT         NOT NULL,
    credential_hint      VARCHAR(40),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    last_collect_at      TIMESTAMPTZ,
    last_collect_status  VARCHAR(20),
    last_collect_message TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT upstream_account_login_nonblank CHECK (btrim(login) <> ''),
    CONSTRAINT upstream_account_cipher_nonblank CHECK (btrim(credential_cipher) <> ''),
    CONSTRAINT upstream_account_status_ck
        CHECK (last_collect_status IS NULL
            OR last_collect_status IN ('SUCCESS', 'FAILED', 'PARTIAL', 'SKIPPED'))
);

COMMENT ON TABLE upstream_account IS '上游账号；凭证只存 AES-GCM 密文，任何接口都不回明文';
COMMENT ON COLUMN upstream_account.credential_hint IS '脱敏提示，例如 to***en，仅用于页面识别';

CREATE UNIQUE INDEX upstream_account_platform_login_uniq
    ON upstream_account (platform_id, login);
CREATE INDEX upstream_account_enabled_idx ON upstream_account (enabled);

CREATE TABLE upstream_account_snapshot
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    BIGINT      NOT NULL REFERENCES upstream_account (id) ON DELETE CASCADE,
    collected_at  TIMESTAMPTZ NOT NULL,
    status        VARCHAR(20) NOT NULL,
    balance       NUMERIC(20, 4),
    used_quota    NUMERIC(20, 4),
    request_count BIGINT,
    raw           JSONB,
    message       TEXT,
    CONSTRAINT upstream_snapshot_status_ck CHECK (status IN ('SUCCESS', 'FAILED', 'PARTIAL', 'SKIPPED'))
);

COMMENT ON TABLE upstream_account_snapshot IS '每次采集一条快照；raw 保留上游原始响应用于回放与排错';

CREATE INDEX upstream_snapshot_account_idx
    ON upstream_account_snapshot (account_id, collected_at DESC);

CREATE TABLE upstream_collect_run
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trigger_source VARCHAR(20) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    total_accounts INTEGER     NOT NULL DEFAULT 0,
    succeeded      INTEGER     NOT NULL DEFAULT 0,
    failed         INTEGER     NOT NULL DEFAULT 0,
    message        TEXT,
    CONSTRAINT upstream_collect_run_source_ck
        CHECK (trigger_source IN ('SCHEDULE', 'MANUAL'))
);

COMMENT ON TABLE upstream_collect_run IS '一次全量采集的运行记录，用于回答"采集是不是挂了"';

CREATE INDEX upstream_collect_run_recent_idx ON upstream_collect_run (started_at DESC);

-- 演示平台（停用）：账号与凭证由页面创建，避免把任何示例密钥写进仓库。
INSERT INTO upstream_platform (code, name, type, base_url, enabled)
VALUES ('demo-newapi', '演示 New API', 'NEWAPI', 'https://api.example.com', FALSE);

-- 默认采集任务：每 10 分钟一次，纯技术周期，不跟随工作日历。
INSERT INTO scheduling_task (task_type, name, kind, spec, timezone, params, enabled,
                             misfire_policy, concurrency_policy, timeout_seconds,
                             retry_max, retry_backoff_seconds, version)
VALUES ('upstream.collect-all', '全量账号采集', 'CRON',
        '{"kind":"CRON","cron":"0 */10 * * * ?","timezone":"Asia/Shanghai"}'::jsonb,
        'Asia/Shanghai', '{}'::jsonb, TRUE, 'SKIP', 'SKIP', 120, 0, 60, 0);
