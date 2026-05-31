> 本文档已归档，仅作为历史参考；当前口径以 docs/ 下主文档为准。

# Full System Visible QA Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `3000/8080` 基线下，以可见浏览器方式完成当前项目已实现模块的全量回归，并沉淀截图与汇报材料。

**Architecture:** 复用现有 `runtime/qa` 浏览器脚本，统一补齐“可见浏览器 + 可配置端口 + 慢速操作”能力，再按“全链路回归 -> 交互补充 -> 达人专项”三批执行。所有结果统一落入 `runtime/qa/out/`，最终汇总为飞书可粘贴报告。

**Tech Stack:** Playwright、Node.js、现有 `runtime/qa/*.cjs`、`3000/8080` 本地前后端、截图归档

---

### Task 1: 固化测试批次与覆盖范围

**Files:**
- Create: `docs/superpowers/plans/2026-05-04-full-system-visible-qa.md`
- Read: `docs/04-开发进度.md`
- Read: `docs/10-V2.2场景覆盖矩阵.md`
- Read: `frontend/src/router/index.ts`

- [ ] **Step 1: 以当前代码事实定义三批测试**

批次口径：

1. `full-browser-e2e.cjs`
   - 登录鉴权
   - 首页/路由覆盖
   - 调试台 reset/seed
   - 订单/看板/寄样/权限边界/异常回归
2. `local-mock-supplement.cjs`
   - 商品详情抽屉
   - 寄样申请
   - 角色 CRUD
   - 用户 CRUD / 重置密码
3. `talent-platform-smoke.cjs` + `talent-multiclaim-smoke.cjs`
   - 团队公海 / 私海
   - 保护期释放
   - 黑名单
   - 多人认领

- [ ] **Step 2: 记录当前“全系统”边界**

本轮“全系统”仅指当前已实现并已在文档中进入验收范围的模块：

- 登录 / 鉴权
- Dashboard / Orders / Data
- 选品库 / 商品库 / 活动商品
- 达人经营台
- 寄样台
- 运营中心（独家状态 / 物流发货）
- 系统管理（用户 / 角色 / 配置）
- 调试台

未进入当前主线的模块（如客户端找达人、合作管理、推广效果）不纳入本轮“通过/失败”口径，但可在报告中单列“未进入本阶段”。

### Task 2: 让现有 QA 脚本支持可见浏览器

**Files:**
- Modify: `runtime/qa/full-browser-e2e.cjs`
- Modify: `runtime/qa/local-mock-supplement.cjs`
- Modify: `runtime/qa/talent-platform-smoke.cjs`
- Modify: `runtime/qa/talent-multiclaim-smoke.cjs`

- [ ] **Step 1: 给脚本补环境变量**

统一支持：

- `QA_FRONTEND`
- `QA_BACKEND`
- `QA_HEADLESS`
- `QA_SLOW_MO`

- [ ] **Step 2: 统一可见模式规则**

统一行为：

- `QA_HEADLESS=false` 时使用可见浏览器
- `QA_SLOW_MO=300~800` 时减慢动作便于人工观察
- 保留原有 headless 默认值，避免破坏现有自动化基线

- [ ] **Step 3: 保留现有输出目录结构**

所有脚本仍输出：

- `report.md`
- `report.json`
- `screenshots/*.png`

### Task 3: 准备执行环境并验证入口

**Files:**
- Read: `docs/06-部署与对接计划.md`

- [ ] **Step 1: 验证本地入口**

命令：

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:3000
Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/actuator/health
```

预期：

- 前端返回 `200`
- 后端返回 `{"status":"UP"...}`

- [ ] **Step 2: 确认必须容器健康**

命令：

```powershell
docker inspect -f "{{.Name}}|{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}" saas-test-postgres-1 saas-test-redis-1 saas-test-backend-1
```

预期：

- 三者均为 `running`
- PostgreSQL / Redis 为 `healthy`

### Task 4: 执行三批可视化测试

**Files:**
- Execute: `runtime/qa/full-browser-e2e.cjs`
- Execute: `runtime/qa/local-mock-supplement.cjs`
- Execute: `runtime/qa/talent-platform-smoke.cjs`
- Execute: `runtime/qa/talent-multiclaim-smoke.cjs`

- [ ] **Step 1: 跑全链路批次**

```powershell
$env:QA_FRONTEND='http://localhost:3000'
$env:QA_BACKEND='http://localhost:8080'
$env:QA_HEADLESS='false'
$env:QA_SLOW_MO='500'
node runtime/qa/full-browser-e2e.cjs
```

- [ ] **Step 2: 跑交互补充批次**

```powershell
$env:QA_FRONTEND='http://localhost:3000'
$env:QA_BACKEND='http://localhost:8080'
$env:QA_HEADLESS='false'
$env:QA_SLOW_MO='500'
node runtime/qa/local-mock-supplement.cjs
```

- [ ] **Step 3: 跑达人专项批次**

```powershell
$env:QA_FRONTEND='http://localhost:3000'
$env:QA_BACKEND='http://localhost:8080'
$env:QA_HEADLESS='false'
$env:QA_SLOW_MO='500'
node runtime/qa/talent-platform-smoke.cjs
node runtime/qa/talent-multiclaim-smoke.cjs
```

### Task 5: 汇总结果并形成飞书汇报

**Files:**
- Create: `runtime/qa/out/<timestamp>/report.md`
- Create: `runtime/qa/out/<timestamp>/report.json`
- Modify: `docs/04-开发进度.md`（如结论有变化）

- [ ] **Step 1: 汇总每批结果**

记录：

- 模块名
- 测试点
- Pass / Fail
- 截图路径
- Bug 描述

- [ ] **Step 2: 输出飞书可粘贴结构**

最终输出必须包含：

- 模块
- 执行步骤
- 截图标记
- 测试结果表
- 问题清单

- [ ] **Step 3: 完成前复核**

复核项：

- 前后端入口可访问
- 可见浏览器确实执行过
- 每批均有截图
- 成功/失败结论均有 fresh evidence

