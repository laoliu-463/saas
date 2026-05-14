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

### Real-pre 抖店联调（真实 upstream / docker 8081）

逻辑已并入 Playwright：`tests/e2e/08-real-pre-douyin-integration.spec.ts`。默认 **`npm run e2e` 会跳过**该文件（需 `E2E_REAL_PRE=true`）。

```bash
# 推荐：脚本自动设置默认 3001 / 8081 并开启标记
npm run e2e:real-pre

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
- 转链仅做单商品最小验证
- 权限校验只做页面进入与路由守卫验证
- 优先使用 `data-testid`，避免脆弱定位器

## 失败排查

失败后优先查看:

1. `playwright-report/index.html`
2. `test-results/playwright/**/trace.zip`
3. `test-results/playwright/**` 下失败截图和视频
