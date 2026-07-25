# Jenkins 发布失败修复证据

- 时间：2026-07-25（Asia/Shanghai）
- 环境：real-pre 发布链路；代码验证在本地 Windows 工作区完成
- 分支：`codex/fix-real-pre-p0-33-claim-20260725`
- 修复提交：`d7a9baac`、`7e0b6f79`
- 工作区：生成本报告前已通过静态检查；报告加入后需提交并推送

## 已修复

1. P0-33 采样链路不再重复调用认领接口。`POST /api/talents` 已自动认领创建人，测试再次调用 `/claims` 会得到正确的 `462 DUPLICATE`；测试现改为校验创建响应中的 `ownerId`。
2. Jenkins 在数据库备份 / 迁移前写入 `deployment-started`，迁移失败也会进入锁内回滚路径。
3. Jenkins 失败兜底证据不再固定写 `Production touched: NO`，改为依据状态文件记录生产触碰、回滚 / 发布状态和调度状态。

## 验证

- `git diff --check`：PASS
- `check-harness-limits.ps1 -BaselineRef HEAD`：TASK_GATE=PASS；REPOSITORY_HEALTH=PARTIAL（历史 `harness/reports` 根目录债务）
- GitHub PR #285：后台、前端、治理检查在上一轮提交均 PASS；最新提交触发的新 CI 仍 QUEUED，尚未完成 CI Gate。
- 本地 Playwright：BLOCKED，工作区未安装 `@playwright/test`，未把本地 E2E 写成 PASS。
- Jenkins #64：真实部署后 P0-33 因重复认领失败，随后锁内回滚；服务器最终健康，说明回滚链路已实际执行。

## 未完成 / 风险

- 修复尚未进入 `main` 或 `release/real-pre`，尚未重跑 Jenkins。
- 需要 CI Gate 完成后合并 PR #285，再提升到 `release/real-pre` 做一次真实验证。
- 订单归因真实样本仍是 `PENDING`：当前真实订单未形成可证明的归因闭环，不能用测试数据替代。
- 生产磁盘在上次发布后约 99% 使用率；下一次发布前必须先执行只读空间检查，空间不足应在备份前拒绝发布。

## Retro

本次失败暴露了两个流程问题：E2E 没有按接口副作用校验测试前置条件，Jenkins 状态标记晚于数据库迁移。后续新增发布门禁时，应同时覆盖“迁移失败”和“失败证据真实反映状态”，并坚持先读取真实归属 / 状态再断言。

结论：`PARTIAL`。
