# real-pre 单机受控部署手册

本文面向服务器第一轮 real-pre 部署。当前目标是“手动受控部署 -> 跑门禁 -> 观察真实订单 -> 再决定是否放量”，不是高可用、Jenkins、蓝绿发布。

## 一、部署结论口径

- 允许：服务器 real-pre 受控部署。
- 不允许：宣称 real-pre P0 完全通过 / 可正式生产全量上线。
- 当前部署目标：让真实订单进入系统后验证订单同步、`pick_source` 归因、寄样自动完成、业绩双轨金额。

部署成功后报告只能按以下三类归档：

| 结果 | 结论 |
| --- | --- |
| 无失败 + 无真实订单 | real-pre 环境部署成功，P0 仍因真实样本不足保持 PENDING |
| 无失败 + 有真实订单 + 归因 / 寄样 / 业绩通过 | real-pre P0 可升级为通过 |
| 出现失败 | 按失败项定级回滚或修复，不得放量 |

## 二、推进顺序

```text
本地最终打包
-> 服务器初始化
-> 上传/拉取代码
-> 配置 real-pre 环境变量
-> 启动 PostgreSQL / Redis / 后端 / 前端 / Nginx
-> 跑健康检查
-> 跑 real-pre 三组 E2E 门禁
-> 观察真实订单回流
-> 出部署验收报告
```

## 三、服务器目录

```bash
sudo mkdir -p /opt/saas/app
sudo mkdir -p /opt/saas/env
sudo mkdir -p /opt/saas/logs
sudo mkdir -p /opt/saas/backups
sudo mkdir -p /opt/saas/runtime/qa/out
sudo chown -R "$USER":"$USER" /opt/saas
```

目录用途：

| 目录 | 用途 |
| --- | --- |
| `/opt/saas/app` | 项目代码 |
| `/opt/saas/env` | 环境变量文件，禁止提交 Git |
| `/opt/saas/logs` | 部署和容器日志快照 |
| `/opt/saas/backups` | PostgreSQL 备份 |
| `/opt/saas/runtime/qa/out` | QA 证据目录 |

## 四、基础组件

服务器至少具备：

```bash
docker --version
docker compose version
git --version
```

Ubuntu / Debian 安装示例：

```bash
sudo apt-get update
sudo apt-get install -y git curl ca-certificates gnupg lsb-release
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

执行 `usermod` 后需要重新登录服务器。

## 五、拉取代码

推荐服务器直接拉仓库：

```bash
cd /opt/saas
git clone https://github.com/laoliu-463/saas.git app
cd /opt/saas/app
git checkout main
git pull --ff-only
git rev-parse --short HEAD
```

如果服务器不能连 GitHub，可以本地打包上传后解压到 `/opt/saas/app`。

## 六、配置 real-pre 环境变量

```bash
cp /opt/saas/app/.env.real-pre.example /opt/saas/env/.env.real-pre
chmod 600 /opt/saas/env/.env.real-pre
vi /opt/saas/env/.env.real-pre
```

必须保持：

```dotenv
COMPOSE_PROJECT_NAME=saas-active
SPRING_PROFILES_ACTIVE=real-pre
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false
DB_NAME=saas_real_pre
BACKEND_HOST_PORT=8081
FRONTEND_HOST_PORT=3001
```

必须填真实值或强密码：

- `DB_PASSWORD`
- `ADMIN_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `DOUYIN_APP_ID`
- `DOUYIN_CLIENT_KEY`
- `DOUYIN_CLIENT_SECRET`
- `DOUYIN_OAUTH_REDIRECT_URI`
- `DOUYIN_OAUTH_FRONTEND_SUCCESS_URL`
- `DOUYIN_OAUTH_FRONTEND_FAILURE_URL`
- `CORS_ALLOWED_ORIGIN_PATTERNS`

说明：`ADMIN_PASSWORD` 只在 PostgreSQL volume 首次初始化时参与默认管理员初始化；已有 volume 不会因为改 env 自动重跑初始化 SQL。

## 七、启动 real-pre

```bash
cd /opt/saas/app
chmod +x scripts/deploy-real-pre.sh scripts/health-check.sh scripts/backup-db.sh scripts/run-real-pre-db-migrations.sh scripts/rollback-real-pre.sh
```

先静态渲染 compose：

```bash
export REAL_PRE_ENV_FILE=/opt/saas/env/.env.real-pre
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  config >/tmp/saas-real-pre-compose.yml
```

执行受控部署：

```bash
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/deploy-real-pre.sh
```

如需部署前拉最新代码：

```bash
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/deploy-real-pre.sh --pull
```

脚本会执行：

```text
环境变量守卫
-> docker compose config
-> 启动 PostgreSQL / Redis
-> 备份 PostgreSQL 到 /opt/saas/backups
-> 执行 scripts/run-real-pre-db-migrations.sh
-> 构建并启动 backend / frontend
-> /api/system/health 与前端端口验活
-> 写入 /opt/saas/logs/deploy-real-pre-*.log
```

## 八、健康检查

```bash
docker ps
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/health-check.sh
curl -fsS http://127.0.0.1:8081/api/system/health
curl -I http://127.0.0.1:3001
```

期望后端返回：

```json
{"status":"UP"}
```

查看日志：

```bash
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  logs --tail=300 backend-real-pre
```

重点确认：

```text
activeProfiles=real-pre
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false 或未开启
订单同步任务已启动
抖音 API 请求无系统性 401/403/5xx
```

## 九、本地三组 E2E 门禁

在本地项目根目录执行，目标地址改成服务器 real-pre 地址。

PowerShell 示例：

```powershell
$env:E2E_BASE_URL="http://服务器IP:3001"
$env:E2E_BACKEND_URL="http://服务器IP:8081"
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:p0
npm run e2e:real-pre:roles
```

如果通过 Nginx 域名统一反代 `/api`，可把两个变量都指向同一个域名：

```powershell
$env:E2E_BASE_URL="https://real-pre.xxx.com"
$env:E2E_BACKEND_URL="https://real-pre.xxx.com"
```

判断规则：

| 结果 | 处理 |
| --- | --- |
| preflight 失败 | 环境变量、连通性、真实开关、数据库或接口配置有问题，不能继续 |
| roles 失败 | 权限域不能过，不能给业务使用 |
| p0 FAIL | 核心链路有硬失败，需要修复 |
| p0 PENDING | 没有真实订单 / 成交 / 业绩样本，不等于代码失败 |

## 十、真实订单观察

进入 PostgreSQL：

```bash
docker exec -it saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre
```

订单同步：

```sql
select count(*) from colonelsettlement_order;

select
  order_id,
  product_id,
  colonel_activity_id,
  pick_source,
  channel_user_id,
  talent_id,
  order_amount,
  settle_amount,
  estimate_service_fee,
  effective_service_fee,
  create_time,
  settle_time,
  attribution_status
from colonelsettlement_order
where deleted = 0
order by create_time desc
limit 20;
```

归因映射：

```sql
select
  id,
  pick_source,
  product_id,
  activity_id,
  user_id,
  promotion_link_id,
  colonel_buyin_id,
  source_type,
  create_time,
  update_time
from pick_source_mapping
where deleted = 0
order by update_time desc nulls last, create_time desc
limit 20;
```

寄样自动完成：

```sql
select
  id,
  request_no,
  status,
  channel_user_id,
  talent_uid,
  product_id,
  ship_time,
  deliver_time,
  complete_time,
  update_time
from sample_request
where deleted = 0
order by create_time desc
limit 20;
```

业绩双轨金额：

```sql
select
  order_id,
  product_id,
  activity_id,
  talent_id,
  final_channel_user_id,
  estimate_channel_commission,
  effective_channel_commission,
  estimate_recruiter_commission,
  effective_recruiter_commission,
  estimate_gross_profit,
  effective_gross_profit,
  order_create_time,
  settle_time,
  calculated_at
from performance_records
order by calculated_at desc nulls last, created_at desc
limit 20;
```

如果列名与服务器实际 schema 不一致，先执行 `\d 表名`，以实体类和迁移结果为准。

## 十一、Nginx 暴露

初期只暴露 real-pre 域名，不暴露 PostgreSQL / Redis。

```nginx
server {
    listen 80;
    server_name real-pre.xxx.com;

    location /api/ {
        proxy_pass http://127.0.0.1:8081/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:3001/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

HTTPS 证书可在第一轮 HTTP 受控验证通过后再接入。

## 十二、回滚

部署前记录 commit：

```bash
cd /opt/saas/app
git rev-parse --short HEAD
```

应用回滚：

```bash
cd /opt/saas/app
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/rollback-real-pre.sh 上一个稳定commit
```

默认策略：先回滚应用，不随意回滚数据库。只有迁移破坏表结构且有明确证据时，才评估数据库恢复。

## 十三、部署验收报告模板

```text
服务器 real-pre 受控部署完成。
commit:
环境健康检查:
real-pre 测试开关:
真实 upstream 模式:
真实推广写开关:
E2E preflight:
E2E p0:
E2E roles:
真实订单回流:
PENDING / FAIL 明细:
结论:
```
