# Runbook: real-pre change

## 适用场景

real-pre 代码、配置、联调或验收相关变更。

## 修改前检查

1. 读取 `AGENTS.md`、`harness/rules/state/snapshots/01-当前项目状态.md`、`docs/10-部署运行总览.md`、`docs/验收/real-pre联调手册.md`。
2. 执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre
```

3. 确认不清库、不 mock、不删除 volume。

## 构建

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -Message "说明本次 real-pre 修改"
```

若只改后端或前端，将 `-Scope` 改为 `backend` 或 `frontend`。

## 重启容器

由 `agent-do.ps1` 调用 `restart-compose.ps1`，不得手写 `down -v`。

## 健康检查

由 `verify-local.ps1` 检查：

- 后端：`http://127.0.0.1:8081/api/system/health`
- 前端：`http://127.0.0.1:3001/healthz` 或 `/login`

## SQL/API 验证

按任务对应 skill 执行：

- 订单归因：`harness/rules/skills/ddd/order-attribution.skill.md`
- 寄样自动完成：`harness/rules/skills/ddd/sample-lifecycle.skill.md`
- 商品库：`harness/rules/skills/ddd/product-library.skill.md`

## Git 提交

由 `git-push-safe.ps1` 执行敏感文件检查、提交和推送。

## 证据报告

检查 `harness/reports/evidence-*.md`，未采集项必须保留“未采集 / 阻塞原因”。

## Retro summary

执行或确认 retro 已生成。若本次暴露 Harness 问题，更新对应 Harness 文件并追加 `harness/rules/changelog.md`。

## 失败回滚

1. 不清库。
2. 不删除 volume。
3. 用 Git revert 或切回上一个 commit。
4. 重新执行 `restart-compose.ps1` 和 `verify-local.ps1`。
5. 生成新的 evidence report，标记 `FAIL` 或 `PARTIAL`。
