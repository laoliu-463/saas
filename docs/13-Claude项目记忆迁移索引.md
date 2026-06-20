# Claude 项目记忆迁移索引

> 更新时间：2026-06-20
> 来源：`C:\Users\caojianing\.claude\projects\d--Projects-SAAS\memory\` 与 `C:\Users\caojianing\.claude\archive\cleanup-20260619-134941\projects-memory-snapshot\`。

## 迁移结论

- 已读取当前 Claude 项目记忆：`MEMORY.md`、`adr-and-context-paths.md`、`existing-hooks-cover-git-guardrails.md`。
- 已读取 2026-06-19 清理归档快照 7 个文件：`project.md`、`approach.md`、`ddd-config-004-event-compat.md`、`ddd-product-005-quick-sample-port.md`、`dead-code-api-layer.md`、`dead-code-comprehensive.md`、`mattpocock-skills-install.md`。
- 本文只做迁移索引和可信度分层；当前事实仍以代码、ADR、docs、harness evidence 为准。

## 当前有效记忆

### ADR 与 Context 路径

- ADR 实际路径：`docs/决策/`，不是 `docs/adr/`。
- CONTEXT 实际路径：仓库根 `CONTEXT.md`，不是 `docs/CONTEXT.md`。
- 当用户口头说 `docs/adr/` 或 `docs/CONTEXT.md` 时，先确认真实路径，不要默默新建冲突文件。
- 可选处理：复用现有路径、迁移并同步、或建立指针文件；需要用户确认。

### Git Guardrails Hook

- 用户全局 `C:\Users\caojianing\.claude\hooks\hooks.json` 已有 PreToolUse Bash 守卫：
  `block-no-verify`、`git-push-reminder`、`commit-quality`。
- `git-guardrails-claude-code` skill 可作为技能来源使用，但不要再把它的 `block-dangerous-git.sh` 接到 settings / hooks 中。
- 原因：现有 hook 已覆盖关键风险，再加一层会冗余并增加 Windows / Git Bash 前置开销。

### Matt Pocock Skills 安装口径

- 2026-06-19 已按“全局 skill 能生效即可”的原则，只安装 6 个用户级技能：
  `codebase-design`、`diagnosing-bugs`、`domain-modeling`、`git-guardrails-claude-code`、`implement`、`resolving-merge-conflicts`。
- 不需要主动补齐未安装的 15 个；只有具体任务需要时再确认价值与代价。
- `domain-modeling` 默认目录和本仓库真实目录不一致，使用时按上文 ADR / Context 路径规则处理。

## DDD 历史记忆

### DDD-CONFIG-004

- 配置更新事件兼容层曾于 2026-06-10 交付。
- 记忆中的后续基线建议：配置域后续任务基于 `5dcf2e5f`，而不是 `a2d4e6af`。
- 约束：不引入 Kafka/RabbitMQ，不自动重算业绩，不改变配置接口响应，不删除旧缓存刷新逻辑，不合并多个任务为一个 commit。
- 风险：DDD-CONFIG-003 的两个测试失败是历史 baseline 问题，不能直接归因到 CONFIG-004。

### DDD-PRODUCT-005

- 商品域快速寄样入口曾改走 `SampleApplicationPort`。
- 关键约束：商品域不直接判断寄样状态机，不直接查询达人收件地址，前端 API 路径 / 响应保持兼容，旧 `SampleService` 保留。
- 风险：该记忆来自 2026-06-10 归档快照，当前代码是否仍完全一致需以代码和最新测试为准。

## 历史项目概览

- 归档 `project.md` 记录的是 2026-05-25 的 V2.2 / P3 real-pre 联调收口 + KD100 特性开发阶段。
- 其中分支、commit、测试数量、未提交变更数量、目录结构等均是历史快照，不能作为 2026-06-20 当前事实。
- 可保留的长期事实：技术栈、test / real-pre 双环境、real-pre 不得 mock、真实 `pick_source` 样本长期是关键阻塞。

## Claude History 用户指令索引

- 来源：`C:\Users\caojianing\.claude\archive\cleanup-20260619-134941\logs\history.jsonl`。
- 结果：2624 行历史记录中，SAAS / 抖音团长 / 项目路径命中 137 行，分布在 33 个 session。
- 未迁移原始行和粘贴正文；`[Pasted text ...]` 只记录为存在粘贴来源，不能直接当事实。
- 主题线索：需求 gap analysis、按顺序实现未完成模块、商品库与商品管理拆分、招商组长权限边界、三方联调进度、本地 MCP / pencil、容器端口与 real-pre 启动、远端部署与 GitHub 推送、E2E 验证、商品库前端布局回退。
- 这些是用户指令历史索引，进入业务文档前必须用当前代码、docs、evidence 复核。

## Claude File History 索引

- 来源：`C:\Users\caojianing\.claude\file-history\` 与 `...\archive\cleanup-20260619-134941\file-history\`。
- 当前 file-history：8 个快照文件中 6 个命中 SAAS / DDD / harness / real-pre 关键词。
- 归档 file-history：541 个快照文件中 474 个命中；其中 5 个文件有类似敏感配置的文本模式，未迁移原文。
- 主题线索：AGENTS / Harness 规则、Domain Docs、SAAS Project Memory、CI / DDD evidence、EverOS 记忆集成代码、DDD feature flags、Harness GC / 归档。
- file-history 是编辑快照和历史代码片段，不是当前事实；只能作为“曾经存在过的文档/代码线索”索引。

## 死代码记忆

- 2026-05-24 曾做前端 API、后端 Controller、Service、前端 View 等多层死代码审计。
- 记忆强调 code-review-graph 的 dead code 结果存在较高误报，必须用路由、模板绑定、HTTP 调用和 grep 交叉验证。
- 高优先级风险曾集中在归因重放、业绩回填、物流同步、角色菜单、部门成员、商品选品、规则中心和 Dashboard 等功能。
- 这些清单只能作为历史审计入口；当前清理必须重新跑图谱 / grep / 测试验证，不能直接按旧清单删除代码。

## Harness 方法论记忆

- 原始方法论是先构建约束系统，再让智能体写代码。
- 旧三层口径为 Requirements / Rules / AGENTS.md；当前仓库已演进为 `docs/` 主事实源 + `harness/` 执行基座 + `.claude/` 工作台。
- 可继承原则：约束先于实现、CRITICAL 违规阻塞、测试覆盖约束、证据报告收口。

## 可信度分层

| 内容 | 可信度 | 使用方式 |
| --- | --- | --- |
| ADR / CONTEXT 真实路径 | 高 | 可直接作为当前操作规则 |
| Git hook 不重复安装 | 高 | 可直接作为当前操作规则 |
| Matt Pocock 6 个 skill 安装口径 | 中 | 使用前可按本机文件复核 |
| DDD-CONFIG-004 / DDD-PRODUCT-005 | 中 | 作为历史任务索引，需代码复核 |
| 死代码清单 | 低到中 | 只能作为重新审计入口 |
| 2026-05-25 项目概览 | 历史 | 不作为当前事实 |

## 待复核

- 当前 `CONTEXT.md` 仍写 V1，需要按 ADR-010 的 V2 口径另行处理；本文不直接修改业务术语。
- 当前 DDD 任务进展以 `harness/rules/state/snapshots/DOMAIN_STATUS.md` 和最新 evidence 为准。
