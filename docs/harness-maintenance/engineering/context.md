# 工程上下文

本目录只保存工程 skill 的配置，不保存业务事实。

## 开始任务前

按下面顺序读取：

1. `AGENTS.md`：项目协议和安全边界。
2. `CONTEXT.md`：业务词汇和模块边界。
3. `CLAUDE.md`：Java 全栈工程约束。
4. `docs/README.md`：文档地图。
5. 与任务直接相关的 `docs/` 主文档、代码、测试和运行配置。

涉及发布、real-pre 或数据库时，补读：

- `docs/10-部署运行总览.md`
- `docs/验收/real-pre联调手册.md`
- `docs/决策/ADR-010-仓库阶段口径拍板为V2.md`
- `release/README.md`

涉及寄样、商品、订单、达人、业绩或权限时，优先读取对应的 `docs/领域/`、`docs/流程/`、`docs/对接/` 和 `docs/验收/` 文档。

## 工程配置

- `issue-tracker.md`：GitHub Issues 配置。
- `triage-labels.md`：canonical triage 标签。
- `issues-index.md`：Issues 本地镜像。
- `README.md`：本目录入口。

## 事实优先级

用户当前要求 > `AGENTS.md` > 当前 `docs/` 主文档 > `CONTEXT.md` > 本目录配置 > skill 默认流程。

代码、测试、运行配置和真实日志与旧文档冲突时，以可复现证据为准；不要把历史计划当成当前实现。

## 历史目录

`docs/harness-maintenance/legacy-rules/` 只作历史追溯，不是普通开发入口。当前执行入口是 `harness/README.md`、`harness/policy/`、`harness/runbooks/`、`harness/checks/` 和 `harness/scripts/`。
