---
name: e2e-test-design
description: 设计 test/mock 与 real-pre 的浏览器、API 和 SQL 补证方案，明确角色、数据前置、证据和结论边界。
---

# E2E 测试设计

## 触发场景

- 用户要求新增、拆分或审计浏览器 E2E、API E2E、角色权限或真实业务闭环测试。
- 用户要求把某个验收项映射到脚本、角色、数据和 evidence。

## 输入

- 环境：`test`/mock 或 `real-pre`/live；目标业务链、角色、前置数据和预期结果。
- 页面/API/SQL 字段、已有脚本、fixtures、截图/Network 证据和清理策略。

## 必读依据

- `docs/09-测试验收总览.md`。
- `docs/验收/E2E浏览器测试手册.md`、`docs/验收/real-pre联调手册.md`、`docs/验收/V1-P0验收清单.md`。
- 对应领域合同、权限文档和当前 `package.json` 脚本。

## 步骤

1. 先写验收目标和失败条件，再选择脚本；一条测试只验证一个清晰业务切片。
2. 明确角色、登录方式、数据来源、唯一标识、状态迁移、API/SQL 补证和证据目录。
3. `test` 可使用 mock/fixture，入口为 `npm run e2e:v1-p0`；`real-pre` 必须使用真实 Token、真实上游和真实样本，入口为 `npm run e2e:real-pre:p0`，角色补证使用 `npm run e2e:real-pre:roles`。
4. real-pre 前先跑 `npm run e2e:real-pre:p0:preflight`；缺 Token、样本或权限时停止业务流并保留 `BLOCKED`/`PENDING` 证据。
5. 测试完成后检查 `summary.json`、`report.md`、Playwright report、Network、SQL 和日志，不能只看浏览器截图或退出码。

## 输出

输出测试设计表：场景、环境、角色、前置数据、步骤、断言、补证、证据路径、清理方式和状态。

## 验证

- real-pre 状态只允许 `PASS`、`PASS_NEEDS_CLEANUP`、`BLOCKED`、`PENDING`、`FAIL`。
- `PASS` 要求真实链路和必需证据完整；无真实订单、归因、寄样、业绩或 Dashboard 对账时保持 `PENDING`。
- 新增或修改测试后按项目 Definition of Done 验证；只设计方案时明确未执行测试。
