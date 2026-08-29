# Playwright E2E

## 入口

普通浏览器用例按这条链运行：

```text
playwright.config.ts
  → auth.setup.ts 生成角色 storageState
  → fixtures.ts 提供 LoginPage / AppShellPage
  → 业务 spec 只验证业务行为
```

登录相关代码只有两个职责：

- `helpers/auth.ts`：API 登录、角色状态文件、real-pre 认证注入；
- `pages/login.page.ts`：浏览器登录页面操作。

页面跳转和应用启动等待统一使用 `helpers/page-ready.ts` 的 `gotoApp`。业务用例不直接拼 `localStorage`，不使用固定 sleep 等页面加载。

## 常用命令

```powershell
# 先确认测试文件和项目都能被发现
npx playwright test --list

# test 环境完整 P0
npm run e2e:v1-p0

# real-pre 只走专用入口
npm run e2e:real-pre:p0
npm run e2e:real-pre:roles
```

`real-pre` 必须使用真实上游和真实账号。Token、活动、订单或 `pick_source_mapping` 前置条件缺失时，只能记录 `BLOCKED` / `PENDING`，不能用 mock 数据冒充通过。

## 编写规则

- 登录：复用 `LoginPage` 或 `helpers/auth.ts`。
- 角色：复用 `helpers/test-data.ts` 的 `storageStates`，不要另写账号和密码。
- 跳转：使用 `gotoApp`；点击后用 URL、响应或稳定 `data-testid` 等待结果。
- 断言：优先 `data-testid`、语义角色和可见业务状态；不要依赖 CSS 层级。
- 固定等待只允许出现在视觉演示 helper 中，业务用例必须等待可观察结果。
- UI 通过不等于业务闭环通过；关键链路要补 API / SQL 证据。
