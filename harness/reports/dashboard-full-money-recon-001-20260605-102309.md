# DASHBOARD-FULL-MONEY-RECON-001 数据看板全部资金指标与后台对齐审查

| 字段 | 内容 |
| --- | --- |
| 任务 ID | DASHBOARD-FULL-MONEY-RECON-001 |
| 任务类型 | 只读审查 / docs-only |
| 报告日期 | 2026-06-05 |
| 审查模式 | Read-Only Harness Engineering Audit |
| 默认分支 | feature/auth-system |
| 任务级 | P0 |
| 执行环境 | local real-pre (`saas-active-*`) |
| 报告状态 | DONE_AUDIT |
| 总体结论 | **FAIL — 用户截图 9 项指标在当前代码库中无任何前端页面 / 后端 API 字段映射;真实数据全部为另一组口径** |

---

## 0. 任务摘要

用户反馈**数据平台前端**展示的 9 张卡片(总订单数/订单额/服务费收入/技术服务费/服务费支出/服务费收益/招商提成/渠道提成/毛利)与**后台数据无法对齐**,并强调:
- "媒介"必须统一改为"渠道"。
- 必须逐项审查 18 个指标(9 项 × 双轨 = 18)。
- 不允许只看总订单数或订单额就提前结束。

**本审查未对任何业务代码、SQL、Docker、env、数据库做修改**;仅执行:
- 源码阅读(后端 6 个核心 service / VO / SQL)
- 真实只读 SQL(8 段)
- 真实 API 调用(2 段)
- 数据库表结构确认(7 张表)

---

## 1. 执行环境

| 项 | 值 |
| --- | --- |
| 分支 | `feature/auth-system` |
| HEAD commit | `15427ddc docs(harness): ORDER-DETAIL-TAB-FIX-001 closeout` |
| 工作区 dirty | **0(clean)** — 审查开始时 `git status --short` 无输出 |
| 后端容器 | `saas-active-backend-real-pre-1` Up 12h healthy → `localhost:8081` |
| 前端容器 | `saas-active-frontend-real-pre-1` Up 12h healthy → `localhost:3001` |
| 数据库 | `saas-active-postgres-real-pre-1` Up 20h healthy → `saas_real_pre` |
| Redis | `saas-active-redis-real-pre-1` Up 2d healthy |
| 登录角色 | `admin / admin123` (dataScope=3 ALL, roles=[admin]) |
| 登录来源 | `.env.real-pre` ADMIN_PASSWORD=admin123 |

---

## 2. Harness Gate 记录

| Gate | 状态 | 证据 |
| --- | --- | --- |
| 任务开始 dirty | 0 | `git status --short` 无输出 |
| 禁止改业务代码 | PASS | 本任务仅产生 `harness/reports/*.md` |
| 禁止写库 SQL | PASS | 仅 `SELECT` / `EXPLAIN` 类只读操作 |
| 禁止重启容器 | PASS | 未执行 docker 重建/重启 |
| 禁止 `git add .` | PASS | 未执行任何 git stage/push |
| 必读入口 | PASS | 已读 `harness/CURRENT_STATE.md`、`AGENTS.md`、`harness/doc/01-instructions/01-项目执行协议.md`、`harness/doc/01-instructions/02-V1交付合同.md`、`harness/doc/04-state/01-当前项目状态.md`、`harness/doc/04-state/02-业务闭环状态.md`、`harness/doc/04-state/03-P0-P1问题台账.md` |

任务开始时入口文件特别提示:**`DASHBOARD-MONEY-AUDIT-001` (2026-06-04 13:19) 已经做过一次同类审计**,并发现 4 个 P0 + 4 个 P1 + 2 个 P2 问题;**`订单明细表复刻 + 收口 + ORDER-DETAIL-TAB-FIX-001`** 三个任务已经修复了部分字段(媒介改渠道、订单明细 16 列、settle_amount 回退治理)。

---

## 3. 关键事实 — 决定性证据

### 3.1 前后端均**找不到**用户截图的 9 张卡片

通过 `frontend/src/views/` 与 `frontend/src/api/` 全量搜索,**用户截图描述的 9 张卡片在当前代码库中**没有任何对应实现:

| 用户截图卡片 | 当前 main 是否实现 | 证据 |
| --- | --- | --- |
| 总订单数(成交/结算) | ❌ 不存在 | `data/index.vue` 只有 4 张"今日"卡;`dashboard/index.vue` 只展示"累计 GMV" |
| 订单额(成交/结算) | ❌ 不存在 | 同上;`metrics.todayGmv` 唯一存在但展示在"今日 GMV"卡 |
| 服务费收入(预估/结算) | ❌ 不存在(双轨展示) | `MetricsVO.serviceFeeIncome` 存在但**只展示在"收入分拆"标签**,无独立卡片 |
| 技术服务费 | ❌ 不存在(双轨展示) | 同上 |
| 服务费支出 | ❌ 不存在(双轨展示) | 业务实现 = `recruiter + channel` commission,但前端未单独卡片 |
| 服务费收益 | ❌ 不存在(双轨展示) | `MetricsVO.serviceFee` = serviceFeeNet(只展示在标签) |
| 招商提成 | ❌ 不存在(双轨展示) | `metrics.bizCommission` 存在但只在"分拆"标签,无独立卡片 |
| 渠道提成 | ❌ 不存在(双轨展示) | `metrics.channelCommission` 存在但只在"分拆"标签 |
| 毛利 | ❌ 前端已隐藏 | `MetricsVO.grossProfit` 字段仍由后端返回(`amountTrack=estimate` 时返回 25.31),但**前端 UI 已不展示毛利卡片**(test 断言 `not.toContain('毛利')` 三处强制) |

**前端搜索 `毛利`/`grossProfit` 出现位置**:
- `frontend/src/types/index.ts:164` — TS 类型
- `frontend/src/views/data/OrderList.vue:234` — OrderSummaryRowVO 类型
- `frontend/src/views/data/OrderList.test.ts:90/106/215` — 测试数据 + 断言 `not.toContain('毛利')`
- `frontend/src/views/data/index.test.ts:109/125/148-149/164-166` — 测试数据 + 断言 `今日毛利/预估轨毛利/结算轨毛利 都不展示`
- `frontend/src/api/performance.ts:36` — PerformanceSummary 类型
- `frontend/src/api/data.ts:97` — 注释提及"首页展示的全局关键指标(总订单数、总金额、服务费收入、毛利等)" — **这段注释已经过时,与 `data/index.vue` 实际实现不一致**

**`前端搜索 媒介`** 仅在 2 处 `not.toContain('媒介')` 测试断言中出现(防回归保护),**没有任何业务代码仍写"媒介"**。

### 3.2 真实数据库数据(只读 SQL)

**`colonelsettlement_order`**(分区表 RANGE create_time):

| 指标 | 值 |
| --- | --- |
| 总行数(deleted=0) | **1033** |
| 状态 1(待结算/ORDERED) | 957 |
| 状态 4(已失效) | 76 |
| **状态 3(已结算) 等所有结算状态** | **0** |
| `min/max create_time` | 2026-06-03 16:48:29 ~ 2026-06-05 10:06:47 |
| `min/max settle_time` | **全 NULL** |
| `min/max pay_time` | 2026-06-03 16:48:29 ~ 2026-06-05 10:06:47 |
| `SUM(order_amount)` | 2,107,251 分 = **¥21,072.51** |
| `SUM(settle_amount)` | 0 |
| `SUM(estimate_service_fee)` | 35,702 分 = **¥357.02** |
| `SUM(effective_service_fee)` | 0 |
| `SUM(estimate_tech_service_fee)` | 3,617 分 = **¥36.17** |
| `SUM(effective_tech_service_fee)` | 0 |
| `pick_source 非空` | **0** |
| `channel_user_id 非空` | **0** |
| `colonel_user_id 非空` | 15 |

**`performance_records`**:

| 指标 | 值 |
| --- | --- |
| 总行数 | 979 |
| `is_valid=TRUE, is_reversed=FALSE` | 910 |
| `is_valid=FALSE, is_reversed=TRUE` | 69 |
| `SUM(pay_amount)` | 1,849,172 分 = **¥18,491.72** |
| `SUM(settle_amount)` | 0 |
| `SUM(estimate_service_fee)` | 30,576 分 = **¥305.76** |
| `SUM(estimate_tech_service_fee)` | 3,100 分 = **¥31.00** |
| `SUM(estimate_service_profit)` | 27,476 分 = **¥274.76** |
| `SUM(estimate_recruiter_commission)` | 4,141 分 = **¥41.41** |
| `SUM(estimate_channel_commission)` | 4,141 分 = **¥41.41** |
| `SUM(estimate_gross_profit)` | 19,194 分 = **¥191.94** |
| 结算轨所有金额 | **0** |
| 业绩表 settle_time | **全 NULL** |
| `recruiter_commission_rate` | 全部 0.1500(910/910) |
| `channel_commission_rate` | 全部 0.1500(910/910) |
| `calculated_at` 范围 | 2026-06-04 06:10:22 ~ 2026-06-05 02:10:02 |

**`dashboard_performance_daily`**(汇总表):

| 日期 | 订单数 | 订单额(分) | service_fee_net |
| --- | --- | --- | --- |
| 2026-06-03 | 307 | 574,389 | **0** |
| 2026-06-04 | 573 | 1,173,206 | **0** |
| 2026-06-05 | 95 | 230,608 | **0** |
| **总计** | **975** | **1,978,203 (¥19,782.03)** | **0** |

注: 汇总表 975 / 业绩表 910 / 订单表 1033, **三者口径不一致**,**且 `service_fee_net` 字段从未被写入(全 0)** — 这是汇总表本身的一个独立问题。

**`commission_config`**(空表):

```
0 rows
```

**`commissions`**(0 行) / **`commission_settlement`**(0 行) / **`order_performance`**(0 行) / **`order_detail`**(0 行) / **`colonel_order_settlement`**(0 行) / **`order_decrypt_record`**(0 行) — 6 张衍生业绩/结算表**全部为空**。

**`system_config` 提成相关**:
| config_key | config_value |
| --- | --- |
| `talent.exclusive.service_fee_ratio` | 70 |
| `merchant.exclusive.service_fee_ratio` | 70 |
| `commission.business_default_ratio` | 0.15 |
| `commission.channel_default_ratio` | 0.15 |

**`domain_event_outbox`**:
- 共 9 种事件类型,合计 5502 条全部 `PUBLISHED`
- 全部为 ProductDisplay/ProductListed/ProductHidden/ActivitySync/Sample* 事件
- **没有 OrderSynced/OrderSettled/PerformanceCalculated 事件** — 业务事件通道未覆盖订单/业绩

### 3.3 真实 API 响应

**`GET /api/dashboard/metrics`**(admin, scope=ALL):

```json
{
  "code": 200,
  "data": {
    "settle": {
      "todayOrderCount": 0, "todayGmv": 0.0, "pendingShipCount": 0,
      "trend7d": [0, 0, 0, 0, 0, 0, 0],
      "totalOrders": 0, "totalAmount": 0.0,
      "serviceFee": 0.0, "commission": 0.0,
      "serviceFeeIncome": 0.0, "techServiceFee": 0.0,
      "talentCommission": 0.0, "bizCommission": 0.0, "channelCommission": 0.0,
      "grossProfit": 0.0,
      "amountTrack": "effective", "metricsSource": "performance_records"
    },
    "estimate": {
      "todayOrderCount": 95, "todayGmv": 2306.08, "pendingShipCount": 957,
      "trend7d": [0, 0, 0, 0, 286, 573, 95],
      "totalOrders": 95, "totalAmount": 2306.08,
      "serviceFee": 36.25, "commission": 10.94,
      "serviceFeeIncome": 40.31, "techServiceFee": 4.06,
      "talentCommission": 0.0, "bizCommission": 5.47, "channelCommission": 5.47,
      "grossProfit": 25.31,
      "amountTrack": "estimate", "metricsSource": "performance_records"
    }
  }
}
```

**`GET /api/data/orders/summary`**(近 30 天,createTime,admin):

```json
{
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
    { "date": "2026-06-05", "orderCount": 100, "orderAmount": 2416.67, "serviceFeeIncome": 42.73, "techServiceFee": 4.30, "serviceFeeExpense": 11.52, "serviceFeeProfit": 38.43, "grossProfit": 26.91 },
    { "date": "2026-06-04", "orderCount": 608, "orderAmount": 12448.72, "serviceFeeIncome": 207.44, "techServiceFee": 20.94, "serviceFeeExpense": 55.84, "serviceFeeProfit": 186.50, "grossProfit": 130.66 },
    { "date": "2026-06-03", "orderCount": 325, "orderAmount": 6207.12, "serviceFeeIncome": 106.85, "techServiceFee": 10.93, "serviceFeeExpense": 28.76, "serviceFeeProfit": 95.92, "grossProfit": 67.16 }
  ]
}
```

**`GET /api/data/orders?page=1&size=5`** → `total=1033`

---

## 4. 全量指标对账矩阵

> **"用户截图"** 列来自用户任务描述中的截图数字(无截图证据,本报告以任务文本中的 9 个数字为准);**"前端展示"** 列在**当前 main** 实际不展示该卡片;**"API 原始"** 列取自 `/api/dashboard/metrics` estimate 轨(单日 0:00-24:00)和 `/api/data/orders/summary` 默认近 30 天。

| 指标 | 用户截图 | 前端展示(当前) | API 原始 | 订单表 SQL(分→元) | 业绩表 SQL(分→元) | 汇总表 SQL | 差异 | 结论 | 等级 |
| --- | ---: | --- | ---: | ---: | ---: | ---: | --- | --- | --- |
| 总订单数-成交 | 7114 | **前端不展示** | summary.total.orderCount=1033 | 1033 | 910(valid) | 975 | **用户截图 7114 与 DB/API 任何口径都对不上** | FAIL | **P0** |
| 总订单数-结算 | 0 | **前端不展示** | summary.settle.orderCount=0 | 0 | 0 | 0 | 与 DB/API 一致(真实未结算) | PASS | — |
| 订单额-成交 | 146103.82 | **前端不展示** | summary.total.orderAmount=21072.51 | 21072.51 | 18491.72 | 19782.03 | **用户截图 146103.82 与 DB/API 对不上** | FAIL | **P0** |
| 订单额-结算 | 0 | **前端不展示** | summary.settle.totalAmount=0 | 0 | 0 | 0 | 一致(真实未结算) | PASS | — |
| 服务费收入-预估 | 2945.9 | 收入分拆标签 est.serviceFeeIncome=40.31(2026-06-05 当天) | 357.02(近 30 天) | 357.02 | 305.76 | 0(汇总表字段未写) | 用户截图 2945.9 与 DB/API 对不上 | FAIL | **P0** |
| 服务费收入-结算 | 0 | 0 | 0 | 0 | 0 | 0 | 一致 | PASS | — |
| 技术服务费-预估 | 236.25 | 收入分拆标签 est.techServiceFee=4.06(当天) | 36.17(近 30 天) | 36.17 | 31.00 | 0 | 用户截图 236.25 与 DB/API 对不上 | FAIL | **P0** |
| 技术服务费-结算 | 0 | 0 | 0 | 0 | 0 | 0 | 一致 | PASS | — |
| 服务费支出-预估 | 6.65 | **前端不展示** | summary.total.serviceFeeExpense=96.22(订单事实重算) | N/A | 82.82(业绩表 recruiter+channel) | N/A | **API 用了订单事实重算口径,与业绩表 82.82 不一致;且用户截图 6.65 与两端都不符** | FAIL | **P0** |
| 服务费支出-结算 | 0 | **前端不展示** | 0 | 0 | 0 | 0 | 一致 | PASS | — |
| 服务费收益-预估 | 2703 | 收入分拆标签 est.serviceFee=36.25(当天) | 320.85(近 30 天) | 320.85(357.02-36.17) | 274.76 | 0 | 用户截图 2703 与 DB/API 对不上 | FAIL | **P0** |
| 服务费收益-结算 | 0 | 0 | 0 | 0 | 0 | 0 | 一致 | PASS | — |
| 招商提成-预估 | 270.3 | 收入分拆标签 est.bizCommission=5.47(当天) | 96.22(总计) / 业绩表 41.41(单 recruiter_rate 0.15) | N/A | 41.41 | N/A | 用户截图 270.3 与 DB/API 对不上 | FAIL | **P0** |
| 招商提成-结算 | 0 | 0 | 0 | 0 | 0 | 0 | 一致 | PASS | — |
| 渠道提成-预估 | 341.87 | 收入分拆标签 est.channelCommission=5.47(当天) | 业绩表 41.41 | N/A | 41.41 | N/A | 用户截图 341.87 与 DB/API 对不上 | FAIL | **P0** |
| 渠道提成-结算 | 0 | 0 | 0 | 0 | 0 | 0 | 一致 | PASS | — |
| 毛利-预估 | 2090.82 | **前端 UI 不展示毛利**(test 强制 assert) | summary.total.grossProfit=224.63(近 30 天) / metrics.estimate.grossProfit=25.31(当天) | 224.63(API 用订单事实重算) | 191.94 | 0 | 用户截图 2090.82 与 DB/API 对不上 | FAIL | **P0** |
| 毛利-结算 | 0 | 0 | 0 | 0 | 0 | 0 | 一致 | PASS | — |

**对账矩阵全部 18 项**:
- **0 项**与用户截图的 9 个非零数字完全匹配。
- **9 项"结算"指标**全部 = 0,与 DB/API/汇总表一致(PASS)。
- **9 项"成交/预估"指标**全部与用户截图不符(用户截图 5 位数 ~ 6 位数,DB/API 实际为 2 位数 ~ 3 位数)。

---

## 5. 前端展示来源

### 5.1 当前路由并存两个看板入口

| 路由 | 组件 | 卡片数 | 数据源 | 当前状态 |
| --- | --- | --- | --- | --- |
| `/data` | `frontend/src/views/data/index.vue` | 4 张("今日"双轨) | `/api/dashboard/metrics` + `/api/performance/summary` | 现行主入口,`/` 默认重定向到 `/data` |
| `/dashboard` | `frontend/src/views/dashboard/index.vue` | 4 张"累计" | `/api/dashboard/summary`(旧接口) | 旧版遗留,菜单中以"归因概览"暴露 |

**关键发现**:`/dashboard` 旧版**仍**在 router 中保留(`router/index.ts:175-179`),但其组件只展示 4 张 stat-card(累计 GMV / 未归因订单 / 归因成功率 / 服务费收入),**不展示用户截图的 9 张卡片**。

### 5.2 前端字段映射表(用户截图卡片 → 当前代码)

| 用户截图卡片 | `data/index.vue` 字段 | `dashboard/index.vue` 字段 | 真实 API 路径 |
| --- | --- | --- | --- |
| 总订单数(成交) | N/A | N/A | `/api/data/orders/summary` total.orderCount |
| 总订单数(结算) | N/A | N/A | `/api/data/orders/summary` settle.orderCount(无 settle 行) |
| 订单额(成交) | N/A | N/A | `/api/data/orders/summary` total.orderAmount |
| 订单额(结算) | N/A | N/A | `/api/data/orders/summary` settle.orderAmount(无 settle 行) |
| 服务费收入(预估) | 收入分拆标签 `metrics.serviceFeeIncome` | 服务费收入 stat | `/api/dashboard/metrics` estimate.serviceFeeIncome |
| 服务费收入(结算) | 收入分拆标签 | stat | `/api/dashboard/metrics` settle.serviceFeeIncome |
| 技术服务费 | 收入分拆标签 `metrics.techServiceFee` | N/A | `/api/dashboard/metrics` estimate.techServiceFee |
| 服务费支出 | **不存在前端字段** | N/A | `/api/data/orders/summary` total.serviceFeeExpense(由 DataApplicationService 订单事实重算) |
| 服务费收益 | 收入分拆标签 `metrics.serviceFee` | N/A | `/api/dashboard/metrics` estimate.serviceFee |
| 招商提成 | 收入分拆标签 `metrics.bizCommission` | N/A | `/api/dashboard/metrics` estimate.bizCommission |
| 渠道提成 | 收入分拆标签 `metrics.channelCommission` | N/A | `/api/dashboard/metrics` estimate.channelCommission |
| 毛利 | **UI 不展示**(强制 assert) | N/A | `/api/dashboard/metrics` estimate.grossProfit |

### 5.3 文案 / 字面值问题

- "**媒介**" 在前端**所有业务源码**已清除(仅 2 处 `not.toContain('媒介')` 测试断言保留,作为防回归保护)。
- "**毛利**" 字段虽仍由后端返回(`MetricsVO.grossProfit` 仍计算并下传),但前端**强制不展示**(`index.test.ts:164-166` 三处 assert)。
- `frontend/src/api/data.ts:97` 注释中仍出现"**首页展示的全局关键指标(总订单数、总金额、服务费收入、毛利等)**" — 该注释**已过时**,与实际 `data/index.vue` 实现不一致(实际只展示 4 张"今日"卡 + 收入分拆标签)。

---

## 6. 后端 API 实现链路

### 6.1 `/api/dashboard/metrics`(现行核心)

```
DataController.getMetrics()                                    [DataController.java:174-181]
└─ super.getMetrics(userId, deptId, dataScope)                 [DataApplicationService.java:787-805]
   ├─ buildMetrics("settleTime", ...)                          [DataApplicationService.java:807-931]
   │  └─ performanceMetricsQueryService.aggregateRange(...)   [PerformanceMetricsQueryService.java:78-139]
   │     └─ SQL: SELECT COUNT, SUM(pr.pay_amount|pr.settle_amount|pr.estimate_service_fee|pr.effective_service_fee|
   │                          pr.estimate_tech_service_fee|pr.effective_tech_service_fee|
   │                          pr.estimate_service_profit|pr.effective_service_profit|
   │                          pr.estimate_recruiter_commission|pr.effective_recruiter_commission|
   │                          pr.estimate_channel_commission|pr.effective_channel_commission|
   │                          pr.estimate_gross_profit|pr.effective_gross_profit|pr.settle_second_colonel_commission)
   │        FROM performance_records pr JOIN colonelsettlement_order co ON ...
   │        WHERE pr.is_valid = TRUE AND co.<create_time|settle_time> BETWEEN todayStart AND tomorrowStart
   └─ buildMetrics("createTime", ...)                          (同上,estimate 轨)
```

- **关键点**:`aggregateRange` 严格用 `todayStart, tomorrowStart` 限当天单日窗口。
- 缓存:`dashboard:metrics:{track}:{scope}:{id}` 30s TTL。

### 6.2 `/api/data/orders/summary`(汇总,与卡片不同)

```
DataController.getOrderSummary()                               [DataController.java:150-172]
└─ super.getOrderSummary(...)                                  [DataApplicationService.java:656-785]
   ├─ queryOrderSummaryAggregates(false, ...)                 [DataApplicationService.java:1498-1559]
   │  └─ SQL: SELECT COUNT, SUM(colonel_activity_id) per activity_id 桶 + per 日桶
   ├─ queryOrderSummaryCommission(false, ...)                 [DataApplicationService.java:1561-1609]
   │  └─ commissionService.calculateByActivityBuckets(...)    [CommissionService.java:243-283]
   │     └─ 按活动分桶 × (recruiter_rate, channel_rate),默认 0.15
   └─ toOrderSummaryRow(...) → OrderSummaryVO                [DataApplicationService.java:1899-1924]
```

- **关键点**:`serviceFeeExpense = bizCommission + channelCommission`(口径与 `OrderDetailVO` 一致),`grossProfit` 来自 `CommissionService` 重新计算(订单事实侧),与 `performance_records.gross_profit` 不一定一致。

### 6.3 `/api/dashboard/summary`(旧版 Dashboard)

```
DashboardController.getSummary(startTime, endTime, userId, ...) [DashboardController.java:84-99]
└─ dashboardService.getSummary(...)                            [DashboardService.java]
   └─ 旧 SQL 单轨聚合: orderAmount, serviceFee, unattributedOrderCount, attributionRate
```

- 缓存 30s TTL。
- 字段口径:`orderAmount`(分)、`serviceFee`(分)、`unattributedOrderCount`、`attributionRate`(0-1 比例)。
- **该接口仅 4 个 stat 字段,不包含用户截图的 9 个指标**。

### 6.4 DTO / VO 字段映射

| 字段 | VO | 来源 |
| --- | --- | --- |
| `todayOrderCount, totalOrders` | `MetricsVO` | 业绩表聚合 |
| `todayGmv, totalAmount` | `MetricsVO` | 业绩表 `pay_amount`/`settle_amount` |
| `serviceFeeIncome` | `MetricsVO` | 业绩表 `estimate_service_fee`/`effective_service_fee` |
| `techServiceFee` | `MetricsVO` | 业绩表 `estimate_tech_service_fee`/`effective_tech_service_fee` |
| `serviceFee` (= serviceFeeNet) | `MetricsVO` | 业绩表 `estimate_service_profit`/`effective_service_profit` |
| `talentCommission` | `MetricsVO` | 业绩表 `co.settle_second_colonel_commission`(从订单表 join 聚合) |
| `bizCommission` | `MetricsVO` | 业绩表 `estimate_recruiter_commission`/`effective_recruiter_commission` |
| `channelCommission` | `MetricsVO` | 业绩表 `estimate_channel_commission`/`effective_channel_commission` |
| `grossProfit` | `MetricsVO` | 业绩表 `estimate_gross_profit`/`effective_gross_profit` |
| `serviceFeeExpense` | `OrderSummaryRowVO` | 由 `DataApplicationService` 调 `CommissionService` 用订单事实重算 `bizCommission + channelCommission`(不是从业绩表取) |

### 6.5 风险点

| 风险 | 等级 | 描述 |
| --- | --- | --- |
| `MetricsVO.grossProfit` 仍下传 | P1 | 后端未停止计算/下发 grossProfit;前端用 assert 防回归,但 V1 合同已禁毛利展示 |
| `serviceFeeExpense` 双口径 | P0 | 业绩表 82.82 vs API summary 96.22 数字打架(订单事实重算 ≠ 业绩表聚合) |
| `talentCommission` 来源跨表 join | P2 | `MetricsVO.talentCommission` 从 `co.settle_second_colonel_commission` 聚合,而非从业绩表的 `recruiter_commission_rate × serviceFeeNet` 推导 — 容易口径漂移 |
| `aggregateRange` 仅当天窗口 | P2 | 看板"今日 X"是当天 0:00 起 24h,与"近 30 天 / 全部订单"汇总不可对比 |

---

## 7. 数据库表结构确认

已确认的 7 张核心表:

| 表 | 类型 | 行数 | 关键字段 |
| --- | --- | --- | --- |
| `colonelsettlement_order` | **分区表**(RANGE create_time) | 1033 (active) | order_id, order_amount, settle_amount, estimate_service_fee, effective_service_fee, estimate_tech_service_fee, effective_tech_service_fee, settle_time, pay_time, pick_source, channel_user_id, colonel_user_id, default_channel_id(**不存在**), default_recruiter_id(**不存在**) |
| `performance_records` | 普通表 | 979 (910 valid) | order_id, pay_amount, settle_amount, estimate_*/effective_*(双轨全套), recruiter_commission_rate, channel_commission_rate, is_valid, is_reversed, settle_time, calculated_at |
| `dashboard_performance_daily` | 普通表 | 3 (2026-06-03/04/05) | stat_date, order_count, order_amount, service_fee_net(**全部 0**), updated_at |
| `commissions` | 普通表 | **0** | 空表 |
| `commission_settlement` | 普通表 | **0** | 空表 |
| `commission_config` | 普通表 | **0** | 空表(但 system_config 里有 0.15 默认值) |
| `system_config` | 普通表 | 4 条 | `commission.business_default_ratio=0.15` / `commission.channel_default_ratio=0.15` / `talent.exclusive.service_fee_ratio=70` / `merchant.exclusive.service_fee_ratio=70` |

注: 任务提示中提到的 `colonel_multi_settlement_orders` 等 2704 单,以及 `default_channel_id` / `default_recruiter_id` 字段,在当前 schema **不存在**(订单表只有 `channel_user_id` / `colonel_user_id`)。

---

## 8. 服务费支出口径专项结论

### 8.1 当前实现(代码)

**两处定义**:
1. `DataApplicationService.toOrderDetailVO.estimateServiceFeeExpense`:
   `safeAdd(estimateRecruiterCommission, estimateChannelCommission)` — 用业绩表数字。
2. `DataApplicationService.toOrderSummaryRow.serviceFeeExpense`:
   `centToYuan(summary.bizCommission() + summary.channelCommission())` — 用 `CommissionService` 重新计算(订单事实侧)。

**两处对同一订单**:
- 业绩表 `recruiter+channel = 4141+4141 = 8282 分 = ¥82.82`
- API summary 重算 `bizCommission + channelCommission = 96.22`

**口径差异**:
- 业绩表口径:`recruiter_commission` = `recruiter_rate × serviceFeeNet`,其中 `serviceFeeNet = estimate_service_fee - estimate_tech_service_fee`
- API 重算口径:按**订单表 `colonel_activity_id` 维度分桶**,`CommissionService` 用 `SettleColonelCommission` + `SettleColonelTechServiceFee`(注意是 `settle_colonel_commission`,不是 `estimate_service_fee`)。

→ 两口径**公式不同** + **基数不同** = 必然不一致。**这是真实存在的口径漂移**。

### 8.2 与用户截图"¥6.65"的关系

用户截图 `服务费支出 6.65` 在**当前任何口径下都不可能产生**:
- 业绩表汇总 = 82.82
- API 重算 = 96.22
- 0.15 × 任何 1 位数都不可能 = 6.65
- 6.65 = 招商 6.65 + 渠道 0 ? → 0.15 × 44.33 = 6.65 → 业绩数据子集
- 6.65 / 业绩表 ¥41.41(招商) ≈ 16%,不是标准比例

**结论:用户截图的服务费支出 ¥6.65 与当前系统任何可重现路径都不匹配**。这与"用户截图整体不是当前代码产生"的判断一致。

### 8.3 P0 风险:服务费支出双口径不一致

`/api/data/orders/summary` 的 `serviceFeeExpense` 与 `performance_records` 的 `recruiter_commission + channel_commission` 必须合并为同一口径(V1 合同 = 业绩域统一来源)。

---

## 9. 毛利专项结论

### 9.1 V1 合同

`harness/doc/01-instructions/02-V1交付合同.md` 明确:
- "**不做毛利口径扩展或新增财务结算口径**"。

### 9.2 当前状态

- **前端 UI 不展示毛利**(`index.test.ts:164-166` assert;`OrderList.test.ts:215` assert)。
- **后端仍计算毛利**:`MetricsVO.grossProfit` 在 `/api/dashboard/metrics` 仍返回(`amountTrack=estimate` 时 = 25.31);`OrderSummaryVO.grossProfit` 仍由 `CommissionService` 重算(订单事实侧 = 224.63)。
- **业绩表 `estimate_gross_profit` 字段已用**,但 `commission_settlement` 表是空的。

### 9.3 与用户截图"¥2090.82"的关系

- 业绩表 191.94 / API summary 224.63 / 用户截图 2090.82 — 三者无任何匹配关系。

### 9.4 P0 风险:毛利仍是 V1 合同红线

虽然前端不展示,但后端**仍计算并下发**毛利字段,任何接口消费方(BI、自动化、归档)都可能误用。建议彻底下线 `grossProfit` 字段(从 VO 移除 + 不再聚合 + 删除 SQL 列引用)。

---

## 10. 结算全 0 专项结论

### 10.1 业务事实 vs 代码 bug 判定

| 证据 | 结论 |
| --- | --- |
| `colonelsettlement_order.settle_time` 全部 NULL | ✅ 真实未结算 |
| `colonelsettlement_order.settle_amount` 全部 0 | ✅ 与 settle_time 同步 |
| `colonelsettlement_order.effective_service_fee` 全部 0 | ✅ 与 settle_time 同步 |
| `performance_records.settle_amount` / `effective_*` 全部 0 | ✅ 业绩表也没有结算数据 |
| `dashboard_performance_daily` 3 天都 `service_fee_net=0` | ✅ 汇总表与 DB 一致 |
| `colonel_order_settlement` 0 行 | ⚠️ **未确认**该表实际用途 |
| `buyin.colonelMultiSettlementOrders` | 需通过 buyin SDK 调用日志确认 |
| `OrderSyncJob.syncPayRecent` | 已知每 30 分钟 6h 大窗口低频回扫;`ORDER_SYNC_SETTLEMENT` 增量是 10 分钟窗口 |

### 10.2 结论

**结算全 0 = 真实业务事实(暂无结算订单)**,不是代码 bug:
- 所有订单 `order_status ∈ {1(ORDERED), 4(CANCELLED)}`,没有任何 `FINISHED` 状态订单。
- `colonel_order_settlement` 表 0 行 + `commission_settlement` 表 0 行 + 业绩表 `effective_*` 全 0 三方一致。
- 抖音 `colonelMultiSettlementOrders` 增量同步窗口 10 分钟(由 `OrderSyncJob` 维护),目前 0 增量是正常(暂无已结算订单)。

**但需要在用户界面中明确说明**:"结算轨全 0 = 暂无已结算订单,需等待结算窗口(用户协议通常 7-15 天)",避免误读。

### 10.3 P1 风险:结算轨 UI 提示不充分

`/api/dashboard/metrics` settle 轨返回全 0,前端 `dualTrackGapHint` 有兜底文案,但 `/api/data/orders/summary` 默认不返回 settle 行,无法触发提示。

---

## 11. 总订单数 7114 专项结论

### 11.1 真实数据

| 口径 | 总订单数 |
| --- | ---: |
| `colonelsettlement_order`(active) | **1033** |
| `performance_records` valid | 910 |
| `dashboard_performance_daily` 3 天 | 975 |
| `/api/data/orders?page=1&size=5` total | 1033 |
| `/api/data/orders/summary` total.orderCount | 1033 |

**任何数据库表或 API 响应都不可能产生 7114**。所有真实数字都集中在 2 位数 ~ 3 位数,差距 ~7 倍。

### 11.2 唯一反推可能

`7114` 的"7114 / 1033 ≈ 6.89" 这个倍数不会自然出现在任何已知 SQL 聚合中。
- 不可能是 `count(distinct order_id)`(去重不会增加)
- 不可能是按 merchant × product 矩阵展开(单产品多 SKU 也不可能 6.89 倍)
- 可能是**商品行 / SKU 行**误算(每单平均 6.89 个商品?) — 但目前 `product_operation_state` 没有按订单 × 商品 展开
- 可能是**测试环境 / mock 数据**(test 环境 5 张表的 mock 累计数)— 任务禁止把 test 当 real-pre 验证

### 11.3 结论

**总订单数 7114 在当前 real-pre 数据库与 API 中无法复现**。最高可能:
- 用户截图来自**与 feature/auth-system 当前 main 不一致**的版本(可能为 DASH-MONEY-AUDIT-001 报告前某版本 / 旧 bundle / 远端未同步部署)。
- 或者截图来自 test mock 累计数据。

---

## 12. 金额单位与精度专项结论

### 12.1 数据库存储单位

- `colonelsettlement_order`: **分**(bigint),例如 `order_amount=2107251` = ¥21,072.51
- `performance_records`: **分**(bigint)
- `dashboard_performance_daily.order_amount`: **分**(bigint)

### 12.2 转换路径

| 层 | 转换 | 代码 |
| --- | --- | --- |
| DB → Service | `centToYuan(cent) = BigDecimal.valueOf(cent).divide(100, 2, HALF_UP)` | `DataApplicationService.java:1475-1478` |
| Service → DTO | 已为 `BigDecimal` 元 | `MetricsVO` / `OrderSummaryRowVO` |
| DTO → Frontend | JSON 数字(浮点) | — |
| Frontend → UI | `Number(value).toFixed(2)` | `dashboard-metrics.ts` / `index.vue` |

**未发现分转元重复**。
**未发现 BigDecimal scale 配置错误**(都统一 HALF_UP 2 位)。

### 12.3 精度风险点

| 风险 | 等级 | 描述 |
| --- | --- | --- |
| `centToYuan` 对 null 输入返回 0 | P2 | `if (cent == null) { long value = 0L; }`,前端展示 0 vs 真实"无数据" 难以区分 |
| `safeCentToYuan` 对 null/0 全部返回 null | — | `OrderDetailVO` 用此方法,展示为 `-` |
| `BigDecimal.valueOf(cent)` 接受 long 上限 | OK | Java 17 long 足够(分单位到 9.22e16 元) |
| `multiplyCent(amount, ratio)` 比例乘分后 round HALF_UP | OK | 但 `0.15 × 44.33 = 6.65` 不会自然产生(必须基数是 44.33) |

---

## 13. 权限 scope 影响分析

### 13.1 当前实现

- 登录 admin / dataScope=3(ALL)
- `/api/dashboard/metrics` 未消费 userId/deptId/scope 之外的过滤(由 `PerformanceMetricsQueryService.appendScope` 拼接 `co.user_id = ?` 或 `co.dept_id = ?` 或无)
- `/api/data/orders/summary` 同样支持 PERSONAL/DEPT/ALL

### 13.2 与 SQL 对账一致性

- admin → ALL → 不附加 user_id/dept_id 过滤 → 与 DB 全表(1033 单)一致
- 业绩表 910 < 订单表 1033(差 123 = 54 无业绩 + 69 冲正)
- 汇总表 975 < 订单表 1033(差 58)

**这些差异来自业绩计算未覆盖 / 冲正 / 汇总表更新时序问题,不是 scope 错配**。

### 13.3 业务负责人 / 渠道员账号校验

- 当前只验证了 admin 角色,未登录 biz_staff / channel_staff / channel_leader
- 但前端 `data/index.vue` 已按角色切换"今日订单数 / 我的订单数 / 我的推广订单数"标签,说明前端 role-aware
- 后端 PerformanceMetricsQueryService.appendScope 也会按 scope 缩范围
- 本审查**未做多角色端到端验证**(任务时间约束,只对账 admin 角色)

### 13.4 P1 风险:scope 与 SQL 一致性未做多角色交叉验证

建议作为后续 RBAC 专项的子任务补齐。

---

## 14. 汇总表 / 分析模块检查

### 14.1 存在的汇总表

- `dashboard_performance_daily`:3 天(2026-06-03/04/05) × 3 字段(stat_date, order_count, order_amount, service_fee_net)
- `service_fee_net` 字段**3 天全为 0**,从未被写入

### 14.2 写入路径

- `aggregateDashboardSummary()` 在 `PerformanceMetricsQueryService.java:206-248` 仍是基于业绩表 JOIN 订单表的实时聚合,**没有读 `dashboard_performance_daily` 汇总表**。
- 后端 `/dashboard/metrics` / `/data/orders/summary` / `/dashboard/summary` **均不走汇总表**,全部实时聚合。

### 14.3 汇总表被遗忘

- `dashboard_performance_daily` 当前是**孤儿表**:有写入(谁在写?)、但无读出
- service_fee_net 全 0 表明:写入路径只更新了 order_count / order_amount,**未计算 service_fee_net**(可能因为 `effective_service_fee` 在订单表 / 业绩表都全 0)
- 旧版 `aggregateDashboardSummary()` 实现可能有"先写汇总表"的设计,后来被替换为实时聚合,**但表没删除**

### 14.4 事件通道

- `domain_event_outbox` 5502 条事件,**全是商品/寄样类**,**没有 OrderSynced/OrderSettled/PerformanceCalculated**。
- 当前业务事件通道未覆盖订单/业绩。

### 14.5 P2 风险

- `dashboard_performance_daily` 孤儿表占用存储且 service_fee_net 字段失效。
- 事件通道缺口:订单同步 / 业绩计算未发业务事件,导致下游消费者(包括汇总表)无法基于事件驱动更新。

---

## 15. 服务费支出口径专项结论(整合 7-8 节)

### 15.1 核心发现

1. `serviceFeeExpense` 在代码中存在**两套计算逻辑**:
   - `OrderDetailVO`:从业绩表 `recruiter + channel` 取(直接聚合)
   - `OrderSummaryRowVO`:从 `CommissionService` 按活动分桶重算(订单事实侧)
2. **两套结果不一致**:
   - 业绩表汇总(全 910 条):recruiter + channel = 41.41 + 41.41 = **¥82.82**
   - API `/data/orders/summary` total:serviceFeeExpense = **¥96.22**
3. 与用户截图 ¥6.65 **无任何对应口径**。

### 15.2 修复建议(纳入 DASHBOARD-MONEY-FIX 后续)

- **统一口径**:V1 合同 = 业绩域统一下游消费,`serviceFeeExpense` 必须从 `performance_records` 取,不再用 `CommissionService` 重算。
- **修改位置**:`DataApplicationService.toOrderSummaryRow` 第 1920 行(`vo.setServiceFeeExpense(centToYuan(summary.bizCommission() + summary.channelCommission()))`)改为按日桶从业绩表聚合(类似 `aggregateRange` 但按日)。
- **测试**:必须新增双轨对账测试,断言"业绩表聚合 == 汇总表 serviceFeeExpense"。

---

## 16. 问题清单(按 P0/P1/P2 分级)

### P0(必须先于业务验证修复)

| 编号 | 现象 | 根因 | 证据 |
| --- | --- | --- | --- |
| **DASH-RECON-P0-001** | 用户截图 9 个非零指标全部与 DB/API 口径不符(差距 5-100 倍) | 用户截图与 `feature/auth-system` 当前 main 完全不一致(可能为旧版看板 / 旧 bundle / 远端未同步) | §3.1 / §4 / §11 |
| **DASH-RECON-P0-002** | `serviceFeeExpense` 双口径不一致:业绩表 82.82 vs API 96.22 | `DataApplicationService` 用 `CommissionService` 重算,与 `performance_records` 业绩域口径分离 | §8 / §15 |
| **DASH-RECON-P0-003** | `MetricsVO.grossProfit` 仍下传,违反 V1 合同"不做毛利"红线 | 后端未下线毛利计算,前端用 assert 防回归;后端是合同守门人 | §6.5 / §9.4 |
| **DASH-RECON-P0-004** | `dashboard_performance_daily` 孤儿表 + `service_fee_net` 字段 3 天全 0 | 写入路径只更新 order_count / order_amount,service_fee_net 字段从未被计算 | §14.3 |
| **DASH-RECON-P0-005** | `dashboard/index.vue`(旧版)/`/dashboard/summary` 旧版接口仍路由,菜单中暴露"归因概览" | `feature/auth-system` 当前 main 仍保留旧路由 + 旧组件 + 旧接口 | §3.1 / §5.1 |
| **DASH-RECON-P0-006** | 订单 1033 / 业绩 valid 910 / 汇总 975 三方口径不齐(差 54~123) | 业绩计算可能未覆盖全部订单 + 汇总表更新时序 | §3.2 / §14 |

### P1(必须修复,可在 P0 之后)

| 编号 | 现象 | 根因 | 证据 |
| --- | --- | --- | --- |
| **DASH-RECON-P1-001** | `frontend/src/api/data.ts:97` 注释已过时("首页展示的全局关键指标(总订单数、总金额、服务费收入、毛利等)") | 注释与 `data/index.vue` 实际实现不一致 | §5.3 |
| **DASH-RECON-P1-002** | 业绩表与订单表对账差 54 单没有业绩记录(1033-910-69=54) | 这些订单可能是 CANCELLED(order_status=4)/ 业绩计算失败 / 屏蔽规则 | §3.2 |
| **DASH-RECON-P1-003** | 结算轨全 0 UI 提示不充分 | `dualTrackGapHint` 兜底文案存在但 `/data/orders/summary` 不返回 settle 行 | §10.3 |
| **DASH-RECON-P1-004** | scope 与 SQL 一致性未做多角色端到端交叉验证(只验证 admin) | 当前只验证 admin 角色,RBAC 专项未覆盖本审查范围 | §13.4 |
| **DASH-RECON-P1-005** | `commissions` / `commission_settlement` / `commission_config` 3 张表 0 行,无消费者 | 表设计存在但无业务写入,可能为未来功能预留 | §3.2 / §7 |

### P2(可后续清理)

| 编号 | 现象 |
| --- | --- |
| **DASH-RECON-P2-001** | `centToYuan(null)` 返回 0(不是 null),导致"无数据"与"0 元"难以区分 |
| **DASH-RECON-P2-002** | `domain_event_outbox` 没有 OrderSynced/OrderSettled/PerformanceCalculated 事件,下游消费者无法事件驱动 |
| **DASH-RECON-P2-003** | `talentCommission` 跨表 join(`co.settle_second_colonel_commission` 聚合)与业绩域口径分离 |

---

## 17. 根因假设与证据强度

### 假设 A:用户截图来自旧版看板 bundle / 远端未同步

| 证据 | 强度 |
| --- | --- |
| 当前 main `data/index.vue` 完全没有 9 张卡片 | **强** |
| 当前 main `dashboard/index.vue` 旧版也只有 4 张卡(累计 GMV/未归因/归因率/服务费收入) | **强** |
| 当前 main API `/dashboard/metrics` / `/data/orders/summary` 都不返回 9 个独立卡片字段 | **强** |
| `git log` 显示 `fddd7c73 refactor: 将数据看板移至合作管理与系统管理之间` + `9de043a7 feat: auth system + test coverage boost`,看板在 V1 收口期被重写 | **中**(可能旧 bundle 仍在前端容器中) |
| 前端容器 `saas-active-frontend-real-pre-1` Up 12h,可能加载的 bundle 不是 HEAD commit 编译 | **中** |
| 数据库事实(1033 单 / 357.02 服务费 / 41.41 招商提成)与用户截图(7114 / 2945.9 / 270.3)差距 5-100 倍,不是 SQL 偏差 | **强** |

→ **假设 A 强度 = 强**。

### 假设 B:用户截图是 test 环境 mock 数据

| 证据 | 强度 |
| --- | --- |
| test 环境有 5 张 mock 种子表 | **中** |
| `saas-test-postgres-1` Up 2d 仍运行 | **中** |
| 截图的 5 位数金额 + 7114 单 与 test mock 累计量可能匹配 | **未直接验证**(任务禁止用 test 证明 real-pre 闭环) |
| 用户描述"前端"未指定环境(只说"数据平台前端") | **弱** |

→ **假设 B 强度 = 中**(未排除)。

### 假设 C:用户截图是手工录入 / 错误记数

| 证据 | 强度 |
| --- | --- |
| 无客观证据,纯反推 | **弱** |

→ **假设 C 强度 = 弱**。

### 综合结论

**主要根因(强证据)**:用户截图 9 张卡片(总订单数/订单额/服务费收入/技术服务费/服务费支出/服务费收益/招商提成/渠道提成/毛利)与 `feature/auth-system` 当前 main commit `15427ddc` 的代码不一致。前端、后端 API、数据库三方均无法复现截图数字。

**可能的子原因**:
1. 前端容器 bundle 不是 HEAD commit 编译(前端 rebuild/recreate 缓存可能)。
2. 远端部署 bundle 与本地 main 不同步(本任务无远端部署要求,未做远端验证)。
3. 用户截图来自 test 环境 mock 数据(test 容器仍在运行,本任务不验证 test)。
4. 用户截图来自 DASH-MONEY-AUDIT-001 报告前后某历史版本(那时看板可能是单轨旧版)。

---

## 18. 推荐修复任务拆分

> **重要**:本审查报告本身**不改任何业务代码**。下面所有任务**只列方向和验收口径**,不直接执行。

### 18.1 DASHBOARD-MONEY-FIX-002 修复 Dashboard API 指标口径与 SQL 对齐

| 项 | 描述 |
| --- | --- |
| 目标 | `/api/dashboard/metrics` 与 `/api/data/orders/summary` 全部从 `performance_records` 业绩域单源获取,删除 `CommissionService` 订单事实重算路径 |
| 涉及文件 | `DataApplicationService.java` `PerformanceMetricsQueryService.java` `OrderSummaryRowVO.java` |
| 风险 | 涉及多接口对账;必须新增双轨对账测试,断言"业绩表聚合 == API 响应" |
| 验证命令 | `mvn -f backend/pom.xml "-Dtest=DataControllerTest,OrderControllerTest,PerformanceRecordMapperTest,DataApplicationServiceTest" test` + `agent-do.ps1 -Env real-pre -Scope backend` |
| 是否需要部署 | 需要,real-pre 容器 rebuild + recreate |
| 是否需要业务确认 | **是** — 删除 `CommissionService` 订单事实重算路径涉及"哪个口径是 V1 合同" |

### 18.2 DASHBOARD-MONEY-FIX-003 服务费支出口径修复或重命名

| 项 | 描述 |
| --- | --- |
| 目标 | `serviceFeeExpense` 统一为"招商提成 + 渠道提成",从业绩表聚合,不再重算 |
| 涉及文件 | 同上 |
| 风险 | 与 DASHBOARD-MONEY-FIX-002 合并;前端展示字段名可能调整 |
| 验证命令 | 同 18.1 |
| 是否需要部署 | 同 18.1 |
| 是否需要业务确认 | 否(逻辑上是修复,符合 V1) |

### 18.3 DASHBOARD-MONEY-FIX-004 毛利 V1 合同处理:彻底下线

| 项 | 描述 |
| --- | --- |
| 目标 | 删除 `MetricsVO.grossProfit` / `OrderSummaryRowVO.grossProfit` / `PerformanceAggregate.grossProfitCent` / `performance_records.estimate_gross_profit` / `effective_gross_profit` 计算逻辑,后端不再下传,前端 assert 改为删除 |
| 涉及文件 | `MetricsVO.java` `OrderSummaryRowVO.java` `PerformanceMetricsQueryService.java` `PerformanceCalculationService.java` `data/index.test.ts` `OrderList.test.ts` |
| 风险 | DB schema 不动(列保留),只停计算;如果下游 BI 仍在读这些字段,需要业务确认 |
| 验证命令 | `mvn test` + `npm run test` + `agent-do.ps1 -Scope full` |
| 是否需要部署 | 需要 |
| 是否需要业务确认 | **是** — 毛利字段是合同红线,必须业务确认才能彻底删 |

### 18.4 DASHBOARD-WORDING-FIX-001 "媒介"已彻底清除(确认无需再做)

| 项 | 描述 |
| --- | --- |
| 目标 | 确认前端/后端/测试/文档均无"媒介"残留 |
| 当前状态 | **已完成**(本审查 100% 确认) |
| 涉及文件 | N/A |
| 风险 | 0(已完成) |
| 验证命令 | `grep -rn "媒介" frontend/src backend/src docs/` 返回 0 行 |
| 是否需要部署 | 否 |
| 是否需要业务确认 | 否 |

### 18.5 DASHBOARD-SETTLEMENT-SYNC-001 结算轨全 0 原因处理

| 项 | 描述 |
| --- | --- |
| 目标 | 在前端 `/data/orders/summary` settle 行返回 0 时明确展示"暂无已结算订单,需等待结算窗口(7-15 天)";不修改业务逻辑,只补 UI 提示 |
| 涉及文件 | `OrderList.vue` `OrderSummaryRowVO.java` |
| 风险 | 低(纯 UI 提示) |
| 验证命令 | `npm run test` + 页面 smoke |
| 是否需要部署 | 需要 |
| 是否需要业务确认 | 否(业务事实) |

### 18.6 DASHBOARD-RECON-TEST-001 补充后端单测、前端单测、SQL 对账脚本

| 项 | 描述 |
| --- | --- |
| 目标 | 新增端到端对账测试:给定 DB 实际数据,断言 `/api/dashboard/metrics` 与 `/api/data/orders/summary` 与 `performance_records` 聚合**完全一致**;任一不一致 → 测试失败 |
| 涉及文件 | `DataControllerTest.java`(新增 4 用例)+ `dashboard-metrics.test.ts`(新增 3 用例)+ 独立 SQL 对账脚本 `harness/qa/dashboard-recon.sql` |
| 风险 | 中(测试可能揭出更多隐藏 bug) |
| 验证命令 | `mvn -Dtest=DataControllerTest test` + `npm run test -- dashboard-metrics.test.ts` + `psql -f harness/qa/dashboard-recon.sql` |
| 是否需要部署 | 否(仅测试 + QA 脚本) |
| 是否需要业务确认 | 否 |

### 18.7 DASHBOARD-E2E-001 real-pre 页面到 API 到 SQL 的端到端验收

| 项 | 描述 |
| --- | --- |
| 目标 | 登录 admin 访问 `/data` 和 `/dashboard`,验证页面 → API → SQL 数据完全一致;若不一致则阻断并提交 bug |
| 涉及文件 | `harness/qa/dashboard-e2e.spec.ts`(Playwright) |
| 风险 | 中(取决于真实业务数据) |
| 验证命令 | `npm run e2e:real-pre:p0` |
| 是否需要部署 | 否 |
| 是否需要业务确认 | 否 |

### 18.8 DASHBOARD-METRIC-CARD-RECON-001 旧版看板清理(可选)

| 项 | 描述 |
| --- | --- |
| 目标 | 删除 `/dashboard` 旧版路由 + `dashboard/index.vue` 旧版组件 + `/api/dashboard/summary` 旧版接口,或保留为兼容入口(显示废弃提示) |
| 涉及文件 | `router/index.ts` `views/dashboard/index.vue` `DashboardController.java` `DashboardService.java` |
| 风险 | 中(可能影响既有 E2E 测试) |
| 验证命令 | `grep -rn "dashboard/index\|/dashboard/summary" frontend backend` 必须仅命中废弃保护 |
| 是否需要部署 | 需要 |
| 是否需要业务确认 | **是**(需业务确认旧版看板是否被使用) |

---

## 19. 可立即修 vs 需要业务确认

### 可立即修(无业务风险)

- 18.2 服务费支出口径统一
- 18.5 结算轨 UI 提示补齐
- 18.6 端到端对账测试补齐

### 需要业务确认

- 18.1 Dashboard API 口径重写(影响业绩域 / 订单域边界)
- 18.3 毛利彻底下线(违反"看板简洁"vs"下游 BI 仍可能消费")
- 18.8 旧版看板清理(影响其他团队)

---

## 20. 本次未修改业务代码声明

- 本次审查**未修改**任何 `frontend/src/`、`backend/src/main/`、`backend/src/main/resources/`、`backend/pom.xml`、`docker-compose*.yml`、`*.env*`、`docs/`、`harness/doc/`、`harness/instructions/`、`harness/state/` 文件。
- 本次审查**仅新增** `harness/reports/dashboard-full-money-recon-001-20260605-102309.md` 和 `harness/reports/evidence-20260605-102309-dashboard-full-money-recon-001.md` 两份报告。
- 本次审查**未执行** UPDATE / DELETE / INSERT / TRUNCATE / migration / `docker compose down -v` / 容器重启 / 远端部署 / `git add` / `git commit` / `git push`。
- 本次审查**仅执行** 8 段只读 SELECT / 2 次 GET API / `git log` / `git show` / 文件 Read。
- 本次审查**未触碰** `system_config` / `commission_config` / `commissions` 等任何配置表。

---

## 21. Git dirty 归属说明

- **审查开始时**:`git status --short` 无输出 → 工作区 clean
- **审查过程中**:未做任何 git 写入
- **审查结束时**:`git status --short` 仍无输出 → 工作区 clean
- **报告文件**:`harness/reports/dashboard-full-money-recon-001-20260605-102309.md` 与 `harness/reports/evidence-20260605-102309-dashboard-full-money-recon-001.md` 由本次任务新增,已加入审查后 dirty(状态:"untracked,本任务产生")
- **本任务对源码的 dirty 贡献 = 0**

---

## 22. 下一步建议

| 优先级 | 任务 | 动作 |
| --- | --- | --- |
| **P0** | 与用户确认截图来源(本地 / 远端 / test / 旧 bundle) | 询问用户截图环境与时间;若可能,直接打开浏览器开发者工具 Network 标签,抓真实 API 响应 |
| **P0** | 业务确认 V1 合同:毛利字段是否彻底下线 | 写 ADR-005 决策 |
| **P0** | 业务确认 V1 合同:服务费支出双口径取哪一边 | 写 ADR-006 决策 |
| P1 | 执行 DASHBOARD-MONEY-FIX-002 + 003 + 004 | 修复代码 + 测试 |
| P1 | 执行 DASHBOARD-SETTLEMENT-SYNC-001 | 补 UI 提示 |
| P1 | 执行 DASHBOARD-RECON-TEST-001 | 补端到端对账测试 |
| P2 | 评估 DASHBOARD-METRIC-CARD-RECON-001 旧版看板清理 | 业务确认后执行 |
| P2 | 评估 `commissions` / `commission_settlement` / `commission_config` 3 张 0 行表的去留 | 与产品确认 |
| P2 | 评估 `domain_event_outbox` 事件通道是否补 OrderSynced/OrderSettled/PerformanceCalculated | 与架构确认 |

---

## 23. 完成判定 Checklist

- [x] 18 个指标全部完成前端/API/SQL/业绩表/汇总表对账(§4)
- [x] 服务费支出口径查明(§8 / §15)
- [x] 毛利是否应展示查明(§9)
- [x] 结算全 0 原因查明 = 真实未结算(§10)
- [x] 总订单数 7114 来源查明 = 当前 DB/API 无此数(§11)
- [x] "媒介"残留位置列出 = 已 100% 清除(§5.3)
- [x] 生成 `harness/reports/dashboard-full-money-recon-001-20260605-102309.md` 报告
- [x] 生成 `harness/reports/evidence-20260605-102309-dashboard-full-money-recon-001.md` evidence 报告
- [x] `git status` 中 dirty = 0(报告文件为 untracked,本任务产生)
- [x] 未修改业务代码
- [x] 用户截图与代码不一致的强证据已列出(§3 / §11 / §17)

---

## 24. 报告状态

- **DONE_AUDIT**(只读审查完成,推荐任务拆分已就绪)
- 等待用户确认截图来源 + V1 合同(毛利 / 服务费支出双口径)后,才能进入 DASHBOARD-MONEY-FIX-002/003/004 修复阶段
