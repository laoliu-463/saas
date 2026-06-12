# GC Manifest - HARNESS-DOC-GC-OPTIMIZE-003

> 生成时间：2026-06-12T19:30:00+08:00
> 任务：Harness 文档体系收口治理（极致瘦身第二轮）
> 上游证据：`第二大脑-10-SaaS/06-报告/报告-006.md`（项目侧 5 处违规修复方案）
> 上游任务：`HARNESS-DOC-GC-OPTIMIZE-002`（2026-06-11 在跑，003 收口）
> 范围：仅 harness/ 内部文档；不触碰源码、配置、env、DB migration、test、生产密钥、部署脚本

## 当前违规盘点（2026-06-12 18:30 扫描）

| 类别 | 数量 | 严重度 |
|---|---:|---|
| md 行数 > 200 | 5 | 中（含 1 个 1390 行证据报告）|
| 目录文件数 > 10 | 1 | 低（reports/ 45 文件）|
| 一级子目录 > 10 | 0 | 良（实际 8 个）|

注：与 6/11 002 阶段相比，`reports/current/` 与 `templates/prompts/agents/` 路径已消失，实际违规位置有变化。

## Archive

无新归档。所有"已退役"内容已在 6/11 002 阶段归档完毕（388 文件）。

| Source | Target | Reason |
|---|---|---|
| 无 | — | — |

## Split

| Source | Targets | Reason |
|---|---|---|
| `rules/policies/agent-contract.md` (203 行) | `rules/policies/agent-contract.md` (核心契约 180 行) + `rules/policies/agent-contract-exemptions.md` (豁免清单 30 行) | 超过 200 行限制 |
| `rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md` (218 行) | `DDD_DOMAIN_TASK_MATRIX.md` (总览 150 行) + `DDD_DOMAIN_TASK_MATRIX_order.md` (订单域 60 行) + `DDD_DOMAIN_TASK_MATRIX_product.md` (商品域 60 行) + `DDD_DOMAIN_TASK_MATRIX_talent.md` (达人域 60 行) + `DDD_DOMAIN_TASK_MATRIX_sample.md` (寄样域 60 行) | 超过 200 行限制 |

## Trim

| Source | Before | After | Reason |
|---|---|---|---|
| 无 | — | — | — |

## Restructure

| Source | Targets | Reason |
|---|---|---|
| `reports/` (45 文件，平铺) | `reports/2026-06-12/` (按日期子目录) | 超过 10 文件/目录限制 |

注：原 001 阶段已有 `reports/archive/YYYYMMDD/` 子目录模式，本次沿用一致结构。

## Reorganize

| Source | Target | Reason |
|---|---|---|
| `reports/evidence-20260612-130919.md` (1390 行) | **待用户拍板**（Step 3 暂缓）| 高风险：审计证据，破坏会丢审计链 |
| `reports/evidence-20260612-131014.md` (248 行) | 同上 | 中风险 |
| `reports/evidence-20260612-131111.md` (254 行) | 同上 | 中风险 |

## New

| Path | Reason |
|---|---|
| `rules/policies/agent-contract-exemptions.md` | 豁免清单（从 agent-contract.md 拆出）|
| `rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX_order.md` | 订单域任务矩阵（从总览拆出）|
| `rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX_product.md` | 商品域任务矩阵（从总览拆出）|
| `rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX_talent.md` | 达人域任务矩阵（从总览拆出）|
| `rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX_sample.md` | 寄样域任务矩阵（从总览拆出）|

## Delete

无直接删除。

## Keep

| Path | Reason |
|---|---|
| 所有 `rules/policies/*.md` 保留（除拆分外）| 政策文件 |
| 所有 `rules/runbooks/ddd/*.md` 保留（除拆分外）| runbook |
| 所有 `rules/environment/*.md` 保留 | 环境文档 |
| 所有 `tasks/*.md` 保留 | 任务卡 |
| 所有 `manifests/*.md` 保留 | 治理清单 |
| 所有 `probes/*.md` 保留 | 探针 |
| 所有 `archive/*` 保留（不重处理）| 历史归档 |

## 执行顺序（按风险递增）

1. **Step 1**：拆 `agent-contract.md`（低风险，30 min）
2. **Step 2**：拆 `DDD_DOMAIN_TASK_MATRIX.md`（中风险，1 hour）
3. **Step 3**（**待用户拍板**）：拆 1390 行 evidence-130919.md（高风险，2 hour）
4. **Step 4**：reports/ 按日期子目录化（低风险，30 min）

## 风险与约束

- 跨文件引用必须同步更新：
  - `harness/INDEX.md`
  - 各 `rules/` 之间的双向跳转
  - runbook 引用
  - 知识库 `第二大脑-10-SaaS/` 内的报告
- 不动业务代码
- 不动 SQL migration
- 不动 Docker/env/部署脚本
- 不 git push
- 不重启容器
- 不数据库写

## 关联

- 上游：`第二大脑-10-SaaS/06-报告/报告-006.md`
- 上游：`第二大脑-10-SaaS/05-任务/任务-003.md`
- 平行：`manifests/gc/harness-doc-gc-optimize-001-manifest.md`（已 DONE）
- 平行：`manifests/gc/2026-06-11-harness-gc-plan.md`（002 阶段）
