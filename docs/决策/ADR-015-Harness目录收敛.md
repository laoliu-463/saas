# ADR-015：Harness 目录收敛为入口、内容和执行区

日期：2026-07-21
状态：实施中，待 PR CI 验证

## 决策

Harness 只保留五个一级目录：

```text
harness/
├─ README.md
├─ policy/
├─ runbooks/
├─ checks/
├─ scripts/
└─ templates/
```

`README.md` 是唯一导航；`policy` 管边界，`runbooks` 管操作，`checks` 管验收，`scripts` 管机器执行，`templates` 只保留真正复用的证据、发布和事故模板。

## 迁移关系

| 原位置 | 新位置或处理 | 原因 |
| --- | --- | --- |
| `harness/README.md` + `INDEX.md` | `harness/README.md` | 去掉双入口 |
| `harness/rules` | `harness/policy`、`harness/runbooks`、`harness/checks`；其余历史资料移至 `docs/harness-maintenance/legacy-rules` | 只保留当前主源 |
| `harness/probes` | `harness/checks/scenarios` | 明确验收职责 |
| `harness/rules/test-impact-map.json` | `harness/checks/impact-map.json` | 与验收映射同处 |
| `harness/src`、`contracts`、`tests` | `harness/scripts/lib`、`harness/scripts/tests` | 机器实现和自测集中到执行区 |
| `harness/engineering`、`tasks`、旧模板 | `docs/harness-maintenance/` | 不作为日常入口 |
| `harness/reports`、`archive`、`manifests` | 删除仓库副本；新运行产物写入 `runtime/qa/out/` | 不把一次性输出变成长期规则 |

## 运行合同

公开执行入口为 `harness/scripts/run.ps1`；根目录 `harness.ps1` 和 `harness.cmd` 只作为兼容转发。Evidence 的稳定摘要和原始输出都位于 `runtime/qa/out/`，CI 负责保存 artifact。

普通开发者只需要：建短分支、修改代码、执行 `harness verify`、提交 PR、等待 CI 通过。数据库、real-pre、部署、回滚和第三方接口的高风险要求由 Harness、Jenkins 和维护者 runbook 自动或按权限执行。

## 验证与恢复

- 删除前已确认报告、归档和清单目录是一次性产物，历史提交仍可追溯。
- Node 核心类型检查和自测必须通过；PowerShell Harness 测试必须覆盖目录门禁、evidence 生命周期和内容退役行为。
- 若 PR 失败，恢复目标是该 PR 的提交，不恢复旧目录为第二套入口；先修复新主源或回滚本次目录迁移。
