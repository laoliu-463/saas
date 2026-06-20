# PLAN-004 DDD 边界与 Harness 工程收口

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。

## 定位

- [V1 必做] 本文件只做文档收口和后续治理计划，不代表代码已经完成对应重构。
- [V1 必做] 当前业务事实仍以 `docs/01-V2交付范围与边界.md`、`docs/03-领域架构总览.md`、`docs/领域/*.md`、`harness/rules/state/snapshots/01-当前项目状态.md` 为准。
- [V1 必做] DDD 负责澄清业务边界；Harness Engineering 负责约束后续 AI / 人工修改时的读取、执行、验证、证据和复盘流程。
- [V1 不做] 本计划不引入 FastAPI、Celery、Python 爬虫、外部 MQ、微服务拆分或旧 V2.2 全量范围。

## 当前阶段性结论

- [V1 必做] 仓库已经采用模块化单体和七域 + 分析模块边界，见 `docs/决策/ADR-001-模块化单体.md` 与 `docs/03-领域架构总览.md`。
- [V1 必做] Harness 已经落地统一入口、任务路由、状态、runbook、skill、evidence report 和 retro summary，见 `harness/README.md`。
- [V1 必做] 后续优化重点不是新增巨型说明文件，而是把跨域访问规则、架构检查、任务路由和验证证据继续固化到现有主源。

## DDD 与 Harness 分工

| 层面 | 主责 | 当前主源 | 验证方式 |
| --- | --- | --- | --- |
| 领域边界 | DDD | `docs/03-领域架构总览.md`、`docs/领域/*.md` | 能说明主责领域、上游、下游和禁止越界项 |
| 业务流程 | DDD | `docs/02-业务闭环总览.md`、`docs/流程/*.md` | 三条主链能用脚本、API/SQL 或日志验证 |
| 事件契约 | DDD + Harness | `docs/04-事件契约总表.md`、`harness/rules/runbooks/*.md` | 事件有生产者、消费者、幂等键和证据路径 |
| 任务执行 | Harness | `harness/rules/governance/task-routing.md`、`harness/rules/runbooks/*.md` | 修改后按 Scope 执行固定命令 |
| 反馈升级 | Harness | `harness/rules/feedback/*.md`、`harness/reports/*.md` | 失败经验能进入 retro、state 或 runbook |

## 领域访问原则

- [V1 必做] 其他领域读取某领域能力时，应优先通过应用服务、Facade、查询 API 或领域事件获取，不直接穿透到对方 Repository 或业务表。
- [V1 必做] 用户域统一提供 `self/group/all` 数据范围，业务域只消费数据范围结果，不复制权限判断。
- [V1 必做] 配置域提供规则参数和审计事实，具体业务规则由业务域执行。
- [V1 必做] 商品域提供商品、活动、转链和 `pick_source_mapping` 事实，订单域消费归因输入，业绩域计算最终归属。
- [V1 必做] 订单域只保存订单事实、退款事实和默认归因输入；寄样域、业绩域、分析模块分别基于订单事实做自己的业务判断或只读展示。
- [V1 必做] 分析模块只读汇总和事实查询，不写回订单、寄样或业绩归属。

## 需要固化的反馈传感器

以下是后续治理项，当前只能写成计划，不能写成已完成：

| 治理项 | 目的 | 建议落点 | 状态 |
| --- | --- | --- | --- |
| 架构边界测试 | 防止 domain 依赖 controller / dto，防止跨域 Repository 穿透 | 后端测试或架构扫描脚本 | [V2 预留] 待专项评估 |
| Controller 复杂逻辑扫描 | 防止业务规则散落在接口层 | Harness safety-check 或代码审查 skill | [V1 简化] 先作为 review checklist |
| 跨域访问扫描 | 防止订单域、分析模块绕过边界查表 | `harness/rules/skills/workflow/code-review.skill.md`、后续自动扫描 | [V1 简化] 先人工审查 |
| 事件幂等检查 | 防止重复消费造成寄样、业绩、看板不一致 | `docs/04-事件契约总表.md`、`harness/evals/*.md` | [V1 必做] 按事件逐项补证据 |
| 旧内容回收 | 防止旧 V2.2、旧 runbook、重复文档污染当前事实 | `harness/rules/feedback/retire.md` | [V1 必做] 每次任务生成候选计划 |

## 实施顺序

### 第一阶段：文档主源收口

- [V1 必做] 保持 `CLAUDE.md` 只做仓库地图。
- [V1 必做] 保持 `docs/` 只做业务事实、领域合同、流程、接口、数据、验收、部署和 ADR。
- [V1 必做] 保持 `harness/` 只做任务执行、验证、证据、反馈和状态。
- [V1 必做] 新增或调整规则时，优先补到现有主源，不新增平行事实体系。

### 第二阶段：边界审查固化

- [V1 简化] 在代码审查清单中加入跨域 Repository 穿透、Controller 业务规则、分析模块重算归属检查。
- [V1 简化] 对订单域、业绩域、分析模块这条链优先做边界审查，因为它影响归因、寄样交作业和看板闭环。
- [V2 预留] 如后续引入 ArchUnit 或等价架构测试，必须先形成专项方案和验证证据。

### 第三阶段：事件与幂等证据补齐

- [V1 必做] `订单已同步` 事件必须能证明订单事实已入库、归因输入可追溯、消费者幂等。
- [V1 必做] 寄样自动完成必须能证明基于 `channel_id + talent_id + product_id + pay_time` 命中，而不是订单域直接改寄样结论。
- [V1 必做] 业绩计算必须能证明从订单事实、配置参数、归因输入到 `performance_records` 的链路。
- [V1 必做] 分析看板必须能证明只读汇总或事实查询，不重算业绩归属。

### 第四阶段：Harness 反馈升级

- [V1 必做] 每次任务后生成 evidence report 和 retro summary。
- [V1 必做] 如果发现 runbook、skill、eval 或文档主源不能指导新人复现，应升级对应 Harness 文件。
- [V1 必做] 如果发现重复文档或旧口径污染当前事实，应通过旧内容维护计划处理，不能直接删除不确定资料。

## 验收方式

| 验收项 | 证据 |
| --- | --- |
| 文档入口可发现 | `docs/README.md` 能索引到本计划 |
| 领域边界可追溯 | `docs/03-领域架构总览.md` 有跨域访问原则 |
| Harness 分工明确 | `harness/README.md` 说明 DDD 与 Harness 的分工 |
| docs-only 验证 | 执行 `agent-do.ps1 -Env real-pre -Scope docs` 或记录阻塞原因 |

## 剩余风险

- [V1 必做] 本文件是文档收口，不证明代码已经符合全部 DDD 分层。
- [V1 必做] 架构边界测试、跨域扫描和 Controller 复杂逻辑扫描仍需要后续专项落地。
- [V1 必做] 若后续发现本文件与业务主源冲突，以 `docs/01-V2交付范围与边界.md`、`docs/领域/*.md` 和当前代码证据为准，并写入 ADR。
