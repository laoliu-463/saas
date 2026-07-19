# real-pre 发布文档索引

<<<<<<< HEAD
## 当前发布事实

日常流程固定为：

```text
短分支 -> GitHub PR / CI Gate -> main -> GitHub immutable images
       -> release/real-pre promotion PR -> Jenkins saas-real-pre-cd
```

- `main` 是唯一集成主线。
- `release/real-pre` 是唯一远端 real-pre 部署来源。
- GitHub Actions 负责检查和在 `main` 合并后构建一次镜像。
- CI 的前端 job 只运行测试和 typecheck；生产前端构建只由镜像构建 workflow 执行一次。
- Jenkins 负责拉取发布清单中的镜像摘要、锁定部署、迁移、验收、证据和回滚。
- 普通 Agent 不直接 SSH、不在服务器 `git pull`、不在服务器构建镜像。

## 核心入口

- Compose：`docker-compose.real-pre.yml`
- 环境示例：`.env.real-pre.example`
- 发布清单说明：`release/README.md`
- 发布清单校验：`python3 scripts/verify-real-pre-release.py release/real-pre.json`
- Jenkins CD：`Jenkinsfile`
- Jenkins 规则：[07-Jenkins自动化部署规划.md](07-Jenkins自动化部署规划.md)
- 回滚排障：[06-回滚与故障排查.md](06-回滚与故障排查.md)
- 环境参数：[08-real-pre参数开关契约.md](08-real-pre参数开关契约.md)

## Jenkins 前置条件

- Jenkins 节点可以访问容器仓库并使用受控凭据 `saas-container-registry`。
- Jenkins 配置 Lockable Resources 资源 `saas-real-pre-deploy`。
- 服务器环境文件由服务器受控保存：`/opt/saas/env/.env.real-pre`。
- real-pre 的真实开关必须保持真实模式；缺少 Token、订单或上游样本时记录 `BLOCKED` / `PENDING`。

## 破例恢复

SSH、服务器源码更新、现场构建和手工 Compose 仅作为审批后的 Break-glass 紧急恢复，并且必须使用与 Jenkins 相同的锁、备份、健康检查和证据流程。它不是日常发布入口，恢复后必须通过正常 PR / 镜像 / Jenkins 链路收敛。

## 禁止事项

- 不提交 `.env.real-pre`、密码、Token、私钥或服务器连接信息。
- 不执行 `docker compose down -v`，不删除 PostgreSQL / Redis volume。
- 不使用 `latest`、短 SHA 或无 digest 的浮动镜像。
- 不把 `BLOCKED`、`PENDING` 或 `PARTIAL` 写成 `PASS`。
=======
## 当前执行主源

1. `docs/10-部署运行总览.md`：环境、分支、目录和发布总口径。
2. `harness/rules/cicd-real-pre-policy.md`：CI/Jenkins 强制门禁。
3. `harness/rules/runbooks/remote-deploy.md`：普通任务与发布控制器职责。
4. `Jenkinsfile`：唯一远端发布队列。
5. `.github/workflows/release-images.yml`：完整 SHA 镜像构建与入队。
6. `scripts/deploy-release.sh`：digest 固定部署、防降级和版本一致性验证。

## 历史文档状态

本目录下以下旧手册描述 SSH、`/opt/saas/app`、`git pull`、现场构建或手工回滚，均降级为历史排障资料，不得照其命令执行：

- `00-服务器部署总览.md`；
- `01-xshell-manual-deploy.md`；
- `02-Docker手动部署real-pre.md`；
- `06-回滚与故障排查.md`；
- `07-Jenkins自动化部署规划.md`；
- `08-real-pre全过程命令清单.md`。

旧 `deploy-real-pre.sh`、`rollback-real-pre.sh` 和 Harness `deploy-remote.ps1` 已改为阻断入口。若历史文档与当前主源冲突，以当前主源为准。

## 当前边界

- 普通 Codex 任务：本地验证、提交、推送、PR、候选 evidence。
- Merge Queue：串行合并。
- CI：构建并推送完整 SHA 镜像，产出 digest。
- Jenkins：串行迁移、部署、回滚、五项版本验证和发布记录。
- 生产：另建审批型流程，不得复用 real-pre Job 直接发布。
>>>>>>> b8cb837b (refactor: establish real-pre single-channel CD)
