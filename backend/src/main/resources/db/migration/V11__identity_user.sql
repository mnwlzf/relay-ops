-- ============================================================
-- V11: 控制台账号（identity）
-- ============================================================
-- 只做最小可用：用户名 + BCrypt 密码 + 角色。默认管理员由启动器按环境变量创建，
-- 迁移里不写死任何密码。

CREATE TABLE identity_user
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(80),
    role          VARCHAR(20)  NOT NULL DEFAULT 'VIEWER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT identity_user_username_ck CHECK (btrim(username) <> ''),
    CONSTRAINT identity_user_role_ck CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);

COMMENT ON TABLE identity_user IS '控制台账号；密码只存 BCrypt 哈希，角色决定可写范围';
COMMENT ON COLUMN identity_user.role IS 'ADMIN 全权 / OPERATOR 可写但不可管账号 / VIEWER 只读';
