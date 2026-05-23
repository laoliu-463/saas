# 文档总览

更新时间：2026-05-22

## 当前一句话状态

> V1 功能开发已完成，进入验收阶段（功能冻结，仅前端UX优化）。真实抖店订单数据归因"后再说"，确保逻辑正确即可。

说明：

- V1 代码审查全部完成，共发现 P1 × 15（安全 2 + 数据库 3 + 性能 3 + 运维 4 + 测试 3），全部冻结待 V2 处理
- 前端 6 项 P1「可绕过」经与后端交叉验证：后端已有 `@RequireRoles` + Service/DataScope 兜底，真实越权面已排除
- 达人信息获取（PublicWebTalentProvider）：被抖音风控拦截，需接抖音开放平台官方 API 才能真正实现
- 数据库迁移脚本已合并：`backend/src/main/resources/db/migrate-all.sql`（24 个 alter-*.sql 按序合并）
- 本地环境端口：real-pre（前端 3001 / 后端 8081 / PG 5433 / Redis 6380）
- 前端 npm build 遇 rolldown binding 缺失，执行 `rm -rf node_modules package-lock.json && npm install` 即可
- 宝塔 + Docker 部署检查清单已更新至本文档章节十一
- 当前已完成 `3000/8080` 本地 Mock 可见浏览器分批验收，并修复停用登录提示与物流发货数据可见性问题
- 当前已完成认证安全 5 项补齐：Token 双令牌刷新、登出 / Token 吊销（Redis 黑名单）、SecurityConfig 白名单收紧、DataScope 数据权限过滤、权限 / 菜单管理端点
- 当前 real-pre 已固定为 `real-pre` profile 单活运行，可命中真实抖店上游；本地 Mock / test 基线仍不混入真实数据
- 当前已完成真实 Token、授权主体、活动、活动商品、转链、订单同步与 `colonel_native` 订单归因首轮收口
- 当前已完成 `P1-8 Dashboard 全链路真实化验收`：真实数据持续增长下，`summary / activity-products / diagnosis drilldown / 前台链路` 保持一致
- 当前已完成 `M1.7 部署验证`：real-pre compose 重建恢复、健康检查、抖店联调 E2E、Dashboard smoke 与管理员全旅程 smoke 均通过
- 当前已补齐 real-pre Webhook 收件箱缺表问题：老 volume 现可由应用启动自动幂等补建 `douyin_webhook_event`，并已实投确认 `200 success + CONSUMED + event_key 幂等`
- 当前代码已补上“Webhook 带 `order_id / order_ids` 时触发定向订单同步”的保守业务消费，单测通过；real-pre 现场证据待 Docker 环境恢复后补打
- 2026-05-14 已补到 real-pre 现场证据：带 `order_id` 的 Webhook 会真实命中 `buyin.colonelMultiSettlementOrders`，收件箱结果为 `COLONEL_OPEN_EVENT_SYNCED`；当前剩余阻塞是该授权主体下上游返回 `orders=[]`
- 同轮补证显示：主订单真实样本目前仍是未结算态（`settled_goods_amount=0 / real_commission=0`）；虽然本地历史 `settle_time IS NOT NULL` 记录已有 48 条，但当前授权主体最近窗口内仍没有可供多结算接口返回的非空样本
- 已新增自动探针 `scripts/qa/watch-real-pre-order-settlements.ps1`；首轮证据目录为 `runtime/qa/out/real-pre-order-settlements-watch-20260514-130606`
- 2026-05-14 14:29 watcher 已继续复跑：`runtime/qa/out/real-pre-order-settlements-watch-20260514-142923`，结果仍为 `orderCount=0`，该问题已固化为“上游样本等待项”
- 2026-05-14 14:33 real-pre 主链路最终回归已完成：`runtime/qa/out/real-pre-final-regression-20260514-143347`；健康检查、授权主体、活动、商品、既有转链 / `pick_source_mapping`、主订单同步、归因 dry-run、Dashboard、Webhook 接收 / 重放 / 定向同步均通过
- 已新增统一观察入口：`scripts/qa/watch-real-pre-pending-evidence.ps1`，用于统一观察真实 `pick_source` 样本、live unsafe 样本、重复转链只读候选与 SKU 异常历史样本
- 已保留 P3-6 专用轻量观察入口：`scripts/qa/watch-real-pre-pick-source-orders.ps1`
- 已新增上线前清单：`docs/10-上线前验收清单.md`
- 下一阶段重点已切到 `P3-6` / `P3-8`，并保留 `P3-7` 只读复跑入口：真实订单归因样本、异常分支证据补齐；Dashboard 数据库口径对账已通过，后续只需随订单池变化定期复跑
- V1 默认 `EXCLUSIVE_ENABLED=false`，独家商家 / 独家达人不会覆盖订单归因；生产 compose 已补为独立 `docker-compose.prod.yml` + `scripts/start-prod.ps1`，prod 下 `/api/system/env` 仅管理员 JWT 可访问，Swagger / `doc.html` 不加载
- 七个领域设计原文已纳入 `docs/requirements/domain-design/`，新增 [13-领域设计总览](./13-领域设计总览.md)、[14-领域设计实现核验](./14-领域设计实现核验.md) 与 [24-七域需求代码核验报告](./24-七域需求代码核验报告.md)，用于对照用户、商品、达人、寄样、订单、业绩、配置七域的目标需求与当前代码差距

## 当前基线

### 代码与验证基线

- 前端构建：`frontend npm run build` 通过
- 后端测试：`backend mvn clean test` 作为当前代码基线为通过（`652 tests, 0 failures, 0 errors`，2026-05-09 确认）
- 认证安全相关补测（AuthService、JwtTokenProvider、JwtAuthInterceptor 等）已通过
- 本地 Mock 主链路联调记录已形成文档
- real-pre 全路径回归报告已落盘：`runtime/qa/out/e2e-20260503-1353/report.md`
- local-mock 补充验收报告已落盘：`runtime/qa/out/local-mock-supplement-20260503-1430/report.md`
- 数据看板 Gap 4 定向回归报告已落盘：`out/e2e-data-gap4-visible-20260506150815/report.md`
- QA 脚本入口已固定为：`runtime/qa/full-browser-e2e.cjs`、`runtime/qa/local-mock-supplement.cjs`、`runtime/qa/data-gap4-visible.cjs`
- 根目录 Playwright（与 `playwright.config.ts` / `tests/e2e`）：见仓库根 `README-e2e.md`；`npm run e2e` 为日常回归，`npm run e2e:real-pre` 为 real-pre 准入联调，`npm run e2e:real-pre:business` 为单样本业务闭环，`npm run e2e:real-pre:roles` 为全角色账号业务流程验收，`npm run e2e:real-pre:visual` 为业务闭环 + 全角色权限的浏览器可视化顺序回归，`npm run e2e:real-pre:journey:visual` 为“管理员 -> 招商组长 -> 招商 -> 渠道 -> 招商复审 -> 运营 -> 渠道组长 -> 管理员复核”的单剧本可视化全业务旅程回归
- QA 一键命令已固定为：`scripts/run-real-pre-e2e.ps1`、`scripts/run-local-mock-supplement.ps1`、`scripts/run-data-gap4-visible.ps1`、`scripts/run-qa-all.ps1`
- 本地 Mock 可见浏览器验收截图与结果已落盘：`out/e2e-*`
- 本地 Mock 最终可见浏览器烟雾验收已落盘：`out/e2e-final-smoke-20260503114958/results.json`

### 当前本地运行事实

- 当前只允许一套 active 环境运行，统一使用 project name `saas-active`
- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080/api`
- PostgreSQL：`5432`
- Redis：`6379`
- active 容器名固定为 `saas-frontend`、`saas-backend`、`saas-postgres`、`saas-redis`
- test：`.env.test`，`SPRING_PROFILES_ACTIVE=test`，`APP_TEST_ENABLED=true`，`DOUYIN_TEST_ENABLED=true`，数据库 `saas_test`
- real-pre：`.env.real-pre`，`SPRING_PROFILES_ACTIVE=real-pre`，`APP_TEST_ENABLED=false`，`DOUYIN_TEST_ENABLED=false`，数据库 `saas_real_pre`
- 登录账号：`admin / admin123`
- 环境探针：`test` / `real-pre` 下 `GET /api/system/env` 匿名返回 `200`，仅含 `activeProfiles / environmentLabel / appTestEnabled / douyinTestEnabled / database`；`prod` 下需管理员 JWT
- 默认管理员回归入口：`/system`（应自动落到 `/system/users`）

### 当前口径说明

- Mock 联调与回归口径收敛为 `test`，`local-mock` 仅保留为历史脚本、历史报告和回滚参考口径
- `test` 继续用于本地业务联调 / Mock 数据 / 自动化测试 / 隔离测试栈 / 管理员与招商组长权限测试
- `real-pre` 仅用于真实 SDK / 真实上游联调
- 第三方联调当前按精选联盟 / 团长链路统计进度；店铺商品与店铺订单接口不纳入联盟测范围，不依赖权限包的本地收口可继续推进

## 主干文档（23 个）

当前 `docs/` 根目录主要文档如下：

1. [README](./README.md)
2. [00-项目总览](./00-项目总览.md)
3. [01-业务闭环](./01-业务闭环.md)
4. [02-架构设计](./02-架构设计.md)
5. [03-Test与Real网关契约](./03-Test与Real网关契约.md)
6. [04-开发进度](./04-开发进度.md)
7. [05-接口与数据模型](./05-接口与数据模型.md)
8. [06-部署与对接计划](./06-部署与对接计划.md)
9. [09-真实SDK联调准备清单](./09-真实SDK联调准备清单.md)
10. [10-上线前验收清单](./10-上线前验收清单.md)
11. [10-V2.2场景覆盖矩阵](./10-V2.2场景覆盖矩阵.md)
12. [11-real-pre证据索引](./11-real-pre证据索引.md)
13. [12-物流接口适配性调研](./12-物流接口适配性调研.md)
14. [13-领域设计总览](./13-领域设计总览.md)
15. [14-领域设计实现核验](./14-领域设计实现核验.md)
16. [14-快递鸟物流接口对接预研](./14-快递鸟物流接口对接预研.md)
17. [16-real-pre联调记录](./16-real-pre联调记录.md)
18. [18-管理员账户系统功能排查记录](./18-管理员账户系统功能排查记录.md)
19. [19-TEST-mock-warning专项样本清单](./19-TEST-mock-warning专项样本清单.md)
20. [20-TEST/mock 最终验收报告](./20-TEST-mock最终验收报告.md)
21. [21-三方接口缺失降级联调方案](./21-三方接口缺失降级联调方案.md)
22. [23-七域需求缺口收口清单](./23-七域需求缺口收口清单.md)
23. [24-七域需求代码核验报告](./24-七域需求代码核验报告.md)

其余阶段性记录、专项验收、联调实录、整改单统一迁入 [archive/README](./archive/README.md)。

## 当前已打通的本地 Mock 主链路

1. 登录与核心页面访问
2. Token 双令牌刷新（Access Token 2h + Refresh Token 7d）
3. 登出与 Token 吊销（Redis 黑名单）
4. DataScope 数据权限过滤（本人 / 本组 / 全部）
5. 系统用户与系统角色 CRUD
6. 商品库、商品管理（活动列表 / 商品列表）、商品审核、分配、Mock 转链
7. Mock 订单回流、归因、未归因展示与详情排查
8. 寄样申请、审核、发货、签收、待交作业、自动完成
9. 达人列表、达人详情、达人与商品/订单/寄样关联

详细过程与证据见 [archive/records/16-local-mock业务联调记录](./archive/records/16-local-mock业务联调记录.md)。
本轮最终可见浏览器收口记录见 [archive/records/20-20260503-本地Mock可见浏览器验收记录](./archive/records/20-20260503-本地Mock可见浏览器验收记录.md)。

## 建议阅读路径

### 1. 第一次接手项目

1. [00-项目总览](./00-项目总览.md)
2. [01-业务闭环](./01-业务闭环.md)
3. [02-架构设计](./02-架构设计.md)
4. [03-Test与Real网关契约](./03-Test与Real网关契约.md)
5. [05-接口与数据模型](./05-接口与数据模型.md)
6. 如需直接跑浏览器验收，可先看 `runtime/qa/full-browser-e2e.cjs`
7. 如需单独回归数据看板 Gap 4（`timeField` 切换、`talentId/merchantId` 筛选），可执行 `scripts/run-data-gap4-visible.ps1`

### 2. 做本地 Mock 联调

1. [06-部署与对接计划](./06-部署与对接计划.md)
2. [archive/runbooks/07-Test全链路验收](./archive/runbooks/07-Test全链路验收.md)
3. [archive/runbooks/08-test-演示脚本](./archive/runbooks/08-test-演示脚本.md)
4. [archive/records/16-local-mock业务联调记录](./archive/records/16-local-mock业务联调记录.md)
5. [archive/records/17-项目剩余事项看板](./archive/records/17-项目剩余事项看板.md)
6. [runtime/qa/local-mock-supplement.cjs](../runtime/qa/local-mock-supplement.cjs)

### 3. 做真实 SDK 联调准备

1. [09-真实SDK联调准备清单](./09-真实SDK联调准备清单.md)
2. [archive/records/14-抖店SDK全量梳理与逐接口联调规划](./archive/records/14-抖店SDK全量梳理与逐接口联调规划.md)
3. [archive/records/15-real-pre最小联调落地方案](./archive/records/15-real-pre最小联调落地方案.md)
4. [archive/runbooks/19-商品链路本地排查](./archive/runbooks/19-商品链路本地排查.md)
5. [archive/runbooks/20-real-pre商品订单归因逻辑排查](./archive/runbooks/20-real-pre商品订单归因逻辑排查.md)
6. [runtime/qa/full-browser-e2e.cjs](../runtime/qa/full-browser-e2e.cjs)

### 4. 做专项收口

1. [10-V2.2场景覆盖矩阵](./10-V2.2场景覆盖矩阵.md)
2. [archive/runbooks/11-P0测试数据收口清单](./archive/runbooks/11-P0测试数据收口清单.md)
3. [archive/audits/12-文档编码乱码问题分析报告](./archive/audits/12-文档编码乱码问题分析报告.md)
4. [archive/audits/13-接口导入APIFOX整改任务单](./archive/audits/13-接口导入APIFOX整改任务单.md)
5. [archive/audits/接口整改-现状vs目标](./archive/audits/接口整改-现状vs目标.md)
6. [archive/audits/接口整改-决策备忘录](./archive/audits/接口整改-决策备忘录.md)

### 5. 核对领域需求与实现差距

1. [13-领域设计总览](./13-领域设计总览.md)
2. [14-领域设计实现核验](./14-领域设计实现核验.md)
3. [23-七域需求缺口收口清单](./23-七域需求缺口收口清单.md)
4. [24-七域需求代码核验报告](./24-七域需求代码核验报告.md)
5. [requirements/domain-design/01-用户域](./requirements/domain-design/01-用户域.md)
6. [requirements/domain-design/02-商品域](./requirements/domain-design/02-商品域.md)
7. [requirements/domain-design/03-达人域](./requirements/domain-design/03-达人域.md)
8. [requirements/domain-design/04-寄样域](./requirements/domain-design/04-寄样域.md)
9. [requirements/domain-design/05-订单域](./requirements/domain-design/05-订单域.md)
10. [requirements/domain-design/06-业绩域](./requirements/domain-design/06-业绩域.md)
11. [requirements/domain-design/07-配置域](./requirements/domain-design/07-配置域.md)

### 6. 做全量文档口径整合

1. [archive/records/28-当前文档整合总览](./archive/records/28-当前文档整合总览.md)
2. [archive/audits/14-项目级覆盖审计报告](./archive/audits/14-项目级覆盖审计报告.md)

## 当前最重要的执行原则

- 前端只调用内部 API，不直连第三方
- Service 层只依赖 Gateway 契约
- 本地 Mock 与 Real 只允许在 Gateway 实现与配置层切换
- 本地联调必须先看业务，再看页面，再看接口，再查数据库，再看日志
- 浏览器验收默认执行规范：每个测试用例单独新开一个可见浏览器，逐页操作并截图归档到对应批次目录
- 未经明确要求，不接真实抖店 / 抖音 / SDK

## 当前优先级

1. `P3 Pending Evidence Watch`：通过 `scripts/qa/watch-real-pre-pending-evidence.ps1` 统一观察真实 `pick_source` 订单样本、`EXC-004` live 样本、`EXC-005` 只读候选和 `EXC-006` 历史样本
2. `P3-8`：补齐异常分支证据，优先覆盖“映射晚于订单时间 live 样本、重复转链幂等安全路径、SKU 异常不中断主流程安全路径”
3. `P3-7-R`：保留 `scripts/qa/run-real-pre-dashboard-reconcile.ps1` 作为只读复跑入口，持续验证 `/api/dashboard/summary` 与 PostgreSQL `10` 个核心指标一致
