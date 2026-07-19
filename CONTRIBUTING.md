# 贡献与 Git 协作指南

本仓库采用“并行开发、串行合并、串行发布”。每个任务在独立 worktree 和独立分支中完成，通过 PR 与 CI 进入集成流程；普通开发任务不能直接操作服务器或部署。

## 1. 开始任务

1. 先创建或认领 GitHub Issue，补齐现象、证据、范围和验收标准。
2. 从受保护默认分支 `main` 的最新远端提交创建独立 worktree。
3. 分支使用 `<类型>/<issue>-<slug>`；Codex 默认使用 `codex/<issue>-<slug>`。
4. 执行 Git Intake Gate，确认工作区无来源不明的 dirty 文件。

示例：

```powershell
git fetch origin
git worktree add -b codex/123-task-slug .worktrees/123-task-slug origin/main
```

`main` 是唯一集成主线；`release/real-pre` 只接收已经在 `main` 验证并获准进入 real-pre 发布队列的提交。

## 2. 开发与提交

- 一个 Issue 对应一个短期分支；不要混入其他任务的代码、报告或格式化变更。
- 优先先写失败测试，再实现最小修复，最后执行相关回归。
- 只显式暂存 Owned files，禁止 `git add .`、`git add -A` 和目录级暂存。
- Commit 使用 Conventional Commits，例如 `ci(github): establish collaboration gates`。
- 禁止提交 `.env`、凭证、Token、密码、私钥、证书、构建产物和临时文件。
- Harness 规则变更必须同步 `harness/rules/changelog.md`，并生成稳定 evidence report。

## 3. Pull Request

1. 推送任务分支并创建 Draft PR。
2. PR 必须关联 Issue，列出 Owned files、风险、回滚、数据库影响、部署需求、验证结果和 evidence。
3. 未完成或存在阻塞时保持 Draft，结论使用 `PARTIAL` / `BLOCKED`，不能写成已完成。
4. CI 全部通过并完成评审后才能标记 Ready for review。
5. 禁止直接推送 `main` 或 `release/real-pre`；合并控制器必须逐个确认并串行合并。平台 Merge Queue 可用时必须由队列执行。

高风险路径由 `CODEOWNERS` 指定评审人，包括 `.github/`、Jenkins、Harness、部署脚本、Compose 和数据库迁移目录。

## 4. CI 与验证

每个 PR 至少应得到三个稳定结果：

- `Backend tests`
- `Frontend tests and build`
- `Repository governance`

本地验证必须与变更风险相匹配。仅修改文档、Harness 或 GitHub 配置时，可以使用 `Scope=docs` 跳过应用构建、容器重启和数据库迁移，但仍需执行安全检查、契约测试、Harness 限制检查并生成 evidence。

## 5. 合并、部署与数据库

- 普通开发任务只提交候选变更，不合并、不部署。
- 合并必须串行；发布必须进入唯一 Jenkins 发布队列。
- real-pre 只能部署 `release/real-pre` 上批准提交对应的完整 Git SHA 镜像，不能使用 `latest`、短 SHA 或未合并分支。
- 未包含数据库变更的 Harness / GitHub 治理任务，不要求远端迁移数据库。
- 任何生产发布都需要用户明确授权。

完整执行约束以 `AGENTS.md`、`harness/rules/governance/` 和 `harness/rules/skills/git/` 为准。
