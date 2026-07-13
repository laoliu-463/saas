# .claude 智能体工作台

更新时间：2026-05-26

## 定位

[V1 必做] `.claude/` 存放智能体执行资料，不存业务百科。业务事实放 `docs/`，本目录只放可执行规则、工作流和工具边界。

[V1 必做] 进入任务时先读 `CLAUDE.md`，再按任务类型读取本目录对应子项。

[历史归档] 旧 `.claude/skills/debug-issue`、`explore-codebase`、`refactor-safely`、`review-changes` 保留为兼容入口；新任务优先使用本次新增中文 skill。

## 目录

[V1 必做] 运行时 Hook 以 `.claude/settings.json` 为准；工程门禁统一使用 `harness/rules/`，不在 `.claude/` 维护第二套守卫文档。

[V1 必做] `skills/`：面向任务的智能体技能。

[V1 必做] `plugins/`：组合技能和命令的交付包。

[V1 必做] `lsp/`：语言服务诊断口径。

[V1 必做] `mcp/`：MCP 服务器、权限和安全边界。

[V1 必做] `subagents/`：角色化审计代理说明。

[V1 必做] `commands/`：常用任务命令手册。

[V1 必做] `memory/`：短事实，不替代 `docs/`。

[V1 必做] `qa/`：验收映射与证据索引。

[V1 必做] `templates/`：领域、流程、API、事件、测试、ADR、Runbook 模板。

## 使用规则

[V1 必做] 智能体必须先查 `memory/项目事实.md` 和相关领域合同，再执行命令。

[V1 必做] 任何 real-pre 操作必须先读 `harness/rules/governance/forbidden-scope.md`、`harness/rules/environment/envs/real-pre-env.md` 与 `mcp/安全边界.md`。

[V1 必做] 所有验收输出必须更新或引用 `.claude/qa/证据索引.md` 与 `docs/验收/验收证据索引.md`。
