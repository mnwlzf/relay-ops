# RelayOps 重构计划 v2：Quartz 调度 × 三模块助手 × 插件化 × 定时消息工具编排

> 目标：`E:\code\relay-ops`（Spring Boot 模块化单体）
> 参照：`E:\code\monitor`（44 个迁移、`bot` 包 130+ 文件、单字段 Cron 动态调度）
> 本文件替代 v1；v1 的选项对比压缩进附录 A 作为"为什么选乙"的依据。
>
> **本版落定的决策**：路线乙 —— Quartz（JDBC JobStore）+ `agent`/`assistant`/`copilot` 三顶级模块 + pgvector 记忆；embedding 走阿里云百炼 `text-embedding-v4`（1024 维）。
> **本版新增的三块内容**：① 大肥鱼式角色卡（角色 + 关系 + 群聊接话）；② 插件系统（自定义人设/命令/工具/玩法/模板）；③ 定时消息的"工具结果携带"编排（可选择工具、可指定参数）。

---

## 0. 本版与 v1 的差异

| 项 | v1（甲） | v2（乙，本版） |
|---|---|---|
| 调度引擎 | 自研 `NextFirePlanner` + 单次注册 | **Quartz JDBC JobStore**（11 张 `QRTZ_*` 表，Flyway 管理） |
| 中国日历 | 自研日历表 + planner 判定 | 日历表不变，但由**自定义 `org.quartz.Calendar`** 承载筛选 |
| 模块拆分 | `assistant` 内 kernel + 双 pack | **`agent` / `assistant` / `copilot` 三个顶级模块** |
| 记忆 | 窗口 + 摘要 + 事实（无向量） | 四层 + **pgvector（HNSW）语义召回** |
| 人设 | 单表字段扩展 | **角色卡（结构化 6 段）+ 关系分级 + 音色 + 示例** |
| 扩展方式 | 能力包（代码内） | 能力包（代码内）**+ 插件系统（用户自定义，声明式为主）** |
| 定时消息 | 模板 / 报告 / AGENT 三档 | 三档 + **消息蓝图（Blueprint）：分段编排，可携带任意只读工具结果并可指定参数** |
| 粗估 | 52–74 人日 | **96–125 人日** |

---

## 1. 目标架构

### 1.1 模块与依赖

```text
com.relayops
├── shared/            # 时间、错误、统一响应、脱敏基础
├── integration/       # onebot / sub2api / sub2db 协议适配（无业务）
├── identity/          # Web 用户、角色、QQ 绑定、访问策略
├── upstream/          # 平台、账号、凭证、采集编排
├── monitoring/        # 快照、看板、号池、变更、状态
├── operations/        # 事故、自愈、诊断
├── notification/      # 邮件 / QQ / Webhook 投递（受投递方）
├── scheduling/        # ★ Quartz 调度：任务定义、触发、执行记录、工作日历
│
├── agent/             # ★ 内核：角色卡、记忆、提示词装配、模型路由、工具注册表、能力包 SPI、渲染
├── assistant/         # ★ 通道与通用能力：OneBot/Web 接入、消息编排、通用包、归档、插件运行时
└── copilot/           # ★ 平台能力包：查询、诊断、日报生成、播报编排
```

依赖方向（**ArchUnit 强制**）：

| 模块 | 允许依赖 |
|---|---|
| `shared` | 无 |
| `integration` | `shared` |
| `scheduling` | `shared`（**只依赖自己的 contract**，不认任何业务模块） |
| `agent` | `shared` |
| `assistant` | `agent`、`scheduling.contract`、`notification.contract`、`identity.contract` |
| `copilot` | `agent`、`monitoring.contract`、`upstream.contract`、`operations.contract`、`scheduling.contract` |
| `monitoring` / `upstream` / `operations` / `notification` / `identity` | `shared`、`integration`、彼此 contract |

禁止清单（写成测试）：

1. 任何业务模块不得依赖 `agent` / `assistant` / `copilot`（扩展 v1 已有规则）。
2. `assistant` 与 `copilot` **不得互相依赖**；两者只通过 `agent` 的 SPI 在运行期组装。
3. `agent` 不得依赖 `assistant` / `copilot` / 任何业务模块（内核不许知道业务）。
4. `copilot` 只能触达其他模块的 `contract`，不得出现 `*.adapter.*` / `*.domain.*` / `*.application.*`。
5. 顶层模块无环。

> 关键运行期机制：`assistant` 在启动时注入 `List<CapabilityPack>`（Spring 收集），`copilot` 提供 `platform` 包实现，`assistant` 提供 `general` 包实现。编译期互不相识，运行期按作用域路由。

### 1.2 为什么把"接入层"放在 `assistant` 而不是新建 `gateway`

OneBot 回调、Web 会话、消息编排、会话存储、插件运行时都是"消息域"的事，和"平台数据"无关。放 `assistant` 可以让 `copilot` 保持纯能力包（只声明工具和提示词），也避免第 5 个模块。代价是 `assistant` 会成为最大的模块（约 180–220 文件），必须靠 ArchUnit + 包内分层约束。

### 1.3 包内分层（统一约定）

```text
<module>/
├── contract/                  # 跨模块稳定契约（Port / 只读 record / SPI）
├── application/<子域>/        # 用例编排
├── domain/                    # 值对象、规则、领域事件
└── adapter/
    ├── in/<web|onebot>/       # 入站
    └── out/<persistence|ai|http|onebot>/  # 出站
```

`agent` 额外有 `kernel/{prompt,persona,memory,model,tool,render}` 七个内聚子包，但它们仍属 `domain`/`application` 语义，**不引入新分层**。

---

## 2. 专题一：调度（Quartz 落地）

### 2.1 Quartz 的能力边界（先认清两个坑）

Quartz 能直接给的：

- 秒级 6/7 段 Cron（`CronTrigger`），与现有 `0 */10 * * * ?` 写法一致；
- 固定间隔（`SimpleTrigger` / `CalendarIntervalTrigger`）；
- 一次性（`SimpleTrigger` startAt）；
- 错过补偿（misfire 指令 + `misfireThreshold`）；
- 并发禁止（`@DisallowConcurrentExecution`）；
- 集群（`isClustered=true`，DB 行锁抢占，无需自研锁）；
- 日历（`org.quartz.Calendar` + `HolidayCalendar` / `CronCalendar` / `DailyCalendar`）。

两个必须自己处理的坑：

1. **Quartz 日历只能"排除"，不能"追加"**。`HolidayCalendar` 只能让某些日期不触发。而中国日历需要"周末的调休补班日要触发" —— 这是"追加"。解法：**实现自定义 `org.quartz.Calendar`**，`isTimeIncluded(ts)` 直接返回"这一天是否命中我们的日规则"（调休补班 → true，法定节假日 → false），把 `MON-FRI` 的概念整个扔掉。见 2.3。
2. **Quartz 不持久化 Calendar 对象**。`JobStore` 只保存触发器对日历名的引用，日历实例每次启动都要重新 `scheduler.addCalendar(...)`；日历数据变更后要 `addCalendar(name, cal, replace=true, updateTriggers=true)` 才能让已有触发器生效。这一条必须写进启动流程，否则"改了假期没生效"会变成疑难杂症。

### 2.2 `spec` → Quartz 触发器编译

统一规则：**"按日 + 时刻"型一律编译成 `0 m h * * ?`，日期筛选全部交给自定义日历**。这样表达式只有一种形态，不需要为"每月第一个工作日""第 N 个工作日"发明语法。

| `spec.kind` | Quartz 触发器 | 日历 | 说明 |
|---|---|---|---|
| `CRON` | `CronTrigger(cron, tz)` | 无 | 技术型任务（采集、心跳），不碰日历 |
| `INTERVAL` | `SimpleTrigger(startAt, repeatInterval, repeatCount=-1)` | 可选 | 间隔任务；带 `activeWindow` 时由日历做时段筛选 |
| `CALENDAR_TIMES` | 每个时刻一个 `CronTrigger("0 m h * * ?")`，同一 trigger group | **必需** | 播报主力形态；多时刻 = 多触发器 |
| `ONCE` | `SimpleTrigger(startAt, repeatCount=0)` | 可选 | 执行成功后任务自动置停用 |
| `MONTH_NTH_WORKDAY` | `CronTrigger("0 m h * * ?")` + `NthWorkdayDayMatcher` | **必需** | 日历负责"第 N 个工作日"判定 |

`spec` 终稿（与 v1 一致，仅补充 `calendarCode` 必填约束）：

```jsonc
{
  "kind": "CALENDAR_TIMES",
  "timezone": "Asia/Shanghai",
  "calendarCode": "cn-official",
  "dayFilter": { "type": "WORKDAY" },        // ALL|WORKDAY|NON_WORKDAY|WEEKDAYS|MONTH_NTH_WORKDAY|MONTH_LAST_WORKDAY|CUSTOM_DATES
  "times": ["09:00", "18:30"],
  "holidayPolicy": "NEXT_WORKDAY",           // SKIP|NEXT_WORKDAY|PREV_WORKDAY|FORCE
  "activeWindow": { "from": "08:00", "to": "22:30" },
  "pureCron": null,
  "interval": null,
  "once": null
}
```

**为什么要保留 `CRON` 这个 kind**：并不是所有任务都该受假期影响。采集、号池采样必须每 10 分钟跑，节假日更不能停。把"跟随业务日历"和"绝对周期"分成两种 kind，是这套设计最重要的分界线。

### 2.3 中国工作日历 = 自定义 Quartz Calendar

```java
// agent 不参与；scheduling 自己实现
public final class WorkdayQuartzCalendar implements org.quartz.Calendar {

    private final WorkdayCalendarData data;   // 从 scheduling_calendar_day 加载
    private final DayMatcher matcher;         // 由 spec.dayFilter 构造

    @Override
    public boolean isTimeIncluded(long timestamp) {
        LocalDate day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
        return matcher.matches(day, data);
    }

    @Override
    public long getNextIncludedTime(long timestamp) { /* 逐日推进，最多 400 天 */ }
}
```

`DayMatcher` 与 `WorkdayCalendarData` 的关系：

| `dayFilter.type` | 判定 |
|---|---|
| `ALL` | 恒真（等价"自然日"） |
| `WORKDAY` | `day_type ∈ {WORKDAY, MAKEUP_WORKDAY}` |
| `NON_WORKDAY` | `day_type ∈ {WEEKEND, HOLIDAY}` |
| `WEEKDAYS` | 指定星期几（不看法定节假日） |
| `MONTH_NTH_WORKDAY` | 当月第 N 个工作日 |
| `MONTH_LAST_WORKDAY` | 当月最后一个工作日 |
| `CUSTOM_DATES` | 白名单日期集合 |

`day_type` 与判定顺序（与 v1 一致，此处重申为契约）：

1. `MAKEUP_WORKDAY`（调休补班，周末上班）→ **算工作日**
2. `HOLIDAY`（法定节假日）→ 不算
3. `WORKDAY`（工作日，含被强制标为上班的周末）→ 算
4. 未登记 → 周一至周五算工作日，周六日不算

存储策略：`scheduling_calendar_day` **只存例外**（`HOLIDAY` / `MAKEUP_WORKDAY` / 强制 `WORKDAY`），未命中回落星期规则。一年约 30–40 行，导入与人工校订都轻。

`holidayPolicy`（触发器被日历排除时的补偿）：

| 策略 | 行为 | 实现 |
|---|---|---|
| `SKIP` | 跳过，下一次正常 | 纯日历筛选即可 |
| `NEXT_WORKDAY` | 顺延到下一个工作日 | 应用层在"计划触发点被排除"时补一条 `ONCE` 触发器（`planned_at` 标记来源），用 `scheduling_task_run` 唯一键防重 |
| `PREV_WORKDAY` | 提前到上一个工作日 | 同上，方向相反 |
| `FORCE` | 无视日历照发 | 触发器不挂日历 |

> 注意：`SKIP` 才是"日历"的自然语义；顺延/提前本质是"补偿任务"，所以由应用层生成一次性触发器比在日历里伪造时间更可控，也更容易在预览里解释清楚。

### 2.4 执行语义

| 维度 | 落地方案 |
|---|---|
| 任务并发 | `@DisallowConcurrentExecution` + `scheduling_task_run` 里 `RUNNING` 检查（双保险） |
| misfire | `SKIP` → `withMisfireHandlingInstructionDoNothing()`；`FIRE_ONCE` → `...FireAndProceed()`；`CATCH_UP(n)` → Quartz 只保证"最近一次"，**n>1 由应用层扫描 run 表补**（Quartz 不擅长"补 3 次"） |
| 幂等 | `scheduling_task_run` 唯一索引 `(task_id, planned_at) WHERE trigger_source <> 'MANUAL'` |
| 超时 | 应用层 `Future.get(timeout)`；超时写 `TIMEOUT` 并中断线程 |
| 重试 | 默认 0；采集/推送可配 2，指数退避（`retry_at` 字段 + 一次性触发器） |
| 集群 | `spring.quartz.properties.org.quartz.jobStore.isClustered=true`、`instanceId=AUTO`、`clusterCheckinInterval=15000`；现在单实例但配置就绪 |
| 启动对账 | 启动时 DB 任务 ↔ Quartz 触发器双向比对，多退少补（`SchedulerReconciler`），并提供 `/resync` 管理端点 |
| 沉默告警 | 扫描 `next_fire_at + grace < now()` 且仍 `enabled` → 发运维告警（"任务挂了"要能被发现） |
| 观测 | `SchedulerListener` / `TriggerListener` → Micrometer（`relayops_scheduling_runs_total{status}`、`relayops_scheduling_lag_seconds`）+ run 表 |

**Job 里只做三件事**：读任务行 → 校验启用 → 交给处理器。不写业务逻辑，不碰其它表。

```java
@DisallowConcurrentExecution
public class ScheduledTaskJob extends QuartzJobBean {
    @Override protected void executeInternal(JobExecutionContext ctx) {
        long taskId = Long.parseLong(ctx.getMergedJobDataMap().getString("taskId"));
        executionService.run(taskId, ctx.getScheduledFireTime().toInstant(), TriggerSource.SCHEDULE);
    }
}
```

`JobDataMap` 只放 `taskId`（字符串），**绝不放参数对象** —— 避免序列化版本问题与配置漂移；参数永远从 `scheduling_task.params` 现读。

### 2.5 表结构

Quartz 官方 11 张表（`QRTZ_` 前缀，取自 Quartz 2.5.x `tables_postgres.sql`），由 Flyway 管理、`spring.quartz.jdbc.initialize-schema=never`：

```text
QRTZ_JOB_DETAILS  QRTZ_TRIGGERS  QRTZ_SIMPLE_TRIGGERS  QRTZ_CRON_TRIGGERS
QRTZ_SIMPROP_TRIGGERS  QRTZ_BLOB_TRIGGERS  QRTZ_CALENDARS  QRTZ_PAUSED_TRIGGER_GRPS
QRTZ_FIRED_TRIGGERS  QRTZ_SCHEDULER_STATE  QRTZ_LOCKS
```

> `QRTZ_CALENDARS` 表实际不用（日历不持久化），保留是官方脚本的完整性要求。

业务表：

```sql
CREATE TABLE scheduling_calendar (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    timezone    VARCHAR(60)  NOT NULL DEFAULT 'Asia/Shanghai',
    source      VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',   -- MANUAL | IMPORT
    covered_to  DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE scheduling_calendar_day (
    calendar_id BIGINT      NOT NULL REFERENCES scheduling_calendar(id) ON DELETE CASCADE,
    day         DATE        NOT NULL,
    day_type    VARCHAR(20) NOT NULL,                     -- HOLIDAY | MAKEUP_WORKDAY | WORKDAY
    name        VARCHAR(80),
    origin      VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    PRIMARY KEY (calendar_id, day)
);

CREATE TABLE scheduling_task (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_type          VARCHAR(100) NOT NULL,             -- ScheduledTaskHandler.taskType()
    name               VARCHAR(120) NOT NULL,
    kind               VARCHAR(30)  NOT NULL,             -- CRON|INTERVAL|CALENDAR_TIMES|ONCE|MONTH_NTH_WORKDAY
    spec               JSONB        NOT NULL,
    timezone           VARCHAR(60)  NOT NULL DEFAULT 'Asia/Shanghai',
    calendar_code      VARCHAR(60),
    params             JSONB,                             -- 处理器参数（含蓝图引用、工具参数）
    misfire_policy     VARCHAR(20)  NOT NULL DEFAULT 'FIRE_ONCE',
    max_catch_up       SMALLINT     NOT NULL DEFAULT 1,
    concurrency_policy VARCHAR(20)  NOT NULL DEFAULT 'SKIP',
    timeout_seconds    INTEGER      NOT NULL DEFAULT 300,
    retry_max          SMALLINT     NOT NULL DEFAULT 0,
    quiet_hours        JSONB,                             -- ["23:00","07:00"] 播报类用
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    next_fire_at       TIMESTAMPTZ,
    version            INTEGER      NOT NULL DEFAULT 0,
    created_at/updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scheduling_task_run (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id        BIGINT      NOT NULL REFERENCES scheduling_task(id) ON DELETE CASCADE,
    planned_at     TIMESTAMPTZ NOT NULL,
    trigger_source VARCHAR(20) NOT NULL,                  -- SCHEDULE|MANUAL|CATCH_UP|RETRY|HOLIDAY_SHIFT
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL,                  -- RUNNING|SUCCESS|FAILED|TIMEOUT|SKIPPED
    duration_ms    INTEGER,
    message        TEXT,
    detail         JSONB                                  -- 各工具段耗时/结果摘要（供排查）
);

CREATE UNIQUE INDEX scheduling_task_run_idem
    ON scheduling_task_run (task_id, planned_at) WHERE trigger_source <> 'MANUAL';
CREATE INDEX scheduling_task_due_idx ON scheduling_task (next_fire_at) WHERE enabled;
```

### 2.6 API 与前端

```text
GET    /api/v1/scheduling/tasks               列表（含 nextFireAt、最近运行摘要）
POST   /api/v1/scheduling/tasks               创建（spec + JSON Schema + 日历存在性校验）
PUT    /api/v1/scheduling/tasks/{id}
POST   /api/v1/scheduling/tasks/{id}/toggle
POST   /api/v1/scheduling/tasks/{id}/run      立即执行（MANUAL）
POST   /api/v1/scheduling/tasks/{id}/preview  仅预览：不发消息，把渲染结果回给管理员（dry-run）
GET    /api/v1/scheduling/tasks/{id}/runs     执行历史（含 detail）
POST   /api/v1/scheduling/spec/preview        spec + calendar → 未来 10 次触发时间
GET    /api/v1/scheduling/calendars[/{code}/days]
PUT    /api/v1/scheduling/calendars/{code}/days
POST   /api/v1/scheduling/calendars/{code}/import
POST   /api/v1/scheduling/resync              DB ↔ Quartz 对账
GET    /api/v1/scheduling/types               任务类型 + 参数 Schema
```

前端（`ScheduledTasksView` 进化版）四个关键交互：

1. **频率构造器**：`Cron / 间隔 / 按日+时刻 / 一次性 / 每月第 N 个工作日` 五个 Tab，切换即生成 spec。
2. **未来 10 次预览**：任何改动实时调 `/spec/preview`，被顺延的条目标注"国庆顺延"。
3. **消息编排器**（看 3.6）：定时消息的任务详情里是分段式编辑器，不是一个大文本框。
4. **执行历史抽屉**：来源（`SCHEDULE/MANUAL/CATCH_UP/HOLIDAY_SHIFT`）、耗时、每段工具调用的成功/失败与耗时。

### 2.7 验收

- [ ] 6 类 kind 全部可创建、可预览、可执行；`INTERVAL` 带 `activeWindow` 时窗口外不触发。
- [ ] 2026 年真实日历：国庆不跑、10-10（周六补班）要跑、春节顺延只补一次。
- [ ] 应用停机 2 小时后启动：只补最近一次，不刷屏。
- [ ] 两台实例同时启动：无重复执行（Quartz 集群锁 + 唯一索引）。
- [ ] 日历数据修改后，已有触发器的下一次触发时间随之改变（`addCalendar(replace=true, updateTriggers=true)` 生效）。
- [ ] 页面能解释"为什么这次没跑"（`SKIPPED` + 原因：节假日 / 静默时段 / 并发中）。

---

## 3. 专题二：机器人（大肥鱼式角色 + 插件 + 定时消息）

### 3.1 "大肥鱼"给我们的设计启示

monitor 里已经有 `V44__set_dafeiyu_default_persona.sql`：一段「蓝色小鲸鱼娘、嘴硬心软、一到三句短句、群聊先判断话题是否与你有关」的超长人设，配 5 种群回复策略（`MENTION / PROACTIVE / RANDOM / KEYWORD / AI_DECIDE`）和"每 10 轮滚动摘要"。它爆火的不是因为模型强，而是三件事做对了：

1. **角色稳定**：有形象、性格、聊天节奏、关系处理、记忆与情绪、真实边界的**结构化约束**，而不是一句"你是一个可爱的助手"。
2. **群聊像人**：能判断"这话跟不跟我有关"（`AI_DECIDE`）、回复短、不追问、不复读。
3. **有玩法**：抽奖、生图、天气、语音，以及社区常见的签到/钓鱼/抽卡类小游戏。

所以 v2 把这三件事做成**产品能力**，而不是一段提示词：

| 体验 | 落成什么 |
|---|---|
| 角色稳定 | **角色卡（Persona Card）**：6 个结构化段落 + 示例 + 风格开关 + 音色 + 版本 |
| 关系分层 | **关系模型**：`STRANGER / GROUP_MATE / FRIEND / CLOSE`，称呼与亲密度由关系驱动，不许对所有人叫"主人" |
| 群聊像人 | **接话判定器**：独立的小模型调用，只输出"接/不接 + 理由"，带冷却与频率预算 |
| 有玩法 | **插件**：签到、钓鱼、抽卡、天气卡、表情包、群规问答，都可声明式定义 |
| 陪伴感 | **记忆**：L1 窗口 + L2 摘要 + L3 事实（谁说过什么、喜欢什么）+ L4 业务事实 |

### 3.2 三模块职责与能力包 SPI

| 模块 | 负责 | 不负责 |
|---|---|---|
| `agent` | 角色卡解析与分层装配、记忆读写与召回、提示词段落、模型路由与降级、工具注册表与权限谓词、渲染（表格/图片/文本）、能力包 SPI | 任何业务数据、任何通道协议 |
| `assistant` | OneBot/Web 接入、准入与路由、命令分发、通用能力包、消息编排（Blueprint）、归档、插件运行时、会话存储 | 平台数据查询（只通过 `copilot` 包或 `contract`） |
| `copilot` | 平台能力包：查询工具、诊断、日报/播报蓝图、口径与脱敏规则 | 通道、会话、记忆存储、模型调用（都由 `agent`/`assistant` 提供） |

```java
// agent.contract
public interface CapabilityPack {
    String id();                                  // "general" | "platform" | "plugin:<key>"
    PackDescriptor describe();                    // 名称、适用通道、所需角色、优先级
    List<PromptSection> prompt(PromptContext ctx);        // 提示词段落（含受保护段）
    List<ToolSpec> tools(ToolContext ctx);                // 按身份/权限/通道裁剪
    boolean supports(ScopeRef scope);
    default List<BlueprintSegmentHandler> segments() { return List.of(); }  // 自定义消息段
}
```

`ToolSpec` 元数据（**插件与定时消息都靠它做安全判定**）：

```java
public record ToolSpec(
        String id,                    // "platform.pool.summary" / "plugin:fishing:cast"
        String title, String description,
        JsonSchema inputSchema,       // 前端据此渲染参数表单
        SideEffect sideEffect,        // READ_ONLY | WRITE | DANGEROUS
        boolean schedulable,          // 是否允许被定时消息引用
        PermissionPredicate permission,   // ADMIN / SELF_ONLY / PLATFORM_SCOPE / PLUGIN_GRANT
        RedactionPolicy redaction,        // NONE / CHANNEL（群聊强制）
        Duration timeout, int maxResultBytes,
        ToolHandler handler
) {}
```

沿用 monitor 三条已验证的安全设计：**工具级隔离**（平台工具只挂平台身份）、**每请求构造**（用户级工具注入身份，方法不接受"查谁"）、**输出脱敏在返回值处执行**（不靠提示词）。

### 3.3 角色卡（Persona Card v2）

把 monitor 那段 70 行提示词拆成结构化字段，既能被页面编辑，也能被插件继承/覆盖：

```sql
CREATE TABLE assistant_persona (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(60)  NOT NULL UNIQUE,
    name                VARCHAR(80)  NOT NULL,
    description         VARCHAR(300),

    -- 角色卡 6 段（都可空，空则跳过）
    appearance_prompt   TEXT,          -- 形象
    personality_prompt  TEXT,          -- 性格
    rhythm_prompt       TEXT,          -- 聊天节奏（长度、语气词、是否追问）
    relation_prompt     TEXT,          -- 关系与群聊（怎么称呼不同关系等级）
    emotion_prompt      TEXT,          -- 记忆与情绪（怎么用记忆、怎么表达情绪）
    boundary_prompt     TEXT,          -- 真实与角色（AI 身份、不编造、不越权）

    style_rules         JSONB,         -- 结构化风格开关：maxSentences/emojiPerTurn/askBack/...
    examples            JSONB,         -- few-shot：[{user, assistant}]
    address_rules       JSONB,         -- {"CLOSE":"昵称或亲昵称呼","GROUP_MATE":"昵称"}
    forbidden_phrases   JSONB,
    voice_id            VARCHAR(80),
    packs               JSONB NOT NULL DEFAULT '["general"]',
    extends_id          BIGINT REFERENCES assistant_persona(id),
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    version             INTEGER NOT NULL DEFAULT 1,
    created_at/updated_at
);

CREATE TABLE assistant_relation (            -- 关系分级：陪伴感的根
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type  VARCHAR(20) NOT NULL,        -- USER
    scope_key   VARCHAR(120) NOT NULL,       -- qq:user:123
    level       VARCHAR(20) NOT NULL,        -- STRANGER | GROUP_MATE | FRIEND | CLOSE
    nickname    VARCHAR(80),
    note        VARCHAR(300),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (scope_type, scope_key)
);
```

装配顺序（`PromptStack`，与 v1 一致，此处为最终版）：

```text
[SAFETY]        不可覆盖：不越权、不编造、不泄露
[CAPABILITY]    能力包声明：口径、工具规则、输出格式、脱敏
[PACK_STYLE]    能力包表达偏好（平台播报偏结构化，闲聊偏口语）
[PERSONA]       角色卡 6 段 + 风格开关 + 示例（可覆盖槽位）
[MEMORY]        摘要 + 召回事实（带来源与时间）
[SESSION]       当前会话（说话人标签、引用消息、关系等级）
[USER]          本轮消息
```

页面必须提供**提示词全文预览**：管理员看到的就是真正发给模型的文本，包含角色卡段落、记忆召回内容与脱敏后的工具说明。这一条能挡掉 80% 的"人设不生效"投诉。

### 3.4 记忆（四层 + pgvector + 阿里云百炼）

| 层 | 内容 | 存储 | 生命周期 |
|---|---|---|---|
| L1 工作记忆 | 最近 N 轮原文 | Redis（现有 `BotGroupContextService` 思路）+ 可选 PG | 会话内，重启不丢 |
| L2 情节记忆 | 消息归档 + 每 10 轮滚动摘要 | `assistant_message` / `assistant_memory_summary` | 30–90 天 |
| L3 语义记忆 | 用户偏好、群约定、共同经历 | `assistant_memory_fact` + `assistant_memory_embedding` | 长期，可 TTL |
| L4 业务记忆 | 平台侧"上次结论/上次异常/当前口径" | 同表 `namespace='platform'` | 短 TTL，由任务/采集写入 |

**向量放侧表，不放在事实表上** —— 维度是"列类型的一部分"，跟事实耦合会让换模型变成改表 + 全量重算。侧表让"事实"与"向量"解耦，可以多模型并存、无停机重建。

```sql
CREATE TABLE assistant_memory_fact (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    namespace    VARCHAR(20) NOT NULL,          -- platform | general | shared
    scope_type   VARCHAR(20) NOT NULL,          -- USER | GROUP | CONVERSATION
    scope_key    VARCHAR(120) NOT NULL,
    category     VARCHAR(40) NOT NULL,          -- preference|profile|convention|business|relation
    fact_key     VARCHAR(120),
    fact_value   TEXT        NOT NULL,
    confidence   NUMERIC(3,2) NOT NULL DEFAULT 0.80,
    source       VARCHAR(20) NOT NULL,          -- EXPLICIT | DERIVED | LLM_EXTRACT
    provenance   JSONB,
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX assistant_memory_fact_scope_idx
    ON assistant_memory_fact (namespace, scope_type, scope_key, category);

CREATE TABLE assistant_memory_embedding (
    fact_id     BIGINT NOT NULL REFERENCES assistant_memory_fact(id) ON DELETE CASCADE,
    model       VARCHAR(60) NOT NULL,           -- text-embedding-v4 / qwen3.7-text-embedding-flash
    dim         SMALLINT    NOT NULL,           -- 实际返回维度，写入时校验
    embedding   vector,                         -- 不写死维度：先跑起来，定了再建索引
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (fact_id, model)
);
-- 维度稳定后再收紧为 vector(1024) 并建索引：
-- ALTER TABLE assistant_memory_embedding ADD COLUMN embedding_v4 vector(1024);
-- CREATE INDEX ... USING hnsw (embedding_v4 vector_cosine_ops);
```

> `vector` 不写维度时不能建 HNSW 索引，只能顺序扫描。几千到几万条事实在 PG 里是毫秒级，**足够用**；等维度稳定再收紧。

#### 3.4.1 模型选型（D7 已定：阿里云百炼）

百炼当前的文本向量模型（官方文档：向量与重排序）：

| 模型 | 维度 | 单条最大 Token | 适用 |
|---|---|---|---|
| `qwen3.7-text-embedding` | 256~2560（默认 1024） | 131,072 | 长文本、精度优先 |
| `qwen3.7-text-embedding-flash` | 256~1024（默认 1024） | 131,072 | 成本与吞吐优先 |
| `text-embedding-v4` | 64~2048（默认 1024） | 8,192 | 通用推荐 |
| `text-embedding-v3` | 512~1024 | 8,192 | 已有 v3 索引迁移 |

**选型结论：`text-embedding-v4` + `1024` 维。** 理由：本场景事实都是短句（几十字），8192 token 的窗口绰绰有余；1024 维是官方"通用场景"默认值；v4 是文档明确推荐的纯文本模型，稳定性与示例最全。`qwen3.7-text-embedding-flash` 作为降本备选（同 1024 维，换模型只需重跑 embed，不动表）。

可选增强（后置）：召回后加 `qwen3-rerank` 重排 top-20 → top-5，精度能再上一档，但多一次调用延迟，先不做。

#### 3.4.2 接入方式与配置

走**百炼的 OpenAI 兼容接口**，复用 Spring AI 已有的 `OpenAiEmbeddingModel`，不引第二个 SDK：

```yaml
relay-ops:
  ai:
    embedding:
      enabled: ${BAILIAN_EMBEDDING_ENABLED:true}
      base-url: ${BAILIAN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
      # 新版工作空间专属域名：https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
      workspace-id: ${BAILIAN_WORKSPACE_ID:}
      api-key: ${DASHSCOPE_API_KEY:}
      model: ${BAILIAN_EMBEDDING_MODEL:text-embedding-v4}
      dimensions: ${BAILIAN_EMBEDDING_DIMENSIONS:1024}
      batch-size: ${BAILIAN_EMBEDDING_BATCH_SIZE:16}
      timeout: ${BAILIAN_EMBEDDING_TIMEOUT:10s}
      query-cache-size: 5000        # 同一句话重复 embed 的本地缓存
```

实现要点：

1. **独立 `EmbeddingModel` Bean**，不要和聊天模型抢 `spring.ai.openai.*` —— 聊天可能接的是另一个供应商。
2. **维度自检**：启动时 embed 一条探针文本，校验返回长度 == 配置维度；不一致**直接禁用向量召回并告警**，绝不写入错维数据。
3. **批量 + 重试**：事实写入走批量 embed（一次 16 条），失败按指数退避重试 2 次，仍失败则先落事实、向量留空（后续 backfill 补）。
4. **降级**：百炼不可用时召回自动退回"关键词 + 作用域"模式，聊天主流程不报错。
5. **密钥**：`DASHSCOPE_API_KEY` 只从环境变量读，不入库、不进日志（monitor 的语音模块已是这个约定）。

> 顺带：monitor 的 ASR/TTS 已经用百炼（Qwen-Audio + 工作空间 ID），本项目的语音能力和 embedding 可以共用同一个 `DASHSCOPE_API_KEY` 与工作空间，少一套凭证。

#### 3.4.3 召回与写入策略

召回：`hybrid = 向量 top-20 ∪ 关键词 top-20 → 去重 → 按 (相似度×0.6 + 置信度×0.2 + 时效×0.2) 排序 → 取 top-5`，总预算 800 token；`namespace` 过滤是硬条件（平台事实永不进闲聊）。

写入三层策略：`EXPLICIT`（`/记住`、后台编辑）直接写；`DERIVED`（日报后写"上次结论"）确定性写；`LLM_EXTRACT`（从对话提炼偏好）进候选池，低风险自动、涉身份/权限一律拒绝。

硬禁令：不存密钥、不存他人邮箱明文、"某人是否管理员"永不入记忆（每次实时判定）。

**部署要求**：镜像用 pgvector 官方镜像（`compose.yaml` 当前为 `pgvector/pgvector:pg18-trixie`），`V4__vector_extension.sql` 负责建扩展；向量维度由百炼返回并写入 `assistant_memory_embedding.dim`，启动自检不一致就禁用向量召回（见 3.4.2）。

### 3.5 插件系统

目标：**管理员不写 Java 也能给机器人加东西** —— 人设、触发词、外部工具、玩法、播报模板。这是"大肥鱼生态"能滚起来的原因，也是这次要一次设计对的地方。

#### 3.5.1 三种插件形态（同一份清单，不同 runtime）

| 形态 | 内容 | 能力上限 | 建议 |
|---|---|---|---|
| `DECLARATIVE` | `plugin.json` + 资源文件（图片/音色/示例）+ 模板 + HTTP 工具声明 | 声明式命令、HTTP 工具、模板、人设、玩法状态机（有限） | **默认，先做这个** |
| `SCRIPT` | 上面 + 受限 JS（GraalJS） | 条件分支、随机、状态机、组合调用 | 阶段二，仅可信作者 |
| `JAVA` | SPI jar + 隔离 ClassLoader | 任意 | 只给自研扩展，不建议开放 |

三者共用：安装、校验、作用域绑定、权限授予、审计、版本回滚。差异只在 `runtime` 字段与执行器。

#### 3.5.2 清单（manifest）

```yaml
id: fishing
name: 大肥鱼钓鱼
version: 1.0.0
apiVersion: 1
runtime: declarative
author: 站长
description: 群友钓鱼、卖鱼、攒好感度
requires:
  packs: [general]
  permissions:
    - memory.read:plugin:fishing
    - memory.write:plugin:fishing
    - image.render
    - http.outbound:api.example.com
personaPatch:                     # 可选：给绑定它的角色补充段落（不能覆盖受保护段）
  emotionPrompt: |
    你会记得群友上次钓到了什么，偶尔提一句。
commands:
  - name: 钓鱼
    match: { type: regex, pattern: '^钓鱼$' }
    cooldown: 30s
    action:
      type: script                 # 或 template / tool / http
      script: |
        const bag = ctx.memory.get("bag") || {fish: 0};
        bag.fish += 1;
        ctx.memory.put("bag", bag);
        ctx.reply(`钓到一条小鱼，今天第 ${bag.fish} 条。`);
tools:
  - id: cast
    title: 钓鱼
    sideEffect: READ_ONLY
    schedulable: false
    http:
      method: GET
      url: https://api.example.com/fish
      timeout: 5s
      response: { path: "$.data", maxBytes: 65536 }
templates:
  - id: daily-fishing
    title: 每日渔获
    body: "今日全群钓到 {{tools.cast.total}} 条鱼"
```

#### 3.5.3 生命周期与安全边界

```text
上传 → 静态校验 → 安装（默认 disabled）→ 授予权限（逐条展示）→ 绑定作用域（全局/群/用户）
     → 启用 → 运行（审计）→ 升级 / 回滚 / 禁用 / 卸载
```

静态校验（安装时，不通过直接拒绝）：

1. schema 校验 + `apiVersion` 兼容性；
2. 工具 `id` 不得侵占 `platform.*` / `general.*` 命名空间；
3. `match.pattern` 正则复杂度与回溯风险（长度、嵌套量词）；
4. 模板变量白名单（只允许 `{{tools.*}}`、`{{args.*}}`、`{{now}}` 等）；
5. `http` 域名必须在 `permissions` 里声明（防 SSRF：内网网段、`localhost`、云元数据地址一律拒绝）；
6. 不得声明 `tool.invoke:platform.*`（**平台能力不可被插件调用**）。

运行时约束：

| 风险 | 约束 |
|---|---|
| 密钥泄漏 | 插件只能引用 `secretRef`，值存 `assistant_plugin_secret`（加密），不落插件包、不进日志 |
| 任意代码 | `SCRIPT` 运行在 GraalJS：无文件/网络/线程 API，仅 host API；CPU 200ms、内存 16MB、输出 32KB 上限 |
| 绕过权限 | 插件只能"请求"工具；权限谓词与脱敏在 `agent` 的 `ToolRegistry` 统一执行，插件无法决定 |
| 记忆污染 | 插件只能读写 `namespace = plugin:<id>`，看不见 `platform`/`shared` |
| 无限调用 | 每插件每分钟调用上限、每会话冷却、全局并发上限；超限降级为固定话术 |
| 升级破坏 | 版本不可变（`assistant_plugin_release`），安装指向 active release，回滚=切指针 |

`DECLARATIVE` 形态能做到"玩法"的原因：提供**受限状态机动作**（`counter / bag / cooldown / random / choice`）作为声明式原语，覆盖签到、钓鱼、抽卡、好感度这类 80% 的群玩法，不需要真脚本。

#### 3.5.4 表结构

```sql
CREATE TABLE assistant_plugin (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plugin_key   VARCHAR(60) NOT NULL UNIQUE,
    name         VARCHAR(120) NOT NULL,
    runtime      VARCHAR(20) NOT NULL,            -- DECLARATIVE | SCRIPT | JAVA
    active_release_id BIGINT,
    status       VARCHAR(20) NOT NULL,            -- INSTALLED|ENABLED|DISABLED|BROKEN
    granted_permissions JSONB NOT NULL DEFAULT '[]',
    created_at/updated_at
);

CREATE TABLE assistant_plugin_release (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plugin_id   BIGINT NOT NULL REFERENCES assistant_plugin(id) ON DELETE CASCADE,
    version     VARCHAR(40) NOT NULL,
    manifest    JSONB NOT NULL,
    bundle      BYTEA,                            -- 资源 zip（图片/音色）
    checksum    VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (plugin_id, version)
);

CREATE TABLE assistant_plugin_scope (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plugin_id  BIGINT NOT NULL REFERENCES assistant_plugin(id) ON DELETE CASCADE,
    scope_type VARCHAR(20) NOT NULL,              -- GLOBAL | GROUP | USER
    scope_key  VARCHAR(120) NOT NULL DEFAULT '*',
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    config     JSONB,
    UNIQUE (plugin_id, scope_type, scope_key)
);

CREATE TABLE assistant_plugin_invocation (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plugin_id    BIGINT NOT NULL,
    kind         VARCHAR(20) NOT NULL,            -- COMMAND | TOOL | SEGMENT | SCRIPT
    target       VARCHAR(120),
    scope_key    VARCHAR(120),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    duration_ms  INTEGER,
    status       VARCHAR(20) NOT NULL,            -- OK | FAILED | DENIED | TIMEOUT
    error        TEXT,
    detail       JSONB
);
```

### 3.6 定时消息的消息编排（Blueprint）

这是本轮新增的第二个重点：**定时消息不是"一段文本"，而是"分段编排"，每段可以拉一个工具的实时结果，并且可以指定参数。**

#### 3.6.1 蓝图结构

```jsonc
{
  "id": 12,
  "code": "platform.pool.daily",
  "name": "号池日报",
  "packId": "platform",
  "personaId": 3,
  "segments": [
    { "type": "text",  "template": "【{{date}} 号池日报】共 {{tools.pool.total}} 个渠道" },

    { "type": "tool",  "ref": "platform.pool.summary",
      "as": "pool",
      "args": { "window": "24h", "sort": "requests", "top": 5 },
      "render": "table", "title": "流量 TOP5",
      "onError": "DEGRADE", "cacheTtl": "5m" },

    { "type": "tool",  "ref": "platform.account.abnormal",
      "as": "abnormal", "args": {},
      "render": "list", "title": "异常账号", "onEmpty": "HIDE" },

    { "type": "tool",  "ref": "platform.channel.status",
      "as": "channels",
      "args": { "window": "{{args.window}}", "group": "{{args.group}}" },   // 参数可来自任务参数
      "render": "table", "title": "分组健康" },

    { "type": "image", "renderer": "pool.heatmap", "source": "{{tools.pool.rows}}" },

    { "type": "persona_note", "prompt": "用一句话点评今天的号池，不超过 30 字" }
  ],
  "options": {
    "timeout": "60s",
    "fallback": "TEXT",
    "onToolError": "DEGRADE",
    "maxToolCalls": 8,
    "redaction": "CHANNEL",
    "suppressDuplicatesWithin": "PT6H"
  }
}
```

#### 3.6.2 语义定义（这些细节决定它好不好用）

| 维度 | 规则 |
|---|---|
| 执行顺序 | 默认按 `segments` 顺序；同一 `parallelGroup` 的相邻工具段并发；每段有独立超时 |
| 变量命名空间 | `{{args.*}}` 任务参数、`{{now/date/today/yesterday}}` 时间、`{{tools.<as>.*}}` 前置工具结果（受限 JSONPath，禁函数调用） |
| 参数来源 | 字面量、任务参数、时间变量、前置工具结果（支持"把上一步的账号 ID 传给下一步"） |
| 工具准入 | 只能引用 `sideEffect = READ_ONLY` **且** `schedulable = true` 的工具；写操作（自愈、充值、改倍率）**结构上不可能**被定时消息引用 |
| 权限 | 以任务绑定身份执行（系统 / 指定管理员）；调用仍走 `ToolRegistry` 的权限谓词，**不因为是定时任务就提权** |
| 脱敏 | 强制 `CHANNEL` 策略：群播报按群通道脱敏，不因"是管理员创建的"而豁免 |
| 目标校验 | `platform` 包的蓝图只能发到平台群/管理员私聊；目标不匹配直接拒绝保存（防"平台日报误发到闲聊群"） |
| 失败 | `SKIP`（丢该段留占位）/ `DEGRADE`（降级纯文本）/ `ABORT`（整条不发并告警），可在段级与全局级分别设置 |
| 空结果 | `HIDE`（不渲染标题）/ `PLACEHOLDER`（显示"暂无异常"） |
| 缓存 | `cacheTtl` 防止多个任务重复打上游；同一次播报内相同 `ref+args` 只调一次 |
| 去重 | `suppressDuplicatesWithin`：内容 hash 相同且窗口内已发过则跳过（防"补跑导致重复播报"） |
| 成本上限 | `maxToolCalls`、总超时、图片尺寸/张数上限；超限按 `DEGRADE` |
| 预览 | **保存与启用前必须能 dry-run**：`/tasks/{id}/preview` 用真实工具生成一条草稿发给操作者本人，不投递目标群 |
| 落记忆 | 播报内容作为 `ASSISTANT` 消息写入目标会话（可在 `memory_policy` 关闭），支持"群里接着追问刚才那份日报" |

#### 3.6.3 与调度、投递的连接

```text
Quartz Trigger 触发
   └─ ScheduledTaskJob → ExecutionService
        └─ handler = "assistant.broadcast"（在 assistant 模块）
             ├─ 读 scheduling_task.params → blueprintId + 目标 + 覆盖参数
             ├─ assistant.application.compose: 执行 segments（工具 → agent.ToolRegistry；图片 → agent.render）
             ├─ agent 记忆：召回（用于 persona_note 段）/ 写入（播报内容）
             └─ notification.NotificationPort 投递（QQ / 邮件 / Webhook，统一重试与限流）
```

依赖方向：`assistant → scheduling.contract`（实现 handler），`scheduling` 不认识 `assistant`。这样"定时消息"对调度来说只是又一个任务类型。

#### 3.6.4 前端：分段式编排器

不做"一个大的 Markdown 输入框"，而是：

```text
[+ 文本] [+ 工具结果] [+ 图片] [+ 一句话点评]   ← 段类型
─────────────────────────────────────────
 1  文本      【{{date}} 号池日报】…            ✎ ✕
 2  工具      platform.pool.summary            ✎ ✕
              window=24h  sort=requests  top=5
              渲染=表格  onError=降级
 3  工具      platform.account.abnormal        ✎ ✕
...
[预览草稿]  [保存]  [启用]
```

"工具"下拉的数据来自 `GET /api/v1/assistant/tools?pack=platform&schedulable=true`，选中后**按 `inputSchema` 自动渲染参数表单**，不需要前端为每个工具写死 UI。

### 3.7 其余表（会话/消息/摘要/绑定/蓝图）

```sql
CREATE TABLE assistant_profile (               -- 作用域绑定：用哪套能力与人设
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type    VARCHAR(20) NOT NULL,        -- GLOBAL|CHANNEL|GROUP|USER
    scope_key     VARCHAR(120) NOT NULL,
    pack_id       VARCHAR(30) NOT NULL,
    persona_id    BIGINT REFERENCES assistant_persona(id),
    model_profile VARCHAR(60),
    memory_policy JSONB,                       -- 窗口大小、摘要间隔、召回开关、播报写记忆
    guard_policy  JSONB,                       -- requireMention、白名单、接话预算
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (scope_type, scope_key, pack_id)
);

CREATE TABLE assistant_conversation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type VARCHAR(20) NOT NULL, scope_key VARCHAR(120) NOT NULL,
    pack_id VARCHAR(30) NOT NULL, persona_id BIGINT, persona_version INTEGER,
    last_message_at TIMESTAMPTZ, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    UNIQUE (scope_type, scope_key, pack_id, persona_id, persona_version)
);

CREATE TABLE assistant_message (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES assistant_conversation(id) ON DELETE CASCADE,
    seq BIGINT NOT NULL, role VARCHAR(16) NOT NULL,
    speaker_id VARCHAR(64), speaker_name VARCHAR(80),
    content TEXT NOT NULL, content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    tokens INTEGER, redacted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, seq)
);

CREATE TABLE assistant_memory_summary (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES assistant_conversation(id) ON DELETE CASCADE,
    upto_message_id BIGINT NOT NULL, summary TEXT NOT NULL,
    tokens INTEGER, model VARCHAR(80), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE assistant_blueprint (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL,
    pack_id VARCHAR(30) NOT NULL, persona_id BIGINT,
    segments JSONB NOT NULL, options JSONB NOT NULL DEFAULT '{}',
    enabled BOOLEAN NOT NULL DEFAULT TRUE, version INTEGER NOT NULL DEFAULT 1,
    created_at/updated_at
);
```

### 3.8 验收

**分离与安全**

- [ ] ArchUnit：`assistant` ↔ `copilot` 零依赖；`copilot` 仅 import 其他模块 `contract`；`agent` 不 import 业务模块。
- [ ] 访客在命令、工具、提示词、记忆召回四条路径上都拿不到平台信息。
- [ ] 平台蓝图无法保存到非平台群（目标校验拒绝）。

**角色与体验**

- [ ] 大肥鱼角色卡可从页面编辑；"提示词全文预览"包含 6 段 + 示例 + 召回记忆。
- [ ] 群里 @ 与非 @ 两套策略都生效；`AI_DECIDE` 有频率预算（每小时最多主动 N 次，冷却生效）。
- [ ] 关系等级从 `STRANGER` 升到 `CLOSE` 后称呼随之变化；不会对全体群友用亲昵称呼。

**记忆**

- [ ] 重启后 L1 不丢；L2 摘要可查；`/记住` `/忘记` 生效；平台事实不被闲聊召回。
- [ ] 向量召回与纯关键词召回结果可对比（`/debug recall` 给管理员看）。

**插件**

- [ ] 上传一个 `.zip` 声明式插件（含 1 个命令 + 1 个 HTTP 工具 + 1 个人设补丁），安装后可用、可禁用、可回滚。
- [ ] 插件尝试声明 `tool.invoke:platform.*` 或内网 HTTP 地址，安装被拒绝。
- [ ] 插件超时/死循环被中断，记 `TIMEOUT` 审计，不影响其他群。

**定时消息**

- [ ] 蓝图含 3 个工具段（含参数）+ 1 张图 + 1 段点评，`preview` 能在私聊收到完整草稿。
- [ ] 工具段参数可引用 `{{args.*}}` 与前一工具结果；非法 JSONPath 在保存时被拒绝。
- [ ] 写操作工具不出现在可选列表；强行构造的引用在保存时被拒绝。
- [ ] 工具全部失败时按 `DEGRADE` 发出纯文本版，并在 run.detail 里记录失败原因。
- [ ] 播报后可追问"刚才那个结论再说一遍"，能引用到播报内容。

---

## 4. 里程碑（路线乙）

| 里程碑 | 内容 | 人日 |
|---|---|---|
| **M0** 决策与基线 | D1–D12 定稿；三条依赖规则进 ArchUnit（先红后绿）；表名/前缀评审 | 3 |
| **M1** Quartz 调度内核 | 依赖与配置、`QRTZ_*` Flyway 脚本、`spec→trigger` 编译器、`ScheduledTaskJob`、执行器（并发/超时/重试/幂等）、run 表、启动对账、观测 | 12–15 |
| **M2** 工作日历 | `WorkdayQuartzCalendar` + `DayMatcher`、日历表与导入/校订、顺延补偿、未来 10 次预览 | 5–6 |
| **M3** 三模块拆分 + agent 内核 | 模块骨架与规则、角色卡模型与装配、模型路由与降级、`ToolRegistry` + `ToolSpec` + 权限谓词、`CapabilityPack` SPI | 12–15 |
| **M4** 记忆 | L1（Redis/PG）、L2 归档+摘要任务、L3/L4 事实表、pgvector 召回与混合排序、写入策略、隐私接口 | 12–16 |
| **M5** 插件系统 | manifest 与校验、安装/作用域/权限/回滚、声明式命令与状态机原语、HTTP 工具、模板、审计、管理页 | 15–20 |
| **M6** 消息编排 | `assistant_blueprint`、段执行器、变量与 JSONPath、工具准入与脱敏、失败策略与去重、dry-run、投递 | 8–10 |
| **M7** 平台能力迁移（copilot） | 只读工具（平台/号池/账号/变更/用量/异常/渠道/错误/监控）、命令路由、口径与脱敏 | 15–20 |
| **M8** 大肥鱼式群聊体验 | 关系分级、接话判定器、主动回复预算与冷却、音色、图片渲染策略、群聊策略页 | 8–12 |
| **M9** 迁移与切流 | monitor 数据迁移（任务/人设/设置/归档）、双跑影子、灰度、回滚、文档 | 6–8 |
| | **合计** | **96–125** |

推荐顺序（先打通一条最完整的竖切）：

```text
M0 → M1 → M2 → M3 → M6（一条"工作日上午 9 点号池日报"跑通）
   → M4（让播报能被追问）→ M7（把平台工具补全）
   → M5（插件，可与 M7 并行）→ M8（群聊体验）→ M9
```

理由：M6 会把 M1/M2/M3 的接口全部撞一遍，早撞早改；插件（M5）依赖 `ToolRegistry` 与蓝图段机制，放在 M6 之后能直接复用。

---

## 5. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| Quartz 日历不持久化 + 忘记重注册 | "改了假期没生效" | 启动流程强制 `registerAllCalendars()`；改数据后 `addCalendar(replace=true, updateTriggers=true)`；集成测试覆盖"改假期→下次触发变化" |
| Quartz 表与业务迁移混在一个 Flyway 历史 | 升级冲突 | `QRTZ_*` 用单独迁移文件并锁定版本；禁用 `initialize-schema`；升级 Quartz 前先跑表结构 diff |
| `spec` 编排能力过强 | 用户写出无法解释的调度 | 前端只允许构造器生成；后端用 JSON Schema + 枚举校验；保存前强制预览 |
| 插件引入任意 HTTP | SSRF / 数据外泄 | 域名白名单 + 内网/元数据地址黑名单 + 出站超时与体积上限 + 密钥只走 ref |
| 脚本插件逃逸 | 任意代码执行 | GraalJS 沙箱、无 IO/网络上下文、CPU/内存/输出限额、默认不开放 `SCRIPT` |
| 定时消息把平台数据发错群 | 数据泄漏 | 保存时校验 `blueprint.pack` 与目标作用域；强制通道脱敏；`preview` 强制 |
| 工具段让播报变慢/变贵 | 触发超时、刷上游 | `maxToolCalls`、`cacheTtl`、同播报内同参去重、总超时 + `DEGRADE` |
| 百炼 embedding 接口不可用/限流 | 写入无向量、召回退化 | 事实先落库、向量留空；召回自动退回关键词+作用域；后台 backfill 任务补 embed；批量+退避重试 |
| pgvector 维度与模型不匹配 | 召回全错 | 侧表存 `model`/`dim`，启动探针自检，维度不符拒绝写入并告警；换模型走"新增 model 行 + 重建索引" |
| 记忆写入隐私 | 合规 | 硬禁令 + 保留期 + `/忘记` + 管理员导出/删除 |
| 三模块拆分后 `assistant` 膨胀 | 回归难维护 | 包内分层 + ArchUnit 子包规则（如 `assistant.adapter.in.onebot` 不得依赖 `assistant.application.compose` 之外） |

---

## 6. 需要你确认的剩余决策

| # | 问题 | 选项 | 推荐 |
|---|---|---|---|
| **D1** | 插件形态先做到哪一档 | 仅声明式 / **声明式 + 后续脚本** / 直接含脚本 | **声明式优先**，`SCRIPT` 留接口但不开放 |
| **D2** | 插件能否访问外部 HTTP | 禁止 / **允许（域名白名单）** / 允许任意 | **白名单** |
| **D3** | 插件能否调用平台工具 | **不可** / 只读可 / 全部可 | **不可**（保持能力隔离） |
| **D4** | 定时消息允许引用写操作工具 | **禁止** / 需二次确认 | **禁止**（结构上不可选） |
| **D5** | 播报前是否强制 dry-run 预览 | **强制** / 可选 / 不要求 | **强制**（首次启用） |
| **D6** | 角色卡是否结构化 6 段 | **结构化 + 自由补充** / 单一长文本 | **结构化** |
| **D7** | embedding 模型与维度 | ✅ **已定：阿里云百炼 `text-embedding-v4`，1024 维**（侧表存 `model`/`dim`，可换 `qwen3.7-text-embedding-flash`） | 已定 |
| **D8** | pgvector 部署 | **换 `pgvector/pgvector:pg17` 镜像** / 保留原镜像并手工装扩展 | **换镜像** |
| **D9** | Quartz 表前缀 | `QRTZ_`（官方默认）/ 自定义 | 官方默认 |
| **D10** | 集群模式 | **现在开 `isClustered=true`** / 先单机 | **先开配置，单实例运行** |
| **D11** | 关系分级是否本期做 | 做 / 只留字段 | 做（大肥鱼体验的核心） |
| **D12** | 插件市场（导出/导入 zip 分享） | 本期做 / 后置 | 后置，但 manifest 先按可导出设计 |
| **D13** | 前端 | **复用 monitor 的 Vue3 + Element Plus 改造** / 重写 | 复用 |
| **D14** | 表名前缀 | **业务域前缀**（`scheduling_*` / `assistant_*`）+ `QRTZ_*` | 是 |

---

## 7. 附录 A：为什么选乙（v1 对比摘要）

| 维度 | 甲（自研 planner） | 乙（Quartz） |
|---|---|---|
| 集群 | 自研锁 + 唯一索引 | 原生 |
| misfire | 自研 | 原生 + 需应用层补多次 |
| 中国日历 | 自研判定（简单直接） | 自定义 `Calendar`（能复用引擎，但要知道"只能排除"） |
| 依赖成本 | 0 | 11 张表 + 一套配置 + 序列化约束 |
| 长期演进 | 任务量上来后要自己补集群/补偿 | 引擎能力已就位 |

结论：你的场景（单库、可接受多 11 张表、未来可能多实例）选乙合适。**唯一需要在实现时守住的原则是：业务逻辑永远不在 Job 里，Quartz 只当"闹钟"。**

## 8. 附录 B：monitor → relay-ops 映射（含新增）

| monitor | relay-ops 落点 | 处理 |
|---|---|---|
| `scheduler/ScheduledTask*` | `scheduling/{domain,adapter/out/persistence}` | 重写，spec/params/执行语义 |
| `scheduler/DynamicScheduledTaskManager` | `scheduling/application/{planner,execution}` | 改为 Quartz 编译器 + 协调器 |
| `common/schedule/ScheduledTaskHandler` | `scheduling/contract/ScheduledTaskHandler` | 升级为 `execute(TaskExecution)` |
| `api/controller/ScheduledTaskController` | `scheduling/adapter/in/web` | 重写（preview / runs / 日历 / resync） |
| `bot/BotChatClientFactory` 三段提示词 | `copilot` + `assistant` 各自 `CapabilityPack.prompt()` | 提示词从常量搬进能力包 |
| `bot/MonitorChatTools`、`ops/OpsMonitoringTools`、`bot/{order,usage,channel}/**Tools` | `copilot/tool` | 重写为 `ToolSpec` + contract 依赖 |
| `bot/weather|image|search|voice|vision/` | `assistant/capability/general` | 平移 |
| `bot/BotPromptComposer` | `agent/kernel/prompt` | 升级为 `PromptSection` 栈 |
| `bot/BotPersona*` + `V43/V44` | `assistant_persona`（角色卡 6 段） | 迁移 + 字段扩展；大肥鱼内容拆段 |
| `bot/BotGroupReplyPolicy/Strategy` | `assistant_profile.guard_policy` + 接话判定器 | 语义保留，结构显式化 |
| `bot/BotGroupContextService`（Redis 摘要） | `agent/kernel/memory` L1/L2 | 迁移到 Redis + PG 双层 |
| `bot/archive/` | `assistant/application/archive` + `assistant_message` | 合并 |
| `bot/BotLottery*` | `assistant` 内置玩法 or 插件 | 建议做成**第一个示例插件** |
| `mail/DailyBalanceReportService` + `V12` | `copilot` 蓝图 + `assistant.broadcast` | 变成 blueprint + 定时任务 |
| `bot_settings.platform_groups` | `assistant_profile`（pack 绑定） | 拆表 + 语义显式化 |
| —（新增） | `assistant_plugin*`、`assistant_blueprint` | 插件与消息编排 |

## 9. 下一步

D7 已定（百炼 `text-embedding-v4` / 1024 维 / 侧表存 model+dim），不再阻塞建表。开工前只剩一件事：

**确认第 6 节其余项按推荐执行**，其中三项影响面最大：D1 插件先只做声明式、D3 插件不可调用平台工具、D5 播报首次启用强制 dry-run 预览。

之后我按 `M0 → M1 → M2 → M3 → M6` 推进，第一个可验收的产物是：**Quartz 跑起来的"工作日上午 9 点号池日报"，带 3 个工具段 + 1 张图 + 一段人设点评，能在私聊收到 dry-run 草稿。**

首批代码改动清单（M0–M1 会落到仓库里）：

| 文件 | 改动 |
|---|---|
| `backend/pom.xml` | 加 `spring-boot-starter-quartz`；pgvector 走 JDBC 原生 SQL，不需要额外 Java 依赖 |
| `compose.yaml` | `postgres:17-alpine` → `pgvector/pgvector:pg17` |
| `application.yml` | 加 `spring.quartz.*`（jdbc job store、集群、`initialize-schema=never`）与 `relay-ops.ai.embedding.*` |
| `db/migration/V1__scheduling.sql` | `scheduling_task` / `scheduling_task_run` / `scheduling_calendar(_day)` |
| `db/migration/V2__quartz.sql` | 官方 11 张 `QRTZ_*` 表 |
| `db/migration/V3__assistant_memory.sql` | `CREATE EXTENSION vector` + 事实/向量侧表（可选先建，供 M4 用） |
| `ModuleBoundaryTest` | 三条新依赖规则（先红后绿） |

---

## 10. 实施进展

### M0 已交付（2026-11-10）

| 项 | 落点 |
|---|---|
| Quartz 依赖 | `backend/pom.xml` → `spring-boot-starter-quartz` |
| pgvector 镜像 | `compose.yaml` → `pgvector/pgvector:pg17` |
| 调度配置 | `application.yml` → `spring.quartz.*`（JDBC JobStore、集群、`initialize-schema=never`）、`relay-ops.scheduling.*`、`relay-ops.ai.embedding.*` |
| 业务表 | `db/migration/V1__scheduling.sql`（4 张表 + 幂等唯一索引 + 种子日历 `cn-official`） |
| Quartz 表 | `db/migration/V2__quartz.sql`（官方 11 张 `QRTZ_*`） |
| 边界规则 | `ModuleBoundaryTest` 新增 agent/assistant/copilot 五条规则，`allowEmptyShould(true)` 先红后绿已就位 |

`V3__assistant_memory.sql` 未建：它依赖 `CREATE EXTENSION vector`，属于 M4；提前建会让没有 pgvector 的环境直接迁移失败。D7 的侧表设计已在文档里定稿，M4 落表即可。

**2026-11-10 补充**：`V4__vector_extension.sql` 已落地，内容只有 `CREATE EXTENSION IF NOT EXISTS vector;`，向量表结构仍随 M4。
`CREATE EXTENSION` 需要超级用户（pgvector 不是 trusted 扩展）：本地 compose 的 `POSTGRES_USER=relay_ops` 是容器超级用户，迁移可直接通过；
连外部 PostgreSQL 且应用账号是普通角色时，需 DBA 先用超级用户执行一次 `CREATE EXTENSION vector;`，迁移本身在扩展已存在时是幂等空操作。

### M1 已交付（同日）

| 能力 | 落点 |
|---|---|
| `spec` 解析与校验 | `application/plan/ScheduleSpecCodec`（kind 必填、日历必填、activeWindow 边界、cron 范围） |
| `spec` → 触发器编译 | `application/plan/ScheduleSpecCompiler`（按日+时刻统一编译成 `0 m h ? * *`） |
| 中国工作日 | `domain/day/{CalendarData,DayMatcher,DayMatchers}`（含调休补班、每月第 N/最后一个工作日） |
| Quartz 日历桥 | `application/calendar/WorkdayQuartzCalendar`（`isTimeIncluded` 直接判定日规则，绕开"日历只能排除"的限制） |
| 日历注册表 | `application/calendar/SchedulingCalendarRegistry`（确定性命名、启动重注册、改数据后 `refresh`） |
| 触发器同步 | `application/task/TaskRegistrationService`（注册/注销/对账/一次性触发/回填 `next_fire_at`） |
| 执行器 | `application/execution/TaskExecutionService`（幂等、静默时段、并发、超时、失败重试、run 记录） |
| 启动对账 | `application/task/SchedulingBootstrap`（收尾遗留 RUNNING → FAILED，DB↔Quartz 双向对齐） |
| 管理 API | `adapter/in/web/SchedulingController`（tasks CRUD/启停/立即执行/runs、spec 预览、types、日历读改导入、resync） |
| 自检处理器 | `application/task/SelfTestTaskHandler`（`scheduling.selftest`，`params.fail=true` 可验证失败链路） |

**验收结果**：`mvn test` 52 项全绿，含新增 30 项调度单测（预览一致性、调休补班、第 N/最后工作日、Quartz 日历跳假、spec 校验、模块边界）。

### 与原计划的偏差（有意为之）

1. **`activeWindow` 只支持 `CALENDAR_TIMES` / `MONTH_NTH_WORKDAY`**。对 `INTERVAL` 做窗口跳过需要"跳到窗口起点再重算"，与 Quartz 的 SimpleTrigger 语义不易对齐，强行做会让预览与执行不一致。当前保存时明确报错，而不是悄悄忽略。
2. **`CATCH_UP(n>1)` 尚未实现**。Quartz 只保证"补最近一次"，多次补跑要应用层扫 run 表；M1 先按 `FIRE_ONCE` 交付，多次补跑随 M2 一起做。
3. **节假日顺延（`NEXT_WORKDAY` / `PREV_WORKDAY`）只存策略、不生成补偿**。M2 用"被排除的计划点 → 生成 `HOLIDAY_SHIFT` 一次性触发器"实现，`trigger_source` 与唯一键已留好。
4. **沉默告警**（`next_fire_at + grace` 超时未执行）未实现，属可观测性收尾。

### 一个已修掉的真实坑

Quartz 的 `CronExpression.isValidExpression("0 0 99 * * ?")` 返回 **true** —— 它接受越界的小时值，然后永不触发。现在 `ScheduleSpecCodec` 在保存时按字段做范围校验（秒/分 0-59、时 0-23、日 1-31、月 1-12、周 1-7、年 1970-9999），把"配错了但保存成功"变成"保存即报错"。这条有单测守护。

### 尚未验证的部分

本机没有 Docker CLI，`compose up` + Flyway + Quartz 启动的**运行时联调未执行**。需要在一台有 Docker 的机器上跑：

```powershell
docker compose up -d
cd backend; mvn spring-boot:run
# 然后打开 /api/v1/scheduling/calendars 与 /api/v1/scheduling/types 确认迁移与注册正常
```

---

## 11. M2 已交付（2026-11-10）

### 节假日顺延补偿

| 部件 | 说明 |
|---|---|
| `HolidayShiftPlanner` | 纯函数：pattern 想要的日期被日历挡住时，算出应顺延/提前到哪个工作日 |
| `HolidayShiftService` | 把计划物化成 `HOLIDAY_SHIFT` 一次性触发器；每次注册任务、改日历、每日 00:05 扫描时重算 |
| 幂等 | 触发器名 `shift-<时间戳>`，任务组内唯一；多个被挡日期落到同一时刻只发一次 |

**语义澄清（这一版才想明白）**：顺延不是"把每个被吞掉的计划点都挪到节后"（那会把 7 天假期堆成 7 条消息），而是：

1. 仅当**目标工作日当天没有常规触发**时才补——所以"每工作日 9:00"永远不产生补发（节后第一天本来就发），补发只对"每周一""每月 1 日""指定日期"这类**日期型 pattern** 有意义；
2. 多个被挡日期映射到同一时刻时合并为一次；
3. 顺延的补偿触发器与常规触发器同组，重新注册任务时一起重建，不会残留。

为此新增了 `MONTH_DAYS`（每月 N 日）规则——没有它，"顺延"几乎没有用武之地（每月 1 日的月报遇周末顺延到周一，才是真实需求）。

### 顺带修掉的一个执行/预览不一致 bug

原先 `WEEKDAYS` / `CUSTOM_DATES` 这类日期型规则被判定为"不需要日历"，于是 Quartz 触发器**不挂 matcher**、每天都会触发；而预览走的是 matcher，只显示周一。也就是"预览说周一，实际天天跑"。

修复：`ScheduleSpec.usesDayMatcher()` 与 `needsCalendarData()` 拆成两个概念——**凡按日筛选的调度都必须挂日历**（否则 Quartz 无法筛日），只是日期型规则用空日历数据即可。日历注册表改为接收编译后的 matcher（而不是自己重建），保证注册、预览、执行三者用同一个谓词。

另外：Shift 策略下 matcher 会额外要求"当天是工作日"，因此假期的周一既不正常触发、也不会被补发重复。

### 日历导入

`CalendarImportParser` 支持三种格式，`POST /calendars/{code}/import/raw`（`format` 可为 `AUTO/JSON/ICS/TEXT`）：

| 格式 | 形态 |
|---|---|
| JSON 数组 | `[{"date":"2026-10-01","type":"HOLIDAY","name":"国庆"}]` |
| holiday-cn JSON | `{"year":2026,"days":[{"date":"...","name":"...","isOffDay":true}]}`，`isOffDay=false` → 补班 |
| ICS | `VEVENT` + `DTSTART;VALUE=DATE` + `SUMMARY`（含"补班"字样判为补班） |
| 纯文本 | 每行 `2026-10-01 HOLIDAY 国庆`，也接受 `2026-10-10 补班` |

`/calendars` 新增 `coverageWarning`：未导入任何日期、或覆盖不足 60 天时提示"请导入 YYYY 年放假安排"——避免跨年后静默退回"周一至周五"的默认规则。

### 验收

`mvn test` **69 项全绿**（新增 17 项：顺延计划 8、日历导入 6、日期规则 3）。仍缺运行时联调（本机无 Docker）。

### 仍未做

- 沉默告警（`next_fire_at + grace` 超期未跑）
- `CATCH_UP(n>1)` 多次补跑
- 真实 2026/2027 日历数据本身（需要导入，代码不内置——避免用错的法定节假日数据）

---

## 12. 配置外置（2026-11-10）

### 做法

所有可调项收敛到仓库根的 `.env.example`，`application.yml` 与 `compose.yaml` 只保留
`${VAR:default}` 引用：

| 区域 | 变量前缀 | 说明 |
|---|---|---|
| 应用与运行时 | `RELAY_OPS_SERVER_*`、`RELAY_OPS_APP_*`、`RELAY_OPS_TIMEZONE` | 端口、时区、虚拟线程、JSON 包含策略 |
| 会话 | `RELAY_OPS_SESSION_*` | Redis 会话、Cookie 属性、命名空间 |
| 主库 | `RELAY_OPS_DB_*` | URL/账号/池参数；compose 用 `DB_NAME/DB_PORT` 建容器 |
| Redis | `RELAY_OPS_REDIS_*` | 地址、库号、超时 |
| 迁移与 ORM | `RELAY_OPS_FLYWAY_*`、`RELAY_OPS_MAPPER_*` | 关闭迁移、改位置等运维开关 |
| 调度 | `RELAY_OPS_SCHEDULING_*`、`RELAY_OPS_QUARTZ_*` | 时区、预览条数、顺延窗口、Quartz 线程/集群/misfire |
| 观测 | `RELAY_OPS_ACTUATOR_*`、`RELAY_OPS_LOG_LEVEL` | 暴露端点、健康探针、日志级别 |
| 聊天模型 | `SPRING_AI_*` | OpenAI 兼容端点；`SPRING_AI_MODEL_CHAT=none` 关闭自然语言 |
| 向量化 | `BAILIAN_*`、`DASHSCOPE_API_KEY` | 百炼 embedding（与语音共用 Key） |
| Sub2API 只读库 | `SUB2API_DB_*` | 关闭时相关查询直接返回"未启用" |

`TimeConfig` 改为读取 `relay-ops.timezone`，不再写死 `Asia/Shanghai`；`spring.jackson.time-zone`
复用同一个变量，避免"业务时区"和"序列化时区"漂移。

### 防漏登记

`ConfigurationReferenceTest` 会：

1. 扫描 `application.yml` 里所有 `${VAR...}`，断言 `.env.example` 有同名条目；
2. 扫描 `compose.yaml` 里的 `${VAR:-...}`，同样断言；
3. 检查 `.env.example` 没有重复键。

这样"加了个配置项忘了写进模板"会在测试阶段失败，而不是等部署时才发现。

### 验收

`mvn test` **88 项全绿**（新增 3 项配置引用检查 + 16 项 agent 内核测试）。

---

## 13. M3 进行中：agent 内核（2026-11-10）

三模块拆分的第一块：内核不依赖任何业务模块，两个能力包只通过 SPI 与它对接。

| 部件 | 内容 |
|---|---|
| `agent.domain.prompt.PromptStack` | 分层装配：`SAFETY > CAPABILITY > PACK_STYLE > PERSONA > MEMORY > SESSION`；同名字段"受保护者胜"；超预算时从最低层开始丢弃，最高层永远保留 |
| `agent.domain.persona.PersonaCard` | 角色卡六段（形象/性格/节奏/关系/情绪/边界）+ 风格开关 + 示例 + 关系称呼规则 + 适用能力包 + 版本；`neutral()` 作为兜底人设 |
| `agent.domain.persona.RelationshipLevel` | `STRANGER / GROUP_MATE / FRIEND / CLOSE`，供"不要叫所有人主人"这类规则落地 |
| `agent.contract.CapabilityPack` | 能力包 SPI：`prompt(ctx)` + `tools(ctx)` + `supports(scope)` |
| `agent.contract.ToolSpec` | 工具元数据：副作用等级、是否可被定时消息引用、权限谓词、脱敏策略、超时、参数 Schema |
| `agent.application.tool.ToolRegistry` | 唯一查找入口：按作用域选包 → 权限谓词过滤 → 去重（先注册者胜）→ 单独暴露"可被消息引用"的工具集 |

关键约定：**工具的身份由调用方注入**（`ToolInvocation` 带 scope/userKey/admin），handler 不接受
"查谁"的参数；权限谓词永远在内核执行，能力包与插件只能声明、不能绕过。

下一步（M3 剩余）：人设的持久化（`assistant_persona` 表）与 CRUD、提示词装配的编排服务、
模型路由与降级；随后 M6 的消息编排复用 `ToolRegistry.schedulable()`。

---

## 14. M3 续：人设持久化与提示词装配（2026-11-10）

| 部件 | 落点 |
|---|---|
| V3 迁移 | `assistant_persona`（角色卡六段 + `style`/`examples`/`address_rules`/`packs`/`version`，单一默认人设的唯一索引）、`assistant_profile`（作用域绑定，`scope_type + scope_key + pack_id` 唯一） |
| `PersonaRepository` | 实体 ↔ `PersonaCard` 映射；四个 JSON 字段（风格、示例、称呼规则、适用能力包） |
| `PersonaResolver` | 纯函数：精确作用域 → 全局绑定；停用、空绑定、异包绑定自动跳过 |
| `PersonaService` | CRUD、切换唯一默认人设、作用域绑定、解析（绑定 → 默认 → 中性兜底） |
| `PromptAssemblyService` | 安全边界（受保护）→ 能力包段落 → 人设段落；超预算从最低层裁剪 |
| API | `/api/v1/assistant/personas` CRUD 与 `/default`、`/profiles` 绑定、`/prompt/preview` 预览 |

三条设计取舍：

1. **人设不含权限**。角色卡只产出 `PERSONA` 层段落；`SAFETY` 与 `CAPABILITY` 段落受保护，
   人设写什么都没法覆盖——这正是"角色设定不改变口径"的落地点。
2. **绑定失效不报错**。绑定指向已停用/包不匹配的人设时，解析自动回落到默认人设或中性卡，
   线上不会因为配错人设而整条链路不可用；管理页仍能看到绑定本身。
3. **预览即真相**。`/prompt/preview` 返回的就是发给模型的文本 + 段落结构（名称/层级/是否可覆盖/长度），
   "人设不生效"这类争议可以当场自证。

`mvn test` **100 项全绿**（新增 12 项：绑定解析 6、提示词装配 6）。

### M3 仍未做

- ~~模型路由与降级~~（见第 15 节，已完成）
- 会话记忆（M4）与消息编排（M6）

---

## 15. M3 完成：模型路由与降级（2026-11-10）

| 部件 | 说明 |
|---|---|
| `ChatProperties` | `relay-ops.ai.chat.*`：主/降级两个槽位，每槽 `base-url` / `api-key` / `model` / `temperature` / `max-tokens`；缺任一项的槽位自动跳过 |
| `ModelRouter` | 解析顺序 primary → fallback；全部不可用时进入"仅命令模式"，`status()` 给出原因与脱敏后的槽位 |
| `ChatClientRegistry` | 按槽位懒构建并缓存 `ChatClient`（Spring AI 1.1 的 `OpenAiApi.builder()` / `OpenAiChatModel.builder()`），无模型时完全不触碰网络 |
| API | `GET /api/v1/assistant/models`：是否启用、是否可用、原因、各槽位的模型与打码密钥 |

三条取舍：

1. **两个开关各管一段**：`SPRING_AI_MODEL_CHAT` 只影响 Spring AI 的自动装配；本项目用
   `RELAY_OPS_AI_CHAT_ENABLED` 控制自有路由。前者保持 `none` 也能正常构建客户端。
2. **"没有模型"是合法状态**。解析为空时助手回落到命令模式（`/平台`、`/号池` 等仍可用），
   不抛异常、不阻塞启动；夜间无人值守时缺配置不该让整个应用起不来。
3. **密钥只活两处**：配置对象与请求头。状态接口只回 `sk-a****mnop` 形式的打码值，
   日志里只有槽位与模型名。

验收：`mvn test` **107 项全绿**（新增 7 项：路由 5、客户端 2）。

### M3 收尾状态

`agent` 内核（提示词分层、角色卡、能力包 SPI、工具注册表、模型路由）、`assistant` 的人设持久化
与提示词装配均已就位。下一站 **M4：记忆**——四张表（会话/消息/摘要/事实 + 向量侧表）、
百炼 embedding 接入、混合召回与写入策略。

### 文本编码守护（来自一次真实事故）

另一个会话用 ANSI/GBK 写了 `V4__vector_extension.sql`，中文注释被双重编码破坏，且已含替换字符、
无法逐字还原（只能按原意重写）。为此新增 `TextFileEncodingTest`，扫描 README、`.env.example`、
`compose.yaml`、`docs/**/*.md`、`db/migration/**/*.sql` 与 `src/main/**/*.java`：

1. 按 UTF-8 **严格解码**，非 UTF-8 直接失败；
2. 不含替换字符 `U+FFFD`；
3. 不含双重编码的乱码标记（GBK 误读常见的 `U+951B`、`U+9428`、`U+93B5` 一类罕见字符），
   因为这类文件是"合法 UTF-8"，单靠解码校验发现不了。

顺带统一了两件事：`compose.yaml` 使用 `pgvector/pgvector:pg18-trixie`（用户本人调整），
`V4__vector_extension.sql` 只负责 `CREATE EXTENSION IF NOT EXISTS vector;`，向量表结构随 M4 落地。

---

## 16. M4 第一块：事实记忆与混合召回（2026-11-10）

| 部件 | 说明 |
|---|---|
| `V5__assistant_memory.sql` | `assistant_conversation` / `assistant_message` / `assistant_memory_summary` / `assistant_memory_fact` / `assistant_memory_embedding`；事实与向量解耦，`namespace`、`source`、`scope_type` 都有约束 |
| `MemoryNamespace` / `FactSource` | `PLATFORM` 只对平台能力包可见；来源分 `EXPLICIT` / `DERIVED` / `LLM_EXTRACT` 三档 |
| `MemoryGuard` | 拒绝密钥与口令、拒绝身份/权限结论、拒绝模型推断的平台事实、限长 500 字 |
| `EmbeddingClient` / `BailianEmbeddingClient` | 百炼 `text-embedding-v4` 走 OpenAI 兼容 `/embeddings`；调用失败或维度不符一律返回空，只降召回质量、不阻断对话 |
| `MemoryStore` / `MemoryRepository` | 端口 + 适配器：ILIKE 关键词检索、余弦距离邻居、`cast(? as vector)` 写入、`touch` 更新使用时间、过期清理 |
| `MemoryService` | 混合召回评分 `0.45*相似度 + 0.25*关键词 + 0.20*置信度 + 0.10*时效`（30 天半衰期），命中即 `touch` |
| API | `/api/v1/assistant/memory`：事实写入 / 列表 / 删除、召回预览（返回 `hybrid` 还是 `keyword`）、过期清理 |

三条取舍：

1. **向量进侧表**，维度不写进 schema；换模型只增 `model` 行，不会锁死表结构。
2. **模型不可用是降级不是错误**：召回退回关键词模式，聊天照常进行。
3. **身份永不入记忆**：`is_admin`、`是管理员` 之类内容写入即被拒；平台业务事实只接受代码或人工写入。

一个自指的小插曲：文档里原本直接贴了乱码样例字符，结果 `TextFileEncodingTest` 把文档自己判成乱码。
现在文档改用 Unicode 码位描述（`U+951B` 等），校验规则保持严格 —— 规则不该为"举例"让路。

验收：`mvn test` **118 项全绿**（新增 10 项：写入防护 5、召回与写入 5）。

### M4 剩余

- 会话 / 消息 / 摘要的读写服务（表已建，主要服务 M6 的对话与播报链路）
- 每 N 轮滚动摘要任务、过期记忆的定时清理
- 提示词装配接入召回结果（`MEMORY` 层段落）

---

## 17. M4 第二块：会话摘要、维护任务与召回入提示词（2026-11-10）

| 部件 | 说明 |
|---|---|
| `ConversationStore` / `ConversationJdbcStore` | L1/L2 读写：会话 `ensure`（`ON CONFLICT`）、消息追加（先 `for update` 锁会话再取 `max(seq)+1`，避免并发下的唯一键冲突）、最近消息、最新摘要、待摘要会话（`LATERAL` 连接） |
| `TextSummarizer` / `ChatTextSummarizer` | 摘要端口 + 模型实现；无模型或调用失败一律返回空，只减少记忆、不阻断维护任务 |
| `ConversationService` | 追加 user / assistant / broadcast 消息；滚动摘要把"已有摘要 + 新增对话"合并压缩，锚点记在一个 message id 上；`summarizePending` 批量处理 |
| `MemoryMaintenanceTaskHandler` | 任务类型 `memory.maintenance`：清理过期事实 + 生成滚动摘要，params 支持 `minMessages` / `limit`；通过 M1 的调度模块注册 |
| `PromptAssemblyService.MemoryPrompt` | 召回结果作为 `MEMORY` 层段落（会话摘要 + 长期记忆），排在人设之后 |

两个实现上的选择：

1. **消息表用 `JdbcTemplate` 而不是 MyBatis-Plus**。这两张表是纯追加、无实体状态，真正的逻辑是
   `max(seq)+1` 与 `LATERAL` 连接，写成 SQL 比 ORM 映射直白；事实表仍用 MyBatis-Plus（有实体和
   JSONB 字段）。同一模块内混用持久化方式不是洁癖问题，而是"按访问模式选工具"。
2. **摘要锚点用 message id**，而不是时间戳或条数：补写历史消息、删除消息后仍能判断"哪些还没摘要"。

一个测试暴露的真实问题：`ConversationServiceTest` 里 fake store 的 `recent()` 没有按会话过滤，
于是"两个会话只有一个有消息"的用例把两个都摘要了 —— 假的实现同样要守契约，否则测的是假象。

验收：`mvn test` **125 项全绿**（新增 7 项：会话读写 6、记忆入提示词 1）。

### M4 剩余

- 迁移里插入一行 `memory.maintenance` 的 CRON 任务种子（代码已就绪，缺默认调度）
- `memory_policy` 按群开关召回（表字段已有，服务未读）
- 召回结果接入真实对话链路（随 M7 一起）

---

## 18. M6 第一块：消息蓝图与 dry-run（2026-11-10）

| 部件 | 说明 |
|---|---|
| `V6__assistant_blueprint.sql` | `assistant_blueprint`（segments/options 为 JSONB）+ 演示蓝图 `demo.daily-note` + 演示任务（默认停用） |
| `Blueprint` / `BlueprintCodec` | 三种段：`text` / `tool` / `persona_note`；保存时校验"工具必须只读且允许调度"、工具段数不超过 `maxToolCalls` |
| `MessageComposer` | 顺序执行段：变量替换（`{{date}}` `{{args.*}}` `{{tools.<ns>}}`）、工具调用与失败策略（`SKIP` / `DEGRADE` / `ABORT`）、无人设模型时跳过点评段 |
| `BlueprintService` / `BlueprintController` | CRUD + `POST /{id}/preview`（dry-run，返回文本与每段状态） |
| `BroadcastTaskHandler` | 任务类型 `assistant.broadcast`，params 支持 blueprintCode / scope / args / remember；播报内容写入会话记忆 |
| `MessageSender` / `LoggingMessageSender` | 投递端口 + 占位实现（日志 + 最近一条快照，供页面验证渲染）；M7 换成 OneBot/邮件 |
| 演示能力包 | `GeneralDemoCapabilityPack`：`general.clock` 与 `general.echo` 两个只读工具，只为让 dry-run 有真实结果 |

三条关键约束：

1. **写操作工具结构上进不了播报**：候选集来自 `ToolRegistry.schedulable()`，保存时再校验一次。
2. **未解析的占位符保持原样**（`{{args.missing}}` 不会被替换成空串），dry-run 里能直接看到缺什么。
3. **降级而不是失败**：工具不可用按段策略处理，整条消息仍可发出并标记 `degraded`。

前端控制台（Vue 3 + Vite + Element Plus）已可用：运行状态 / 定时任务 / 人设与提示词预览 /
消息蓝图 dry-run / 记忆。本地需 `RELAY_OPS_DEV_OPEN_API=true` 放行 `/api/**`（生产必须关闭）。

验收：后端 `mvn test` **130 项全绿**（新增 5 项编排测试）；前端 `npm run build` 通过。

---

## 19. M7 进行中：对话链路与确定性命令（2026-11-10）

| 部件 | 说明 |
|---|---|
| `ChatService` | 人设 → 会话 → 记忆召回 → 提示词装配 → 模型 → 写助手消息；无模型时返回 `UNAVAILABLE` 且**不写**助手消息 |
| `ChatCompletion` / `ModelChatCompletion` | 模型调用端口与实现，异常按"无模型"处理 |
| `CommandDispatcher` / `CommandRouter` | `/help`、`/status`、`/remember`、`/report`；未知命令回帮助 |
| `BlueprintRenderer` / `ServiceBlueprintRenderer` | 命令与页面 dry-run 共用同一渲染入口，保证"预览 = 播报" |
| Web 入口 | `POST /api/v1/assistant/chat`；OneBot 入口后续复用 `ChatService` |

三条约束：

1. **命令优先于模型**：`/help` 这类不需要模型，配不配模型都能用；这也是模型不可用时的兜底。
2. **`/remember` 走 `MemoryGuard`**：密钥、身份/权限结论会被拒，并把原因回给用户，而不是静默失败。
3. **命名空间隔离有测试**：`platform` 包只召回 `PLATFORM` 事实，闲聊召回不到平台结论。

验收：`mvn test` **140 项全绿**（新增 7 项命令测试）。

### M7 剩余

- OneBot 入口（`integration/onebot` 仍为空）：复用 `ChatService`，只需实现协议适配
- `copilot` 平台能力包：`monitoring` / `upstream` 只有 contract，工具暂时只能返回"数据源未接入"
- 真实投递：`LoggingMessageSender` → `NotificationPort`（邮件已有依赖，QQ 依赖 OneBot 客户端）
- `memory_policy` 按群开关召回

---

## 20. 采集闭环（2026-11-10）

| 部件 | 说明 |
|---|---|
| `V7__upstream_collection.sql` | 平台、账号（凭证密文）、快照、采集运行四张表；种一个停用的演示平台与"全量账号采集" CRON 任务 |
| `CredentialCipher` | AES-GCM，密钥来自 `RELAY_OPS_CREDENTIAL_KEY`；无密钥时应用照常启动，但保存凭证失败而不是明文落库 |
| `UpstreamClient` / `NewApiUpstreamClient` | 采集端口 + New API 实现（`GET /api/user/self`，同时带 `Authorization` 与 `New-Api-User`） |
| `UpstreamStore` / `UpstreamRepository` | 显式 SQL：平台与账号 CRUD、快照与运行记录追加、账号状态回写 |
| `CollectionService` | 逐账号采集：解密凭证 → 调客户端 → 写快照与账号状态；单账号失败不中断整轮，并汇总到运行记录 |
| `CollectAllAccountsTaskHandler` | 任务类型 `upstream.collect-all`，由 M1 的调度器按 CRON 触发 |
| API / 页面 | `/api/v1/upstream/*`：平台、账号、立即采集、快照、运行记录；前端新增「采集」页 |

四条设计取舍：

1. **凭证只存密文**：AES-GCM 随机 IV，接口只回 `sk****56` 形式的提示；换密钥后旧凭证按"解密失败"处理，不静默返回空。
2. **单账号失败不影响整轮**：失败信息同时写进快照与账号行，页面能直接看出"哪个账号坏了"。
3. **响应形状不对算 PARTIAL 而不是 FAILED**：凭证通过了但字段缺失时，保留原始 `raw` 便于回放，同时标记为部分成功。
4. **额度单位不在这里换算**：New API 的 `quota` 按原值入库，换算属于展示层口径，避免采集层埋业务假设。

### 采集相关待办

- `Sub2API` 采集客户端（当前会明确返回"暂不支持采集该平台类型"）
- New API 额度单位换算与"今日消耗"推算（需要历史快照，属 `monitoring` 模块）
- 采集完成后发布 `AccountCollectedEvent`，驱动看板与告警（事件契约已存在）
- 采集并发度控制（当前串行；账号多时按 `RELAY_OPS_COLLECT_CONCURRENCY` 分批）

---

## 21. 采集补齐：Sub2API、事件与总览（2026-11-10）

| 部件 | 说明 |
|---|---|
| `Sub2ApiUpstreamClient` | `GET /v1/usage` + Bearer；解析同时接受 `data.*` 与根对象、snake_case 与 camelCase 的常见字段 |
| `AccountCollectedEvent` | 每成功采集一个账号发布一次；发布失败只记日志，不影响采集结果 |
| `GET /api/v1/upstream/dashboard` | 每个启用账号的最新快照（`LATERAL`），按余额倒序 |
| 前端「总览」页 | 余额/已用/请求数/状态/采集时间 + 最近一次运行统计，支持一键采集 |

两条判断：

1. **Sub2API 的字段解析故意宽容**：不同部署的 payload 位置不一致，未识别时返回 PARTIAL 并保留 `raw`，
   而不是把缺失字段写成 0 —— 写 0 会让看板看起来"余额为 0"。
2. **事件发布不阻塞采集**：事件消费者（看板、告警）出问题时只丢一次通知，不拖垮本轮采集。

验收：`mvn test` **158 项全绿**（新增 5 项：Sub2API 解析 4、事件发布 1）。

---

## 22. 监控读模型：今日消耗、异常账号与趋势（2026-11-10）

| 部件 | 说明 |
|---|---|
| `UsageMath` | 纯函数：`used_quota` 差值算消耗，负差值（上游重置计数器）返回空而不是负数 |
| `UsageReadStore` / `UsageReadRepository` | 只读 SQL：每个账号最新快照、当天最早快照、账号趋势、沉默/异常账号 |
| `MonitoringService` | 组装总览（含今日消耗）、异常账号（`FAILED`/`PARTIAL`/`NEVER_COLLECTED`/`STALE`）、趋势 |
| `MonitoringController` | `/api/v1/monitoring/{overview,abnormal,accounts/{id}/trend}` |
| 前端「监控」页 | 合计余额/今日消耗、账号表、异常列表、趋势抽屉（6h/24h/7d） |

两条关键判断：

1. **今日消耗用快照差值算，不依赖上游字段**。New API 没有"今日消耗"，用当天最早与最新快照的
   `used_quota` 差值；上游重置计数器导致负差值时返回"未知"——把负数当消耗会让报表出现负费用。
2. **"沉默"也是异常**。账号最近一次采集超过 `RELAY_OPS_MONITORING_STALE_HOURS`（默认 2 小时）
   即便上次成功，也会进入异常列表：采集停摆往往比采集失败更危险。

一处有意的技术债：`monitoring` 目前用只读 SQL 直接查采集表，而不是走 `upstream.contract`。
列已经限定在只读列上，行为不受影响；等 `upstream` 发布查询契约后再迁移，不改变调用方。

验收：`mvn test` **163 项全绿**（新增 4 项差值计算测试），前端构建通过。

---

## 23. 告警与真实播报（2026-11-10）

### 告警（`notification`）

| 部件 | 说明 |
|---|---|
| `V8` 迁移 | `notification_alert_state`（按账号+类型记冷却）、`notification.alerts` CRON 任务、`platform.daily` 播报蓝图 |
| `AlertRules` | 纯函数：余额低于阈值 / 采集失败 / 采集沉默（含"从未采集"），按类型独立冷却 |
| `AlertService` + `AlertTaskHandler` | 每 15 分钟扫一次，读 `MonitoringQueryPort.accounts()`，发送后记冷却状态 |
| `MailNotificationAdapter` | 用 `JavaMailSenderImpl` 自行组装（不依赖 `spring.mail.*`），未配置时只记日志、不阻塞调用方 |

四条判断：

1. **告警在调度器里跑，不挂在采集流程上**：告警风暴不该拖慢或搞挂采集；同一轮扫描还能发现"根本没在采集"的账号。
2. **冷却按类型独立**：余额低的账号仍在采集失败时，两类告警互不抑制。
3. **邮件失败只降级为日志**：通知失败不能让采集/告警任务失败。
4. **"从未采集"归入沉默**：新账号加进来没跑过，也应该被告警提醒，而不是安静地一直空着。

### 真实播报（`copilot` + `assistant`）

| 部件 | 说明 |
|---|---|
| `PlatformCapabilityPack` | `platform.overview` / `platform.abnormal` 两个只读工具，走 `MonitoringQueryPort`，不碰 monitoring 表 |
| `MonitoringQueryPort.accounts()` | 跨模块只读契约：账号 + 最新快照 + 状态 |
| `EmailMessageSender` | `@Primary` 的 `MessageSender`：先经 `NotificationPort` 发邮件，再交给 `LoggingMessageSender` 记账 |
| `platform.daily` 蓝图 | 文本 + 两个平台工具，可用 `/report platform.daily` 或定时任务播报 |

一处边界修正：`EmailMessageSender` 最初写在 `notification` 里，但它要依赖 `assistant` 的 `MessageSender`，
而规则禁止业务模块依赖 `assistant`。最终放在 `assistant/adapter/out/mail`，只依赖 `notification.contract` ——
通道归 `assistant`，投递归 `notification`，依赖方向才对。

验收：`mvn test` **169 项全绿**（新增 6 项告警规则测试）。

---

## 24. QQ 接入（OneBot v11 / NapCat）（2026-11-10）

| 部件 | 说明 |
|---|---|
| `integration.onebot.OneBotProperties` | 启用开关、webhook token、API 地址与 token、是否需要 @、超时；未配齐时 `usable()=false` |
| `integration.onebot.OneBotEvent` | 解析 v11 消息事件：群/私聊、`@` 判定（CQ 码与消息段双通道）、自身消息识别、去掉 CQ 噪声 |
| `integration.onebot.OneBotClient` | 发送 `send_group_msg` / `send_private_msg`，失败只记日志 |
| `assistant.adapter.in.onebot.OneBotWebhookController` | 反向 HTTP 入口：常量时间比对 token、失败关闭、异常不外抛（避免 NapCat 重投） |
| `assistant.adapter.out.onebot.RoutingMessageSender` | `@Primary` 通道路由：群作用域优先 QQ，其余回落邮件；全程记账 |
| `compose.yaml` | 内置 `napcat` 服务（端口、数据卷、`host.docker.internal` 映射），全部参数走 `.env` |

四个判断：

1. **通道与协议分层**：`integration.onebot` 只做协议（事件解析、发送），
   `assistant.adapter.in.onebot` 决定"回不回、回什么"。放在一起会让 integration 依赖 assistant。
2. **webhook 失败关闭**：token 没配或未启用直接 404/401，绝不"为了方便"放行。
3. **异常不抛回 OneBot**：NapCat 会对非 2xx 重投同一事件，处理异常必须吞掉并记日志，
   否则一次模型故障会变成事件风暴。
4. **群消息默认要 @**：群里不 @ 就不回，避免刷屏；私聊不需要。

部署侧：`napcat` 已进 compose，参数（`NAPCAT_ACCOUNT`、`NAPCAT_WEBUI_TOKEN`、端口）全部走 `.env`；
`./napcat` 数据目录含登录态，已加入 `.gitignore`。唯一无法自动化的一步是首次扫码登录。

验收：`mvn test` **174 项全绿**（新增 5 项事件解析测试）。

### 接入待办

- 群白名单与管理员判定（当前所有 @ 都回应）
- 引用回复与图片消息（现在只处理文本）
- 发送限流与长消息分片（QQ 单条长度有限）
- `AccountCollectedEvent` 驱动的群内主动告警播报

---

## 25. 群准入与告警推群（2026-11-10）

| 部件 | 说明 |
|---|---|
| `OneBotAccessPolicy` | 群白名单（空=不限制，配置后严格校验）+ QQ 管理员名单；私聊始终放行 |
| `OneBotWebhookController` | 非白名单群直接 403；管理员身份注入 `ChatService`，群里也能查平台数据 |
| `AlertRaisedEvent` | `notification.contract.event` 新增事件，告警发出后发布 |
| `QqAlertListener` | `assistant` 侧监听事件 → 推送到 `RELAY_OPS_ONEBOT_ALERT_GROUPS` |

三个判断：

1. **准入与身份分开**：群白名单决定"回不回"，QQ 管理员名单决定"能不能查平台数据"，两者独立配置。
2. **告警走事件而不是直接调用**：`notification` 不认识 QQ，`assistant` 监听事件推送，
   依赖方向保持 `assistant → notification.contract`。
3. **推送目标与接话目标分开**：`ALERT_GROUPS` 只收告警，不需要在群里 @ 机器人；
   避免把运维告警和闲聊群混在一起。

验收：`mvn test` **179 项全绿**（新增 5 项准入策略测试）。

---

## 26. 排错：Quartz 报 `不良的类型值 long : \x`

**现象**：启动后日志持续刷

```text
JobPersistenceException: Couldn't retrieve trigger: 不良的类型值 long : \x
  at PgResultSet.getBlob → StdJDBCDelegate.getObjectFromBlob
```

**根因**：Quartz 默认的 `StdJDBCDelegate` 用 `ResultSet.getBlob()` 读 `JOB_DATA`（`bytea`），
而 pgjdbc 的 `getBlob()` 会把内容当 OID 解析；我们写进去的是序列化字节（以 `\x` 开头的十六进制），
于是解析失败。触发器读不出来 → misfire 处理器每分钟重试一次，形成日志风暴。

**修复**（`application.yml`）：

```yaml
org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
org.quartz.jobStore.useProperties: true
```

- `PostgreSQLDelegate` 用 `getBytes()` 读 `bytea`，是 PostgreSQL 的官方搭配；
- `useProperties: true` 让 `JobDataMap` 以字符串属性表存放（我们只有 `taskId`），
  新写入的数据既避开 blob 路径，也能直接在表里看懂。

**历史数据**：`V9__reset_quartz_state.sql` 清空 `QRTZ_*` 业务行。Quartz 表全部是派生状态
（真实定义在 `scheduling_task`），启动时 `SchedulingBootstrap.resync()` 会按库重建，
所以清空是安全的，也避免新旧两种格式混在一张表里。

**顺带修的一处**：Hikari `idle-timeout` 默认 10 分钟大于我们设置的 `max-lifetime`（5 分钟），
启动时会告警并禁用空闲回收；现在显式配成 `RELAY_OPS_DB_IDLE_TIMEOUT`（默认 120s）。

**环境提示**：日志后半段出现 `Connection is not available / SocketTimeoutException`，
那是到远端数据库（WAN 地址）的网络中断，不是应用缺陷；连外网数据库时建议调大
`RELAY_OPS_DB_CONNECTION_TIMEOUT`，或在本地跑 compose 里的 PostgreSQL。

`ApplicationYamlTest` 已加入断言，锁住上面两项 Quartz 配置，防止以后被改回去。

---

## 27. 一键部署：后端镜像与 compose app 服务（2026-11-10）

| 部件 | 说明 |
|---|---|
| `backend/Dockerfile` | 多阶段：`maven:3.9-eclipse-temurin-21` 构建 → `eclipse-temurin:21-jre` 运行；非 root 用户、`JAVA_OPTS` 可配 |
| `backend/.dockerignore` | 排除 `target/`、日志与 IDE 文件，构建上下文只有 pom + src |
| `compose.yaml` 的 `app` | 依赖 `postgres`/`redis` 健康检查；容器内用服务名连库与 NapCat；全部开关透传 `.env` |
| `RELAY_OPS_JAVA_OPTS` | 容器内 JVM 参数（默认 `-XX:MaxRAMPercentage=75 -XX:+UseG1GC`） |

三条判断：

1. **容器内地址与宿主机地址分开**：`.env` 里的 `RELAY_OPS_DB_URL=localhost` 是给本机开发用的，
   `app` 服务显式覆盖为 `postgres:5432`；否则容器连自己的 localhost 必然失败。
2. **不依赖构建机工具链**：镜像里完成 Maven 构建，宿主机只要有 Docker。
3. **NapCat 的反向上报地址随部署方式变化**：全容器用 `http://app:8080/...`，
   后端跑宿主机用 `http://host.docker.internal:8080/...`，README 两种都写了。

验证状态：本机没有 Docker CLI，**镜像构建未能实跑**；但同一份产物已用 `mvn spring-boot:run`
连真实数据库验证过启动、迁移与调度（见第 26 节）。首次在有 Docker 的机器上执行
`docker compose up -d --build` 时，注意观察 `app` 容器日志的迁移与 Quartz 注册。

---

## 28. 控制台进容器（2026-11-11）

| 部件 | 说明 |
|---|---|
| `frontend/Dockerfile` | Node 22 构建 → `nginx:1.27-alpine` 托管；两阶段，运行镜像不含 node |
| `frontend/nginx.conf` | SPA 回落 `index.html`；`/api/` 与 `/actuator/` 反代到 `app:8080` |
| `compose.yaml` 的 `web` | 端口 `${RELAY_OPS_WEB_PORT:-8088}:80`，依赖 `app` |
| `frontend/.dockerignore` | 排除 `node_modules/`、`dist/` |

两个判断：

1. **同源反代而不是 CORS**：浏览器访问 `:8088`，`/api` 由 Nginx 在服务端转发到 `app`，
   页面与接口同源 —— 不需要开 CORS，也不需要把后端端口暴露给浏览器。
2. **运行镜像里没有 Node**：静态资源在构建阶段产出，运行阶段只有 Nginx，
   镜像更小、攻击面更小；`npm ci` 用锁文件保证可重复构建。

现在一条 `docker compose up -d --build` 会起：`postgres`（pgvector）、`redis`、`app`（后端）、
`web`（控制台）、`napcat`（QQ 接入）。仍未实跑验证的部分：三个镜像的构建（本机无 Docker CLI）。

---

## 29. QQ 长消息分片与发送节流（2026-11-11）

| 部件 | 说明 |
|---|---|
| `MessageChunker` | 纯函数切分：优先换行、其次空格、最后硬切；段数上限命中时给最后一段加"已截断"标记 |
| `OneBotClient` | 按 `max-message-chars` / `max-segments` 分片顺序发送，段间按 `send-interval` 节流 |
| 配置 | `RELAY_OPS_ONEBOT_MAX_MESSAGE_CHARS`（默认 1000）、`MAX_SEGMENTS`（默认 5）、`SEND_INTERVAL`（默认 500ms） |

四个判断：

1. **宁可分成三条，也不要一条被丢弃**：QQ 对单条长度有限制，超长消息会发送失败；
   分片后即使中途失败，至少前面的段落已经送达。
2. **优先在换行处切**：日报是表格与列表，切在行中间会让人读不懂；找不到换行再退到空格，最后硬切。
3. **段数有上限**：默认 5 段，超出就截断并显式标注 —— 防止某个模板写出无限长内容把群刷屏。
4. **发送节流**：段间默认 500ms，避免同一账号短时间连发被风控；节流只在多段时生效，单条不受影响。

验收：`mvn test` **185 项全绿**（新增 6 项分片测试，含"无边界硬切""段数上限""最小参数兜底"）。

### 仍未做

- 分片失败的中途重试（目前每段独立发送，失败只记日志）
- 按群独立节流（现在是全局间隔）




