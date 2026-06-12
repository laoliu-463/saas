# Docs-only Evidence Template

> 仅用于 docs-only / harness-only 任务（仅修改 `harness/` `docs/` `AGENTS.md` `CLAUDE.md` 报告 / 状态文件）。
> 不修改后端 / 前端 / SQL / Docker / env / 数据库。
> 完整 11 节模板见 `harness/feedback/evidence-report-template.md`。

```md
# Evidence Report — Docs-only

## 1. 基本信息

- 任务 ID：
- 任务标题：
- 任务类型：docs-only
- 选择 Gate：Gate 0
- 涉及领域：harness / docs（业务域保持不变）
- 涉及子系统：Instruction / Tool / State / Feedback / Environment（按本轮实际勾选）
- 执行环境：real-pre（docs Scope 不构建不重启）
- 执行人：AI Agent
- 开始 / 结束时间：

## 2. Git 信息

- 当前分支：
- 当前 HEAD（commit hash + 短说明）：
- 起始 / 结束 dirty 数（modified / untracked / staged）：
- 是否使用 `git add .` / `-A` / `<dir>/`：❌

## 3. 修改范围

- 列出本轮新增 / 修改 / 删除的 harness 与 docs 文件（按路径）。
- 业务代码、SQL、Docker、env 是否变更：❌

## 4. 验证

| 检查项 | 命令 | 结果 | 证据 |
| --- | --- | --- | --- |
| Safety check | `safety-check.ps1 -Env real-pre -Scope docs -DryRun` | PASS / FAIL | 输出 |
| Verify local | `verify-local.ps1 -Scope docs` | PASS / FAIL | 输出 |
| Diff check | `git diff --check` | PASS / FAIL | 输出 |
| Staged check | `git diff --cached --check` | PASS / FAIL / N/A | 输出 |

## 5. 状态更新

- `harness/CURRENT_STATE.md`：updated / not needed
- `harness/state/DOMAIN_STATUS.md`：updated / not needed
- `harness/HARNESS_CHANGELOG.md`：updated
- `harness/state/HARNESS_DEBT.md`：updated / not needed
- `harness/state/KNOWN_ISSUES.md`：updated / not needed

## 6. 报告

- 旧内容候选 / 归档 / 删除：retire-content Plan（可选）
- evidence：本文件
- retro：`harness/reports/retro-YYYYMMDD-HHMMSS-<task>.md`

## 7. 远端部署

- 是否涉及远端：❌
- 若用户明确要求：见 `harness/runbooks/remote-deploy.md`

## 8. 结论

- Final Status：DONE / PARTIAL / BLOCKED
- 是否符合 docs-only：✅
- 仓库是否可交接：✅ / ❌

## 9. 剩余风险

- None
- 或：列出本轮未做 / 后续任务

## 10. 提交与推送

- commit hash：
- push gitee / origin：✅ / ❌（gitee + origin 都必须）
- Git Exit Gate 终态：DONE_CLEAN / DONE_WITH_REGISTERED_DIRTY / PARTIAL_DIRTY_REMAINING / BLOCKED_DIRTY_UNKNOWN
```

## 关联

- `harness/feedback/evidence-report-template.md`（11 节完整版）
- `harness/feedback/retro-summary-template.md`
- `harness/COMPLETION_GATES.md`（Gate 0）
- `harness/SESSION_EXIT_GATE.md`
