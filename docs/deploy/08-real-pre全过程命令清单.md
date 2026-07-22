# real-pre 全流程命令清单

## 日常发布

```text
1. 短分支从最新 main 创建
2. 本地按变更范围验证
3. GitHub PR -> CI Gate
4. 合并 main
5. GitHub Actions 构建 backend/frontend 不可变镜像并记录 digest
6. 从 main 发起到 release/real-pre 的提升 PR
7. 在提升 PR 中提交 release/real-pre.json
8. GitHub 校验 manifest、main 祖先关系和无代码漂移
9. Jenkins saas-real-pre-cd 获取 saas-real-pre-deploy 锁
10. Jenkins 拉取 repository@sha256:digest，执行迁移、部署、smoke、E2E 和证据
11. 失败时按上一份不可变镜像回滚
```

## 发布清单校验

```bash
python3 scripts/verify-real-pre-release.py release/real-pre.json
python3 scripts/hash-real-pre-migration-inputs.py --ref <sourceMainSha>
```

发布清单必须固定：

- `sourceMainSha`：main 上已经通过 CI Gate 的完整 SHA。
- backend/frontend `repository` 和 `sha256` digest。
- `database.migrationVersion` 与 `database.inputSha256`。
- `previous` 上一份可回滚发布；首次部署才允许 `bootstrap=true` 且 `previous=null`。

## Jenkins 节点前置条件

- 已配置容器仓库读取凭据 `saas-container-registry`。
- 已配置 Lockable Resources 资源 `saas-real-pre-deploy`。
- 已准备 `/opt/saas/env/.env.real-pre`，不写入 Git，不输出日志。
- 节点具备 Docker Compose、Python 3、Node / pnpm、curl 和 Git。

## 只读健康检查

```bash
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
docker compose --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active -f docker-compose.real-pre.yml ps
```

## Break-glass 约束

SSH、服务器源码更新、现场 `docker build` 或手工 Compose 仅用于经批准的紧急恢复。必须先获取与 Jenkins 相同的锁、完成备份、使用固定镜像引用、执行健康检查并补齐证据；恢复后要通过正常 PR / 镜像 / Jenkins 链路收敛。

禁止 `docker compose down -v`，禁止删除 PostgreSQL / Redis volume，禁止用 test/mock 开关证明 real-pre 通过。
