-- ============================================================
-- V3: 助手人设（角色卡）与作用域绑定
-- ============================================================
-- 角色卡只承载表达层：六段文案 + 风格开关 + 示例 + 关系称呼 + 适用能力包。
-- 权限、工具与脱敏不在这里，它们由 agent 内核与能力包决定。

CREATE TABLE assistant_persona
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code               VARCHAR(60)  NOT NULL UNIQUE,
    name               VARCHAR(80)  NOT NULL,
    description        VARCHAR(300),
    appearance_prompt  TEXT,
    personality_prompt TEXT,
    rhythm_prompt      TEXT,
    relation_prompt    TEXT,
    emotion_prompt     TEXT,
    boundary_prompt    TEXT,
    style              JSONB,
    examples           JSONB,
    address_rules      JSONB,
    packs              JSONB        NOT NULL DEFAULT '["general"]',
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default         BOOLEAN      NOT NULL DEFAULT FALSE,
    version            INTEGER      NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT assistant_persona_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT assistant_persona_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT assistant_persona_version_ck CHECK (version >= 1)
);

COMMENT ON TABLE assistant_persona IS '角色卡：只影响表达方式，不承载权限、工具与脱敏';
COMMENT ON COLUMN assistant_persona.packs IS '适用能力包，例如 ["general"] 或 ["general","platform"]';
COMMENT ON COLUMN assistant_persona.is_default IS '未绑定或绑定失效时的兜底人设；全表至多一条为真';
COMMENT ON COLUMN assistant_persona.version IS '修改后递增，用于隔离客户端缓存与会话记忆';

CREATE UNIQUE INDEX assistant_persona_single_default
    ON assistant_persona (is_default)
    WHERE is_default = TRUE;

CREATE INDEX assistant_persona_enabled_idx ON assistant_persona (enabled);

CREATE TABLE assistant_profile
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type    VARCHAR(20)  NOT NULL,
    scope_key     VARCHAR(120) NOT NULL,
    pack_id       VARCHAR(30)  NOT NULL,
    persona_id    BIGINT       REFERENCES assistant_persona (id) ON DELETE SET NULL,
    model_profile VARCHAR(60),
    memory_policy JSONB,
    guard_policy  JSONB,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT assistant_profile_scope_ck
        CHECK (scope_type IN ('GLOBAL', 'CHANNEL', 'GROUP', 'USER')),
    CONSTRAINT assistant_profile_scope_key_ck CHECK (btrim(scope_key) <> ''),
    CONSTRAINT assistant_profile_pack_ck CHECK (btrim(pack_id) <> '')
);

COMMENT ON TABLE assistant_profile IS '作用域绑定：某个群/通道/用户使用哪个人设、哪套记忆与准入策略';
COMMENT ON COLUMN assistant_profile.scope_key IS 'GLOBAL 用 *；GROUP/CHANNEL/USER 用 qq:group:123 这类稳定键';
COMMENT ON COLUMN assistant_profile.enabled IS '停用后回落到更宽的作用域或默认人设';

CREATE UNIQUE INDEX assistant_profile_scope_uniq
    ON assistant_profile (scope_type, scope_key, pack_id);
