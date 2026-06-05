# Evidence Report — DASHBOARD-FULL-MONEY-RECON-001

| 字段 | 内容 |
| --- | --- |
| 任务 ID | DASHBOARD-FULL-MONEY-RECON-001 |
| 任务类型 | 只读审查 / docs-only |
| 报告日期 | 2026-06-05 |
| 报告主路径 | `harness/reports/dashboard-full-money-recon-001-20260605-102309.md` |
| 分支 | `feature/auth-system` |
| HEAD commit | `15427ddc` |
| 工作区 dirty | 0(开始/结束均 clean,报告文件 untracked) |
| 容器 | real-pre(本地)全部 healthy |
| 登录 | admin/admin123 dataScope=3(ALL) |
| 结论 | **FAIL** — 用户截图 9 个非零指标与 feature/auth-system 当前 main 完全不一致(强证据) |

---

## 1. 执行命令

```bash
# 0. Harness Intake
git status --short                                # 0 输出(clean)
git branch --show-current                          # feature/auth-system
git log -1 --oneline                               # 15427ddc docs(harness): ORDER-DETAIL-TAB-FIX-001 closeout
docker ps --format "table {{.Names}}\t{{.Status}}" # 4 容器 healthy

# 1. 前端代码搜索
grep -rn "毛利\|grossProfit" frontend/src
grep -rn "媒介" frontend/src
grep -rn "今日订单数\|今日GMV\|今日服务费\|今日提成\|总订单\|订单额\|招商提成\|渠道提成\|服务费支出" frontend/src/views/data/index.vue

# 2. 后端代码阅读
Read backend/src/main/java/com/colonel/saas/controller/DataController.java
Read backend/src/main/java/com/colonel/saas/controller/DashboardController.java
Read backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
Read backend/src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java
Read backend/src/main/java/com/colonel/saas/service/CommissionService.java
Read backend/src/main/java/com/colonel/saas/vo/data/MetricsVO.java

# 3. 数据库结构确认
docker exec saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre -c "\dt"
docker exec saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre -c "\d colonelsettlement_order"
docker exec saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre -c "\d performance_records"
docker exec saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre -c "\d dashboard_performance_daily"

# 4. 数据库只读统计(详见 §3)

# 5. API 调用
curl -s -X POST http://localhost:8081/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/dashboard/metrics
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/data/orders/summary
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8081/api/data/orders?page=1&size=5"

# 6. 路由/旧版看板确认
Read frontend/src/router/index.ts:170-180
Read frontend/src/views/dashboard/index.vue (旧版 4-stat-card)

# 7. git 历史
git log --all --oneline -- frontend/src/views/data/index.vue
git show HEAD:frontend/src/views/dashboard/index.vue | head
```

---

## 2. SQL 执行结果(全部只读 SELECT)

### 2.1 总行数 + 状态分布

```sql
SELECT COUNT(*) AS total_orders, COUNT(*) FILTER (WHERE deleted=0) AS active_orders FROM colonelsettlement_order;
-- total_orders|active_orders
-- 1033|1033

SELECT order_status, flow_point, COUNT(*) AS cnt FROM colonelsettlement_order WHERE deleted=0 GROUP BY order_status, flow_point ORDER BY cnt DESC;
-- order_status|flow_point|cnt
-- 1||957
-- 4||76
```

### 2.2 时间范围

```sql
SELECT MIN(create_time), MAX(create_time), MIN(settle_time), MAX(settle_time), MIN(pay_time), MAX(pay_time)
FROM colonelsettlement_order WHERE deleted=0;
-- 2026-06-03 16:48:29|2026-06-05 10:06:47|||2026-06-03 16:48:29|2026-06-05 10:06:47
```

→ 全部 `settle_time = NULL`(无结算订单)。

### 2.3 订单金额双轨汇总

```sql
SELECT COUNT(*),
       COALESCE(SUM(order_amount),0) AS pay_amount,
       COALESCE(SUM(settle_amount),0) AS settle_amount,
       COALESCE(SUM(estimate_service_fee),0) AS est_sfee,
       COALESCE(SUM(effective_service_fee),0) AS eff_sfee,
       COALESCE(SUM(estimate_tech_service_fee),0) AS est_tech,
       COALESCE(SUM(effective_tech_service_fee),0) AS eff_tech
FROM colonelsettlement_order WHERE deleted=0;
-- 1033|2107251|0|35702|0|3617|0
```

→ 折算元: 1033 单 / ¥21,072.51 / 0 / ¥357.02 / 0 / ¥36.17 / 0

### 2.4 业绩表汇总

```sql
SELECT COUNT(*),
       COALESCE(SUM(pay_amount),0), COALESCE(SUM(settle_amount),0),
       COALESCE(SUM(estimate_service_fee),0), COALESCE(SUM(effective_service_fee),0),
       COALESCE(SUM(estimate_tech_service_fee),0), COALESCE(SUM(effective_tech_service_fee),0),
       COALESCE(SUM(estimate_service_profit),0), COALESCE(SUM(effective_service_profit),0),
       COALESCE(SUM(estimate_recruiter_commission),0), COALESCE(SUM(effective_recruiter_commission),0),
       COALESCE(SUM(estimate_channel_commission),0), COALESCE(SUM(effective_channel_commission),0),
       COALESCE(SUM(estimate_gross_profit),0), COALESCE(SUM(effective_gross_profit),0)
FROM performance_records WHERE is_valid=TRUE;
-- 910|1849172|0|30576|0|3100|0|27476|0|4141|0|4141|0|19194|0
```

→ 折算元: 910 单 / ¥18,491.72 / 0 / ¥305.76 / 0 / ¥31.00 / 0 / ¥274.76 / 0 / ¥41.41 / 0 / ¥41.41 / 0 / ¥191.94 / 0

### 2.5 业绩表有效性状态

```sql
SELECT is_valid, is_reversed, order_status, COUNT(*) FROM performance_records
GROUP BY is_valid, is_reversed, order_status ORDER BY cnt DESC;
-- t|f|1|910
-- f|t|4|69
```

### 2.6 业绩表与订单表对账

```sql
SELECT
  (SELECT COUNT(*) FROM colonelsettlement_order WHERE deleted=0) AS orders_active,
  (SELECT COUNT(*) FROM performance_records) AS perf_rows,
  (SELECT COUNT(*) FROM colonelsettlement_order o LEFT JOIN performance_records p ON p.order_id = o.order_id WHERE o.deleted=0) AS joined,
  (SELECT COUNT(*) FROM colonelsettlement_order o WHERE o.deleted=0 AND NOT EXISTS (SELECT 1 FROM performance_records p WHERE p.order_id = o.order_id)) AS no_perf;
-- 1033|979|1033|54
```

→ 1033 订单 = 910 业绩(valid) + 69 业绩(reversed) + 54 缺业绩

### 2.7 业绩表实际比例

```sql
SELECT recruiter_commission_rate, channel_commission_rate, COUNT(*) FROM performance_records
WHERE is_valid=TRUE GROUP BY recruiter_commission_rate, channel_commission_rate ORDER BY cnt DESC LIMIT 10;
-- 0.1500|0.1500|910
```

→ 业绩表所有 valid 记录 100% 0.15,与 system_config 默认一致。

### 2.8 归因覆盖率

```sql
SELECT COUNT(*) FILTER (WHERE pick_source IS NOT NULL AND pick_source <> '') AS with_pick_source,
       COUNT(*) FILTER (WHERE channel_user_id IS NOT NULL) AS with_channel,
       COUNT(*) FILTER (WHERE colonel_user_id IS NOT NULL) AS with_colonel,
       COUNT(*) AS total
FROM colonelsettlement_order WHERE deleted=0;
-- 0|0|15|1033
```

→ pick_source 0 / channel 0 / colonel 15, 全部未通过系统转链(对应 RISK-001 阻塞)。

### 2.9 汇总表

```sql
SELECT stat_date, order_count, order_amount, service_fee_net, updated_at
FROM dashboard_performance_daily ORDER BY stat_date DESC LIMIT 10;
-- 2026-06-05|95|230608|0|...
-- 2026-06-04|573|1173206|0|...
-- 2026-06-03|307|574389|0|...
```

→ 汇总表 3 天, service_fee_net 字段**3 天全 0**。

### 2.10 提成相关配置与表

```sql
-- commission_config: 0 行
SELECT COUNT(*) FROM commission_config;  -- 0

-- system_config 提成相关
SELECT config_key, config_value FROM system_config
WHERE config_key LIKE '%commission%' OR config_key LIKE '%ratio%' OR config_key LIKE '%recruiter%' OR config_key LIKE '%channel%';
-- talent.exclusive.service_fee_ratio|70
-- merchant.exclusive.service_fee_ratio|70
-- commission.business_default_ratio|0.15
-- commission.channel_default_ratio|0.15

-- 衍生表
SELECT COUNT(*) FROM commissions;       -- 0
SELECT COUNT(*) FROM commission_settlement;  -- 0
SELECT COUNT(*) FROM order_performance;  -- 0
SELECT COUNT(*) FROM order_detail;       -- 0
SELECT COUNT(*) FROM colonel_order_settlement;  -- 0
SELECT COUNT(*) FROM order_decrypt_record;  -- 0
```

### 2.11 事件通道

```sql
SELECT event_type, COUNT(*), MAX(occurred_at) FROM domain_event_outbox
GROUP BY event_type ORDER BY cnt DESC;
-- ProductDisplayRuleAppliedEvent|2659|2026-06-05 02:05:53
-- ProductListedEvent|2644|2026-06-05 02:05:53
-- ProductHiddenEvent|87|2026-06-03 06:16:59
-- ActivitySyncCompletedEvent|42|2026-06-05 02:05:53
-- SampleCreated|24|2026-06-03 14:39:53
-- SampleSigned|15|2026-05-31 10:43:23
-- SampleApproved|15|2026-05-31 10:43:11
-- SampleShipped|15|2026-05-31 10:43:23
-- ProductOwnerChangedEvent|1|2026-05-28 01:14:42

SELECT status, COUNT(*) FROM domain_event_outbox GROUP BY status;
-- PUBLISHED|5502
```

→ 全部 PUBLISHED;没有 OrderSynced/OrderSettled/PerformanceCalculated 事件。

### 2.12 分区累计

```sql
-- 12 个月分区全部计数
-- cso_2026_04|0
-- cso_2026_05|0
-- cso_2026_06|1033
-- cso_2026_07|0
-- ... cso_2026_12 / 2027_01..03 全部 0
-- 总计 1033(全部在 2026-06)
```

---

## 3. API 响应(完整保存)

### 3.1 POST /api/auth/login

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0YmM1OTM3YS0wZmYyLTQ3OGItOTg4Yi04NjRlZTk2ZjE3OTQiLCJ0eXBlIjoiYWNjZXNzIiwiZGF0YVNjb3BlIjozLCJyb2xlQ29kZXMiOlsiYWRtaW4iXSwidXNlcm5hbWUiOiJhZG1pbiIsInBlbmRpbmdBY3RpdmF0aW9uIjpmYWxzZSwiaWF0IjoxNzgwNjI1Nzc4LCJleHAiOjE3ODA2MzI5Nzh9...",
    "dataScope": 3,
    "roleCodes": ["admin"],
    "username": "admin",
    "realName": "系统管理员"
  }
}
```

### 3.2 GET /api/dashboard/metrics

```json
{
  "code": 200,
  "data": {
    "settle": {
      "todayOrderCount": 0, "todayGmv": 0.0, "pendingShipCount": 0,
      "trend7d": [
        {"date": "2026-05-30", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-05-31", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-06-01", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-06-02", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-05-03", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-06-04", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-06-05", "orderCount": 0, "gmv": 0.0}
      ],
      "totalOrders": 0, "totalAmount": 0.0,
      "serviceFee": 0.0, "commission": 0.0,
      "serviceFeeIncome": 0.0, "techServiceFee": 0.0,
      "talentCommission": 0.0, "bizCommission": 0.0, "channelCommission": 0.0,
      "grossProfit": 0.0,
      "amountTrack": "effective", "metricsSource": "performance_records",
      "track": "settleTime"
    },
    "estimate": {
      "todayOrderCount": 95, "todayGmv": 2306.08, "pendingShipCount": 957,
      "trend7d": [
        {"date": "2026-05-30", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-05-31", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-06-01", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-06-02", "orderCount": 0, "gmv": 0.0},
        {"date": "2026-06-03", "orderCount": 286, "gmv": 5348.17},
        {"date": "2026-06-04", "orderCount": 573, "gmv": 11732.06},
        {"date": "2026-06-05", "orderCount": 95, "gmv": 2306.08}
      ],
      "totalOrders": 95, "totalAmount": 2306.08,
      "serviceFee": 36.25, "commission": 10.94,
      "serviceFeeIncome": 40.31, "techServiceFee": 4.06,
      "talentCommission": 0.0, "bizCommission": 5.47, "channelCommission": 5.47,
      "grossProfit": 25.31,
      "amountTrack": "estimate", "metricsSource": "performance_records",
      "track": "createTime"
    }
  }
}
```

### 3.3 GET /api/data/orders/summary(默认近 30 天,createTime)

```json
{
  "code": 200,
  "data": {
    "total": {
      "talentPromoterCount": 0,
      "colonelPromoterCount": 21,
      "productCount": 198,
      "orderCount": 1033,
      "orderAmount": 21072.51,
      "productAverageServiceFeeRate": 1.69,
      "orderAverageServiceFeeRate": 1.69,
      "serviceFeeIncome": 357.02,
      "techServiceFee": 36.17,
      "serviceFeeExpense": 96.22,
      "serviceFeeProfit": 320.85,
      "grossProfit": 224.63
    },
    "records": [
      {"date": "2026-06-05", "orderCount": 100, "orderAmount": 2416.67, "serviceFeeIncome": 42.73, "techServiceFee": 4.30, "serviceFeeExpense": 11.52, "serviceFeeProfit": 38.43, "grossProfit": 26.91},
      {"date": "2026-06-04", "orderCount": 608, "orderAmount": 12448.72, "serviceFeeIncome": 207.44, "techServiceFee": 20.94, "serviceFeeExpense": 55.84, "serviceFeeProfit": 186.50, "grossProfit": 130.66},
      {"date": "2026-06-03", "orderCount": 325, "orderAmount": 6207.12, "serviceFeeIncome": 106.85, "techServiceFee": 10.93, "serviceFeeExpense": 28.76, "serviceFeeProfit": 95.92, "grossProfit": 67.16}
    ]
  }
}
```

### 3.4 GET /api/data/orders?page=1&size=5

```json
{
  "code": 200,
  "data": {
    "total": 1033,
    "records": [5 条订单 ...]
  }
}
```

### 3.5 GET /api/system/health

```json
{"status":"UP"}
```

---

## 4. 关键代码路径(已审阅)

### 4.1 前端

| 文件 | 关键发现 |
| --- | --- |
| `frontend/src/views/data/index.vue:1-15` | 当前看板注释明确只有"今日订单数 / GMV / 服务费净收 / 提成"4 张卡,无双轨的"总订单数/订单额/服务费支出/服务费收益/招商提成/渠道提成/毛利" |
| `frontend/src/views/data/index.vue:391-416` | `metricLabels` 只生成 4 个 label:orders, amount, fee, profit;无 9 张卡的 label |
| `frontend/src/views/data/index.vue:267-291` | "收入分拆"标签组 6 个:招商+渠道提成 / 服务费收入 / 技术服务费 / 达人分佣 / 招商提成 / 渠道提成 — 不是独立卡片 |
| `frontend/src/views/data/index.vue:index.test.ts:164-166` | 强制 assert: `今日毛利/预估轨毛利/结算轨毛利 都不展示` |
| `frontend/src/views/dashboard/index.vue` | 旧版看板,4 张 stat-card(累计 GMV/未归因/归因率/服务费收入),不是 9 张 |
| `frontend/src/api/data.ts:97` | 注释"首页展示的全局关键指标(总订单数、总金额、服务费收入、毛利等)" — **已过时**,与实际不一致 |
| `frontend/src/router/index.ts:175-179` | 旧版 `/dashboard` 路由仍指向 `views/dashboard/index.vue` |

### 4.2 后端

| 文件 | 关键发现 |
| --- | --- |
| `DataController.java:174-181` | `/api/dashboard/metrics` 入口 |
| `DataApplicationService.java:787-805` | `getMetrics()` 双轨:settle 轨 + estimate 轨,30s 缓存 |
| `DataApplicationService.java:807-931` | `buildMetrics()`:严格用 `todayStart, tomorrowStart` 当天窗口,优先 `performance_records`,回退 `colonelsettlement_order` + `CommissionService` 重算 |
| `DataApplicationService.java:150-172` | `/api/data/orders/summary` 入口 |
| `DataApplicationService.java:656-785` | `getOrderSummary()`:按时间桶聚合 + 按活动桶 `CommissionService.calculateByActivityBuckets` 重算提成 |
| `DataApplicationService.java:1899-1924` | `toOrderSummaryRow()`: `serviceFeeExpense = summary.bizCommission + summary.channelCommission`(订单事实重算口径) |
| `DataApplicationService.java:486-589` | `toOrderDetailVO()`: `estimateServiceFeeExpense = estimateRecruiterCommission + estimateChannelCommission`(业绩表口径) |
| `PerformanceMetricsQueryService.java:78-139` | `aggregateRange()`:从 `performance_records JOIN colonelsettlement_order` 聚合,严格按 `co.create_time/co.settle_time` 范围 |
| `CommissionService.java:243-283` | `calculateByActivityBuckets()`: `serviceFeeNet = serviceFeeIncome - techServiceFee`,`bizCommission = rate × serviceFeeNet`(按活动分桶) |
| `MetricsVO.java:17-52` | 包含 `grossProfit`,`talentCommission`,`bizCommission`,`channelCommission` 等 12 个字段 |
| `DashboardController.java:84-99` | 旧版 `/api/dashboard/summary` 入口,30s 缓存 |
| `DashboardService.java` | 旧版 service,4 stat-card 字段 |

### 4.3 数据库 schema

| 表 | 关键字段 |
| --- | --- |
| `colonelsettlement_order` | `order_amount`, `settle_amount`, `estimate_service_fee`, `effective_service_fee`, `estimate_tech_service_fee`, `effective_tech_service_fee`, `pay_time`, `settle_time`, `pick_source`, `channel_user_id`, `colonel_user_id`(注: 无 `default_channel_id` / `default_recruiter_id`) |
| `performance_records` | `pay_amount`, `settle_amount`, `estimate_*`, `effective_*`(全套), `recruiter_commission_rate`, `channel_commission_rate`, `is_valid`, `is_reversed`, `settle_time`, `calculated_at` |
| `dashboard_performance_daily` | `stat_date`, `order_count`, `order_amount`, `service_fee_net`, `updated_at`(只 3 天) |
| `commission_config` | 0 行(空) |
| `commissions` / `commission_settlement` / `order_performance` / `order_detail` / `colonel_order_settlement` / `order_decrypt_record` | 全部 0 行(空) |

---

## 5. 数据对账计算

### 5.1 业绩表"总" 与 API summary "total" 对账

| 字段 | 业绩表 valid=910 汇总 | API `/data/orders/summary` total | 差异 | 解读 |
| --- | ---: | ---: | ---: | --- |
| orderCount | 910 | 1033 | -123 | API 含 deleted=0 全表订单;业绩表只覆盖 910 单 |
| pay_amount | ¥18,491.72 | ¥21,072.51(由 `order_amount`) | -¥2,580.79 | API 用了订单表 `order_amount` 字段,业绩表 `pay_amount` 字段 |
| estimate_service_fee | ¥305.76 | ¥357.02(由 `estimate_service_fee`) | -¥51.26 | API 用了订单表 estimate_service_fee,业绩表自己的 estimate_service_fee |
| estimate_tech_service_fee | ¥31.00 | ¥36.17 | -¥5.17 | 同上 |
| estimate_recruiter_commission | ¥41.41 | (无直接对应) | — | — |
| estimate_channel_commission | ¥41.41 | (无直接对应) | — | — |
| 招商+渠道(代理) | **¥82.82** | **serviceFeeExpense=¥96.22** | **+¥13.40** | **P0 口径漂移** |
| estimate_service_profit | ¥274.76 | serviceFeeProfit=¥320.85(=357.02-36.17) | -¥46.09 | API 用订单表 estimate_service_fee 减 estimate_tech_service_fee;业绩表存的是已经过 `CommissionService` 重算的服务费净收益 |
| estimate_gross_profit | ¥191.94 | grossProfit=¥224.63 | -¥32.69 | API 用订单事实 `CommissionService` 重算(基数=serviceFeeIncome=357.02,biz+channel=96.22,gross=320.85-96.22=224.63 约) |

### 5.2 用户截图数字反推(必不可能重现)

| 用户截图 | 反推 0.15 比例所需的基数 | 业绩表基数 | 差异 |
| ---: | ---: | ---: | --- |
| 招商提成 ¥270.3 | 270.3 / 0.15 = 1,802 | service_profit=274.76 / pay_amount=18,491.72 | 6.5x ~ 8x 差距 |
| 渠道提成 ¥341.87 | 341.87 / 0.15 = 2,279.13 | 同上 | 8.3x 差距 |
| 服务费收益 ¥2,703 | 2,703 / 0.15 = 18,020(基数) | service_profit=274.76 | **65.5x 差距** |
| 服务费支出 ¥6.65 | 6.65 / 0.15 = 44.33 | 业绩表 82.82 | 12.5x 差距或 0.08x |
| 毛利 ¥2,090.82 | 2,090.82 / 0.15 = 13,938.8 | gross_profit=191.94 | **72.6x 差距** |
| 总订单 ¥7,114 | — | 1,033(订单表)/ 910(业绩 valid) | **6.9x / 7.8x 差距** |

→ **用户截图的所有非零数字与当前 DB/API 任何口径都不匹配,差距 5-100 倍**。

### 5.3 用户截图内部自洽性(即使忽略 DB)

```
2945.9(服务费收入) - 236.25(技术服务费) - 6.65(服务费支出) = 2703.0  ✅ 自洽(前端展示口径)
2703(服务费收益) - 270.3(招商) - 341.87(渠道) = 2090.83  ≈ 2090.82(毛利) ✅ 自洽

但:
- 服务费支出 6.65 ≠ 招商 270.3 + 渠道 341.87 = 612.17  ❌
  → 服务费支出不是招商+渠道之和
- 服务费支出 6.65 是 sum(服务费收入-技术服务费-服务费收益) = 2945.9-236.25-2703 = 6.65 ✅
  → 服务费支出 = 服务费收入 - 技术服务费 - 服务费收益 的反推
- 而服务费收益 2703 = 招商 270.3 / 0.10(10%) → 招商比例 10% ≠ 默认 15%
- 渠道 341.87 / 2703 = 12.65% → 渠道比例 12.65% ≠ 默认 15%
```

→ **用户截图的服务费支出 6.65 = 服务费收入 - 技术服务费 - 服务费收益 的恒等反推**,**不是 "招商 + 渠道" 之和**。这意味着用户截图的代码逻辑里:
- 服务费支出 = "服务费收入 - 技术服务费 - 服务费收益" 公式反推
- 招商比例 10% / 渠道比例 12.65%(非默认 0.15)

**但当前代码**:
- `serviceFeeExpense = recruiter + channel`(口径与用户截图相反)
- 比例默认 0.15(与用户截图的 10% / 12.65% 不一致)
- 服务费支出字段在 DataApplicationService.toOrderSummaryRow 用订单事实重算,**基数是 `settle_colonel_commission` 不是 `serviceFeeIncome`**

**结论**:用户截图的代码版本用的是**完全不同的口径与比例**,与 `feature/auth-system` 当前 main 不一致。

---

## 6. Harness 合规检查

| 检查项 | 状态 |
| --- | --- |
| 未改业务代码 | ✅ 0 行 |
| 未写库 | ✅ 仅 SELECT |
| 未重启容器 | ✅ |
| 未部署远端 | ✅ |
| 未 `git add .` | ✅ |
| 未 `git commit` / `git push` | ✅ |
| 未触发迁移 | ✅ |
| 工作区 clean(开始) | ✅ 0 dirty |
| 工作区 clean(结束) | ✅ 0 dirty |
| 仅新增 `harness/reports/*.md` | ✅ 2 个文件 |
| 未触碰 `.env*` | ✅ |
| 未触碰 `docker-compose*.yml` | ✅ |

---

## 7. 结论

**DONE_AUDIT — 总体结论 = FAIL**

**P0 问题数 = 6** (DASH-RECON-P0-001 ~ 006)
**P1 问题数 = 5** (DASH-RECON-P1-001 ~ 005)
**P2 问题数 = 3** (DASH-RECON-P2-001 ~ 003)

### 每个指标对齐情况

| 指标 | 是否对齐 |
| --- | --- |
| 总订单数-成交 | ❌ 用户截图 7114 vs DB/API 1033,差距 6.9x |
| 总订单数-结算 | ✅ 0 vs 0 |
| 订单额-成交 | ❌ 截图 ¥146103.82 vs DB ¥21,072.51,差距 6.9x |
| 订单额-结算 | ✅ 0 vs 0 |
| 服务费收入-预估 | ❌ 截图 ¥2945.9 vs DB ¥357.02,差距 8.2x |
| 服务费收入-结算 | ✅ 0 vs 0 |
| 技术服务费-预估 | ❌ 截图 ¥236.25 vs DB ¥36.17,差距 6.5x |
| 技术服务费-结算 | ✅ 0 vs 0 |
| 服务费支出-预估 | ❌ 截图 ¥6.65 vs API ¥96.22 / 业绩表 ¥82.82,完全不一致 |
| 服务费支出-结算 | ✅ 0 vs 0 |
| 服务费收益-预估 | ❌ 截图 ¥2703 vs DB ¥274.76,差距 9.8x |
| 服务费收益-结算 | ✅ 0 vs 0 |
| 招商提成-预估 | ❌ 截图 ¥270.3 vs DB ¥41.41,差距 6.5x |
| 招商提成-结算 | ✅ 0 vs 0 |
| 渠道提成-预估 | ❌ 截图 ¥341.87 vs DB ¥41.41,差距 8.3x |
| 渠道提成-结算 | ✅ 0 vs 0 |
| 毛利-预估 | ❌ 截图 ¥2090.82 vs DB ¥191.94,差距 10.9x |
| 毛利-结算 | ✅ 0 vs 0 |

**9 个非零指标全部 FAIL,9 个 0 指标全部 PASS**。

### 最大根因(强证据)

**用户截图 9 张卡片的代码版本与 `feature/auth-system` 当前 main commit `15427ddc` 完全不一致**。
- 截图卡片使用 服务费支出 = 服务费收入 - 技术服务费 - 服务费收益 的恒等反推口径
- 截图使用 招商 10% / 渠道 12.65% 的非默认比例
- 截图数据量级(总订单 7114 / 总额 14.6 万)与当前 DB(1033 单 / 2.1 万)6.9 倍差距
- 当前 main 代码:`serviceFeeExpense = recruiter + channel`,默认 15% 比例,API 实际值 96.22/82.82

**P0 修复路径 = DASHBOARD-MONEY-FIX-002 + 003 + 004**(本审查只读,不入修复阶段)

### 报告路径

- 主报告:`harness/reports/dashboard-full-money-recon-001-20260605-102309.md`
- evidence:`harness/reports/evidence-20260605-102309-dashboard-full-money-recon-001.md`

### 下一步修复任务

1. **DASHBOARD-MONEY-FIX-002** — Dashboard API 指标口径与 SQL 对齐(业绩域单源)
2. **DASHBOARD-MONEY-FIX-003** — 服务费支出口径修复
3. **DASHBOARD-MONEY-FIX-004** — 毛利 V1 合同:彻底下线
4. **DASHBOARD-SETTLEMENT-SYNC-001** — 结算轨全 0 UI 提示补齐
5. **DASHBOARD-RECON-TEST-001** — 端到端对账测试补齐
6. **DASHBOARD-METRIC-CARD-RECON-001** — 旧版看板清理(可选,需业务确认)
7. **DASHBOARD-E2E-001** — real-pre 端到端验收

### 阻塞项

- 用户必须确认截图来源(本地 / 远端 / test / 旧 bundle)
- 业务必须确认 V1 合同(毛利 / 服务费支出双口径)
- 业务必须确认旧版 `/dashboard` 看板是否仍有团队在使用
