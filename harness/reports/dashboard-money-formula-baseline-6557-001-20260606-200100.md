# DASHBOARD-MONEY-FORMULA-BASELINE-6557-001

## 结论

- 结论：PARTIAL。
- 已修复并验证：数据看板服务费支出、服务费收益字段映射、结算轨服务费支出反推不重复扣技术服务费、订单详情旧 `talentCommission` 兜底误用。
- 未达成用户基准：截至 2026-06-06 19:58-20:00 +08:00，real-pre live 数据仍在热同步，今日全量订单已经变为 6598 单、142393.83 元，和用户给定 6557 单、141508.04 元不一致。
- 当前不能把 6557/141508.04 写成 PASS；该基准需要冻结时间点、筛选字段和是否包含无效/冲正订单后才能复核。

## 现象与证据

### 任务前基线

- 初始 DB 今日全量订单：6537 单、141095.87 元。
- 初始有效业绩轨：6071 单、130656.97 元。
- 初始后端日志出现 `isv.signature-invalid`，热同步无法补齐用户基准。

### 任务后运行态

- Docker：`backend-real-pre`、`frontend-real-pre`、`postgres-real-pre`、`redis-real-pre` 均 healthy。
- `/api/data/orders/summary` 默认/`createTime` 今日：6598 单、142393.83 元。
- SQL `colonelsettlement_order` 今日创建时间：6598 单、142393.83 元，服务费收入 2182.17 元，技术服务费 219.36 元。
- SQL `performance_records` 今日有效业绩：6131 单、131935.03 元，服务费收入 2033.40 元，技术服务费 204.42 元，服务费支出 0 元，服务费收益 1828.98 元，招商/渠道各 273.86 元，毛利 1281.26 元。
- 系统配置仍为：`commission.business_default_ratio=0.15`，`commission.channel_default_ratio=0.15`。
- 重启后同步日志显示 `buyin.instituteOrderColonel` 和 `buyin.colonelMultiSettlementOrders` 调用成功，热同步持续插入/更新订单。

## 修改范围

- `backend/src/main/java/com/colonel/saas/service/PerformanceSummaryService.java`
- `backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java`
- `backend/src/main/java/com/colonel/saas/vo/data/MetricsVO.java`
- `backend/src/test/java/com/colonel/saas/service/PerformanceSummaryServiceTest.java`
- `backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java`
- `frontend/src/views/data/index.vue`
- `frontend/src/views/data/index.test.ts`
- `frontend/src/views/data/OrderDetailTab.vue`
- `frontend/src/views/data/OrderDetailTab.test.ts`
- `frontend/src/views/data/OrderList.vue`

## 验证

- PASS：`mvn -f backend/pom.xml "-Dtest=PerformanceCalculationServiceTest,Dashboard*Test,*Summary*Test,DataControllerTest" test`，84 tests，0 failures，0 errors。
- PASS：`mvn -f backend/pom.xml -DskipTests package`。
- PASS：`npm run test -- index.test.ts OrderDetailTab.test.ts`，4 files，51 tests。
- PASS：`npm run typecheck`。
- PASS：`npm run build`，存在既有 Vite chunk > 500k 警告。
- PASS：`powershell ... safety-check.ps1 -Env real-pre -Scope full -DryRun`。
- PARTIAL：`agent-do.ps1 -Env real-pre -Scope full` 完成构建、重建、重启、健康检查、real-pre preflight 和 evidence 采集，但最终 `git diff --cached --check` 因既有暂存/未跟踪文件空白失败。
- PASS with note：Playwright 打开 `http://127.0.0.1:3001/data` 桌面/移动页面，无 4xx/5xx；存在 2 条 Google Fonts CSP console error。

## 风险

- real-pre 是 live 上游模式，数据会持续变化，不能用未冻结时间点的用户截图数值做最终断言。
- 当前没有独立上游服务费支出字段，`performance_records.estimate_service_fee_expense` 仍为 0；不能硬编码用户基准中的支出金额。
- 提成比例配置仍为 15%/15%，如用户基准使用其它业务比例，需要业务确认后通过配置/规则域调整。
- Git gate 阻塞来自既有暂存文件：`build-docker*.txt` 和历史 report 中存在 trailing whitespace；本轮未提交、未推送。
