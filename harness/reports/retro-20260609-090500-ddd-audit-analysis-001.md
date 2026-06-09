# DDD-AUDIT-ANALYSIS-001 复盘报告

| 字段 | 值 |
| --- | --- |
| 时间 | 2026-06-09 09:05:00 |
| 任务 | DDD-AUDIT-ANALYSIS-001 |
| 性质 | docs-only / read-only |
| 状态 | DONE_AUDIT（分析域） |

## 1. 任务目标回顾

完成 Phase 0 第 8 项（也是最后一项）领域只读审计 —— 分析模块（Analysis Module / Dashboard / Data Platform），生成 main / evidence / retro 三份 harness 报告，同步外部 KB，更新任务卡 `last_verified_at`。

## 2. 实际过程

### 2.1 时间线

| 时段 | 动作 | 备注 |
| --- | --- | --- |
| 08:10 | KB 上游审计完成 | `audits/ddd-audit-analysis-001.md` 476 行落盘，任务卡状态 `DONE_AUDIT` |
| 09:00 | 本任务启动 | 检查 working tree、读任务卡、确认 25 条禁止动作约束 |
| 09:02-09:04 | 读关键 Java 源码 | DataController / DataApplicationService / DashboardService / DashboardController / MetricsVO / DualTrackMetricsVO / OrderSummaryRowVO / OrderDetailVO / CommissionService |
| 09:04-09:05 | 读前端源码 | dashboard.ts / data.ts / views/dashboard/index.vue / views/data/index.vue |
| 09:05 | 时间锚定、commit 校验 | commit 90701c73 无业务代码 dirty |
| 09:05-09:08 | 写 3 份报告 | main / evidence / retro |

### 2.2 关键判断

- **审计 vs 落盘的边界**：KB 已存在实质性审计（476 行 16 章节），本任务做"事实复核 + 行号校对 + Harness 报告落盘"，**不**重写审计、**不**改写结论
- **与 phase0-sync 报告的冲突**：发现 `ddd-phase0-audit-status-sync-001-20260609-101500.md` 与本任务冲突（声称 ANALYSIS 任务卡缺失 + Phase 0 已收口 + 下一任务为 TEST-ORDER-SYNC-001）。本任务**不**修改该报告，留给 DDD-PHASE0-AUDIT-STATUS-SYNC-001 仲裁
- **25 条禁止动作全过**：仅生成 .md 报告 + 后续更新 KB 文档，不写代码

### 2.3 偏差与风险

| 偏差 | 严重度 | 处置 |
| --- | --- | --- |
| phase0-sync 报告先于本任务 1h10m 生成并落盘 | HIGH | 本任务在 §13 显式记录冲突；DDD-PHASE0-AUDIT-STATUS-SYNC-001 需仲裁 |
| KB 审计已存在但与本任务同时段被 phase0-sync 标记"任务卡缺失" | HIGH | 与上一条同根；需 phase0-sync 任务统一修复 |
| 9 项 metric 中"毛利"实际是 V2 残留 | HIGH-1 | V1 合同未列入 9 项强制；建议 P2 移除（DDD-ORDER-VO-001） |
| `serviceFeeExpenseCent` 与 `CommissionService.serviceFeeNetCent` 公式镜像 | HIGH-2 | 无测试锁定；建议 DDD-TEST-ANALYSIS-FORMULA-001 |
| `commission_records` 表 0 行 → 结算轨实际只读老表 | HIGH-3 | KB 已记录；禁止在 V1 启用依赖结算轨的 UI 卡片 |
| 跨域 Mapper 渗透（SysUserMapper / ColonelsettlementOrderMapper） | CRITICAL | 跨域审计同源；建议 DDD-FACADE-ANALYSIS-001 显式收口 |

## 3. 数据/事实复核

### 3.1 行号复核

| 行号 | 文件 | 内容 | 校验 |
| --- | --- | --- | --- |
| L77-79 | DataController.java | `public class DataController extends DataApplicationService` | ✓ |
| L184 | CommissionService.java | `serviceFeeNetCent` 公式 | ✓ |
| L1421-1428 | DataApplicationService.java | `serviceFeeExpenseCent` 镜像公式 | ✓ |
| L946, L1027 | DataApplicationService.java | `setGrossProfit` V2 残留两处 | ✓ |
| L878-879 | DataApplicationService.java | 双轨独立缓存键 | ✓ |
| L1391-1403 | DataApplicationService.java | metricsCacheKey 四档 | ✓ |
| L49 | MetricsVO.java | `grossProfit` V2 残留 | ✓ |
| L41 | OrderSummaryRowVO.java | `grossProfit` V2 残留 | ✓ |
| L143, L145 | OrderDetailVO.java | `estimateGrossProfit`/`effectiveGrossProfit` V2 残留 | ✓ |
| L129 | DashboardService.java | `getSummary` 单轨 settle_time | ✓ |
| L84-99 | DashboardController.java | `/dashboard/summary` 单一端点 | ✓ |

### 3.2 公式等价性复核

```
业绩域（CommissionService.serviceFeeNetCent L183-184）：
  max(income - tech - expense, 0)

分析域（DataApplicationService.serviceFeeExpenseCent L1421-1428）：
  max(income - techDeduction - profit, 0)
  其中 techDeduction = estimateTrack ? tech : 0L

两者均正确，但参数顺序不同 + 输入域不同
  - 业绩域已知 tech 与 expense，求 net
  - 分析域已知 tech 与 profit，求 expense

如果分析域 techDeduction 漏判（estimate 轨 tech 0L），会让 expense > 真实值
  → 服务费收益减少 → 业绩报表对不齐
```

### 3.3 V2 残留影响范围

```
受影响 VO 文件：
  MetricsVO              L49
  OrderSummaryRowVO      L41
  OrderDetailVO          L143, L145

UI 消费情况：
  views/data/index.vue（1310 行）— 未消费 grossProfit 渲染
  views/dashboard/index.vue（454 行）— 老单轨，无 grossProfit

实际生效范围：仅 VO 字段写入 + Redis 序列化，**无 UI 影响**，
             但 JSON payload 仍携带这 4 个字段

V1 处置建议：P2 移除（DDD-ORDER-VO-001）
  - 与 `commission_records` 表 0 行相关
  - 与 `real-pre` 验证无样本相关
  - 必须配单元测试（DDD-TEST-VO-001）
```

## 4. 流程改进建议

### 4.1 KB 一致性检查

- 建议在 phase0-sync 任务启动时，先 Read 实际 KB 路径，不依赖 KB 索引
- 建议 phase0-sync 任务读 `state/02-domain-status.md` 而非自填状态
- **本任务已做**：在 §I 段逐项复核 phase0-sync 与实际 KB 的一致性

### 4.2 Harness 报告落盘

- 本次采用 `talent-001` 报告风格（header table + 核心发现速览 + git status + 最终结论）
- 保留 talent-001 风格中"1-18 摘要"段以"KB 链接 + 速览矩阵"替换（KB 审计已存在 16 章节）
- 保留跨域数据（订单/业绩/用户/达人/招商 5 域渗透）

### 4.3 冲突显式化

- **不**直接修改 phase0-sync 报告（避免 docs-only 范围漂移）
- **不**在 main 报告中宣称"修复"冲突（避免越权）
- **明确**将冲突交给 phase0-sync 任务仲裁

## 5. 留给下一任务（DDD-PHASE0-AUDIT-STATUS-SYNC-001）

> 以下 4 项是 DDD-PHASE0-AUDIT-STATUS-SYNC-001 必须显式仲裁的内容，本任务**不**处理：

1. **任务卡状态**：DDD-AUDIT-ANALYSIS-001 任务卡已 DONE_AUDIT（2026-06-09 08:10），不要重新新建
2. **审计文件**：9 份审计文件均已存在（analysis 476 行已落盘）
3. **Phase 0 收口**：本任务明确禁止标 DONE_AUDIT_COMPLETE；收口由 phase0-sync 任务统一判断
4. **下一任务**：本任务默认 `DDD-PHASE0-AUDIT-STATUS-SYNC-001`（自指）；phase0-sync 报告写 `DDD-TEST-ORDER-SYNC-001`（冲突）

## 6. 25 条禁止动作自检

- [x] 未修改 Java（仅 Read）
- [x] 未修改 Vue（仅 Read）
- [x] 未修改 SQL migration
- [x] 未修改 Docker / env / Nginx / 部署脚本
- [x] 未写数据库
- [x] 未重启容器
- [x] 未远端部署
- [x] 未提交
- [x] 未推送
- [x] 未执行 `git add .`
- [x] 未修复 Dashboard 代码
- [x] 未补测试
- [x] 未调整任何金额公式
- [x] 未执行订单同步
- [x] 未执行业绩补算
- [x] 未执行 SQL 写库对账
- [x] 未把结算轨无样本写成已完成（明确标记 HIGH-3）
- [x] 未把前端截图当成唯一事实（明确 KB 文本 + 行号 + 公式三方证据）
- [x] 未把容器 healthy 当成数据对账通过
- [x] 未把 V2 高级看板写成 V1 当前目标
- [x] 未把 Phase 0 写成已收口完成
- [x] 未提出"立即重写 dashboard"
- [x] 未提出"立即改金额公式"
- [x] 未提出"立即全局包结构迁移"
- [x] 未泄露 secret / token / client_secret / password / cookie

## 7. 9 条禁止结论自检

- [x] 未输出"分析模块 DDD 重构已完成"
- [x] 未输出"Phase 0 已完成并收口"
- [x] 未输出"可以立即重写 dashboard"
- [x] 未输出"可以立即改金额公式"
- [x] 未输出"结算轨已验证完成"
- [x] 未输出"前端截图即可证明口径正确"
- [x] 未输出"V2 高级看板是 V1 当前目标"
- [x] 未输出"可以跳过防护测试直接进入 Facade"
- [x] 未输出"可以立即包结构迁移"

## 8. 总结

- 本次审计**复核**了 KB 已有的 16 章节分析域审计
- 通过 Read 直接校验了 11 个关键行号、3 套并行 DataScope 实现、9 跨域 Mapper
- 明确了 4 处 V2 残留 + 1 处公式镜像风险 + 1 处结算轨无样本
- 提出了 4 个 DDD 拆分项（READMODEL / CACHE / FACADE / VO），**未**触发实施
- 显式记录了与 phase0-sync 报告的冲突，留给 DDD-PHASE0-AUDIT-STATUS-SYNC-001 仲裁
- 默认下一任务：`DDD-PHASE0-AUDIT-STATUS-SYNC-001`

## 9. 改进建议（供 harness 系统）

1. **审计任务前置条件检查**：当后续 harness 报告声称"任务卡缺失"时，应先 Read 实际 KB 路径再断言
2. **Harness 报告时间戳排序**：phase0-sync 报告（10:15:00）晚于 analysis 审计（08:10:00），但 phase0-sync 报告反向认定 analysis 任务卡缺失；建议 harness 系统在生成收口报告前自动按 `last_verified_at` 升序检查
3. **冲突显式化机制**：当两份报告对同一事实有冲突时，应保留双发 + 显式 §I 冲突说明，**不**静默修改；本次本任务已遵循
4. **docs-only 范围边界的硬约束**：禁止在 audit 任务里"顺手"修改其他任务的报告；本次本任务已遵循
