# 20-TEST/mock 最终验收报告

更新时间：2026-05-16

## 一、结论

TEST/mock 主流程第二阶段验收通过，允许进入 real-pre 前置联调与风险收口阶段。

本结论只代表 TEST/mock 主流程闭环、角色权限业务操作、Dashboard 对账与 warning 分级已完成；不代表真实三方接口、真实 Token、real-pre 上游权限包或真实样本风险已经关闭。

2026-05-16 当日晚间补充收口结果：

- `runtime/qa/p1-warning-risk-regression.cjs` 已完成最新一轮复跑，`8/8 PASS`
- `runtime/qa/out/p1-warning-risk-regression-20260516-214324-277` 已落盘
- real-pre 只读 preflight 已执行，但未通过
- 当前阻塞项不是代码逻辑，而是 `.env.real-pre` 缺少真实抖店 SDK 凭据：`DOUYIN_APP_ID`、`DOUYIN_CLIENT_KEY`、`DOUYIN_CLIENT_SECRET`
- 下一步必须先在本机补齐 `.env.real-pre`，然后重跑 `qa-real-pre-preflight.ps1`；在 preflight 通过前，不切 real-pre

本轮约束执行情况：

- 未切换 real-pre。
- 未访问真实三方接口。
- 未清库。
- 未修改生产配置。
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

## 八、real-pre 前置风险清单

当前允许进入 real-pre 前置阶段，但以下风险不能在文档口径上被“顺手关闭”：

| 风险项 | 当前状态 | 阻塞口径 |
| --- | --- | --- |
| 抖店 SDK 主链路 real-pre 只读预检 | 已执行，未通过 | `runtime/qa/out/qa-real-pre-preflight-20260516-215205-426/blocking-report.md` 已确认当前阻塞项为 `.env.real-pre` 缺少 `DOUYIN_APP_ID`、`DOUYIN_CLIENT_KEY`、`DOUYIN_CLIENT_SECRET`；补齐后必须重跑 preflight |
| 转链失败专项 | 已补 TEST/mock P1 风险回归入口 | 不阻塞进入 preflight，但进入 real-pre 后必须补真实失败提示与重试兜底取证 |
| 歧义映射专项 | 已补 TEST/mock P1 风险回归入口 | 不阻塞进入 preflight，但进入 real-pre 后必须补映射缺失、多候选、不安全时间、商品未覆盖诊断取证 |
| 物流真实接口 | 未接入真实服务 | 不能标记为真实三方联调完成，但不阻塞抖店 SDK 主链路 |
| 达人真实接口 | 未接入真实服务 | 不能标记为真实三方联调完成，但不阻塞抖店 SDK 主链路 |

preflight 本轮结论：

- 运行边界仍为只读：未切 real-pre、未访问真实三方接口、未清库。
- 运行态检查保持 `SKIP`，这是因为本轮没有启动 real-pre 运行栈。
- 配置检查有效，且当前唯一 blocking check 为 `real_sdk_config_present`。
- blocking report：`runtime/qa/out/qa-real-pre-preflight-20260516-215205-426/blocking-report.md`
- summary：`runtime/qa/out/qa-real-pre-preflight-20260516-215205-426/summary.json`

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

powershell -ExecutionPolicy Bypass -File .\scripts\qa-real-pre-preflight.ps1 -EnvFile .\.env.real-pre -BaseUrl http://127.0.0.1:18080 -BackendContainerName saas-backend-real-pre
FAIL
blocking: DOUYIN_APP_ID, DOUYIN_CLIENT_KEY, DOUYIN_CLIENT_SECRET missing

blocking report: runtime/qa/out/qa-real-pre-preflight-20260516-215205-426/blocking-report.md

summary: runtime/qa/out/qa-real-pre-preflight-20260516-215205-426/summary.json

mvn "-Dtest=PickSourceMappingServiceTest,ProductServiceTest" test
BUILD SUCCESS

docker exec saas-backend sh -lc "mvn -Dmaven.test.skip=true compile"
BUILD SUCCESS
```

环境复核：

```text
activeProfiles=test
envLabel=TEST
app.test.enabled=true
douyin.test.enabled=true
db.name=saas_test
```

## 十、下一阶段入口命令

进入 real-pre 前置联调前，统一从以下入口开始：

```powershell
git status --short runtime/qa scripts docs .gitignore
copy .\.env.real-pre.example .\.env.real-pre
# 在本机补齐真实 DOUYIN_APP_ID / DOUYIN_CLIENT_KEY / DOUYIN_CLIENT_SECRET
powershell -ExecutionPolicy Bypass -File .\scripts\qa-real-pre-preflight.ps1 -EnvFile .\.env.real-pre
node .\runtime\qa\p1-warning-risk-regression.cjs
```

说明：

- 第一个命令用于确认 QA 脚本已被 Git 识别，且 `runtime/qa/out` 没有误入版本库。
- 第二个命令只做 real-pre 只读预检，不清库、不写生产数据、不调用真实业务接口。
- 第三个命令继续在 TEST/mock 口径下跑 P1 风险专项回归，作为进入 real-pre 前的最后一轮 warning 收口。

## 十一、进入 real-pre 判断

TEST/mock 收口已完成，但当前暂不允许切换 real-pre。

当前 real-pre 准入阻塞项：

- `.env.real-pre` 缺少真实抖店 SDK 凭据：`DOUYIN_APP_ID`
- `.env.real-pre` 缺少真实抖店 SDK 凭据：`DOUYIN_CLIENT_KEY`
- `.env.real-pre` 缺少真实抖店 SDK 凭据：`DOUYIN_CLIENT_SECRET`

放行条件：

- 在本机补齐 `.env.real-pre` 上述 3 个真实凭据
- 保持 `APP_TEST_ENABLED=false`
- 保持 `DOUYIN_TEST_ENABLED=false`
- 重跑 `qa-real-pre-preflight.ps1`
- 仅当 preflight 通过后，才允许进入下一步 real-pre 准入判断

进入后注意事项：

- 不用 Mock 数据伪装真实三方通过。
- 转链失败与歧义映射继续按 P1 real-pre 前置风险取证。
- P2 warning 不作为 TEST/mock 主流程阻塞项。
- real-pre 验证前继续确认 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`，并使用真实联调文档口径执行。
