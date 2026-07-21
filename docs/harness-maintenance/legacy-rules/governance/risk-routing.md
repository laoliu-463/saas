# Risk Routing

> 本文件定义按文件路径自动判定任务风险等级（Risk）的规则。配置由 `docs/harness-maintenance/legacy-rules/test-impact-map.json` 承载，本文档说明判定语义和优先级。

## 风险等级

| 等级 | 含义 | 示例 |
|---|---|---|
| R0 | 纯文档、注释、测试说明、归档报告 | `docs/**`、`**/*.md`（排除 AGENTS/CLAUDE/CONTRIBUTING/harness） |
| R1 | 孤立前端组件、文案、样式 | `frontend/src/**` 不命中业务路径 |
| R2 | 单领域普通 Bug / 功能 | 订单 / 业绩 / 商品 / 寄样 / 物流 / 配置 / 分析域 |
| R3 | DB、权限、角色、订单归因、业绩金额、状态机、定时任务、外部写接口、CI/CD、Harness 规则 | `backend/src/main/resources/db/**`、`**/rbac/**`、`**/security/**`、`Jenkinsfile`、`docs/harness-maintenance/legacy-rules/**` |

> **关键**：`AGENTS.md` / `CLAUDE.md` / `CONTRIBUTING.md` / `docs/harness-maintenance/legacy-rules/**` / `Jenkinsfile` / `.github/workflows/**` 属于"会改变工程行为的文档"，**不是 R0，按 R3 处理**。

## 合并语义

多个规则同时命中一个改动文件时：

| 字段 | 合并规则 |
|---|---|
| `risk` | 取所有命中规则中数值最大的 riskRank |
| `backendTests` | 并集去重（**永远并集**，priority 仅用于互斥字段仲裁） |
| `frontendTests` | 并集去重 |
| `scope` | affectedScopes 并集 |
| `forceFullBackendTests` | 任一规则为 true 即 true |
| `forceFullFrontendTests` | 任一规则为 true 即 true |
| `priority` | 仅用于互斥字段仲裁 |

## 路径匹配

路径使用 **glob 模式**：

- `*` — 匹配单层任意字符（不含 `/`）
- `**` — 匹配多层（含 `/`）
- 例：`backend/src/main/java/**/order/**` 匹配 `backend/src/main/java/com/colonel/saas/domain/order/...`

## 判定时机

判定必须在 agent-do 入口一次性完成（`-Phase` 开始时），结果（`$RiskLevel`）作为后续所有子调用共享参数。

## 强制升级，不能降级

- 自动判定算出 R3 → 用户传 `-Risk R2` → 抛错
- 自动判定算出 R2 → 用户传 `-Risk R3` → 允许（人工升级合法）

## 路径规则索引

完整规则列表见 `docs/harness-maintenance/legacy-rules/test-impact-map.json` §rules。当前共 12 条：

1. `order` (R2) — 订单域
2. `performance` (R2) — 业绩/佣金/归因
3. `product` (R2) — 商品/活动/促销/同步
4. `sample-logistics` (R2) — 寄样/物流/快递
5. `user-permission` (R3) — 用户/权限/角色/数据范围
6. `douyin-gateway` (R3) — 抖音 Gateway/Token
7. `database-schema` (R3) — 数据库迁移 / Mapper
8. `ddd-architecture` (R3) — DDD 架构红线
9. `shared-infrastructure` (R3) — 共享基础设施（强制全量）
10. `frontend-isolated` (R1) — 孤立前端
11. `harness-rules` (R3) — Harness 规则 / Jenkins / GHA
12. `docs-only` (R0) — 纯文档

## 静态校验（PR #1 范围）

本规则的静态校验脚本在 `harness/scripts/tests/test-impact-map.Tests.ps1`，仅校验：

- JSON 可解析
- `schemaVersion` 受支持
- rule ID 唯一
- Risk 值合法（R0/R1/R2/R3）
- paths/tests 非空（除非 rule 明确以 `frontendTests`/`backendTests` 为空）
- 精确测试类真实存在（不含 `*` 通配符的字符串必须 glob 至少 1 个当前测试文件）
- glob 至少匹配一个当前测试，或者规则无 backend/frontend test 字段
- Mapper 排除项合法（`excludeGlobs` 不为空且每项是合法 glob）
- 规则重叠合并确定性（任意两条 rule 命中同一文件时，合并结果可重现）

**不在 PR #1 范围**：实际跑 `mvn -Dtest=` 或 `pnpm vitest run`——那是 PR #2 的工作。