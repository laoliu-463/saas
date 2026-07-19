# ADR-015：real-pre 单通道 CD 与不可变发布

- 状态：已接受
- 日期：2026-07-18
- 决策范围：代码合并、镜像构建、数据库迁移、real-pre 部署、回滚和发布证据

## 背景

多个 Codex 任务可同时开发，但旧流程允许各任务直接 SSH、在 `/opt/saas/app` 拉取不同分支、现场构建并部署。由此可能出现：

- 旧任务晚完成后覆盖新版本；
- backend/frontend 镜像 tag 可变，报告无法证明运行内容；
- 共享工作树、Compose 和 env 被不同任务交替修改；
- 数据库迁移、部署和远端 E2E 并发；
- 服务器运行 SHA、镜像与 evidence 不一致。

当前系统是模块化单体 + Docker Compose + 单个 real-pre，不需要用 Kubernetes 才能解决顺序和身份问题。

## 决策

采用“并行开发、串行合并、串行发布”的单通道 CD：

1. 每个任务在独立 worktree / 分支开发、测试和推送。
2. PR 必需 CI 通过，GitHub Merge Queue 串行合并。
3. 只有 `release/real-pre` 可触发发布镜像构建。
4. CI 构建一次并推送镜像，tag 使用 40 位完整 SHA，记录 OCI revision 与 digest。
5. Jenkins 是唯一 real-pre 发布控制器，同时使用 Job 串行队列和跨 Job 全局锁。
6. Jenkins 只拉取 CI 镜像，不在服务器构建应用或镜像。
7. 发布清单和 Compose 写入 `/opt/saas/releases/<SHA>/`，服务器固定 env 独立只读管理。
8. 当前版本只有在运行版本一致性验证全部通过后更新。
9. 非后继提交必须拒绝；回滚仍进入 Jenkins 队列并要求 `ROLLBACK_APPROVED=true`。
10. 数据库迁移由当前部署 SHA 与目标 SHA 的迁移路径差异触发；无迁移差异的发布禁止执行数据库写操作。

## 版本一致性契约

发布成功必须同时证明：

- Jenkins 目标 SHA；
- 后端运行 SHA 与后端镜像 digest；
- 前端 `/version.json` SHA；
- 前后端镜像 tag、OCI revision 与发布清单 digest；
- 当 `databaseMigration.required=true` 时，数据库 aggregate migration 与 Flyway 版本。

数据库/Flyway 字段始终留证，但只有本次需要迁移时，`UNAVAILABLE`、`NOT_MANAGED`、缺字段或不一致才阻断。纯 Harness、文档或普通应用发布不因未启用 Flyway而被迫迁移。

首次发布默认需要迁移；正常后继发布仅在 `backend/src/main/resources/db/` 或迁移执行脚本变化时执行；经批准回滚遵循 forward-only，不逆向运行旧迁移。

## 权限边界

普通 Codex 任务允许：

- 独立开发、测试、推送；
- 提交 PR/候选 SHA；
- 生成本地验证与候选 evidence。

普通 Codex 任务禁止：

- SSH 修改服务器；
- 调用部署或回滚脚本；
- 修改 `/opt/saas/app` 或服务器 `.env.real-pre`；
- 部署未合并分支；
- 使用短 SHA、`latest` 或可变发布 tag。

## 被替代方案

- 禁止 Agent 并行开发：降低吞吐，不能解决镜像与运行身份问题。
- 依靠任务自行协调 SSH：没有全局原子队列，旧任务仍可覆盖。
- 服务器 `git pull` 后现场构建：产物不可变性和来源证明不足。
- 立即迁移 Kubernetes：当前规模下增加运维复杂度，不能替代合并与发布治理。

## 渐进实施

1. 已关闭 `agent-do -DeployRemote` 与旧 SSH/手工部署/回滚入口。
2. 已增加 Merge Queue CI 事件、完整 SHA 镜像工作流和 Jenkins 全局锁。
3. 已增加不可变发布清单、防降级、digest/revision 与运行版本门禁。
4. 已接入 Flyway 依赖和真实 PostgreSQL 集成测试；服务器启用前仍需迁移演练。
5. 后续在 GitHub Ruleset 配置 `release/real-pre` Merge Queue 和必需检查，在 Jenkins/仓库配置凭证与变量。

## 影响

- 优点：并行开发吞吐保留，发布顺序和内容可证明，旧任务无法静默降级。
- 代价：发布依赖 CI/Jenkins/镜像仓库可用；首次切换需配置变量、凭证和 Flyway。
- 失败策略：门禁不完整时停止发布，不回退到手工 SSH；状态写 FAIL/BLOCKED。
