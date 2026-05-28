# Xshell 手工部署 real-pre

本文面向第一次在服务器部署本项目的人。目标是先把 real-pre 环境稳定跑起来，后续再接 Jenkins。

## 一、前置约束

- 不执行 `docker compose down -v`。
- 不删除 Docker volume。
- 不把 `.env.real-pre` 提交到仓库。
- 不把 PostgreSQL `5432` 或 Redis `6379` 暴露到公网。
- real-pre 必须保持 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`。
- 健康检查使用公开探针：`/api/system/health`。

## 二、步骤 1：登录服务器

在 Xshell 中新建会话，填写服务器 IP、端口、用户名，登录后确认当前用户：

```bash
whoami
pwd
```

建议项目部署目录：

```bash
sudo mkdir -p /opt/saas
sudo chown -R "$USER":"$USER" /opt/saas
```

## 三、步骤 2：安装基础工具

Ubuntu / Debian：

```bash
sudo apt-get update
sudo apt-get install -y git curl ca-certificates gnupg lsb-release
```

CentOS / Rocky Linux：

```bash
sudo yum install -y git curl ca-certificates yum-utils
```

## 四、步骤 3：安装 Docker

Ubuntu / Debian：

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

CentOS / Rocky Linux：

```bash
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

把当前用户加入 docker 组，然后重新登录 Xshell：

```bash
sudo usermod -aG docker "$USER"
exit
```

重新登录后验证：

```bash
docker --version
docker compose version
```

## 五、步骤 4：拉取代码

```bash
cd /opt/saas
git clone https://github.com/laoliu-463/saas.git .
git status
```

如果目录已存在：

```bash
cd /opt/saas
git fetch origin
git status
```

只有确认没有未提交的本地代码改动后，才执行：

```bash
git pull --ff-only
```

## 六、步骤 5：创建 .env.real-pre

```bash
cd /opt/saas
cp .env.real-pre.example .env.real-pre
chmod 600 .env.real-pre
vi .env.real-pre
```

至少必须手动修改：

- `DB_PASSWORD`
- `ADMIN_PASSWORD`
- `JWT_SECRET`
- `DOUYIN_APP_ID`
- `DOUYIN_CLIENT_KEY`
- `DOUYIN_CLIENT_SECRET`
- `DOUYIN_OAUTH_REDIRECT_URI`
- `DOUYIN_OAUTH_FRONTEND_SUCCESS_URL`
- `DOUYIN_OAUTH_FRONTEND_FAILURE_URL`

必须保持：

```dotenv
SPRING_PROFILES_ACTIVE=real-pre
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
BACKEND_HOST_PORT=8081
FRONTEND_HOST_PORT=3001
```

说明：`ADMIN_PASSWORD` 只在 PostgreSQL volume 首次初始化时用于创建/更新默认管理员。已有 volume 不会因为改 `.env.real-pre` 自动重跑初始化 SQL。

## 七、步骤 6：启动服务

先给脚本执行权限：

```bash
cd /opt/saas
chmod +x scripts/deploy-real-pre.sh scripts/health-check.sh scripts/backup-db.sh scripts/rollback-real-pre.sh
```

静态校验 Compose：

```bash
docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml config >/tmp/saas-real-pre-compose.yml
```

启动：

```bash
./scripts/deploy-real-pre.sh
```

如果需要部署前拉最新代码：

```bash
./scripts/deploy-real-pre.sh --pull
```

`--pull` 使用 `git pull --ff-only`。如果拉取失败，脚本会停止，不会删除 volume。

## 八、步骤 7：查看容器

```bash
docker ps
docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml ps
```

期望服务：

- `postgres-real-pre`
- `redis-real-pre`
- `backend-real-pre`
- `frontend-real-pre`

## 九、步骤 8：健康检查

```bash
./scripts/health-check.sh
```

手工检查后端：

```bash
curl -fsS http://127.0.0.1:8081/api/system/health
```

期望返回包含：

```json
{"status":"UP"}
```

手工检查前端：

```bash
curl -I http://127.0.0.1:3001
```

## 十、步骤 9：浏览器访问

如果服务器安全组和防火墙已放行端口：

```text
http://服务器IP:3001
```

后端健康地址：

```text
http://服务器IP:8081/api/system/health
```

不要对公网开放：

- PostgreSQL `5432`
- Redis `6379`

## 十一、步骤 10：失败排查

查看服务状态：

```bash
docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml ps
```

查看后端日志：

```bash
docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml logs --tail=300 backend-real-pre
```

查看前端日志：

```bash
docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml logs --tail=300 frontend-real-pre
```

查看数据库日志：

```bash
docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml logs --tail=300 postgres-real-pre
```

查看 Redis 日志：

```bash
docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml logs --tail=300 redis-real-pre
```

常见问题：

- 后端起不来：先看 `backend-real-pre` 日志，重点检查 `SPRING_PROFILES_ACTIVE`、数据库连接、Redis 连接、`JWT_SECRET`、抖音必填项。
- 前端能打开但接口失败：检查 `frontend/nginx/default.conf.template` 渲染后的 upstream 是否指向 `backend-real-pre:8080`，再看后端健康检查。
- 数据库初始化后管理员密码不变：已有 PostgreSQL volume 不会重新执行初始化 SQL。需要单独设计密码重置方案，不能删除 volume。
- 端口访问失败：检查云厂商安全组、服务器防火墙、`docker ps` 端口映射。
- Compose 校验失败：先运行 `docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml config` 看具体缺失变量。

## 十二、备份与回滚

手工备份数据库：

```bash
./scripts/backup-db.sh
```

备份目录：

```text
/opt/saas/backup
```

回滚到上一个 Git commit：

```bash
./scripts/rollback-real-pre.sh HEAD~1
```

回滚脚本会先执行数据库备份，再切换代码，再重新 `docker compose up -d --build`，最后执行健康检查。它不会删除 volume。
