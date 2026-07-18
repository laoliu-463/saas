# Harness 跨平台验证核心设计 Evidence

## 元数据

- 生成时间：2026-07-18 09:30:09 +08:00
- 环境：real-pre
- Scope：docs
- 分支：codex/harness-node-verify-phase1
- 已验证源码 Commit：05f922db5bbe81389fe956f42cd346c8bae298e3
- 证据身份：COMMIT
- 远端部署：未执行
- 关联设计：`docs/方案/PLAN-006-Harness跨平台验证核心重构设计.md`
- 关联决策：`docs/决策/ADR-014-Harness跨平台核心目录与渐进迁移.md`

## 结论

PARTIAL（部分完成）。设计文档、ADR、自审、现有 Harness 测试和任务门禁通过；业务构建、容器重启、健康检查和业务验证按 docs Scope 未执行。固定入口的 Git 收尾暴露两个既有缺陷，已使用受控回退完成设计提交，不得将本报告解释为业务代码或运行环境验收通过。

## 关键结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 设计逐段确认 | PASS | 架构、流程、中文交互、证据、兼容和验收均获用户确认 |
| 文档行数 | PASS | PLAN 169 行、ADR 74 行、README 94 行，均低于 200 行硬上限 |
| 占位符与歧义自审 | PASS | 无未决 TODO；Node 基线明确为 20，Java 基线为 17 |
| Pester 基线 | PASS | 25 tests，0 failed，0 skipped |
| real-pre docs 安全检查 | PASS | 安全开关与敏感文件存在性检查通过；未输出密钥值 |
| Harness 任务门禁 | PASS | TASK_GATE=PASS |
| 仓库整体健康度 | PARTIAL | reports 根 23 个文件、reports/current 54 个文件，为既有数量债务 |
| Git 隔离 | PASS | 使用独立 worktree 和 `codex/harness-node-verify-phase1` 分支；原工作区其他脏文件未暂存 |
| 设计提交与推送 | PASS | `05f922db` 已推送到 `origin/codex/harness-node-verify-phase1` |

## 未执行项

- 后端构建与测试：未执行，原因是 Scope=docs。
- 前端类型检查、测试与构建：未执行，原因是 Scope=docs。
- Docker 重建与重启：未执行，原因是 Scope=docs。
- HTTP 健康检查：未执行，原因是 Scope=docs。
- 业务 E2E：未执行，原因是本轮只固化设计。
- 远端部署：未执行，用户未授权且不属于第一阶段设计交付。

## 固定入口异常与回退

1. 新分支没有 upstream 时，`git-push-safe.ps1` 的 upstream 探测在 `ErrorActionPreference=Stop` 下被 Git stderr 提前中断，未进入预期的 `push --set-upstream` 分支。
2. docs Scope 的 `collect-evidence.ps1` 仍采集 `docker ps`；表格尾随空格使 `git diff --cached --check` 失败，并把无关运行时状态写入文档 evidence。
3. 回退操作仅对隔离分支执行：手动设置 origin upstream，并改为生成不含 Docker 表格的稳定中文摘要。未修改现有 Harness 脚本。

## Retro

- 责任人：后续 Harness Node 核心实施任务。
- 改进动作一：为无 upstream 新分支补兼容测试，并修正 Git 探测的错误流。
- 改进动作二：docs Scope 默认跳过 Docker 运行时采集，对所有外部命令日志统一清理行尾空白。
- 验证方式：Pester 覆盖新分支首次推送；报告生命周期测试覆盖 docs Scope 无 Docker 输出和 `git diff --check` 通过。

## 剩余风险

- 当前仅批准设计，Node Harness 尚未实现。
- 仓库整体报告数量债务尚未清零，不能写成仓库健康度 PASS。
- evidence 报告提交会产生独立报告 Commit；`已验证源码 Commit` 指向设计内容提交，不声称报告文件自引用当前 Commit。
