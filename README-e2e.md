# Playwright E2E 联调说明

## 目录

- 配置: `playwright.config.ts`
- 用例: `tests/e2e`
- 环境样例: `.env.test.example`、`.env.real-pre.example`
- 报告: `playwright-report`
- 失败产物: `test-results/playwright`

## 安装

```bash
npm install
npx playwright install
```

## 环境

1. 复制 `.env.test.example` 为 `.env.test`；真实上游 / 生产形态验证复制 `.env.real-pre.example` 为 `.env.real-pre`
2. 按实际环境调整前端地址和账号

默认口径:

- `E2E_BASE_URL=http://localhost:3000`
- 管理员: `admin / admin123`
- 招商组长: `biz_leader / admin123`
- 渠道组长: `channel_leader / admin123`
- 渠道专员: `channel_staff / admin123`
- 运营: `ops_staff / admin123`

## E2E 套件分层

| 套件 | 脚本 | 文件范围 | 用途 |
|------|------|---------|------|
| **CI 烟测层** | `npm run e2e:smoke` | `00~07` + `10-navigation-regression` | 日常开发保底，页面不炸 |
| **V1-P0 验收层** | `npm run e2e:v1-p0` | `20~24` | test/mock 基线三链闭环 + 权限 + 看板 |
| **real-pre P0 验收层** | `npm run e2e:real-pre:p0` | `31~36` | real-pre 联调验收：商品链/订单/寄样/业绩/RBAC/清理 |
| **全旅程层** | `npm run e2e:journey` | `09-full-user-journey` | 多角色完整链路手动验证 |
| **real-pre 既有专项** | `npm run e2e:real-pre` 等 | `08/10/11/12` | 真实环境联调，需 Token；保留为细分入口 |

> **日常 CI 推荐流程**：`npm run e2e:smoke` → 通过后 `npm run e2e:v1-p0`
> **test/mock 基线上线前必跑**：`npm run e2e:v1-p0`（三链闭环 + RBAC + 看板对账）
> **real-pre 联调上线前必跑**：`npm run e2e:real-pre:p0`（统一入口，串行 preflight + 31~36）

### 手动 real-pre 单机受控部署

当前先按手动单机受控部署推进，Jenkins 只作为后续自动化接入项。服务器标准目录为 `/opt/saas/app`（代码）、`/opt/saas/env`（环境变量）、`/opt/saas/logs`（日志）、`/opt/saas/backups`（备份）和 `/opt/saas/runtime/qa/out`（证据）。详细步骤见 [docs/deploy/01-xshell-manual-deploy.md](docs/deploy/01-xshell-manual-deploy.md)。

- 前端：`http://127.0.0.1:3001`
- 后端：`http://127.0.0.1:8081`
- 后端健康检查：`http://127.0.0.1:8081/api/system/health`
- Compose：`docker-compose.real-pre.yml`
- Compose project：`saas-active`
- 环境文件：`/opt/saas/env/.env.real-pre`

手动受控部署顺序固定为：

```text
本地最终打包
→ 服务器初始化
→ 上传/拉取代码
→ 配置 real-pre 环境变量
→ mvn clean test
→ pnpm install --frozen-lockfile && pnpm build
→ mvn clean package -DskipTests
→ export REAL_PRE_ENV_FILE=/opt/saas/env/.env.real-pre
→ docker compose --env-file /opt/saas/env/.env.real-pre --project-name saas-active -f docker-compose.real-pre.yml config
→ docker compose --env-file /opt/saas/env/.env.real-pre --project-name saas-active -f docker-compose.real-pre.yml up -d postgres-real-pre redis-real-pre
→ scripts/backup-db.sh
→ scripts/run-real-pre-db-migrations.sh
→ docker compose --env-file /opt/saas/env/.env.real-pre --project-name saas-active -f docker-compose.real-pre.yml up -d backend-real-pre frontend-real-pre
→ /api/system/health 与 /login 端口验活
→ npm run e2e:real-pre:p0:preflight
→ npm run e2e:real-pre:p0
→ npm run e2e:real-pre:roles
→ 归档日志和报告
```

real-pre 与 test/mock 基线必须分开理解：

- `docker-compose.test.yml` / `.env.test` 用于 mock/test 回归。
- 服务器 real-pre 使用 `/opt/saas/env/.env.real-pre`，必须满足 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`、`DB_NAME=saas_real_pre`；受控部署默认保持 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false` 与 `ALLOW_REAL_PROMOTION_WRITE=false`，真实推广写操作只能在单独人工批准窗口同时开启。
- 服务器 real-pre 使用 Compose project `saas-active`，与当前 real-pre 启动脚本、容器名和 QA 默认 DB 容器一致，避免切到旧 project 后创建第二套数据卷。
- 端口验活使用 `/api/system/health`；`/api/actuator/**` 需要 JWT，不作为无鉴权探针。
- 部署命令不带 `-v`，不清空 real-pre 数据卷。
- preflight 或 P0 E2E 返回 `BLOCKED`、`PENDING`、`FAIL` 时不得宣称 P0 通过，不把证据不足当作通过。
- 部署报告必须归档 `/opt/saas/logs/**`、`runtime/qa/out/**`、`playwright-report/**`、`test-results/playwright/**` 和后端测试报告。

### 入口边界（强制约束）

- `20~24-v1-*` 只允许跑 test/mock 基线，不代表 real-pre 联调验收。
- `31~36-real-pre-*` 才是 real-pre P0 验收套件，必须在 real-pre 栈（前端 3001 / 后端 8081）运行。
- `npm run e2e:smoke` 的 `chromium` project 已通过 `testIgnore` 排除 08/09/10/11/12 与 20~24 与 31~36，普通烟测不会误触真实环境。
- `v1-p0` 与 `real-pre-p0` 两个 Playwright project 的 `testMatch` 完全互斥（`2[0-4]-v1-*` vs `3[1-6]-real-pre-*`）。

---

## 运行顺序

```bash
npm run build
npm run e2e:headed -- tests/e2e/00-health.spec.ts
npm run e2e:headed -- tests/e2e/01-login.spec.ts
npm run e2e:headed -- tests/e2e/02-dashboard.spec.ts
npm run e2e:headed -- tests/e2e/03-product.spec.ts tests/e2e/04-activity-product.spec.ts
npm run e2e:headed -- tests/e2e/05-promotion-link.spec.ts
npm run e2e:headed -- tests/e2e/06-orders-attribution.spec.ts
npm run e2e:headed -- tests/e2e/07-sample.spec.ts
npm run e2e:trace
```

### V1-P0 验收套件（上线前必跑）

```bash
# 一键跑全部 V1-P0 验收（渠道链 + 招商链 + 管理链 + RBAC + 业绩看板）
npm run e2e:v1-p0

# 有头浏览器调试
npm run e2e:headed -- --project=v1-p0

# 单独调试某条链路
npx playwright test --project=v1-p0 tests/e2e/20-v1-channel-chain.spec.ts
npx playwright test --project=v1-p0 tests/e2e/21-v1-recruiter-chain.spec.ts
npx playwright test --project=v1-p0 tests/e2e/22-v1-admin-config-chain.spec.ts
npx playwright test --project=v1-p0 tests/e2e/23-v1-rbac-scope.spec.ts
npx playwright test --project=v1-p0 tests/e2e/24-v1-performance-dashboard.spec.ts
```

**V1-P0 覆盖矩阵**

> V1-P0 只允许运行在 test/mock 基线。该套件内的 `/api/test/*` 仅用于 test/mock 数据编排，禁止把 V1-P0 指向 real-pre 或真实抖店 upstream。

| 文件 | 角色 | 覆盖内容 |
|------|------|----------|
| `20-v1-channel-chain` | channel_leader | 转链反馈、订单归因列、寄样申请、达人 CRM、403 负向 |
| `21-v1-recruiter-chain` | biz_leader | 活动商品工作台、只读商品库、寄样只看不审、403 负向 |
| `22-v1-admin-config-chain` | admin | 用户/角色/配置/操作日志/抖店联调、健康探针、归因 dryRun |
| `23-v1-rbac-scope` | 全角色 API | 5 类角色权限边界（~25 个 403/401 断言） |
| `24-v1-performance-dashboard` | admin + channel_leader | 指标卡、时间口径、API 对账、DataScope 差异 |


### Real-pre P0 统一验收（推荐）

```bash
# 只跑 real-pre P0 预检
npm run e2e:real-pre:p0:preflight

# 跑统一 real-pre P0 验收（preflight + 08 + 31~36）
npm run e2e:real-pre:p0

# 有头浏览器调试，可放慢动作和录全量 trace/video/screenshot
npm run e2e:real-pre:p0:headed
```

real-pre P0 套件结构：

| 文件 | 角色 | 覆盖内容 |
|------|------|---------|
| `08-real-pre-douyin-integration` | admin | 抖店一键刷新联调状态 + 后端探针 |
| `31-real-pre-product-chain` | admin | 商品链：Token/活动/活动商品/本地业务商品/详情/页面 smoke |
| `32-real-pre-order-attribution` | admin | 订单同步、归因字段、双轨金额、可复用 pick_source_mapping |
| `33-real-pre-sample-chain` | channel + biz + ops | 寄样状态机、7 天重复限制、物流单号、自动完成依赖真实成交 |
| `34-real-pre-performance-dashboard` | admin | dashboard/metrics、summary、performance 公式与页面 smoke |
| `35-real-pre-rbac-scope` | 6 类账号 | 登录、菜单、页面、API 越权、数据范围 |
| `36-real-pre-cleanup-plan` | n/a | 生成 PlanOnly cleanup 计划，禁止高危语句 |

`npm run e2e:real-pre:p0` 内部串行执行 preflight → 08 → 31 → 32 → 33 → 34 → 35 → 36，并把统一报告写到：

```
runtime/qa/out/real-pre-p0-YYYYMMDD-HHmmss/
├── summary.json
├── report.md
└── steps/
    ├── 01-preflight/...
    ├── 02-08-douyin-integration/...
    ├── 03-31-product-chain/...
    └── ...
```

real-pre P0 结论按以下规则归类，与 V1-P0 的 test/mock 验收口径完全分离：

- **服务器 real-pre 受控部署验证**：当前状态可用于真实业务样本验证，但不等于 real-pre P0 全量通过，也不等于正式生产放量。
- **PASS**：所有步骤通过，且 cleanup 计划已经过人工审核并执行且残留为 0。
- **PASS_NEEDS_CLEANUP**：业务步骤通过，但 cleanup-plan 仅生成、未执行；需要人工审核 `cleanup-plan.json/sql`。
- **BLOCKED**：缺真实 Token / 缺授权 / 活动权限受限 / 无可复用 `pick_source_mapping` / 关键角色无法登录等外部前置条件缺失。
- **PENDING**：环境与代码链路正常，但当前窗口缺真实订单 / 缺 `pick_source` 样本 / 缺成交触发寄样自动完成。
- **FAIL**：连接了非 real-pre 环境、`APP_TEST_ENABLED`/`DOUYIN_TEST_ENABLED` 不是 false、`/api/system/env` 不是 `REAL-PRE`、健康检查失败、页面运行时错误、权限越权、订单同步 failed > 0 无法解释、业绩公式错误、cleanup-plan 命中危险语句。

部署后报告只允许按以下结论归档：

| 结果 | 结论 |
| --- | --- |
| 无失败 + 无真实订单 | real-pre 环境部署成功，P0 仍因真实样本不足保持 PENDING |
| 无失败 + 有真实订单 + 归因 / 寄样 / 业绩通过 | real-pre P0 可升级为通过 |
| 出现失败 | 按失败项定级回滚或修复，不得放量 |

退出码：PASS / PASS_NEEDS_CLEANUP → `0`；BLOCKED / PENDING → `2`；FAIL → `1`。

**real-pre 安全规则**：

- 不真实删除（cleanup 仅 PlanOnly；执行需 `scripts/qa/cleanup-real-pre-journey.ps1 -Execute`）。
- 不重置数据库（连接库强制为 `saas_real_pre`，容器名拒绝含 prod/production/formal）。
- 不默认创建真实上游转链（只复用 `pick_source_mapping`，缺映射时直接 BLOCKED）。
- 不把 BLOCKED / PENDING 当 PASS（结论以 `summary.json` 的 `finalStatus` 为准）。

### Real-pre 抖店联调（真实 upstream / 3001/8081）

逻辑已并入 Playwright：`tests/e2e/08-real-pre-douyin-integration.spec.ts`。默认 **`npm run e2e` 会跳过**该文件（需 `E2E_REAL_PRE=true`）。

```bash
# 推荐：先做只读预检，再跑真实联调页面
npm run e2e:real-pre:preflight
npm run e2e:real-pre

# 一键跑 real-pre 预检 + 抖店联调 + 业务闭环 + 角色流 + 可视化旅程
npm run e2e:real-pre:all

# 或手动指定前后端
set E2E_REAL_PRE=true
set E2E_BASE_URL=http://localhost:3001
set E2E_BACKEND_URL=http://localhost:8081
npm run e2e -- --project=real-pre tests/e2e/08-real-pre-douyin-integration.spec.ts
```

兼容旧入口（同上，内部调用 Playwright）：

```bash
node runtime/qa/real-pre-douyin-frontend-e2e.cjs
```

### Real-pre 业务闭环与全角色可视化回归

```bash
# 业务闭环 + 全角色权限，一次性可视化顺序回归
npm run e2e:real-pre:visual

# 单剧本串行可视化全业务旅程
# 管理员 -> 招商组长 -> 招商 -> 渠道 -> 招商复审 -> 运营 -> 渠道组长 -> 管理员复核
npm run e2e:real-pre:journey:visual

# 仅业务闭环，可追加 headed / trace / video / screenshot 等 Playwright 参数
npm run e2e:real-pre:business -- --headed --workers=1 --trace on --video on --screenshot on --reporter=line,html

# 仅全角色权限，可追加同样参数
npm run e2e:real-pre:roles -- --headed --workers=1 --trace on --video on --screenshot on --reporter=line,html
```

- `e2e:real-pre:visual` 默认会开启 `headed`、`workers=1`、`trace/video/screenshot on`，并把证据写到 `runtime/qa/out/real-pre-visual-regression-时间戳/`
- `e2e:real-pre:*` 入口默认会先执行 real-pre 预检：frontend `3001`、backend `8081`、`/api/system/env=REAL-PRE`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、数据库 `saas_real_pre`、Token 可用、关键迁移表/字段存在、可复用推广映射存在、清理计划为 PlanOnly。
- real-pre 后台抖店授权入口位于 `/system/douyin` 的 Token 管理区；本地调试时抖店官方后台的 OAuth 授权回调地址填 `http://localhost:8081/api/douyin/oauth/callback`。若官方后台不接受 `localhost`，使用公网 HTTPS 测试域名并保持路径 `/api/douyin/oauth/callback`。
- 巨量百应官方授权管理页为 `https://buyin.jinritemai.com/dashboard/institution/power-manage`；前端「官方授权管理」按钮用于人工进入该页检查或开通授权主体能力，它不替代 `/oauth2/authorize` 的 code 回调换 Token 链路。
- OAuth 回调成功后默认进入 `/system/douyin?oauth=success`；如果当前浏览器没有 SaaS 登录态，前端路由守卫会跳到 `/login?redirect=...`，登录成功后自动回到抖店联调页并刷新 Token 状态。
- Webhook 消息回调与 OAuth 授权回调不是同一个地址；Webhook 仍使用 `/api/douyin/webhooks/colonel-open-events`。
- `e2e:real-pre:journey:visual` 还会检查六类账号登录与 `.env.real-pre` gitignore，再开启 `headed`、`workers=1`、`trace/video/screenshot on`，并把证据写到 `runtime/qa/out/real-pre-full-business-journey-时间戳/`
- `e2e:real-pre:business` 会把业务闭环证据写到 `runtime/qa/out/real-pre-business-e2e-时间戳/`
- `e2e:real-pre:roles` 会把角色业务流证据写到 `runtime/qa/out/real-pre-role-business-e2e-时间戳/`
- `e2e:real-pre:all` 会把统一报告写到 `runtime/qa/out/real-pre-all-时间戳/`；缺真实 Token、上游样本或可复用 `pick_source` 时输出 `BLOCKED/PENDING`，不能计为业务流 PASS。
- `10/11/12` 均采用生产安全上游模式：只复用 real-pre 已存在的 `pick_source_mapping` / `promotion_link`，若缺少可复用映射则阻塞并提示前置条件，不自动调用真实上游创建转链。
- `10/11/12` 的结论在清理执行前会标记为 `PASS_NEEDS_CLEANUP`；必须先导出清理计划，经人工审核后显式执行清理，并确认残留为 0，才能宣称本次 real-pre 业务 run 完成。
- 若想放慢浏览器动作，可先设置 `PW_SLOWMO_MS`

```powershell
$env:PW_SLOWMO_MS="300"
npm run e2e:real-pre:journey:visual
Remove-Item Env:\PW_SLOWMO_MS
```

### Real-pre 清理门槛

```powershell
# 只生成计划，默认不执行删除/恢复
.\scripts\qa\cleanup-real-pre-journey.ps1 `
  -RunId QA20260521_103000 `
  -StateFile runtime\qa\out\real-pre-full-business-journey-20260521-103000\journey-state.json

# 人工审核 cleanup-plan.json / cleanup-plan.sql 后，才允许显式执行
.\scripts\qa\cleanup-real-pre-journey.ps1 `
  -Execute `
  -RunId QA20260521_103000 `
  -StateFile runtime\qa\out\real-pre-full-business-journey-20260521-103000\journey-state.json
```

清理脚本会三重守卫：`/api/system/env` 必须是 `REAL-PRE` 且 test flags 为 false，连接库必须是 `saas_real_pre`，并拒绝疑似 prod/production 容器或非本机 API。清理范围只覆盖本次 `runId` 的本地 QA 数据与 `product_operation_state` 快照恢复；不清理真实订单、真实商品、真实活动、真实 Token 或真实上游不可回滚记录。

## 报告与回放

```bash
npm run e2e:report
npx playwright show-trace test-results/playwright/<case>/trace.zip
```

## 视觉基线

- 默认总是落地全页截图
- 默认不强制 snapshot diff，避免第一次运行因为缺基线直接失败
- 开启方式:

```bash
set E2E_VISUAL_ASSERT=true
npm run e2e:headed
```

- 更新基线:

```bash
npm run e2e:update-snapshots
```

## 设计约束

- 不做真实删除、真实批量审核、真实发货、真实批量同步
- real-pre 不新建真实上游转链；只复用已有 real-pre 映射/推广链接
- 权限校验只做页面进入与路由守卫验证
- 优先使用 `data-testid`，避免脆弱定位器

## 失败排查

失败后优先查看:

1. `playwright-report/index.html`
2. `test-results/playwright/**/trace.zip`
3. `test-results/playwright/**` 下失败截图和视频
