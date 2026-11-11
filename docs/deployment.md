# 部署与运维（VPS）

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
| 8088 | 公网 | 控制台（Nginx），`/api` 同源反代到 app |
| 8080 | 容器内 | 后端，不对外 |
| 5432 / 6379 | 容器内 | PostgreSQL / Redis，不对外 |
| 6099 | 127.0.0.1 | NapCat WebUI，需 SSH 隧道访问 |
| 13000 | 127.0.0.1 | NapCat HTTP 服务，供调试 |

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
