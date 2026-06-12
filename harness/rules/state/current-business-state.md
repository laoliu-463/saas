# 业务当前状态（索引）

> 任务 ID：HARNESS-DOC-GC-OPTIMIZE-002
> 更新时间：2026-06-11
> 详细快照（含每个领域完整 P0/P1 列表、双轨公式说明、待确认事项）：
>   `reports/current/business-state-snapshot.md`（350 行，作为权威快照）
> 治理政策：`rules/policies/retention-policy.md` 第 1 节（state 索引 ≤200 行）

## 当前日期与口径
- **当前日期**：2026-06-11
- **V1 优先于 V2.2**：所有事实以 `docs/01-V1交付范围与边界.md` 为准
- **环境对照**：`test` = mock 基线；`real-pre` = 真实上游 / 生产形态（不允许 mock 冒充）

## 当前技术栈（V1）
- 后端：Spring Boot 3.2 / Java 17 / PostgreSQL / Redis / Docker Compose
- 前端：Vue 3 / Vite / Pinia / Naive UI / TypeScript
- 测试：Playwright（E2E）/ Vitest（前端单测）/ JUnit 5（后端）
- 构建：Maven（后端）/ npm（前端）

## 当前已完成领域
- DDD-SAMPLE-005（寄样域拆分，commit 4ede4c63 + a60a045a）
- DDD-ORDER-001（订单同步应用服务，commit 9a5bb555）
- DDD-PRODUCT-005（商品域快速寄样改走 SampleApplicationPort，commit 0498b08e）
- DDD-ANALYTICS-001/002（分析事件消费兼容 + dashboard 影子对比，commit 70e1a1af + a60a045a）
- DDD-CONFIG-004（配置域事件兼容，commit 1addc145 + 5dcf2e5f）

## 当前未闭环点
- DDD-CONFIG-003：2 个 baseline 测试失败，需独立修复任务
- real-pre 远端验证：多任务仍待 evidence gate（DDD-SAMPLE-005、DDD-ORDER-001、DDD-PRODUCT-005）
- HARNESS-DOC-GC-OPTIMIZE-002（本任务）：结构治理中

## 当前 P0 / P1
- P0：业绩计算双轨公式（已文档化，需 real-pre 业务验证）
- P0：dashboard 真实数据回归（DASHBOARD-MONEY-AUDIT-001 启动中）
- P1：用户域 self/group/all 数据范围（部分场景仍需前端补齐）
- 完整 P0/P1 列表见 `reports/current/p0-p1-register.md`

## V1 核心闭环（不变量）
1. 订单域只存事实，不算提成，不应用独家覆盖
2. 业绩域负责最终归属、提成、冲正、双轨金额计算
3. 配置域负责配置，不执行具体业务规则
4. 分析模块只读汇总表，不重算业绩归属
5. 用户域统一提供 self / group / all 数据范围
6. 寄样域通过订单已同步事件判断交作业完成
7. 商品域负责转链并落 `pick_source_mapping`

## 双轨公式（关键口径）
- 预估服务费收入 = 预估订单额 × 服务费率（未扣技术服务费）
- 结算服务费收入 = 结算订单额 × 服务费率 - 技术服务费
- 预估服务费收益 = 预估服务费收入 - 预估服务费支出 - 技术服务费
- 结算服务费收益 = 结算服务费收入 - 结算服务费支出
- 旧"服务费收益 = 服务费收入 - 技术服务费"不得继续作为双轨统一公式

## 旧文档冲突处理
- 旧 V2.2 完整方案 / 旧领域设计 / local-mock 旧口径 / FastAPI / Celery / Python 爬虫式设想
  → 全部仅作背景，从 `docs/归档/旧版V2.2完整方案.md` 进入，不把旧口径带回主线
- 冲突写入 `docs/决策/ADR-002-V1范围优先级.md`，不自行拍板

## 状态子系统
- `rules/state/`：长期状态（决策、领域状态、当前业务状态）
- `reports/current/`：当前有效快照（含本快照的 350 行原文）

## Harness 自身治理
- 治理标准：`rules/policies/`（structure / retention / report-style）
- 治理清单：`manifests/gc/`（GC plan / 实施记录）
- 校验脚本：`scripts/check-harness-limits.ps1`

## 待确认
- DDD-CONFIG-003 baseline 失败的根因是否需独立任务卡（建议是）
- real-pre 远端首次跑通的时间窗（待运维排期）

> 完整业务事实、字段对齐、订单明细表复刻、DASHBOARD-MONEY-AUDIT 进展、订单 6468 调查、远端重连任务
> 全部见 `reports/current/business-state-snapshot.md`。
