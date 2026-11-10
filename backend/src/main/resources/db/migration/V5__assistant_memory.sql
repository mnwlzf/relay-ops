-- ============================================================
-- V5: 助手记忆（会话、消息、摘要、事实与向量侧表）
-- ============================================================
-- 分层约定（见 docs/refactor-scheduling-and-assistant.md 3.4）：
--   L1 工作记忆：assistant_conversation + assistant_message（最近 N 条）
--   L2 情节记忆：assistant_memory_summary（每 N 轮滚动摘要）
--   L3 语义记忆：assistant_memory_fact（长期事实）
--   L4 业务记忆：assistant_memory_fact(ns=PLATFORM)，由任务或采集写入
-- 向量单独放 assistant_memory_embedding：维度不写死在表结构里，换模型只增行、重建索引。

CREATE TABLE assistant_conversation
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type      VARCHAR(20)  NOT NULL,
    scope_key       VARCHAR(120) NOT NULL,
    pack_id         VARCHAR(30)  NOT NULL,
    persona_id      BIGINT       REFERENCES assistant_persona (id) ON DELETE SET NULL,
    persona_version INTEGER,
    last_message_at TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT assistant_conversation_scope_ck
        CHECK (scope_type IN ('GLOBAL', 'CHANNEL', 'GROUP', 'USER')),
    CONSTRAINT assistant_conversation_status_ck CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT assistant_conversation_pack_ck CHECK (btrim(pack_id) <> '')
);

COMMENT ON TABLE assistant_conversation IS '会话：按能力包与作用域隔离，人设版本单独记录以支持"换人设即分段记忆"';

CREATE UNIQUE INDEX assistant_conversation_scope_uniq
    ON assistant_conversation (scope_type, scope_key, pack_id);
CREATE INDEX assistant_conversation_active_idx ON assistant_conversation (last_message_at DESC);

CREATE TABLE assistant_message
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES assistant_conversation (id) ON DELETE CASCADE,
    seq             BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    speaker_id      VARCHAR(64),
    speaker_name    VARCHAR(80),
    content         TEXT        NOT NULL,
    tokens          INTEGER,
    redacted        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT assistant_message_role_ck
        CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL', 'BROADCAST')),
    UNIQUE (conversation_id, seq)
);

COMMENT ON TABLE assistant_message IS '会话消息归档：既是 L1 的持久化，也是摘要与审计的数据来源';
COMMENT ON COLUMN assistant_message.redacted IS '入库前是否已脱敏；脱敏后仍保留原始行数与时间';

CREATE INDEX assistant_message_recent_idx ON assistant_message (conversation_id, seq DESC);

CREATE TABLE assistant_memory_summary
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES assistant_conversation (id) ON DELETE CASCADE,
    upto_message_id BIGINT      NOT NULL,
    summary         TEXT        NOT NULL,
    tokens          INTEGER,
    model           VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE assistant_memory_summary IS '滚动摘要：可审计、可重建，摘要失败不影响原始消息';

CREATE INDEX assistant_memory_summary_recent_idx
    ON assistant_memory_summary (conversation_id, created_at DESC);

CREATE TABLE assistant_memory_fact
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    namespace    VARCHAR(20)  NOT NULL,
    scope_type   VARCHAR(20)  NOT NULL,
    scope_key    VARCHAR(120) NOT NULL,
    category     VARCHAR(40)  NOT NULL,
    fact_key     VARCHAR(120),
    fact_value   TEXT         NOT NULL,
    confidence   NUMERIC(3, 2) NOT NULL DEFAULT 0.80,
    source       VARCHAR(20)  NOT NULL,
    provenance   JSONB,
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT assistant_memory_fact_namespace_ck
        CHECK (namespace IN ('PLATFORM', 'GENERAL', 'SHARED')),
    CONSTRAINT assistant_memory_fact_source_ck
        CHECK (source IN ('EXPLICIT', 'DERIVED', 'LLM_EXTRACT')),
    CONSTRAINT assistant_memory_fact_scope_ck
        CHECK (scope_type IN ('GLOBAL', 'CHANNEL', 'GROUP', 'USER')),
    CONSTRAINT assistant_memory_fact_confidence_ck CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT assistant_memory_fact_value_ck CHECK (btrim(fact_value) <> '')
);

COMMENT ON TABLE assistant_memory_fact IS '长期事实：用户偏好、群约定、共同经历与平台业务结论';
COMMENT ON COLUMN assistant_memory_fact.namespace IS 'PLATFORM 只对平台能力包可见，GENERAL 闲聊，SHARED 两者共享的安全事实';
COMMENT ON COLUMN assistant_memory_fact.expires_at IS '过期后不再召回，由清理任务物理删除';

CREATE INDEX assistant_memory_fact_scope_idx
    ON assistant_memory_fact (namespace, scope_type, scope_key, category);
CREATE INDEX assistant_memory_fact_expiry_idx ON assistant_memory_fact (expires_at);

CREATE TABLE assistant_memory_embedding
(
    fact_id    BIGINT      NOT NULL REFERENCES assistant_memory_fact (id) ON DELETE CASCADE,
    model      VARCHAR(60) NOT NULL,
    dim        SMALLINT    NOT NULL,
    embedding  vector,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (fact_id, model),
    CONSTRAINT assistant_memory_embedding_dim_ck CHECK (dim > 0)
);

COMMENT ON TABLE assistant_memory_embedding IS '向量侧表：与事实解耦，可多模型并存；维度稳定后再收紧为 vector(N) 并建 HNSW 索引';
