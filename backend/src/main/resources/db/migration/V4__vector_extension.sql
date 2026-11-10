-- ============================================================
-- V4: pgvector 扩展（L3 语义记忆的向量检索基础）
-- ============================================================
-- 约定：
--   * 维度不写死在这里：assistant_memory_embedding 侧表记录 model/dim，
--     换模型时新增 model 行并重建索引（见 docs/refactor-scheduling-and-assistant.md 3.4）；
--   * CREATE EXTENSION 需要超级用户权限，或镜像已预装扩展（pgvector/pgvector:pg18-trixie）；
--     连接外部 PostgreSQL 时由 DBA 先执行一次 CREATE EXTENSION vector;，本迁移即为幂等空操作；
--   * 本文件只建扩展，向量表结构随 M4 落地。

CREATE EXTENSION IF NOT EXISTS vector;
