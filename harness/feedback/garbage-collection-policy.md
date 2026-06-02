# Garbage Collection Policy

## 目标

防止临时报告、重复文档、过期方案和无入口文件持续堆积，同时避免误删业务事实、证据和部署安全资产。

## 保留什么

- 当前事实主源：`AGENTS.md`、`CLAUDE.md`、`docs/README.md`、`docs/00-*.md` 到 `docs/11-*.md`、`docs/领域/`、`docs/流程/`、`docs/对接/`、`docs/验收/`、`docs/决策/`。
- Harness 主源：`harness/README.md`、`AGENT_CONTRACT.md`、`CURRENT_STATE.md`、`TASK_ROUTING.md`、`FORBIDDEN_SCOPE.md`、五子系统目录。
- 最近和关键 evidence / retro 报告。
- 能证明上线、回滚、真实联调或业务闭环的证据。

## 归档什么

- 已被当前主源替代但仍有参考价值的旧方案。
- 历史审计、历史 runbook、过期计划。
- 不确定是否仍有价值的文档，先归档，不直接删除。
- 归档统一走 manifest，目标为 `harness/archive/retired-content/<timestamp>/` 或既有 `docs/archive/**`。

## 删除什么

- 明显临时且无证据价值的根目录 `*.log`、`snap*.txt`、`args.json`、`env.json`、`health.json`。
- 已归档且确认无引用的空文件。
- 过期构建产物或报告目录：`tmp/`、`out/`、`test-results/`、`playwright-report/`，删除前必须确认当前任务不依赖。

## 合并什么

- 同一事实的重复入口，保留当前主源，旧入口改成索引或归档。
- 重复的环境说明，合并到 `harness/environment/*.md` 和 `docs/10-部署运行总览.md`。
- 重复的验收说明，合并到 `docs/09-测试验收总览.md`、`docs/验收/*.md` 和 `harness/evals/*.md`。

## 禁止删除什么

- `.env*` 真实文件、密钥、证书、私钥。
- `.git/`、Docker Compose 文件、数据库 migration、源码、脚本。
- 业务需求、ADR、上线证据、回滚证据。
- 未经确认的历史报告。

## 删除前检查

1. 路径必须在仓库内。
2. 不属于受保护路径。
3. `rg` 确认没有当前主源引用。
4. 对目录删除，manifest 必须写 `allowRecursive=true`。
5. 先执行 dry-run 或 `retire-content.ps1 -Action Plan`。
6. evidence report 写明移动/删除/保留结果。

## 默认命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan
```

归档和删除必须显式提供 manifest，不能靠口头描述执行。
