# RelayOps / 上游智控台

RelayOps 是一个面向 New API、Sub2API 等中转上游的运维与监控平台。
当前仓库先建立后端基础骨架，采用 **Spring Boot 模块化单体**，而不是微服务。

## 当前基础能力

- Java 21 + Spring Boot 3.5.6
- PostgreSQL 17 + Flyway
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

## 本地启动

要求：JDK 21、Maven 3.9+、Docker。

```powershell
Copy-Item .env.example .env
docker compose up -d
cd backend
mvn spring-boot:run
```

`.env.example` 是**唯一的环境变量清单**：应用（`application.yml`）与 `docker compose` 的
所有可调项都从这里注入，代码里不再有硬编码的端口、连接串、线程数或超时。
新增配置项时同步补进 `.env.example`，`ConfigurationReferenceTest` 会在 CI 里检查是否漏登记。

健康检查：

```text
http://localhost:8080/actuator/health
```

Prometheus 指标默认要求认证；健康检查接口允许匿名访问。

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
