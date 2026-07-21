# Harness Agent Contract

## 目的

本文件定义后续 AI Agent 在本仓库执行工程任务时的强制协议。业务事实仍以 `CLAUDE.md`、`docs/README.md` 和 `docs/*.md` 为主源；本 Harness 只把读取、修改、构建、重启、验证、取证、提交和部署流程固定下来。

## 必读入口

1. `AGENTS.md`
2. `CLAUDE.md`
3. `docs/README.md`
4. `docs/harness-maintenance/legacy-rules/state/snapshots/01-当前项目状态.md`
5. `docs/harness-maintenance/legacy-rules/governance/task-routing.md`
6. 当前任务对应的领域、流程、接口、数据、权限、验收和部署文档

## DDD 优化总规则

本项目采用 V1 模块化单体 + DDD 方式持续优化。DDD 优化只约束领域边界、任务顺序、执行证据和反馈同步，不改变当前 Spring Boot / PostgreSQL / Redis / Docker Compose 技术事实。

领域优化顺序固定为：

1. 用户域
2. 配置域
3. 订单域
4. 业绩域
5. 分析模块
6. 商品域
7. 达人域
8. 寄样域
9. Outbox 事件
10. 前端领域化
11. E2E 验收
12. 垃圾回收

所有 DDD 领域优化必须遵守：

- 先读 `docs/harness-maintenance/legacy-rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md` 和 `docs/harness-maintenance/legacy-rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md`。
- 再读对应 `docs/harness-maintenance/legacy-rules/instructions/domain/*.md` 与 `docs/领域/*.md`。
- 修改前必须确认 `docs/harness-maintenance/legacy-rules/governance/forbidden-scope.md`，不得扩大当前范围。
- 一次任务只推进一个主责领域任务卡；跨域影响只记录依赖和验证点。
- 修改后必须执行当前 Scope 的固定验证；docs-only 使用 `Scope=docs`。
- 测试或验证后必须更新 state；失败、阻塞或新风险必须写入 feedback 或 evidence report。
- 业务规则、架构边界或旧文档冲突不得由 AI 自行裁决，必须补证据并回到 ADR 或领域主源。

## 执行主线

任何工程任务必须按以下主线推进：

```text
明确任务
-> 读取上下文
-> 判断领域和 V1 边界
-> 收集证据
-> 最小修改
-> 构建
-> 重启对应 Docker 服务
-> 健康检查
-> 业务验证
-> 旧内容维护计划 / 归档 / 删除候选报告
-> 生成/覆盖稳定 evidence，并内联 retro 结论
-> Git 提交与推送
-> 按用户明确要求提交发布候选，由 Jenkins 队列部署远端
-> 仅在存在可执行改进时生成独立 retro
-> 必要时升级 Harness 并更新 HARNESS_CHANGELOG.md
-> 输出结论和剩余风险
```

## 统一命令入口

默认入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -Message "说明本次修改"
```

`ReportKey` 使用稳定主题键；`OwnedFiles` 逐项列出当前任务拥有的仓库相对路径。自动生成的 evidence、content-retire 报告和归档目标由脚本合并进任务文件集合。后续 Agent 不允许临时发明构建、重启、部署流程。除非用户明确要求 `test`，默认使用本地 `real-pre`；`-DeployRemote true` 已停用，远端只能由 Jenkins 从 `release/real-pre` 部署。

## Completion Gate：禁止提前完成

Agent 不得仅因完成代码修改、文档修改、编译通过、单个接口通过、单个页面可打开而声明 DONE。

除 Gate 0（docs-only）任务外，DONE 必须同时满足：

1. **Scope 已确认**：明确本次任务影响哪些领域、接口、页面、表、脚本、容器。
2. **Code 已落地**：相关后端 / 前端 / SQL / 测试脚本已完成最小必要修改。
3. **Build 已通过**：后端编译 / 测试、前端构建或对应最小验证通过。
4. **Runtime 已加载**：如修改 Java / Vue / SQL / Docker，必须按 real-pre / test 规则重启或确认热更新生效。
5. **Health 已通过**：后端 health、前端 health、容器状态、日志异常检查通过。
6. **Business Flow 已验证**：按任务影响范围跑通最小业务闭环，而不是只测单点。
7. **Evidence 已落盘**：摘要写入 `runtime/qa/out/latest-<topic>.md`；命令输出、截图、日志和 SQL 原文写入 `runtime/qa/out/<run-id>/`。
8. **State 已更新**：更新 DOMAIN_STATUS、CURRENT_STATE、DECISIONS / CHANGELOG 中与本任务相关的状态。
9. **Git 已确认**：展示 `git diff` / `git status`；如用户要求，提交并推送。
10. **Remaining Risks 已说明**：未验证项必须标记 BLOCKED 或 RISK，不能隐藏。

只要任一项缺失，最终状态不得写 DONE，只能写 PARTIAL、BLOCKED_BY_SAMPLE、BLOCKED_BY_EXTERNAL 或 FAILED。

Gate 定义、分类和验证要求见 `docs/harness-maintenance/legacy-rules/governance/COMPLETION_GATES.md`。Agent 必须在任务开始时声明本次选择的 Gate，执行中发现影响范围扩大时必须升级 Gate，不能降级。

## Session Exit Gate

所有 Agent 会话结束前必须执行 `docs/harness-maintenance/legacy-rules/governance/session-exit-gate.md`。

Completion Gate 只证明"本次任务有没有做成"；
Session Exit Gate 证明"仓库是否处于可交接状态"。

最终 DONE 必须同时满足：

1. 任务对应的 Completion Gate 通过。
2. Session Exit Gate 五项硬门禁通过（Build Clean、Test Clean、Progress Recorded、Artifacts Clean、Startup Path Clean）。
3. Evidence 已落盘。
4. State 已更新。
5. Git 状态已说明。

如果任务完成但仓库状态不干净，最终状态必须是 PARTIAL，而不是 DONE。

## Definition of Done

只有同时满足以下条件，才允许声明任务完成：

- 已说明修改范围和影响范围。
- 已选择 Completion Gate 并按 Gate 要求执行验证。
- 代码或文档已按任务要求修改。
- 构建通过，或 `Scope=docs` 明确跳过构建。
- 对应 Docker 服务已重启，或 `Scope=docs` 明确不需要重启。
- 健康检查通过，或明确记录阻塞原因。
- 相关业务验证通过，或明确记录 `BLOCKED` / `PENDING` / `FAIL` 证据。
- 已生成旧内容维护计划，或明确说明本轮无需整理归档删除。
- 已生成或覆盖 `runtime/qa/out/latest-<topic>.md`。
- Git commit 已生成并 push 到当前分支上游，或明确说明本轮被用户要求不提交 / 推送。
- 若用户明确要求发布，候选已进入 `release/real-pre` 与 Jenkins 队列，并记录 Jenkins 结果、远端健康和版本一致性证据。
- Evidence 已内联 retro 结论；只有存在责任人、改进动作和验证方式时才生成独立 retro。
- 剩余风险已列出。
- 统一最终输出模板已按 `docs/harness-maintenance/legacy-rules/governance/COMPLETION_GATES.md` 格式填写。

## Git 工作区治理强约束

任何 Agent 任务必须按 `docs/harness-maintenance/legacy-rules/skills/git/git-change-control.md` 的 Gate 顺序执行：

1. **Git Intake Gate**：任务开始前必须执行 `git status --short` / `git diff --name-only` / `git log -1 --oneline` / `git branch --show-current` / `git remote -v`，并输出 Intake 报告。dirty 不干净时当前任务不得开始；unknown dirty 必须先调查。
2. **Allowed Change Set**：每个任务开始时必须明确本次允许修改的文件目录与命名空间；超出范围的修改必须立即回滚或停止。
3. **Dirty Classification**：所有 dirty 必须归入 `current_task / previous_partial / docs_state / report_only / frontend / backend / sql_migration / docker_deploy / cleanup_retire / unknown` 之一。unknown 文件禁止 stage / commit / push / deploy。
4. **Staged Scope Gate**：提交前必须执行 `git diff --cached --name-only` / `git diff --cached --stat` / `git diff --cached --check`，并逐项确认 6 条通过条件。禁止 `git add .` / `git add -A` / `git add <dir>/`。
5. **Commit Gate**：commit message 必须含类型和 scope（如 `feat(product-ui)` / `fix(user-domain)` / `docs(harness)` / `chore(cleanup)`）。不允许 `git commit --amend`、空 message、`--no-verify` 跳过 hook。
6. **Push Gate**：推送当前分支已配置的 upstream；无 upstream 时设置为 `origin/<current-branch>`。`gitee` 是只读镜像，不作为自动推送目标。
7. **Deploy Commit Gate**：部署前远端必须 `git fetch` + `git checkout` + `git pull --ff-only` 拉到目标 commit；远端 `git rev-parse HEAD` 必须等于目标 commit；远端 `git status --short` 必须为空。禁止从本地 dirty 工作区直接部署，禁止使用未提交代码部署，禁止远端 dirty 源码部署。
8. **Git Exit Gate**：任务结束前必须输出 `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一。`DONE` 状态不得有 unknown dirty。
9. **Unknown Dirty Policy**：unknown 文件必须立即停止 commit / push / deploy，调查摘要写入任务稳定 evidence。
10. **Rollback Policy**：部署失败时把 rollback 证据写入任务稳定 evidence，远端 `git revert`，重新构建/重启，最终状态写 `ROLLBACK_REQUIRED`。
11. **批次提交**：多任务 dirty 必须按 `docs/harness-maintenance/legacy-rules/skills/git/git-batch-submit.md` 划分批次，业务代码批次和 docs / reports 批次必须分开。
12. **禁止行为**：
    - 禁止 `git add .` / `git add -A` / `git add <dir>/`。
    - 禁止提交 unknown 文件。
    - 禁止混合多任务 commit。
    - 禁止从 dirty 工作区部署。
    - 禁止把 PARTIAL 写成 DONE。
    - 禁止把未部署写成已部署。
    - 禁止把未提交写成已推送。

详细规则、命令模板、报告模板见 `docs/harness-maintenance/legacy-rules/skills/git/git-change-control.md` 和 `docs/harness-maintenance/legacy-rules/skills/git/git-batch-submit.md`。

## 状态结论口径 / 证据优先级 / 旧内容维护 / 与现有内容的关系

详见 `agent-contract-extras.md`（拆分自本文件 GC-OPTIMIZE-003 Step 1）。

## 关联

- `agent-contract-extras.md`：补充规范（状态口径 / 证据 / 维护 / 关系）
