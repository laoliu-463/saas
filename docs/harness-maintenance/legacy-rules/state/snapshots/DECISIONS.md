# Decisions

## 作用

本文件只做重要决策索引，避免 Agent 在旧方案和当前 V1 事实之间自行裁决。正式决策仍写入 `docs/决策/*.md`。

## 当前决策入口

| 决策 | 主源 | 当前口径 |
| --- | --- | --- |
| V1 范围优先级 | `docs/决策/ADR-002-V1范围优先级.md` | 旧 V2.2 与 V1 冲突时，以 V1 范围和当前代码证据为准 |
| 模块化单体 | `docs/决策/ADR-001-模块化单体.md` | 当前不是 FastAPI/Celery 旧方案 |
| 订单域边界 | `docs/决策/ADR-003-订单域只存事实.md` | 订单域不算提成和最终归属 |
| 业绩域边界 | `docs/决策/ADR-004-业绩域负责最终归属.md` | 最终归属、提成、冲正属于业绩域 |
| 分析模块边界 | `docs/决策/ADR-005-分析模块只读汇总.md` | 分析模块不重算业绩 |
| real-pre 环境 | `docs/决策/ADR-006-real-pre作为上线前联调环境.md` | real-pre 是真实上游 / 生产形态联调环境 |
| 服务费收入 / 收益双轨公式 | `docs/决策/ADR-002-V1范围优先级.md` | 预估服务费收入 = 预估订单额 × 服务费率（未扣除技术服务费）；结算服务费收入以官方结算服务费字段为准，不用订单额重算且不扣技术服务费；预估服务费收益 = 预估服务费收入 - 预估服务费支出 - 技术服务费；结算服务费收益 = 结算服务费收入 - 结算服务费支出 |
| DDD 增量合并 | `docs/harness-maintenance/legacy-rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md` | 本项目已有 Harness，本次只增量合并 DDD 计划，不重建 Harness |
| 用户域 dept_type 标准 | `runtime/qa/out/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md` | `sys_dept.dept_type` Java / seed 标准统一为 `department/recruiter_group/channel_group/ops_group`，旧 `recruiter/channel/dept` 仅允许出现在 migration 兼容映射中 |
| Git 工作区治理与批次提交门禁 | `runtime/qa/out/git-harness-001-worktree-governance-20260603-*.md` + `docs/harness-maintenance/legacy-rules/skills/git/git-change-control.md` | 远端部署必须只使用已提交 commit；本地 dirty 文件禁止直接部署；所有任务必须有任务编号；所有提交必须按任务编号或 batch 编号拆分；任何任务提交前禁止使用 `git add .`；工作区不干净时，新任务必须先执行 Git Intake Gate |

## 2026-06-02 DDD 优化决策摘要

- 本项目已有 Harness，本次采用增量合并，不重建 Harness。
- DDD 优化顺序固定为：用户域 -> 配置域 -> 订单域 -> 业绩域 -> 分析模块 -> 商品域 -> 达人域 -> 寄样域 -> Outbox 事件 -> 前端领域化 -> E2E 验收与垃圾回收。
- V1 使用 Spring Boot + PostgreSQL + Redis + Docker Compose，不切换 FastAPI。
- V1 采用模块化单体，不拆微服务。
- V1 订单域只存事实，不计算提成，不直接写 `performance_records`。
- V1 业绩域负责最终归属、提成、冲正和汇总刷新。
- V1 分析模块只读汇总，不重新计算归因。
- V1 不启用独家达人、独家商家或个别品负责人覆盖；经营毛利（Y-04）按 2026-06-05 用户决策纳入 V1 验收，不扩展为财务结算口径。
- 服务费收入 / 收益双轨公式按 2026-06-06 用户口径执行；旧“服务费收入 - 技术服务费”不得继续作为预估 / 结算统一公式。

## 2026-06-03 Git 工作区治理决策摘要

- 远端部署必须只使用已提交 commit；本地 dirty 工作区不得直接部署。
- 所有任务必须有任务编号（如 `GIT-HARNESS-001` / `GIT-BATCH-N` / 业务任务 ID）。
- 所有提交必须按任务编号或 batch 编号拆分，不允许把多任务 commit 合并为单 commit。
- 任何任务提交前禁止使用 `git add .` / `git add -A` / `git add <dir>/`。
- 工作区不干净时，新任务必须先执行 Git Intake Gate。
- 任务结束前必须输出 `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一。
- unknown dirty 不得进入任何 commit / push / deploy，必须先在 `runtime/qa/out/unknown-dirty-investigation-*.md` 调查。

## 新决策写入规则

1. 先收集源码、接口、数据、日志或团队输入证据。
2. 涉及业务规则或架构边界时，不在本文件直接拍板。
3. 新增 ADR 后，在本文件补一行索引，并更新 `docs/harness-maintenance/legacy-rules/changelog.md`。
