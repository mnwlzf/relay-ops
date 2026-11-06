# RelayOps 后端架构

## 架构决策

采用 **模块化单体 + 端口/适配器**：

- 一个 Spring Boot Jar；
- 一个 PostgreSQL；
- 一个 Redis；
- 不拆微服务，不引入 MQ；
- 按业务域划分顶级包；
- 业务模块内部按 `contract / application / domain / adapter` 分层；
- 跨模块同步查询通过 `contract` 接口，状态变化通过领域事件；
- 使用 ArchUnit 防止模块边界回退。

## 模块职责

| 模块 | 职责 |
|---|---|
| `identity` | Web 用户、角色、Bot 管理员、QQ 绑定、访问策略 |
| `upstream` | 平台、账号、凭证、API Key、渠道/分组、采集编排 |
| `monitoring` | 用量/余额快照、看板、变更时间线、号池和状态监控 |
| `notification` | 邮件、QQ 推送、收件人、通知设置、告警分发 |
| `assistant` | OneBot 接入、命令、AI 工具、人格、归档、抽奖、媒体 |
| `operations` | 错误诊断、事故、自愈计划和执行记录 |
| `scheduling` | 动态定时任务、Cron 与执行状态 |
| `integration` | OneBot、Sub2API、Sub2 只读数据库协议适配 |
| `shared` | 异常、统一响应、时间、脱敏和极小通用工具 |
| `infrastructure` | Spring、MyBatis、安全、Redis、执行器等应用级配置 |

## 依赖规则

```text
assistant     -> identity / upstream / monitoring / notification / operations.contract
operations    -> upstream.contract / monitoring.contract
monitoring    -> upstream.contract
notification  -> upstream.contract / monitoring.contract / operations.contract
upstream      -> shared / integration
identity      -> shared
scheduling    -> shared
integration   -> shared
```

禁止：

- Controller 直接调用 Repository；
- 跨模块引用 `entity`、`repository` 或 application service 实现；
- `upstream`、`monitoring` 反向依赖 `assistant`；
- `notification` 直接读取采集 Repository；
- `shared` 中出现业务 DTO。

## 跨模块通信

同步只读查询使用 `contract` 中的 Port 和只读 record。
状态变化使用领域事件，例如：

- `AccountCollectedEvent`
- `UpstreamChangedEvent`
- `PoolHealthChangedEvent`

通知消费者应使用：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

关键告警后续如果需要可靠投递，再引入 Outbox 表，而不是现在直接上 MQ。

## 包结构

```text
com.relayops
├── RelayOpsApplication.java
├── shared/
│   ├── error/
│   ├── web/
│   └── time/
├── integration/
│   ├── onebot/
│   ├── sub2api/
│   └── sub2db/
├── identity/
│   ├── contract/
│   ├── application/
│   ├── domain/
│   └── adapter/{in,out}/
├── upstream/
│   ├── contract/{,event}/
│   ├── application/{platform,account,credential,apikey,collection}/
│   ├── domain/
│   └── adapter/{in/out}/
├── monitoring/
│   ├── contract/{,event}/
│   ├── application/{dashboard,usage,change,pool,status}/
│   ├── domain/
│   └── adapter/{in,out}/
├── notification/
│   ├── contract/
│   ├── application/{alert,recipient,settings}/
│   ├── domain/
│   └── adapter/{in,out}/
├── assistant/
│   ├── application/{message,command,tool,persona,archive,lottery,media}/
│   ├── domain/
│   └── adapter/{in,out}/
├── operations/
│   ├── contract/{,event}/
│   ├── application/{incident,plan,execution}/
│   ├── domain/
│   └── adapter/{in,out}/
├── scheduling/
│   ├── contract/
│   ├── application/
│   └── adapter/{in,out}/
└── infrastructure/
    ├── config/
    ├── persistence/mybatis/handler/
    └── executor/
```

## 迁移原则

重构期间保持前端 URL 和响应结构不变。先迁低风险的 `shared`、`integration`、
`scheduling`，再打破当前项目中的关键循环依赖：

1. `api <-> bot`
2. `mail -> collector.repository`
3. `ops -> bot.error / bot.report`

不要在结构调整的同时升级 Spring Boot、MyBatis-Plus 或 Spring AI 大版本，
也不要在第一轮同时拆 Maven 多模块。
