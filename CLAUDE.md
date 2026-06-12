# 抖音团长 SaaS Harness Engineering 地图

更新时间：2026-05-26

## 定位

[必做] 本文件只做仓库地图，不做百科全书。业务事实、领域合同、流程、接口、验收和部署口径均进入 `docs/`。

[必做] 智能体工作台统一放在 `.claude/`，包括 hooks、skills、plugins、LSP、MCP、subagents、commands、memory、qa 和 templates。

[历史归档] 旧 V2.2 完整方案、旧领域设计、local-mock 旧口径、FastAPI / Celery / Python 爬虫式设想只作为背景，不能写成当前事实。

## 当前事实

[必做] 当前技术栈以 Spring Boot、PostgreSQL、Redis、Docker Compose、Vue 3 / TypeScript、Playwright 为准。

[必做] 当前默认交付范围以 `docs/01-V2交付范围与边界.md` 为准。

[必做] `test` 是 Mock 回归基线；`real-pre` 是当前唯一真实上游 / 生产形态环境。

[必做] real-pre 不允许用 mock 数据冒充真实闭环。缺 Token、缺授权、缺真实订单、缺 `pick_source` 样本时只能标记 `BLOCKED` 或 `PENDING`。

## 阅读路径

[必做] 新任务先读：

1. `docs/README.md`
2. `docs/01-V2交付范围与边界.md`
3. 当前相关领域合同：`docs/领域/*.md`
4. 当前相关流程：`docs/流程/*.md`
5. 接口 / 事件 / 数据：`docs/04-事件契约总表.md`、`docs/05-API契约总表.md`、`docs/06-数据模型总表.md`
6. 验收：`docs/09-测试验收总览.md` 与 `docs/验收/*.md`

[必做] 旧文档需要查证时，从 `docs/归档/旧版V2.2完整方案.md` 进入，不直接把旧口径带回主线。

## 不变量

[必做] 订单域负责基础事实落库，提成与独家覆盖规则可在此处或业绩域扩展。

[必做] 业绩域负责最终归属、提成、冲正、双轨金额计算。

[必做] 配置域负责配置，不执行具体业务规则。

[必做] 分析模块可读汇总表，支持更复杂的数据聚合与分析增强。

[必做] 用户域统一提供 `self / group / all` 数据范围。

[必做] 寄样域通过订单已同步事件判断交作业完成。

[必做] 商品域负责转链并落 `pick_source_mapping`。

[必做] real-pre 验收不得使用 mock 数据冒充真实闭环。

## 执行规则

[必做] 任何修改前先确认所属领域、流程和验收项；冲突写入 `docs/决策/ADR-002-V1范围优先级.md`，不要自行拍板。

[必做] 修改代码时必须同步对应文档；包含业务逻辑变更时必须同步更新 `.claude/` 与 `harness/` 工程化文档。

[必做] 验收必须能落到脚本、SQL/API、证据路径三类之一。没有证据时只能写阶段性结论。

[V2 必做] 独家达人 / 商家全量闭环、历史重算、MQ 化事件、外部 `quick_sample_apply`、真实物流自动化及差异化提成，是当前阶段的核心架构演进目标。

## 常用入口

[必做] `npm run e2e:v1-p0`：test/mock V1-P0 基线。

[必做] `npm run e2e:real-pre:p0`：real-pre 统一验收入口。

[必做] `npm run e2e:real-pre:p0:preflight`：real-pre 预检。

[必做] `cd backend && mvn test`：后端回归。

[必做] `cd frontend && npm run build`：前端构建。
