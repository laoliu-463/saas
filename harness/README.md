# Harness

Harness 是机器执行区。普通开发者只需要记住五步：

```text
从 main 建短分支 → 修改代码 → harness verify → 提交 PR → CI 通过后合并
```

## 日常入口

```powershell
git switch main
git pull
git switch -c fix/example

powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify

git push -u origin fix/example
```

也可以继续使用兼容入口：`.\harness.cmd inspect` 和 `.\harness.cmd verify`。

Harness 会按变更范围自动选择后端、前端、数据库、文档或完整检查，并自动生成本次运行的证据。普通开发者不需要手工选择几十个参数。

三条红线：

- 不直接推送 `main`。
- 不把直接 SSH 部署作为日常发布方式。
- 不提交 `.env`、Token、密码、私钥或真实环境配置。

## 按场景找入口

| 需要做什么 | 看哪里 |
| --- | --- |
| 确认边界、安全和完成标准 | [`policy/`](policy/) |
| 查管理员或值班操作 | [`runbooks/`](runbooks/) |
| 查验收场景和变更影响 | [`checks/`](checks/) |
| 运行机器检查 | [`scripts/run.ps1`](scripts/run.ps1) |
| 复用证据、发布或事故模板 | [`templates/`](templates/) |
| 查历史规则、任务和工程配置 | [`../docs/harness-maintenance/`](../docs/harness-maintenance/) |

## 证据与运行产物

运行输出只写入 `runtime/qa/out/`：

- `runtime/qa/out/<run-id>/`：单次运行的原始输出；
- `runtime/qa/out/latest-<report-key>.json`：最新机器证据；
- `runtime/qa/out/latest-<report-key>.md`：最新人类摘要。

这些文件被 Git 忽略，由本地运行或 CI artifact 保存；不会成为仓库里的第二套规则。

## 高级维护

Jenkins、数据库迁移、远端部署、回滚和 Break-glass 只由维护者按 [`runbooks/`](runbooks/) 执行。复杂的实现细节位于 [`scripts/`](scripts/)，不作为日常开发流程阅读材料。
