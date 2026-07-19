# real-pre 发布文档索引

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
