# 文档地图

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。

## 当前事实

- [V2 必做] 本仓库是抖音团长 SaaS V2 演进中的记录系统，当前可执行事实以代码、测试、运行配置和本目录文档共同确认。
- [V1 必做] 当前真实技术栈为 Spring Boot、PostgreSQL、Redis、Docker Compose、Vue / TypeScript、Playwright。
- [V1 必做] `CLAUDE.md` 只做地图，智能体执行细节放在 `.claude/`。
- [V1 必做] `docs/` 只存事实、领域合同、流程、接口、验收、部署和决策。
- [历史归档] 旧 V2.2 完整方案、FastAPI、Celery、Python 爬虫等历史口径只能作为背景，入口见 [归档/旧版V2.2完整方案.md](归档/旧版V2.2完整方案.md)。

## 阅读顺序

1. [V2 必做] 先读 [00-项目总览.md](00-项目总览.md)、[01-V2交付范围与边界.md](01-V2交付范围与边界.md)、[决策/ADR-010-仓库阶段口径拍板为V2.md](决策/ADR-010-仓库阶段口径拍板为V2.md)。
2. [V1 必做] 涉及业务主链路时读 [02-业务闭环总览.md](02-业务闭环总览.md) 与 [流程/](流程/)。
3. [V1 必做] 涉及领域职责时读 [03-领域架构总览.md](03-领域架构总览.md) 与 [领域/](领域/)。
4. [V1 必做] 涉及事件、接口、数据时读 [04-事件契约总表.md](04-事件契约总表.md)、[05-API契约总表.md](05-API契约总表.md)、[06-数据模型总表.md](06-数据模型总表.md)。
5. [V1 必做] 涉及权限时读 [07-权限与数据范围.md](07-权限与数据范围.md)。
6. [V1 必做] 涉及抖音 / 抖店真实联调时读 [08-第三方对接总览.md](08-第三方对接总览.md)、[对接/](对接/) 和 [验收/real-pre联调手册.md](验收/real-pre联调手册.md)。
7. [V1 必做] 修改完成前读 [09-测试验收总览.md](09-测试验收总览.md)、[验收/](验收/) 和 [../harness/rules/governance/COMPLETION_GATES.md](../harness/rules/governance/COMPLETION_GATES.md)。
8. [V1 必做] 部署和环境排障读 [10-部署运行总览.md](10-部署运行总览.md)。
9. [V1 必做] 业务试用、用户培训和操作答疑读 [11-用户操作手册.md](11-用户操作手册.md)。

## 主干文档

| 文档 | 作用 | 范围 |
| --- | --- | --- |
| [00-项目总览.md](00-项目总览.md) | 当前项目事实和技术栈 | V1 必做 |
| [01-V2交付范围与边界.md](01-V2交付范围与边界.md) | V2 交付范围和冲突处理入口 | V2 必做 |
| [02-业务闭环总览.md](02-业务闭环总览.md) | 业务闭环总图 | V1 必做 |
| [03-领域架构总览.md](03-领域架构总览.md) | 领域职责边界 | V1 必做 |
| [04-事件契约总表.md](04-事件契约总表.md) | 事件生产、消费、证据 | V1 必做 |
| [05-API契约总表.md](05-API契约总表.md) | 内部 API 总入口 | V1 必做 |
| [06-数据模型总表.md](06-数据模型总表.md) | 表、事实、归属边界 | V1 必做 |
| [07-权限与数据范围.md](07-权限与数据范围.md) | self/group/all 数据范围 | V1 必做 |
| [08-第三方对接总览.md](08-第三方对接总览.md) | 抖音对接和降级口径 | V1 必做 |
| [09-测试验收总览.md](09-测试验收总览.md) | 测试脚本、证据、验收 | V1 必做 |
| [10-部署运行总览.md](10-部署运行总览.md) | test / real-pre 运行口径 | V1 必做 |
| [11-用户操作手册.md](11-用户操作手册.md) | 面向业务用户的登录、菜单、角色和日常操作说明 | V1 必做 |
| [12-项目记忆索引.md](12-项目记忆索引.md) | 本地项目记忆迁移索引和短事实入口 | V2 必做 |
| [13-Claude项目记忆迁移索引.md](13-Claude项目记忆迁移索引.md) | Claude 本地项目记忆与归档快照迁移索引 | V2 必做 |
| [14-Codex会话记忆迁移索引.md](14-Codex会话记忆迁移索引.md) | Codex 本地 SAAS 相关会话索引与导入边界 | V2 必做 |
| [ddd-validation-guide.md](ddd-validation-guide.md) | DDD 收口验收脚本、矩阵证据和白名单 debt 口径 | V2 必做 |

## 兼容入口

以下文件是 Harness 五子系统要求的兼容入口，只引用主源，不重复维护第二套事实：

| 文档 | 指向主源 |
| --- | --- |
| [01-V1交付合同.md](01-V1交付合同.md) | [01-V2交付范围与边界.md](01-V2交付范围与边界.md)、[决策/ADR-010-仓库阶段口径拍板为V2.md](决策/ADR-010-仓库阶段口径拍板为V2.md) |
| [02-业务闭环地图.md](02-业务闭环地图.md) | [02-业务闭环总览.md](02-业务闭环总览.md) |
| [03-领域边界总表.md](03-领域边界总表.md) | [03-领域架构总览.md](03-领域架构总览.md) 与 [领域/](领域/) |
| [04-上线验收清单.md](04-上线验收清单.md) | [09-测试验收总览.md](09-测试验收总览.md)、[10-部署运行总览.md](10-部署运行总览.md) |
| [05-real-pre证据索引.md](05-real-pre证据索引.md) | [验收/验收证据索引.md](验收/验收证据索引.md) |
| [06-P0-P1问题台账.md](06-P0-P1问题台账.md) | [../harness/rules/state/snapshots/03-P0-P1问题台账.md](../harness/rules/state/snapshots/03-P0-P1问题台账.md) |

## 专项目录

- [V1 必做] [领域/](领域/)：按统一领域合同模板维护。
- [V1 必做] [流程/](流程/)：按统一业务流程模板维护。
- [V1 必做] [对接/](对接/)：抖音 / 抖店接口、Token、转链、订单、物流、达人信息。
- [V1 必做] [验收/](验收/)：P0、E2E、real-pre、回归脚本、证据索引。
- [V1 必做] [release/](release/)：real-pre 上线前总审查报告和证据目录索引；最近审查见 [release/real-pre上线总审查报告-20260528-171543.md](release/real-pre上线总审查报告-20260528-171543.md)。
- [V1 必做] [方案/](方案/)：专项计划和执行提示；DDD / Harness 收口见 [方案/PLAN-004-DDD边界与Harness工程收口.md](方案/PLAN-004-DDD边界与Harness工程收口.md)，跨平台验证核心见 [方案/PLAN-006-Harness跨平台验证核心重构设计.md](方案/PLAN-006-Harness跨平台验证核心重构设计.md) 和 [方案/PLAN-007-Harness跨平台验证核心实施计划.md](方案/PLAN-007-Harness跨平台验证核心实施计划.md)。
- [V1 必做] [决策/](决策/)：ADR 和冲突处理。
- [历史归档] [归档/](归档/)：旧 V2.2 主干、旧七域文档、历史记录、审计与 runbook。

## Harness Engineering

- [V1 必做] [../harness/README.md](../harness/README.md)：AI Agent 固定执行入口、脚本、skills、runbooks、任务和 evidence reports。
- [V2 必做] [决策/ADR-013-Harness分层文件门禁.md](决策/ADR-013-Harness分层文件门禁.md)：Harness 文件预算、基线感知门禁和报告生命周期的已批准设计。
- [V2 必做] [决策/ADR-014-Harness跨平台核心目录与渐进迁移.md](决策/ADR-014-Harness跨平台核心目录与渐进迁移.md)：扩展 Harness 目录边界并批准 Node 核心的绞杀式迁移。
- [V1 必做] [../harness/rules/state/snapshots/01-当前项目状态.md](../harness/rules/state/snapshots/01-当前项目状态.md)：当前技术栈、V2 闭环、real-pre 状态和旧文档冲突处理。
- [V1 必做] [../harness/rules/governance/task-routing.md](../harness/rules/governance/task-routing.md)：任务分流到领域、验证和执行 Scope。
- [V1 必做] [../harness/rules/governance/forbidden-scope.md](../harness/rules/governance/forbidden-scope.md)：real-pre、Git 密钥和模块边界禁止项。
- [V1 必做] [../harness/rules/instructions/](../harness/rules/instructions/)：Instructions 指令系统。
- [V1 必做] [../harness/rules/skills/](../harness/rules/skills/)：Skills 工具化规则系统。
- [V1 必做] [../harness/rules/environment/](../harness/rules/environment/)：Environment 环境系统。
- [V1 必做] [../harness/rules/state/](../harness/rules/state/)：State 状态系统。
- [V1 必做] [../harness/rules/feedback/](../harness/rules/feedback/)：Feedback 反馈系统。
- [V1 必做] Harness 不替代本目录事实主源；涉及业务规则仍以 `docs/*.md` 和当前代码证据为准。

## 不变量

- [V1 必做] 订单域只存事实，不算提成，不应用独家覆盖。
- [V1 必做] 业绩域负责最终归属、提成、冲正、双轨金额计算。
- [V1 必做] 配置域负责配置，不执行具体业务规则。
- [V1 必做] 分析模块只读汇总表，不重算业绩归属。
- [V1 必做] 用户域统一提供 `self/group/all` 数据范围。
- [V1 必做] 寄样域通过订单已同步事件判断交作业完成。
- [V1 必做] 商品域负责转链并落 `pick_source_mapping`。
- [V1 必做] real-pre 验收不得使用 mock 数据冒充真实闭环。
