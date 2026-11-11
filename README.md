# RelayOps / 上游智控台

RelayOps 是一个面向 New API、Sub2API 等中转上游的运维与监控平台。
当前仓库先建立后端基础骨架，采用 **Spring Boot 模块化单体**，而不是微服务。

## 当前基础能力

- Java 21 + Spring Boot 3.5.6
- PostgreSQL 17/18 + pgvector + Flyway
- MyBatis-Plus 3.5.12
- Redis-backed HTTP Session
- Spring Security 基线
- Spring AI 可选接入（默认关闭）
- Actuator / Prometheus
- 统一响应、错误码、请求 ID
- ArchUnit 模块边界测试
- Testcontainers 与 contract tests 构建入口

## 目录

```text
relay-ops/
├── backend/                    # 单个可部署 Spring Boot 应用
├── docs/
└── compose.yaml                # 本地 PostgreSQL + Redis
```

后端按业务域组织：

```text
com.relayops
├── shared/
├── integration/
├── identity/
├── upstream/
├── monitoring/
├── notification/
├── assistant/
├── operations/
├── scheduling/
└── infrastructure/
```

业务模块内部遵循 `contract / application / domain / adapter` 的依赖方向。
跨模块只允许调用目标模块的 `contract`，禁止直接依赖对方的 entity、repository 或
application service 实现。

## 一键部署（全容器）

要求：Docker（Compose v2）。根目录 `Dockerfile` 三阶段构建**一体化镜像**（前端产物打进后端 jar），
不需要本机 JDK/Node，也不额外起 Nginx 容器。

```powershell
Copy-Item .env.example .env
# 打开 .env，至少确认：
#   RELAY_OPS_DB_PASSWORD / RELAY_OPS_CREDENTIAL_KEY（账号凭证加密）
#   RELAY_OPS_ADMIN_PASSWORD（控制台管理员，首次启动创建）
#   COMPOSE_PROFILES=local-deps,napcat（想用外部数据库就留空并填 DB_URL/REDIS_HOST）
docker compose up -d --build
```

起来之后：

| 地址 | 说明 |
|---|---|
| `http://localhost:8088` | 控制台 + API（同一容器，同源） |
| `http://localhost:8088/actuator/health` | 健康检查 |
| `http://127.0.0.1:6099` | NapCat WebUI（`NAPCAT_WEBUI_TOKEN`） |

`app` 服务内部用容器名访问依赖：数据库 `postgres:5432`、Redis `redis:6379`、OneBot `napcat:3000`，
因此 `.env` 里给宿主机用的地址不会影响容器内的连通性。生产覆盖（`compose.prod.yaml`）还会把
PostgreSQL 发布到 `15342`、Redis 发布到 `16379`，两者都强制密码认证。

## 本机开发

要求：JDK 21、Maven 3.9+、Node 22、Docker。

```powershell
Copy-Item .env.example .env
docker compose up -d postgres redis napcat   # 只起依赖，不起 app
cd backend; mvn spring-boot:run              # 后端在宿主机 8080
cd ..\frontend; npm run dev                  # 前端 5173，/api 代理到 8080
```

`.env.example` 是**唯一的环境变量清单**：应用（`application.yml`）与 `docker compose` 的
所有可调项都从这里注入，代码里不再有硬编码的端口、连接串、线程数或超时。
新增配置项时同步补进 `.env.example`，`ConfigurationReferenceTest` 会在 CI 里检查是否漏登记。

> pgvector：`compose.yaml` 用 pgvector 官方镜像（当前 `pgvector/pgvector:pg18-trixie`），
> `V4__vector_extension.sql` 会建 `vector` 扩展。连外部 PostgreSQL 且应用账号不是超级用户时，
> 需要 DBA 先用超级用户执行一次 `CREATE EXTENSION vector;`，否则 V4 会因权限不足失败。

健康检查：

```text
http://localhost:8080/actuator/health
```

Prometheus 指标默认要求认证；健康检查接口允许匿名访问。

## 前端控制台（Vue 3 + Element Plus）

两种用法：全容器部署时由 Nginx 托管（见上面的 `web` 服务，端口 `RELAY_OPS_WEB_PORT`，默认 8088）；
本机开发时用 Vite：

```powershell
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Vite 把 `/api` 代理到 `http://localhost:8080`。因为 `identity` 还没落地，本地联调需要后端放行 API：
在 `.env` 里把 `RELAY_OPS_DEV_OPEN_API` 改成 `true`（启动日志会打印 WARN 提醒，生产必须保持 `false`）。

页面：运行状态（模型槽位、最近一次播报）、定时任务（启停 / 立即执行 / 未来 10 次触发）、
人设与提示词预览、消息蓝图 dry-run、记忆（写入 / 召回预览）。

构建产物：

```powershell
npm run build      # 输出到 frontend/dist
```

## QQ 群接入（NapCat / OneBot v11）

`compose.yaml` 已经带 `napcat` 服务，只需要给环境变量，其余在 NapCat WebUI 点两下：

```powershell
# 1. .env 里填（其余保持默认即可）
#    NAPCAT_WEBUI_TOKEN=<自己定一个强口令>
#    NAPCAT_ACCOUNT=<QQ 号，留空则首次扫码后写入>
#    RELAY_OPS_ONEBOT_WEBHOOK_TOKEN=<自己定一个强口令>
# 2. 起 NapCat
docker compose up -d napcat
# 3. 打开 WebUI（token 用上面的 NAPCAT_WEBUI_TOKEN）扫码登录
#    http://127.0.0.1:6099
# 4. WebUI → 网络配置：
#    HTTP 服务端：0.0.0.0:3000（容器内固定，compose 已映射到宿主机 127.0.0.1:3000）
#    反向 HTTP 上报地址按后端部署方式二选一：
#      全容器（app 也在 compose 里）：http://app:8080/api/v1/integration/onebot
#      后端跑在宿主机：              http://host.docker.internal:8080/api/v1/integration/onebot
#    Header  Authorization: Bearer <RELAY_OPS_ONEBOT_WEBHOOK_TOKEN>
# 5. .env 设 RELAY_OPS_ONEBOT_ENABLED=true，重启后端（全容器模式会随 app 容器重建）
```

要点：

- `napcat/` 目录存登录态与 NapCat 配置，含敏感数据，已在 `.gitignore` 中排除；
- webhook 默认**失败关闭**：没配 token 或接口未启用时返回 404/401，不会裸奔；
- 群里默认需要 @机器人 才响应（`RELAY_OPS_ONEBOT_REQUIRE_MENTION`），私聊不需要；
- 机器人的播报会优先走 QQ 群（`RoutingMessageSender`），OneBot 未启用时回落邮件。

## 当前基础能力补充

- 动态调度：Quartz JDBC JobStore + 中国工作日历（法定节假日 / 调休补班 / 每月第 N 个工作日）
- 助手内核：提示词分层装配、角色卡、能力包 SPI、工具注册表、模型路由与降级
- 记忆：事实记忆（写入防护 + 混合召回）、会话与滚动摘要、`memory.maintenance` 维护任务
- 消息编排：蓝图分段（文本 / 只读工具 / 人设点评）、dry-run 预览、`assistant.broadcast` 播报任务

## 构建与测试

```powershell
cd backend
mvn test
mvn clean package
```

外部联调测试统一使用 `contract` tag：

```powershell
mvn test -Pcontract-tests
```

没有真实 PostgreSQL、Redis 或上游服务时，不要运行该 profile。

## 接下来建议的落地顺序

1. 在 `identity` 接入真实登录、角色和会话。
2. 在 `upstream/domain` 建立平台、账号、凭证与 API Key 模型。
3. 将原项目 `collector` 的采集逻辑迁移到 `upstream/application/collection`。
4. 将快照、看板和号池逻辑迁移到 `monitoring`。
5. 采集完成后发布领域事件，由 `notification` 通过
   `@TransactionalEventListener(AFTER_COMMIT)` 消费。
6. 最后迁移 `assistant`、`operations` 和动态调度，避免机器人模块成为业务中心。
