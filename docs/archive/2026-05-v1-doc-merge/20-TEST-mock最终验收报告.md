> 本文档已归档，仅作为历史参考；当前口径以 docs/ 下主文档为准。

# 20-TEST/mock 最终验收报告

更新时间：2026-05-17

## 一、结论

TEST/mock 主流程第二阶段验收通过，允许进入 real-pre 前置联调与风险收口阶段。

本结论只代表 TEST/mock 主流程闭环、角色权限业务操作、Dashboard 对账与 warning 分级已完成；不代表真实三方接口、真实 Token、real-pre 上游权限包或真实样本风险已经关闭。

2026-05-17 09:04 补充：real-pre 运行态准入和前端 + 后端 E2E 已通过；这只关闭本轮 Token、授权主体、活动商品、SKU、订单同步与 Dashboard 链路的准入验证，不等同于关闭所有真实三方专项风险。

2026-05-16 当日晚间补充收口结果：

- `runtime/qa/p1-warning-risk-regression.cjs` 已完成最新一轮复跑，`8/8 PASS`
- `runtime/qa/out/p1-warning-risk-regression-20260516-214324-277` 已落盘
- real-pre 只读 preflight 已执行，但未通过
- 当前阻塞项不是代码逻辑，而是 `.env.real-pre` 缺少真实抖店 SDK 凭据：`DOUYIN_APP_ID`、`DOUYIN_CLIENT_KEY`、`DOUYIN_CLIENT_SECRET`
- 下一步必须先在本机补齐 `.env.real-pre`，然后重跑 `qa-real-pre-preflight.ps1`；在 preflight 通过前，不切 real-pre

2026-05-17 08:08 复跑结果：

- 已按只读入口复跑 `powershell -ExecutionPolicy Bypass -File .\scripts\qa-real-pre-preflight.ps1 -EnvFile .\.env.real-pre`
- 证据目录：`runtime/qa/out/qa-real-pre-preflight-20260517-080851-975`
- `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false` 复核通过
- `real_sdk_config_present` 仍未通过，缺失字段仍为 `DOUYIN_APP_ID`、`DOUYIN_CLIENT_KEY`、`DOUYIN_CLIENT_SECRET`
- 运行态检查为 `SKIP`，原因是本轮未启动 real-pre 运行栈且本机 Docker engine 不可连；这不是额外 blocking item
- 结论：暂不允许进入 real-pre 下一步验证

2026-05-17 08:13 补齐本机 `.env.real-pre` 后复跑结果：

- 已按只读入口复跑 `powershell -ExecutionPolicy Bypass -File .\scripts\qa-real-pre-preflight.ps1 -EnvFile .\.env.real-pre`
- 证据目录：`runtime/qa/out/qa-real-pre-preflight-20260517-081343-927`
- `overallPass=true`
- blocking checks：无
- `real_sdk_config_present` 已通过，报告只保留脱敏摘要
- `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false` 复核通过
- 运行态检查仍为 `SKIP`，原因是本轮未启动 real-pre 运行栈且本机 Docker engine 不可连；这不是本次准入预检 blocking item
- 结论：允许进入 real-pre 下一步验证

2026-05-17 08:34 进入 real-pre 运行态结果：

- 已执行 `scripts/start-real-pre.ps1`，当前 active 栈为 `saas-active`
- 运行态证据目录：`runtime/qa/out/qa-real-pre-preflight-20260517-083455-441`
- `runtime_container_env_safe / runtime_health_up / runtime_system_env_safe` 均为 `PASS`
- `GET /api/system/env` 返回 `activeProfiles=["real-pre"]`、`environmentLabel=REAL-PRE`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`database=saas_real_pre`
- `saas-backend / saas-frontend / saas-postgres / saas-redis` 当前均为 `healthy`
- 本轮启动中修正了本机 `.env.real-pre` 的 `SPRING_PROFILES_ACTIVE=real-pre` 和非占位 `JWT_SECRET`；真实值未写入文档或 Git
- 已用 `@Lazy` 最小拆除 `RealDouyinTokenGateway -> InstitutionApi -> DouyinApiClient -> DouyinTokenService` 启动循环依赖，定向测试 `mvn "-Dtest=RealDouyinTokenGatewayTest" test` 通过
- 前端 `/login` 初始首响约 `19.4s`；已定位为 Docker + Vite polling 间隔过短，本机 `.env.real-pre` 设置 `CHOKIDAR_INTERVAL=30000` 后复核约 `0.02s`
- 浏览器已打开登录页并用 `admin/admin123` 登录到 `/dashboard`，页面显示 `环境：REAL-PRE`；证据目录 `runtime/qa/out/real-pre-browser-check-20260517-0847`
- 结论：real-pre 已进入运行态，允许继续下一步验证

2026-05-17 09:04 real-pre E2E 补充结果：

- 已使用新 OAuth code 初始化 token：`POST /api/douyin/tokens` HTTP 200 / `code=200`，`hasAccessToken=true`、`hasRefreshToken=true`、`reauthorizeRequired=false`
- 未在文档或 Git 中记录 OAuth code、access token、refresh token、JWT 或 client secret
- `npm run e2e:real-pre` 已复跑通过：`1/1 PASS`
- 覆盖链路：Token 状态、授权主体、活动商品刷新、精选联盟 SKU、订单同步、Dashboard 指标、`/system/douyin` 页面一键刷新状态
- 首轮 E2E 越过 token 后失败在测试 helper 对 SKU 探针响应形状的判定过窄；后端和页面均已返回 `status=success`、`skuCount=1`，已修正 `tests/e2e/helpers/real-pre-api.ts` 并复跑通过
- 最新证据目录：`runtime/qa/out/real-pre-e2e-20260517-090457-069`
- 最新 preflight：`runtime/qa/out/qa-real-pre-preflight-20260517-090308-762`
- 结论：允许进入 real-pre 下一步验证

本轮约束执行情况：

- TEST/mock 收口阶段未切换 real-pre；2026-05-17 08:34 后已按准入结果进入 real-pre 运行态。
- 只读 preflight 未访问真实三方业务接口；09:04 real-pre E2E 阶段已按授权码补充继续访问真实抖店链路。
- 未清库。
- 未提交或写入真实密钥。
- 新增脚本均可重复运行，并输出 `summary.json` 与 `report.md`。
- `runtime/qa/out/` 继续作为运行产物目录忽略，不纳入 Git。

## 二、Git 纳入说明

本轮 `.gitignore` 已按“只放开 `runtime/qa` 脚本与配置，继续忽略运行产物”收口：

- `runtime/` 仍默认忽略。
- `runtime/qa/*.cjs` 已纳入 Git，可见新增 QA 脚本。
- `runtime/qa/*.json` 仅放开当前固定配置文件：`business-crud-cases.json`、`mock-scenario-matrix.json`、`role-page-cases.json`。
- `runtime/qa/out/`、`runtime/qa/screenshots/` 与 `runtime/qa/*.log` 继续忽略，不把运行产物纳入 Git。
- `.env.*` 仍保持示例文件纳入、真实环境文件默认忽略，本轮没有把 `.env.test`、`.env.real-pre` 纳入版本库。
- `.env.real-pre.example` 现已补齐 real-pre 必需键位：`DOUYIN_APP_ID`、`DOUYIN_CLIENT_KEY`、`DOUYIN_CLIENT_SECRET`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`，但不写入任何真实密钥。

确认方式：

```text
git status --short runtime/qa scripts docs .gitignore
```

预期结果：

- 能看到新增或修改的 `runtime/qa/*.cjs`、`scripts/*.ps1`、`docs/*.md`
- 看不到 `runtime/qa/out/*` 运行产物

## 三、Mock Audit

- 报告目录：`runtime/qa/out/qa-mock-data-audit-20260516-194040-956`
- `overallPass=true`
- `coverage=84.58%`
- `hardMissing=0`
- `api-errors=[]`
- warning 数量：`17`

当前不再继续盲目补 seed。剩余 warning 只按分级进入专项或 real-pre 前置风险清单。

## 四、状态流转回归

- 脚本：`runtime/qa/business-state-flow-regression.cjs`
- 报告目录：`runtime/qa/out/business-state-flow-regression-20260516-201336-212`
- 输出文件：`summary.json`、`report.md`
- 结果：`22/22 PASS`

覆盖范围：

- 商品状态流转：待审核 -> 已上架、待审核 -> 已拒绝、已上架渠道可见、已拒绝渠道不可见。
- 寄样状态流转：待审核 -> 待发货 -> 快递中 -> 待交作业 -> 已完成。
- 寄样分支：待审核 -> 已拒绝、待交作业 -> 已关闭、已拒绝后重新申请。
- 订单归因流转：已归因、无 `pick_source`、映射缺失、商品未覆盖、映射时间不安全、重放归因。
- 非法流转：已拒绝不能发货、已完成不能再次审核、渠道不能审核、运营不能审核商品。

## 五、角色业务 Smoke

- 脚本：`runtime/qa/page-role-business-smoke.cjs`
- 报告目录：`runtime/qa/out/page-role-business-smoke-20260516-202802-261`
- 输出文件：`summary.json`、`report.md`
- 结果：`6/6 PASS`

覆盖角色：

- `admin`
- `biz_leader`
- `biz_staff`
- `channel_leader`
- `channel_staff`
- `operator`（现有账号映射为 `ops_staff`）

每个角色均输出菜单、按钮、数据范围、允许业务操作与禁止业务操作结果。

## 六、Dashboard 对账

- 脚本：`runtime/qa/dashboard-reconcile.cjs`
- 报告目录：`runtime/qa/out/dashboard-reconcile-20260516-203253-797`
- 输出文件：`summary.json`、`report.md`、`reconcile-summary.json`、`reconcile-report.md`
- 结果：`23/23 PASS`

已对账指标：

- 今日订单数、GMV、服务费。
- Summary 订单数、GMV、服务费。
- 已归因、未归因。
- 商品未覆盖、退款关闭。
- 订单明细聚合与 DB 聚合。
- 招商维度、渠道维度。

## 七、Warning 分级

P1 处理结果：

| 场景 | 当前处理 | real-pre 前置风险 |
|---|---|---|
| 转链失败 | 本轮不继续补 seed；未作为 TEST/mock 主流程阻塞项 | 是，进入真实转链失败提示与兜底验证清单 |
| 爬虫兜底 | 已有 `third-party-fallback-regression` 覆盖采集成功、采集失败手动补录、手动补录后认领与寄样 | 否，保留 real-pre 真实达人接口未接入风险说明 |
| 歧义映射 | 状态流转回归已覆盖诊断位读取；未强行补歧义 seed | 是，进入 real-pre 归因诊断与映射沉淀风险清单 |

P2 处理结果：

| 场景 | 当前处理 |
|---|---|
| 投流支持 / 不支持 | 不作为 TEST/mock 主流程阻塞项，后续进入商品属性筛选专项 |
| 独家达人 | 不作为 TEST/mock 主流程阻塞项，后续进入 `exclusive-rule-regression` |
| 独家商家 | 不作为 TEST/mock 主流程阻塞项，后续进入 `exclusive-rule-regression` |

补充证据：

- `runtime/qa/out/third-party-fallback-regression-20260516-201448-032`
- 结果：`8/8 PASS`
- 约束：`switchedRealPre=false`、`realThirdPartyAccessed=false`、`databaseCleared=false`、`productionConfigModified=false`
- `runtime/qa/out/p1-warning-risk-regression-20260516-214324-277`
- 结果：`8/8 PASS`
- 覆盖：`PROMO-001~004`、`ATTR-001~004`
- 配套单测：`node runtime/qa/p1-warning-risk-regression.test.cjs` 通过

## 八、real-pre 后续风险清单

当前 real-pre 准入已通过，并已进入 P3 真实业务闭环验证；以下风险不能在文档口径上被“顺手关闭”：

| 风险项 | 当前状态 | 阻塞口径 |
| --- | --- | --- |
| 抖店 SDK 主链路 real-pre 准入与 E2E | 已复跑，通过 | `runtime/qa/out/qa-real-pre-preflight-20260517-090308-762/report.md` 确认 `overallPass=true`、blocking checks 为空；`runtime/qa/out/real-pre-e2e-20260517-090457-069/report.md` 记录 `npm run e2e:real-pre` 为 `1/1 PASS`；已进入 P3 真实业务闭环验证 |
| P3 real-pre 业务闭环 smoke | 首轮通过 | `runtime/qa/out/real-pre-business-e2e-20260517-091828/summary.json` 记录 `npm run e2e:real-pre:business` 为 `1/1 PASS`；该结论只代表单样本链路可执行 |
| 转链失败专项 | 已补 TEST/mock P1 风险回归入口 | 不阻塞 P3 首轮 smoke，但仍需补真实失败提示与重试兜底取证 |
| 歧义映射专项 | 已补 TEST/mock P1 风险回归入口 | 不阻塞 P3 首轮 smoke，但仍需补映射缺失、多候选、不安全时间、商品未覆盖诊断取证 |
| 物流真实接口 | 未接入真实服务 | 不能标记为真实三方联调完成，但不阻塞抖店 SDK 主链路 |
| 达人真实接口 | 未接入真实服务 | 不能标记为真实三方联调完成，但不阻塞抖店 SDK 主链路 |

preflight 最新结论：

- 当前已切入 real-pre 运行态；未清库，未把真实密钥写入文档或 Git。
- 运行态检查已从 `SKIP` 更新为 `PASS`。
- 配置检查有效，且当前 blocking checks 为空。
- report：`runtime/qa/out/qa-real-pre-preflight-20260517-090308-762/report.md`
- summary：`runtime/qa/out/qa-real-pre-preflight-20260517-090308-762/summary.json`
- real-pre E2E：`runtime/qa/out/real-pre-e2e-20260517-090457-069/report.md`
- P3 business smoke：`runtime/qa/out/real-pre-business-e2e-20260517-091828/summary.json`

P1 风险专项回归入口：

```text
node runtime/qa/p1-warning-risk-regression.cjs
```

输出目录：

```text
runtime/qa/out/p1-warning-risk-regression-<时间戳>/
├── summary.json
└── report.md
```

覆盖范围：

- 转链失败：接口失败、无 `pick_source`、参数非法、重复尝试。
- 歧义映射：映射缺失、多候选、不安全时间、商品未覆盖。

## 九、命令结果

本轮关键命令结果：

```text
node --check runtime/qa/business-state-flow-regression.cjs
PASS

node runtime/qa/business-state-flow-regression.test.cjs
5/5 PASS

node runtime/qa/business-state-flow-regression.cjs
22/22 PASS

node --check runtime/qa/page-role-business-smoke.cjs
PASS

node runtime/qa/page-role-business-smoke.test.cjs
4/4 PASS

node runtime/qa/page-role-business-smoke.cjs
6/6 PASS

node --check runtime/qa/dashboard-reconcile.cjs
PASS

node runtime/qa/dashboard-reconcile.test.cjs
5/5 PASS

node runtime/qa/dashboard-reconcile.cjs
23/23 PASS

node runtime/qa/p1-warning-risk-regression.test.cjs
PASS

node runtime/qa/p1-warning-risk-regression.cjs
8/8 PASS

powershell -ExecutionPolicy Bypass -File .\scripts\qa-real-pre-preflight.ps1 -EnvFile .\.env.real-pre
PASS
overallPass=true
blockingChecks=[]

report: runtime/qa/out/qa-real-pre-preflight-20260517-081343-927/report.md

summary: runtime/qa/out/qa-real-pre-preflight-20260517-081343-927/summary.json

powershell -ExecutionPolicy Bypass -File .\scripts\qa-real-pre-preflight.ps1 -EnvFile .\.env.real-pre
PASS
overallPass=true
blockingChecks=[]

report: runtime/qa/out/qa-real-pre-preflight-20260517-090308-762/report.md

summary: runtime/qa/out/qa-real-pre-preflight-20260517-090308-762/summary.json

npm run e2e:real-pre
1/1 PASS

evidence: runtime/qa/out/real-pre-e2e-20260517-090457-069/report.md

mvn "-Dtest=RealDouyinTokenGatewayTest" test
BUILD SUCCESS
5/5 PASS

mvn "-Dtest=PickSourceMappingServiceTest,ProductServiceTest" test
BUILD SUCCESS

docker exec saas-backend sh -lc "mvn -Dmaven.test.skip=true compile"
BUILD SUCCESS
```

最新 real-pre 运行态环境复核：

```text
activeProfiles=real-pre
envLabel=REAL-PRE
app.test.enabled=false
douyin.test.enabled=false
db.name=saas_real_pre
```

## 十、下一阶段入口命令

后续 real-pre 验证统一从以下入口开始：

```powershell
git status --short runtime/qa scripts docs .gitignore
powershell -ExecutionPolicy Bypass -File .\scripts\qa-real-pre-preflight.ps1 -EnvFile .\.env.real-pre
npm run e2e:real-pre
```

说明：

- 第一个命令用于确认 QA 脚本已被 Git 识别，且 `runtime/qa/out` 没有误入版本库。
- 第二个命令只做 real-pre 只读预检，不清库、不写生产数据、不调用真实业务接口。
- 第三个命令会访问真实抖店链路；执行前需确认本轮允许触发 Token、授权主体、活动商品、SKU、订单同步与 Dashboard 验证。

## 十一、进入 P3 real-pre 判断

TEST/mock 收口已完成，real-pre 运行态准入已通过，P3 real-pre 业务闭环 smoke 首轮通过；当前允许继续补齐真实链路业务闭环验证。

当前 real-pre 准入阻塞项：

- 暂无。

放行条件：

- `.env.real-pre` 已在本机补齐真实 SDK 凭据与非占位 `JWT_SECRET`，文档与报告只记录脱敏摘要
- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `qa-real-pre-preflight.ps1` 运行态复跑通过，证据目录为 `runtime/qa/out/qa-real-pre-preflight-20260517-090308-762`
- `npm run e2e:real-pre` 已复跑通过，证据目录为 `runtime/qa/out/real-pre-e2e-20260517-090457-069`
- `npm run e2e:real-pre:business` 已首轮通过，证据目录为 `runtime/qa/out/real-pre-business-e2e-20260517-091828`

进入后注意事项：

- 本机 real-pre 前端已设置 `CHOKIDAR_INTERVAL=30000`，用于避免 Docker + Vite polling 导致页面首响拖慢。
- 不用 Mock 数据伪装真实三方通过。
- 转链失败、歧义映射、真实订单归因样本、Dashboard 数据库口径对账继续按 P3 风险取证。
- P2 warning 不作为 TEST/mock 主流程阻塞项。
- real-pre 验证前继续确认 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`，并使用真实联调文档口径执行。

