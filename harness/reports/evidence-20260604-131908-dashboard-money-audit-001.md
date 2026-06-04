# Evidence Report — DASHBOARD-MONEY-AUDIT-001

> **时间**：2026-06-04 13:19 +08:00
> **环境**：real-pre（本地）
> **分支**：feature/auth-system
> **HEAD**：d32bbef5 docs(harness): HARNESS-DEBT-GC-001 archive stale reports and cleanup artifacts
> **工作区**：clean（任务启动时）
> **Scope**：docs（只读审查，不修改业务代码）

---

## 1. 执行命令与结果

### 1.1 Git 状态

```
$ git status --short
（空，工作区 clean）

$ git branch --show-current
feature/auth-system

$ git log -1 --oneline
d32bbef5 docs(harness): HARNESS-DEBT-GC-001 archive stale reports and cleanup artifacts
```

### 1.2 代码搜索

**后端关键文件已读取**：

| 文件 | 行数 | 关键发现 |
| --- | --- | --- |
| DashboardController.java | 158 | 旧版单轨 API，无 time_filter_type |
| DashboardService.java | 1072 | 回退用 order_amount + settle_colonel_commission |
| DataController.java | 216 | /dashboard/metrics 双轨 API 入口 |
| DataApplicationService.java | 1559 | buildMetrics talentCommission 计算错误 |
| PerformanceCalculationService.java | 208 | L113 settle_amount 回退逻辑（P0-001） |
| PerformanceMetricsQueryService.java | 422 | aggregateRange 双轨列选择正确 |
| CommissionService.java | — | calculateTrack 公式正确 |
| ColonelsettlementOrder.java | 941 | 双轨字段完整 |
| PerformanceRecord.java | 305 | 双轨字段完整 |
| MetricsVO.java | 53 | 含 grossProfit（V1 不做） |
| DualTrackMetricsVO.java | 19 | settle + estimate 两轨 |

**前端关键文件已读取**：

| 文件 | 行数 | 关键发现 |
| --- | --- | --- |
| data/index.vue | 1151 | 双轨展示正确，但展示毛利（P0-004） |
| dashboard/index.vue | 455 | 旧版单轨，/100 转元正确 |
| data/dashboard-metrics.ts | 66 | resolveDualTrackMetrics 解包正确 |
| api/dashboard.ts | 24 | getSummary 单轨 API |

### 1.3 SQL 对账（只读 SELECT）

**数据库连接**：`docker compose exec postgres-real-pre psql -U saas -d saas_real_pre`

**订单表汇总**：

```sql
SELECT count(*) AS cnt,
       sum(order_amount) AS order_amount,
       sum(settle_amount) AS settle_amount,
       sum(estimate_service_fee) AS est_svc,
       sum(effective_service_fee) AS eff_svc,
       sum(estimate_tech_service_fee) AS est_tech,
       sum(effective_tech_service_fee) AS eff_tech
FROM colonelsettlement_order;
```

结果：
- cnt=460, order_amount=893832, settle_amount=0
- est_svc=14438, eff_svc=0, est_tech=1472, eff_tech=0
- **所有 460 个订单 settle_time IS NULL**

**业绩表汇总**：

```sql
SELECT count(*) AS cnt,
       sum(pay_amount) AS pay_amt,
       sum(settle_amount) AS settle_amt,
       sum(estimate_service_fee) AS est_svc,
       sum(effective_service_fee) AS eff_svc,
       sum(estimate_service_profit) AS est_profit,
       sum(estimate_recruiter_commission) AS est_recruit,
       sum(estimate_channel_commission) AS est_channel
FROM performance_records WHERE status = 1;
```

结果：
- cnt=404, pay_amt=771125, **settle_amt=771125**（应=0）
- est_svc=12125, eff_svc=0, est_profit=10887
- est_recruit=1643, est_channel=1643

**P0-001 确认**：业绩表 settle_amount=771125 ≠ 订单表 settle_amount=0

### 1.4 API 对账

未执行 API 调用对账（需要登录 token + 运行态容器）。
基于代码审查，API 返回结构已通过 DTO 分析确认。

---

## 2. 代码搜索结果摘要

### 2.1 双轨字段搜索

```
rg "estimate_service_fee|effective_service_fee" backend/src
```

- ColonelsettlementOrder.java：字段定义完整
- PerformanceRecord.java：字段定义完整
- PerformanceCalculationService.java：calculateTrack 分别处理
- CommissionService.java：公式正确

### 2.2 毛利搜索

```
rg "gross_profit|grossProfit" backend/src frontend/src
```

- MetricsVO.java：含 grossProfit 字段
- data/index.vue：多处引用 grossProfit
- CommissionService.java：计算 grossProfit（公式正确但 V1 不展示）

### 2.3 时间字段搜索

```
rg "settle_time|pay_time|create_time|time_filter_type" backend/src
```

- DashboardService.java：始终用 settle_time
- PerformanceMetricsQueryService.java：按轨道选择时间字段
- DashboardController.java：无 time_filter_type 参数

---

## 3. 未执行项及原因

| 未执行项 | 原因 |
| --- | --- |
| API 实际调用对账 | 只读审查优先代码分析；API 调用需 token，可后续 FIX 任务执行 |
| mvn test | 只读审查不触发构建 |
| npm run build | 只读审查不触发前端构建 |
| docker compose restart | 不重启容器 |
| 远端部署 | 不远端部署 |
| 数据库写操作 | 只执行 SELECT |

---

## 4. 结论

- **结论**：FAIL
- 4 个 P0 问题已确认（代码证据 + SQL 证据）
- 4 个 P1 问题已确认（代码证据）
- 2 个 P2 问题已登记
- 未修改任何业务代码、SQL、Docker、env
- 所有结论均有代码路径、SQL 结果或文档依据
