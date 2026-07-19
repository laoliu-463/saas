# real-pre 单通道 CI/CD 策略

## 1. 不可变原则

> 允许并行开发，禁止并行合并和并行部署。所有 real-pre 部署必须进入 Jenkins 唯一发布队列。

- Codex 任务可在独立 worktree / `codex/*` 分支并行开发、测试、推送并提交 PR。
- 同机独立分析和单元测试仍可并行；涉及共享 Docker、健康检查和业务验证的 `agent-do` 集成执行按环境进入本机唯一运行队列。
- GitHub Merge Queue 串行决定合并顺序，必需 CI 必须响应 `merge_group`。
- 只有 `release/real-pre` 当前提交可构建发布镜像和进入部署队列。
- Jenkins 是唯一发布控制器；普通 Codex 任务不得 SSH、部署、回滚或改服务器工作树。
- 生产分支为 `main`；生产发布需另建审批型 Job，不复用 real-pre Job。

## 2. 发布链路

```text
独立分支 → PR/CI → Merge Queue → release/real-pre
→ GitHub Actions 构建并推送一次 → 完整 SHA + digest
→ Jenkins 全局队列 → 串行迁移 → digest 固定部署 → 五项版本验证 → 发布记录
```

GitHub 主源固定为 `https://github.com/laoliu-463/saas.git`。Gitee 只可作为只读镜像，不参与发布判定。

## 3. CI 镜像契约

`.github/workflows/release-images.yml` 只在 `release/real-pre` 上运行：

- 后端与前端镜像 tag 必须等于 40 位完整 Git SHA；
- OCI `org.opencontainers.image.revision` 必须等于同一 SHA；
- CI 构建一次并推送镜像仓库，输出两个 `sha256` digest；
- 禁止 `latest`、短 SHA、`real-pre` 可变发布 tag 和服务器现场构建；
- CI 只把 SHA、镜像仓库和 digest 传给 Jenkins，不直接操作服务器。

仓库需配置：

- Variables：`REAL_PRE_REGISTRY`、`REAL_PRE_BACKEND_IMAGE_REPOSITORY`、`REAL_PRE_FRONTEND_IMAGE_REPOSITORY`、`REAL_PRE_JENKINS_JOB`；
- Secrets：`REAL_PRE_REGISTRY_USERNAME`、`REAL_PRE_REGISTRY_PASSWORD`、`REAL_PRE_JENKINS_URL`、`REAL_PRE_JENKINS_USER`、`REAL_PRE_JENKINS_TOKEN`。

## 4. Jenkins 队列契约

`Jenkinsfile` 同时使用：

- `disableConcurrentBuilds(abortPrevious: false)`：同一 Job 排队，禁止新任务取消旧部署；
- `lock(resource: 'saas-real-pre-deploy')`：跨 Job 共享 real-pre 全局锁。

Jenkins 凭证 ID 固定为 `saas-real-pre-registry`。输入为：

- `TARGET_GIT_SHA`：40 位完整 SHA；
- `BACKEND_IMAGE` / `FRONTEND_IMAGE`：不含 tag/digest 的仓库路径；
- `BACKEND_IMAGE_DIGEST` / `FRONTEND_IMAGE_DIGEST`：CI 产生的 digest；
- `ROLLBACK_APPROVED`：普通发布必须为 `false`，明确回滚才允许 `true`。

Jenkins 不运行 Maven、pnpm 或 Docker build，只拉取和部署 CI 产物。

## 5. 防降级与不可变目录

`scripts/deploy-release.sh` 必须校验：

- HEAD、发布清单、镜像 tag、OCI revision 都等于目标完整 SHA；
- 本地 RepoDigest 与发布清单 digest 一致；
- `git merge-base --is-ancestor CURRENT_SHA TARGET_SHA` 成立；
- 非后继提交只有 `ROLLBACK_APPROVED=true` 才可继续；
- 调用方必须提供 `RELEASE_CONTROLLER=jenkins`。

服务器目录固定为：

```text
/opt/saas/env/.env.real-pre       固定配置，不由任务修改
/opt/saas/releases/<完整SHA>/     不可变 release.json、Compose、运行证据
/opt/saas/releases/current.json   验证通过后的当前版本
/opt/saas/releases/previous.json  上一个可回滚版本
```

禁止在 `/opt/saas/app` 执行 `git pull`、`checkout`、`reset`、改 Compose 或现场构建。

## 6. 运行版本门禁

只有以下事实全部一致才允许写 `DEPLOYMENT=PASS` 并更新 `current.json`：

1. Jenkins 目标 Git SHA；
2. 后端 `/api/system/health` 的 `gitSha`；
3. 前端 `/version.json` 的 `gitSha`；
4. 后端/前端镜像 tag、OCI revision 与 digest；
5. 数据库 `schema_migration_log` 与 `flyway_schema_history` 版本。

后端还必须返回 `imageDigest`、`databaseMigrationVersion` 和 `flywayVersion`。`UNAVAILABLE`、`NOT_MANAGED`、缺字段或不一致都必须失败。

当前 `.env.real-pre.example` 将 `FLYWAY_ENABLED=false` 作为安全默认值。正式切换前须先完成迁移演练并在服务器固定配置中显式启用；未启用时发布会被版本门禁阻断，不得降级为 PASS。

## 7. 旧入口

以下入口已停用并必须保持阻断：

- `harness/scripts/commands/deploy-remote.ps1`；
- `scripts/deploy-real-pre.sh`；
- `scripts/rollback-real-pre.sh`；
- `agent-do.ps1 -DeployRemote true`。

Harness 只负责本地验证、生成候选证据和记录 Jenkins 发布结果，不拥有远端部署权限。
