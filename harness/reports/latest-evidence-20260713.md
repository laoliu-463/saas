# Evidence Report

## Metadata

- Time: 2026-07-13 15:40 +08:00
- Environment: real-pre
- Scope: full (商品编辑前端抽屉与后端保存契约)
- Branch: codex/ddd-user-role-application
- Commit: 84fa7622
- Worktree: dirty; unrelated QuickSampleModal、Harness 报告清理和 JVM 日志变更保留，未暂存
- Remote deploy: not requested, not executed

## Changes

- 右侧商品编辑抽屉保留：专属价金额（元，用户输入）、专属价说明、是否支持投流、奖励说明、参与要求、开始时间、结束时间；开始/结束时间为只读商品快照事实；移除手卡。
- 新增 `PUT /api/products/{relationId}` 商品编辑保存入口，只允许更新上述可编辑补充字段中的五项：专属价金额、专属价说明、投流开关、奖励说明、参与要求。
- `exclusivePriceAmount` 后端按非负金额、两位小数归一化，写入 `audit_payload`；旧 `exclusivePrice` 布尔字段仍兼容读取；详情摘要和专属价筛选同步支持金额字段。
- 审核请求 DTO 同步支持专属价金额，避免审核保存链路丢字段。

## Verification

| Check | Result | Evidence |
|---|---|---|
| Frontend component test | PASS | `ProductEditModal.test.ts`: 3/3 |
| Frontend typecheck | PASS | `npm run typecheck` |
| Frontend build | PASS | `npm run build` |
| Backend targeted tests | PASS | 55/55: ProductController 26、AuditRequest 1、ProductServiceFilter 28 |
| Backend package | PASS | `mvn -DskipTests package` |
| Docker restart | PASS | `restart-compose.ps1 -Env real-pre -Scope backend`；backend 镜像重建并重启 |
| Local health | PASS | `verify-local.ps1 -Env real-pre -Scope full`；backend 200/UP、frontend `/healthz` 200；compose backend/frontend healthy |
| Harness limits | PASS | `check-harness-limits.ps1` |
| real-pre preflight | FAIL/BLOCKED_AUTH | `runtime/qa/out/real-pre-preflight-20260713-153535/report.md`；admin 登录 HTTP 401，token 不可用；数据库 schema readiness PASS |
| Product edit API/E2E | BLOCKED | 无管理员 token，未执行认证后的商品保存业务流 |
| Git commit/push | PASS | `84fa7622` 已推送到当前分支上游 |

## Conclusion

PARTIAL。商品编辑前后端代码、定向测试、构建、容器重启和本地健康检查均通过；真实 real-pre 认证业务验证因配置管理员登录持续 HTTP 401 阻塞，不能声明已完成真实页面/API 闭环验收。

## Residual risks

- 需要有效的 real-pre 管理员凭据后，补跑商品编辑抽屉保存、刷新回显和权限边界验证。
- 未执行远端部署；本轮仅验证本地 real-pre。
- 当前工作区仍有与本任务无关的 QuickSampleModal、Harness 历史报告和 JVM 日志变更，未纳入本次提交。
