# 部署与运维（VPS）

## 部署形态由配置决定

`compose.yaml` 里 postgres / redis / napcat 都带 profile，默认**不会**全部启动：

| `.env` 里的 `COMPOSE_PROFILES` | 启动内容 | 适用场景 |
|---|---|---|
| `local-deps,napcat` | app + web + postgres + redis + napcat | 一台机器一键全包（默认） |
| `napcat` | app + web + napcat | 数据库/Redis 用外部服务 |
| 空 | app + web | 只部署应用，依赖全用外部 |

外部模式需要把地址改成容器可达的：

```dotenv
COMPOSE_PROFILES=
RELAY_OPS_DB_URL=jdbc:postgresql://host.docker.internal:5432/relay_ops
RELAY_OPS_REDIS_HOST=host.docker.internal
```

- `host.docker.internal` 由 compose 的 `extra_hosts: host-gateway` 提供，指向宿主机；
- 若外部数据库是另一个 compose 项目的容器，把两个项目接到同一网络，地址写那个容器名；
- `app` 对 postgres/redis 的依赖是 `required: false`，依赖服务不存在时不会阻止启动。

从"自带依赖"切到"外部依赖"时，数据需要迁移：

```bash
docker exec relay-ops-postgres-1 pg_dump -U relay_ops relay_ops | gzip > /app/relay_ops/backup.sql.gz
# 在外部库建库并恢复
gunzip -c /app/relay_ops/backup.sql.gz | psql -h <外部库> -U <用户> -d relay_ops
# 改 .env 的 COMPOSE_PROFILES 与地址，然后
docker compose ... up -d --remove-orphans
# 确认无误后再删除自带依赖容器与数据卷
```

## 目录布局

```text
/app/relay_ops/
├── src/                 # 代码检出（git pull 更新）
│   ├── .env             # 本机环境变量（含密钥，权限 600，不入库）
│   ├── compose.yaml
│   ├── compose.prod.yaml
│   └── compose.ghcr.yaml
├── data/                # 所有持久化数据
│   ├── postgres/        # PostgreSQL 18 数据目录（挂到容器 /var/lib/postgresql）
│   ├── redis/           # Redis AOF
│   ├── napcat/          # QQ 登录态、NapCat 配置
│   └── logs/            # 应用日志（如启用文件输出）
└── *.sh                 # 部署/验收脚本（可选）
```

## 启动

```bash
cd /app/relay_ops/src
docker compose -f compose.yaml -f compose.prod.yaml -f compose.ghcr.yaml up -d
```

- `compose.yaml`：基础定义（postgres/redis/app/web/napcat）
- `compose.prod.yaml`：生产覆盖（只暴露控制台端口、数据落 `RELAY_OPS_DATA_DIR`）
- `compose.ghcr.yaml`：用 GHCR 镜像替代本地构建

## 更新

```bash
cd /app/relay_ops/src
git pull
docker compose -f compose.yaml -f compose.prod.yaml -f compose.ghcr.yaml pull
docker compose -f compose.yaml -f compose.prod.yaml -f compose.ghcr.yaml up -d
```

镜像由 GitHub Actions（`.github/workflows/images.yml`）在每次 push 到 `main` 后构建并推送 GHCR；
Flyway 迁移在应用启动时自动执行。

## 端口

| 端口 | 暴露 | 说明 |
|---|---|---|
| 8088 | 公网 | 控制台 + API（一体化容器，`app` 直接监听 8080） |
| 15342 | 公网 | PostgreSQL（→ 容器 5432），**必须密码**，`scram-sha-256` |
| 16379 | 公网 | Redis（→ 容器 6379），**必须 AUTH**（ACL 账号） |
| 8080 | 容器内 | 后端（含静态资源），不单独发布 |
| 6099 | 127.0.0.1 | NapCat WebUI，需 SSH 隧道访问 |
| 13000 | 127.0.0.1 | NapCat HTTP 服务，供调试 |

> 注意：PostgreSQL 的容器内端口是 **5432**（官方镜像默认），宿主机发布为 15342；
> 如果要改成 5342 之类的容器端口，必须同时改 `postgres` 的 `command: -p` 与镜像健康检查，
> 不建议，保持 15342→5432 最省事。

外部连接示例：

```bash
# PostgreSQL（root 超级用户，用于运维；应用自身仍用 relay_ops 最小权限账号）
psql "host=117.55.228.245 port=15342 user=root dbname=relay_ops"
# JDBC
jdbc:postgresql://117.55.228.245:15342/relay_ops

# Redis（ACL 账号 admin）
redis-cli -h 117.55.228.245 -p 16379 --user admin -a '<RELAY_OPS_REDIS_PASSWORD>' --no-auth-warning ping
```

## 数据库与 Redis 的安全措施

1. **账号分离**：
   - PostgreSQL：应用用 `relay_ops`（最小权限），运维用 `root`（超级用户，仅排障用）；
   - Redis：默认账号保留密码，另建 ACL 账号 `admin`，应用以 `admin` 连接
     （`RELAY_OPS_REDIS_USERNAME` / `RELAY_OPS_REDIS_PASSWORD`）。
2. **口令**：均随机生成，只存在 `/app/relay_ops/src/.env`（权限 `600`），不进仓库；
3. **PostgreSQL**：官方镜像启用 `scram-sha-256`，无密码连接被拒绝
   （实测 `fe_sendauth: no password supplied`）；
4. **Redis**：配置文件里写 `requirepass` 与 `user admin ...`，无认证命令被拒绝
   （实测 `NOAUTH Authentication required.`，`ACL WHOAMI` 返回 `admin`）；
5. **建议再加一层网络限制**：云厂商安全组或 `ufw` 只放行你自己的 IP 访问 15342/16379，
   密码是第二道防线，不是唯一防线。

验证命令（在服务器上执行）：

```bash
# PostgreSQL：应用账号与 root 都能连，无密码必须失败
PGPASSWORD='<应用密码>' docker exec -e PGPASSWORD relay-ops-postgres-1 \
  psql "host=127.0.0.1 port=15342 user=relay_ops dbname=relay_ops" -tAc 'select 1'
PGPASSWORD='<root密码>' docker exec -e PGPASSWORD relay-ops-postgres-1 \
  psql "host=127.0.0.1 port=15342 user=root dbname=relay_ops" -tAc 'select current_user'

# Redis：无密码应 NOAUTH，admin 账号应 PONG
docker exec relay-ops-redis-1 redis-cli -h 127.0.0.1 -p 16379 ping
docker exec relay-ops-redis-1 redis-cli -h 127.0.0.1 -p 16379 --user admin -a '<密码>' --no-auth-warning ping
```

NapCat WebUI 只绑本机，访问方式：

```powershell
ssh -L 6099:127.0.0.1:6099 root@<服务器IP>
# 浏览器打开 http://127.0.0.1:6099
```

## QQ 机器人接入

1. SSH 隧道打开 WebUI，用 `NAPCAT_WEBUI_TOKEN` 登录并扫码；
2. WebUI → 网络配置：
   - HTTP 服务端：`0.0.0.0:3000`
   - 反向 HTTP 上报：`http://app:8080/api/v1/integration/onebot`
     （Header `Authorization: Bearer <RELAY_OPS_ONEBOT_WEBHOOK_TOKEN>`）
3. `.env` 里设 `RELAY_OPS_ONEBOT_ENABLED=true` 并填群号/管理员，然后
   `docker compose ... up -d app`；
4. 控制台「机器人」页可看状态并发送连通性测试。

## 备份与恢复

```bash
# 备份
docker exec relay-ops-postgres-1 pg_dump -U relay_ops relay_ops | gzip > /app/relay_ops/backup-$(date +%F).sql.gz

# 恢复
gunzip -c /app/relay_ops/backup-YYYY-MM-DD.sql.gz | \
  docker exec -i relay-ops-postgres-1 psql -U relay_ops -d relay_ops
```

## 排错

```bash
docker compose -f compose.yaml -f compose.prod.yaml -f compose.ghcr.yaml logs --tail 100 app
docker compose ... ps
curl -s http://127.0.0.1:8088/actuator/health
```

常见问题：

- **PostgreSQL 起不来**：`postgres:18` 要求卷挂在 `/var/lib/postgresql`，挂到 `/var/lib/postgresql/data`
  会报 `unused mount/volume` 并退出。
- **应用起不来且报 `OpenAI API key must be set`**：Spring AI 自动装配缺少 key，
  已通过 `spring.autoconfigure.exclude` 排除，若自行加了其他 Spring AI starter 需同步补上。
- **控制台登录 401/500**：确认 `.env` 里 `RELAY_OPS_ADMIN_USERNAME/PASSWORD` 已设置且容器已重建
  （`docker compose up -d` 会重新注入环境变量）；已有账号时不会覆盖密码。
- **QQ 机器人不回话**：先在「机器人」页看状态提示，再确认 NapCat 反向上报地址与 token 是否一致。

## 安全

- 控制台目前是 HTTP，建议在前面挂 Caddy/Nginx 加 TLS（服务器上已有 Caddy 监听 80）；
- `.env` 含数据库口令、凭证加密密钥、管理员口令，务必保持 `600` 权限；
- `RELAY_OPS_CREDENTIAL_KEY` 丢失会导致已保存的上游凭证无法解密，请纳入备份。
