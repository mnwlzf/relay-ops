-- ============================================================
-- V9: 清理历史 Quartz 状态（切换 PostgreSQLDelegate 的一次性迁移）
-- ============================================================
-- 背景：早先使用 Quartz 默认 StdJDBCDelegate，JOB_DATA 以序列化 bytea 写入，
-- pgjdbc 在 getBlob() 时把它当 OID 解析，持续抛 "不良的类型值 long : \x"，
-- 导致触发器无法读取、misfire 处理反复失败。
--
-- 现在配置为 PostgreSQLDelegate + useProperties=true。Quartz 表全部是
-- 派生状态（真实定义在 scheduling_task），启动时 SchedulingBootstrap 会按库重建，
-- 因此这里直接清空，确保不残留旧格式数据。

DELETE FROM qrtz_fired_triggers;
DELETE FROM qrtz_simple_triggers;
DELETE FROM qrtz_cron_triggers;
DELETE FROM qrtz_simprop_triggers;
DELETE FROM qrtz_blob_triggers;
DELETE FROM qrtz_triggers;
DELETE FROM qrtz_job_details;
DELETE FROM qrtz_paused_trigger_grps;
DELETE FROM qrtz_scheduler_state;
