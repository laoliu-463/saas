# Remaining Issues Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current “no local-mock blocker” state into an ordered delivery track for real SDK联调、数据看板真实化、部署验证 and downstream V2.2 gaps.

**Architecture:** Keep the already-stable `3000/8080` and `3001/8081` baselines untouched, and drive the remaining work in three layers: first clarify and harden current readiness documents, then open the first real-Gateway integration slice, then expand into dashboard-real and deployment validation. Every step must preserve the existing mock/test/browser regression baseline.

**Tech Stack:** Spring Boot, Vue 3, PostgreSQL, Redis, Docker Compose, Playwright QA harness, project docs in `docs/*.md`

---

### Task 1: 固化剩余问题主线与现状

**Files:**
- Create: `docs/superpowers/plans/2026-05-04-remaining-issues-execution-plan.md`
- Modify: `docs/04-开发进度.md`
- Modify: `docs/09-真实SDK联调准备清单.md`
- Modify: `docs/06-部署与对接计划.md`

- [ ] **Step 1: 对齐当前剩余问题主线**

Write down the three highest-priority unfinished tracks:

```text
1. 真实 SDK 联调准备与首轮接入
2. M1.6 数据看板真实化
3. M1.7 部署验证
```

- [ ] **Step 2: 将“当前已完成 / 当前未完成”写回 `docs/04-开发进度.md`**

Add or update text so the document clearly states:

```text
- local-mock / test / real-pre browser regression is stable
- there is no new blocker in the current implemented mock baseline
- remaining work has shifted to real SDK, dashboard real data, and deployment verification
```

- [ ] **Step 3: 更新 `docs/09-真实SDK联调准备清单.md`**

Add a “current blocking facts” section with concrete statements:

```text
- 真实 access_token / refresh_token 仍未落地
- mock / test baseline cannot be broken by real gateway work
- first integration target should be AuthGateway -> Product/Activity -> Promotion -> Order
- current real-pre is still regression topology, not true SDK connected
```

- [ ] **Step 4: 更新 `docs/06-部署与对接计划.md`**

Add or update a short section that says deployment validation is still pending and should be done only after:

```text
- real SDK auth path is proven
- dashboard real data fields are aligned
- current browser regression remains green
```

- [ ] **Step 5: 验证文档更新完成**

Run:

```powershell
Get-Content docs/04-开发进度.md -TotalCount 80
Get-Content docs/09-真实SDK联调准备清单.md -TotalCount 120
Get-Content docs/06-部署与对接计划.md -TotalCount 120
```

Expected: all three docs show the same remaining-work priority and no contradictory wording.

### Task 2: 打开真实 SDK 首轮执行面

**Files:**
- Modify: `docs/09-真实SDK联调准备清单.md`
- Modify: `docs/04-开发进度.md`
- Optional Modify: `docs/archive/records/25-20260504-全系统可视化测试记录.md`

- [ ] **Step 1: 在 `docs/09` 中明确第一批 Gateway 顺序**

Write:

```text
Priority-1: AuthGateway
Priority-2: ActivityGateway / ProductGateway
Priority-3: PromotionGateway
Priority-4: OrderGateway
```

- [ ] **Step 2: 给每个 Gateway 加“开始条件 / 完成条件”**

Use short checklist items:

```text
- 开始条件：真实凭证、回调地址、环境变量、容器拓扑已确认
- 完成条件：接口成功、异常分支已记录、browser/mock baseline 未回退
```

- [ ] **Step 3: 将“下一步执行项”同步回 `docs/04`**

Write a short next-step list:

```text
1. 落真实 token
2. 打通 AuthGateway
3. 补齐真实商品 / 活动样本
4. 再开 Promotion / Order
```

- [ ] **Step 4: 校验文档衔接**

Run:

```powershell
Get-ChildItem docs -Recurse -Include *.md | Select-String -Pattern '真实 SDK|AuthGateway|M1.6|M1.7' | Select-Object -First 60 Path,LineNumber,Line
```

Expected: the docs reference the same ordering.

### Task 3: 预留数据看板真实化与部署验证入口

**Files:**
- Modify: `docs/04-开发进度.md`
- Modify: `docs/06-部署与对接计划.md`
- Modify: `docs/10-V2.2场景覆盖矩阵.md`

- [ ] **Step 1: 明确 M1.6 的最小目标**

Add wording:

```text
M1.6 最小目标 = 指标接口口径对齐、真实订单字段映射、Dashboard / Data 页面稳定展示真实统计
```

- [ ] **Step 2: 明确 M1.7 的最小目标**

Add wording:

```text
M1.7 最小目标 = 标准容器拓扑可一键拉起、健康检查通过、关键浏览器回归可复跑
```

- [ ] **Step 3: 更新 `docs/10` 的状态边界**

Make sure the matrix still says:

```text
- current coverage is mock/test/regression coverage
- true real-SDK production-like validation is still pending
```

- [ ] **Step 4: 验证无口径冲突**

Run:

```powershell
Get-Content docs/10-V2.2场景覆盖矩阵.md -TotalCount 160
Get-Content docs/06-部署与对接计划.md -TotalCount 160
```

Expected: no doc claims real SDK is already fully connected.

### Task 4: 回归与收口

**Files:**
- Modify: `docs/04-开发进度.md`
- Modify: `docs/archive/records/25-20260504-全系统可视化测试记录.md`

- [ ] **Step 1: 回归达人经营台关键脚本**

Run:

```powershell
$env:QA_FRONTEND='http://localhost:3000'
$env:QA_BACKEND='http://localhost:8080'
$env:QA_HEADLESS='false'
$env:QA_SLOW_MO='1200'
node runtime/qa/talent-platform-smoke.cjs
node runtime/qa/talent-multiclaim-smoke.cjs
```

Expected: both reports are green.

- [ ] **Step 2: 记录最新报告路径**

Persist the final report directories into docs if they changed.

- [ ] **Step 3: 整理剩余问题优先级结论**

Write final order:

```text
P0: 真实 SDK 联调
P0: 数据看板真实化
P0: 部署验证
P1: 独家 / 提成 / 规则中心 / 导出
```

