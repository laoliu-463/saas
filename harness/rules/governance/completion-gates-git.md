# Completion Gates - Git 子门禁

> 主文件：[COMPLETION_GATES.md](COMPLETION_GATES.md)
> 详细 Git 规则见：[git-change-control.md](../skills/git/git-change-control.md)

任何 Gate 都必须按 `harness/rules/skills/git/git-change-control.md` 执行下列 Git 子门禁。

## Gate G0：Docs-only clean

适用：纯文档 / Harness 规则 / 状态文件 / 报告变更。

- `git diff --name-only` 仅含 `harness/` / `docs/` / `AGENTS.md` / `CLAUDE.md`
- `git diff --check` 无输出
- 状态文件变更已记录
- 未使用 `git add .` / `git add -A`
- commit message 含类型和 scope

## Gate G1：Frontend clean

适用：纯前端变更。

- `git diff --name-only` 仅含 `frontend/src/` 等前端文件
- frontend `npm run build` + vitest 通过
- commit message 含类型和 scope，不含 backend/SQL/Docker

## Gate G2：Backend clean

适用：纯后端变更。

- `git diff --name-only` 仅含 `backend/src/` / `backend/pom.xml`
- `mvn test` + `mvn package` 通过
- commit message 含类型和 scope，不含 frontend/SQL/Docker

## Gate G3：Deploy clean

适用：Docker / Compose / env / 部署脚本变更。

- 候选提交已推送到当前 upstream，PR/CI 证据可追踪
- real-pre 只接受 `release/real-pre` 当前 40 位完整 SHA
- CI 镜像 tag、OCI revision、digest 与发布清单一致
- Jenkins 全局锁和运行版本一致性门禁通过；普通任务不得直接部署
- `.env` 文件未 commit

## Gate G4：Session clean

适用：所有任务结束 / Session Exit Gate。

- `git status --short` 输出已分类
- 所有 dirty 归入十种分类之一
- 不存在 unknown dirty
- 当前任务已 commit + push
- 终态为 `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一

## 强制规则

- Gate G0-G4 任一未通过，最终状态不得 DONE。
- 同一 commit 不得跨 Gate。
- 业务代码 commit 不得含状态文件（除非范围清晰）。
- 多任务 dirty 必须分批提交。
