# Completion Gates

## 作用

本文件定义 Agent 执行任务时的完成门禁。任何任务完成前必须选择一个 Gate，并按 Gate 输出证据。

## Gate 选择规则

1. Agent 必须在任务开始时声明本次选择的 Gate。
2. 如果执行中发现影响范围扩大，必须升级 Gate，不能降级。
3. 如果任务同时命中多个 Gate，取最高级别 Gate。
4. Gate 4 > Gate 3 > Gate 2 > Gate 1 > Gate 0。

## 统一最终输出模板

每个任务结束时，Agent 必须按以下模板输出：

```text
## Final Status
DONE / PARTIAL / BLOCKED_BY_SAMPLE / BLOCKED_BY_EXTERNAL / FAILED

## Selected Gate
Gate X - xxx

## Scope
- 修改领域：
- 修改文件：
- 影响接口：
- 影响页面：

## Changes
- ...

## Verification
| 检查项 | 结果 | 证据 |
|---|---|---|
| Build | PASS/FAIL | ... |
| Container Reload | PASS/FAIL/SKIP | ... |
| Health | PASS/FAIL/SKIP | ... |
| API Smoke | PASS/FAIL/SKIP | ... |
| Business Flow | PASS/FAIL/PARTIAL/BLOCKED | ... |

## Evidence Paths
- harness/reports/xxx.md

## Not Done / Blockers
- 没有则写：None

## State Updates
- harness/rules/state/snapshots/01-当前项目状态.md: updated / not needed
- harness/rules/state/snapshots/DOMAIN_STATUS.md: updated / not needed
- harness/rules/changelog.md: updated / not needed

## Git
- branch:
- status:
- commit:
```

---

## Gate 概览

| Gate | 适用 | 关键验证 |
|---|---|---|
| Gate 0 | Docs Only | git diff 仅文档, safety-check |
| Gate 1 | Backend Change | 编译, 容器重启, health, API smoke |
| Gate 2 | Frontend Change | build, 页面可访问, 交互 |
| Gate 3 | Domain Change | 领域主流程, 权限, 上下游 |
| Gate 4 | E2E Business Flow | 三条主线全通 |

详细 Gate 定义见：[completion-gates-detail.md](completion-gates-detail.md)
详细 Git 子门禁见：[completion-gates-git.md](completion-gates-git.md)

---

## 强制规则

1. 没有 evidence path，不得 DONE。
2. 修改后端但未重启 / 确认容器加载，不得 DONE。
3. 修改前端但未 build / 页面验证，不得 DONE。
4. 涉及订单 / 寄样 / 业绩 / 看板但未跑下游闭环，不得 DONE。
5. 真实订单样本缺失时必须写 BLOCKED_BY_SAMPLE。
6. 只允许 docs-only 任务跳过容器重启。
7. 未更新 `harness/rules/state/snapshots/DOMAIN_STATUS.md` / `harness/rules/state/snapshots/01-当前项目状态.md` 就结束任务，不得 DONE。
8. 未生成 evidence report 就结束任务，不得 DONE。
