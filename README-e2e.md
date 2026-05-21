# Playwright E2E 联调说明

## 目录

- 配置: `playwright.config.ts`
- 用例: `tests/e2e`
- 环境样例: `.env.e2e.example`
- 报告: `playwright-report`
- 失败产物: `test-results/playwright`

## 安装

```bash
npm install
npx playwright install
```

## 环境

1. 复制 `.env.e2e.example` 为 `.env.e2e`
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
| **V1-P0 验收层** | `npm run e2e:v1-p0` | `20~24` | 上线前必跑，三链闭环 + 权限 + 看板 |
| **全旅程层** | `npm run e2e:journey` | `09-full-user-journey` | 多角色完整链路手动验证 |
| **real-pre 专项** | `npm run e2e:real-pre` 等 | `08/10/11/12` | 真实环境联调，需 Token |

> **日常 CI 推荐流程**：`npm run e2e:smoke` → 通过后 `npm run e2e:v1-p0`
> **上线前必跑**：`npm run e2e:v1-p0`（三链闭环 + RBAC + 看板对账）

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


### Real-pre 抖店联调（真实 upstream / 单活 3000/8080）

逻辑已并入 Playwright：`tests/e2e/08-real-pre-douyin-integration.spec.ts`。默认 **`npm run e2e` 会跳过**该文件（需 `E2E_REAL_PRE=true`）。

```bash
# 推荐：脚本自动设置默认 3000 / 8080 并开启标记
npm run e2e:real-pre

# 或手动指定前后端
set E2E_REAL_PRE=true
set E2E_BASE_URL=http://localhost:3000
set E2E_BACKEND_URL=http://localhost:8080
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
- `e2e:real-pre:journey:visual` 默认会先检查 frontend `3000`、backend `8080`、real-pre env guard、六类账号登录与 `.env.real-pre` gitignore，再开启 `headed`、`workers=1`、`trace/video/screenshot on`，并把证据写到 `runtime/qa/out/real-pre-full-business-journey-时间戳/`
- `e2e:real-pre:business` 会把业务闭环证据写到 `runtime/qa/out/real-pre-business-e2e-时间戳/`
- `e2e:real-pre:roles` 会把角色业务流证据写到 `runtime/qa/out/real-pre-role-business-e2e-时间戳/`
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
