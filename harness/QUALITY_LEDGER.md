# Quality Ledger

本文件用于记录各领域 / 模块的质量状态，供 Agent 选择任务、判断风险和执行清理。

每次任务结束时，如果涉及的模块质量发生变化（提升或退化），Agent 必须更新本文件对应行。

> **与 `state/DOMAIN_STATUS.md` 的分工**：
> - 本文件（`QUALITY_LEDGER.md`）：9 个模块的**质量等级**与下一步指针。
> - `state/DOMAIN_STATUS.md`：每个领域的**详细状态 / 报告路径 / 风险**。
> - 业务 DEBT 优先进 `state/p0-p1-register.md` / `state/KNOWN_ISSUES.md`；harness DEBT 必进 `state/HARNESS_DEBT.md`。
> 本文件不替代 `harness/state/HARNESS_DEBT.md` 的详细债务登记。

---

## 评分标准

| 等级 | 含义 |
|---|---|
| A | 稳定，可放心扩展 |
| B | 可用，有少量风险 |
| C | 部分可用，存在结构性问题 |
| D | 高风险，优先治理 |
| F | 不可交付 |

---

## 模块质量评分

| 模块 | 质量 | 最近验证 | 主要风险 | 下一步 |
|---|---|---|---|---|
| 用户域 | C | 2026-06-03 U-2.5-A docs only | `dept_type` 常量类未统一；DataScope 三套实现并行 | U-2.5-B dept_type 最小修复 |
| 配置域 | B | 已对齐 | V2 配置键存在但未消费 | 保持观察，C-1 盘点 |
| 商品域 | C | 待继续 | 展示规则 / 保护期 / 筛选仍需治理 | P-1 商品域盘点 |
| 达人域 | C | 待继续 | 刷新任务 / API 接入不足 | T-1 达人域盘点 |
| 寄样域 | B- | 已有主链 | 真实订单触发完成依赖订单归因 | 跟订单样本联动验证 |
| 订单域 | C | P0 验收未清零 | 真实 pick_source 样本缺失；pay_time 字段问题 | O-1 订单 P0 复核 |
| 业绩域 | C | DASHBOARD-MONEY-AUDIT-001 审查 | settle_amount 回退逻辑污染（P0-001）；提成计算正确但输入数据不可信 | DASHBOARD-MONEY-FIX-001 |
| 分析模块 | D | DASHBOARD-MONEY-AUDIT-001 审查 | 旧版单轨接口（P0-002）、~~毛利展示（P0-004）~~已撤销、talentCommission 错误（P1-002） | DASHBOARD-MONEY-FIX-001 / FIX-002；毛利已纳入 V1（2026-06-05） |
| Harness | A | 2026-06-04 HARNESS-DEBT-GC-001 清理完成 | DEBT-013 fixed / DEBT-014 wontfix；reports 目录受保护状态明确；playwright / nul 临时物已清理 | HARNESS-AGENT-DO-HARDEN |

---

## 更新规则

1. 每次任务结束前，如果涉及某领域的代码修改或验证，必须重新评估该领域质量等级。
2. 质量等级提升或退化时，必须在"最近验证"列更新日期和阶段标记。
3. 新发现的风险必须写入"主要风险"列。
4. "下一步"列必须指向具体的任务标识（如 U-2.5-B、O-1 等）。
5. 本文件不替代 `harness/state/DOMAIN_STATUS.md` 的详细状态，只作为快速概览。
