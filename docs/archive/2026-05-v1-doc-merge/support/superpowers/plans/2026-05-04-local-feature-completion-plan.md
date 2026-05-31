> 本文档已归档，仅作为历史参考；当前口径以 docs/ 下主文档为准。

# Local Feature Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在暂停 SDK 联调的前提下，优先把本地 Mock 环境中仍处于“部分覆盖”的规则中心能力补成真实可用闭环，并完成可见浏览器慢速验收。

**Architecture:** 复用现有 `/system/config` 后台与 `system_config` 表，不新增复杂模块，只补一个统一规则读取服务，把达人保护期、寄样限制、寄样超时关闭、独家达人阈值等本地规则从硬编码改为配置驱动。前台沿用现有系统配置页，业务侧通过原有达人/寄样链路验证配置生效。

**Tech Stack:** Spring Boot, MyBatis-Plus, Vue 3, Naive UI, Playwright/browser QA, local-mock seed data

---

### Task 1: 落盘本地补全范围

**Files:**
- Create: `D:\Projects\SAAS\docs\superpowers\plans\2026-05-04-local-feature-completion-plan.md`
- Modify: `D:\Projects\SAAS\docs\04-开发进度.md`
- Modify: `D:\Projects\SAAS\docs\10-V2.2场景覆盖矩阵.md`

- [ ] Step 1: 明确当前只做本地功能补全，不进入 SDK 联调
- [ ] Step 2: 记录本轮优先级为“系统配置驱动业务规则生效”
- [ ] Step 3: 实现后同步进度文档与场景覆盖矩阵

### Task 2: 统一读取业务规则

**Files:**
- Create: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\BusinessRuleConfigService.java`
- Modify: `D:\Projects\SAAS\backend\src\main\resources\db\init-db.sql`

- [ ] Step 1: 新增统一规则读取服务，封装 int / boolean / decimal / json 配置读取和默认值兜底
- [ ] Step 2: 为寄样超时关闭规则补齐种子配置键
- [ ] Step 3: 保持兼容已有 `system_config` 数据，不引入破坏性迁移

### Task 3: 接入寄样与达人主链路

**Files:**
- Modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\controller\SampleController.java`
- Modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\SampleLifecycleService.java`
- Modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\job\SampleLifecycleJob.java`
- Modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\TalentService.java`

- [ ] Step 1: 将寄样 7 天限制改为读取 `sample.restrict_enabled` 与 `sample.restrict_days`
- [ ] Step 2: 将达人保护期改为读取 `talent.protection_days`
- [ ] Step 3: 将寄样自动关闭天数改为读取配置，不再写死 30 / 15
- [ ] Step 4: 将达人独家判断阈值改为读取配置，保证前台判断口径与规则中心一致

### Task 4: 回归测试与可见浏览器验收

**Files:**
- Modify: `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\controller\SampleControllerTest.java`
- Modify: `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\service\TalentServiceTest.java`
- Modify: `D:\Projects\SAAS\backend\src\test\java\com\colonel\saas\service\SampleLifecycleServiceTest.java`
- Modify: `D:\Projects\SAAS\runtime\qa\full-browser-e2e.cjs` (only if needed)
- Create: `D:\Projects\SAAS\docs\archive\records\26-20260504-本地规则中心验收记录.md`

- [ ] Step 1: 为新规则读取补充单元测试
- [ ] Step 2: 跑定向后端测试，确认不破坏现有主链路
- [ ] Step 3: 用可见浏览器慢速执行“管理员改规则 -> 业务页面生效”验收
- [ ] Step 4: 将结果写入验收记录与主文档

### Task 5: 操作日志中心与导出口径收口

**Files:**
- Create: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\entity\OperationLog.java`
- Create: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\mapper\OperationLogMapper.java`
- Create: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\OperationLogService.java`
- Create: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\controller\OperationLogController.java`
- Create: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\security\OperationLogInterceptor.java`
- Modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\config\WebConfig.java`
- Modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\controller\DataController.java`
- Modify: `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\SampleLifecycleService.java`
- Create: `D:\Projects\SAAS\frontend\src\views\system\OperationLogList.vue`
- Modify: `D:\Projects\SAAS\frontend\src\router\index.ts`
- Modify: `D:\Projects\SAAS\frontend\src\views\layout\Sider.vue`

- [x] Step 1: 新增管理员统一操作日志中心，默认查询最近 90 天关键变更日志
- [x] Step 2: 通过拦截器统一沉淀 POST / PUT / PATCH / DELETE 关键操作
- [x] Step 3: 修复订单导出仅限管理员 / 组长，并补齐后端数据范围过滤
- [x] Step 4: 修复寄样自动关闭原因文案随规则配置动态变化

