# Harness - 团长 SaaS 工程化协作系统

Harness 是抖音团长 SaaS 项目的统一任务执行系统，负责固定入口、脚本、技能、评估、运行手册和报告。

## 如何开始一个任务

1. 读取必读文档：[TASK_ROUTING.md](TASK_ROUTING.md) 确定任务 Scope 和必读清单
2. 执行 Git Intake Gate：[skills/git-change-control.md](skills/git-change-control.md)
3. 选择 Completion Gate：[COMPLETION_GATES.md](COMPLETION_GATES.md)
4. 执行任务入口脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "说明"
```

## 如何结束一个任务

1. 生成 evidence report
2. 更新状态文件（CURRENT_STATE / DOMAIN_STATUS / HARNESS_CHANGELOG）
3. 执行 Session Exit Gate：[SESSION_EXIT_GATE.md](SESSION_EXIT_GATE.md)
4. Git commit + push

## 如何查找当前有效文档

- 总索引：[INDEX.md](INDEX.md)
- 核心规则：`harness/*.md`
- 技能文件：`harness/skills/`
- 领域指令：`harness/instructions/`
- 状态文件：`harness/state/`
- 当前报告：`harness/reports/current/`

## 如何归档过期内容

1. 确认文件无当前引用
2. 生成 GC Manifest 到 `harness/manifests/gc/`
3. 移动到 `harness/reports/archive/YYYYMMDD/` 或 `harness/archive/retired-content/`
4. 更新引用
5. 详见 [core/04-doc-style-guide.md](core/04-doc-style-guide.md)

## 五大子系统

| 子系统 | 目录 | 说明 |
|---|---|---|
| Instructions | instructions/, AGENT_CONTRACT | 执行规范 |
| Tools | commands/, tools/ | 脚本工具 |
| Environment | environment/ | 环境配置 |
| State | state/, CURRENT_STATE | 状态追踪 |
| Feedback | feedback/, evals/, reports/ | 反馈与评估 |

## 文档规范

- 所有当前文档不超过 200 行
- 每个文档只解决一个问题
- 详见 [core/04-doc-style-guide.md](core/04-doc-style-guide.md)
