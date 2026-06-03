# Session Exit Gate

本文件定义每次 Agent 会话结束前必须执行的清洁状态检查。
无论本次任务是否 DONE，Agent 都必须留下可交接、可复现、可继续执行的仓库状态。

---

## 一、核心原则

任务完成不等于会话完成。

会话完成必须同时满足：

1. **Task Gate 通过**：本次任务按对应 Completion Gate 验证完成。
2. **Clean State Gate 通过**：仓库状态干净，下一个 Agent 可以直接接手。

只要 Clean State Gate 未通过，最终状态不得写 DONE。

---

## 二、Clean State 五项硬门禁

### 1. Build Clean

要求：

- docs-only 任务：确认未修改 Java / Vue / SQL / Docker。
- 后端任务：后端 build 通过。
- 前端任务：前端 build 通过。
- 全栈任务：前后端 build 都通过。

不得：

- 编译失败仍声明完成。
- 跳过 build 但不说明原因。
- 用"理论上能过"代替实际命令。

---

### 2. Test Clean

要求：

- 跑本次任务相关测试。
- 如影响核心链路，跑 smoke / E2E。
- 如测试无法执行，必须写明原因并标记 PARTIAL / BLOCKED。

不得：

- 只测 happy path。
- 只跑单测不跑业务闭环。
- 把 SKIP / WARN 当 PASS。

---

### 3. Progress Recorded

要求更新：

- `harness/CURRENT_STATE.md`
- `harness/state/DOMAIN_STATUS.md`
- `harness/HARNESS_CHANGELOG.md`
- 如有架构决策，更新 `harness/state/DECISIONS.md`
- 如有证据，生成 `harness/reports/*.md`

每次任务结束必须记录：

- 做了什么
- 改了哪些文件
- 验证了什么
- 哪些没验证
- 下一步应该做什么
- 当前最终状态：DONE / PARTIAL / BLOCKED_BY_SAMPLE / BLOCKED_BY_EXTERNAL / FAILED

---

### 4. Artifacts Clean

要求：

- 删除临时 debug 文件。
- 删除临时 SQL、临时脚本、临时日志。
- 不留下无解释的 TODO、console.log、debugger、printStackTrace。
- 不留下 `.tmp`、`.bak`、`debug-*`、`test-output-*` 等无归档文件。
- 必须保留的证据文件要移动到 `harness/reports/` 或 `runtime/qa/out/`。

允许保留：

- 正式报告
- QA 输出
- Evidence
- Retro
- 计划文档
- 可复用脚本

---

### 5. Startup Path Clean

要求：

- 标准初始化路径仍可用。
- Docker Compose 配置未被破坏。
- health check 可执行。
- README / AGENTS / CLAUDE 指向的启动方式仍正确。
- 新 Agent 能按文档启动，不需要猜测。

如修改了启动、部署、环境变量、端口、容器名，必须同步更新：

- `AGENTS.md`
- `CLAUDE.md`
- `docs/`
- `harness/environment/`
- `harness/CURRENT_STATE.md`

---

### 6. Git 状态 Clean（Git Exit Gate 强约束）

按 `harness/skills/git-change-control.md` 第 9 节执行：

必须满足：

- `git status --short` 输出已分类。
- 所有 dirty 文件必须归入十种分类之一（`current_task / previous_partial / docs_state / report_only / frontend / backend / sql_migration / docker_deploy / cleanup_retire / unknown`）。
- 所有 staged 必须为空或已提交并推送。
- 所有 untracked 必须归属（report_only / cleanup_retire / docs_state / 显式当前任务）。
- 不能留下 unknown dirty。
- 当前任务 commit 已推送到目标 remote（`gitee` + `origin`）。
- 状态文件（`CURRENT_STATE.md` / `DOMAIN_STATUS.md` / `HARNESS_CHANGELOG.md`）已更新或明确不更新。

终态判定：

| 终态 | 含义 | 允许后续 |
| --- | --- | --- |
| `DONE_CLEAN` | 工作区干净，commit + push 完成 | 进入下一任务 |
| `DONE_WITH_REGISTERED_DIRTY` | dirty 已分类并登记到下一任务 | 下一任务 Git Intake Gate 必须确认继承 |
| `PARTIAL_DIRTY_REMAINING` | 存在未收口 dirty | 禁止进入无关新任务；只能继续当前任务或收口 |
| `BLOCKED_DIRTY_UNKNOWN` | 存在 unknown dirty | 必须先调查；禁止输出 DONE |

**关键约束**：如果存在 dirty，最终状态不能是 `DONE`，只能是 `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一。

如果存在 unknown dirty，禁止输出 `DONE` 任何变种，必须输出 `BLOCKED_DIRTY_UNKNOWN` 并在 `harness/reports/unknown-dirty-investigation-*.md` 调查。

---

## 三、最终状态规则

### DONE

必须同时满足：

- Completion Gate 通过
- Session Exit Gate 五项全部通过
- Evidence 已落盘
- State 已更新
- Git 状态已说明

### PARTIAL

适用：

- 代码完成，但测试未完整跑完
- 编译通过，但容器未重启验证
- 单点验证通过，但业务闭环未跑
- 状态文档部分更新

### BLOCKED_BY_SAMPLE

适用：

- 真实订单样本缺失
- pick_source 样本缺失
- 第三方真实数据未产生

### BLOCKED_BY_EXTERNAL

适用：

- 抖音 API 不可用
- Token / 授权异常
- 服务器 / 网络 / 安全组阻塞
- 外部系统未返回必要数据

### FAILED

适用：

- build failed
- test failed
- health failed
- 核心业务链路失败
- 数据对账不一致

---

## 四、退出检查模板

每次 Agent 最终回复前必须生成：

```md
# Session Exit Report

## Final Status
DONE / PARTIAL / BLOCKED_BY_SAMPLE / BLOCKED_BY_EXTERNAL / FAILED

## Selected Completion Gate
Gate X - xxx

## Clean State Gate

| 检查项 | 结果 | 证据 |
|---|---|---|
| Build Clean | PASS/FAIL/SKIP | |
| Test Clean | PASS/FAIL/SKIP | |
| Progress Recorded | PASS/FAIL | |
| Artifacts Clean | PASS/FAIL | |
| Startup Path Clean | PASS/FAIL/SKIP | |
| Git State Clean | PASS/FAIL/BLOCKED | |

## Changed Files
- ...

## Evidence Paths
- ...

## State Updates
- CURRENT_STATE.md:
- DOMAIN_STATUS.md:
- HARNESS_CHANGELOG.md:
- DECISIONS.md:

## Remaining Risks
- ...

## Next Recommended Task
- ...

## Git Status
（git status --short 输出 + Git Exit Gate 终态：DONE_CLEAN / DONE_WITH_REGISTERED_DIRTY / PARTIAL_DIRTY_REMAINING / BLOCKED_DIRTY_UNKNOWN）
```

---

## 五、禁止事项

以下情况不得声明 DONE：

1. 没有执行 Session Exit Gate。
2. 没有 evidence / report 路径。
3. 修改代码后未验证 build。
4. 修改后端后未确认容器加载。
5. 修改前端后未确认页面可用。
6. 修改业务规则后未验证上下游。
7. 未更新 CURRENT_STATE / DOMAIN_STATUS。
8. 留下临时 debug 文件。
9. 留下无解释 TODO。
10. 下一个 Agent 无法根据文档继续工作。
11. 工作区存在 unknown dirty。
12. 工作区存在未分类 dirty。
13. 业务代码 commit 混入 docs / 报告 / 状态文件。
14. 当前任务 commit 未推送到目标 remote。
15. 状态文件未更新。

---

## 与 Completion Gate 的关系

| 维度 | Completion Gate | Session Exit Gate |
|---|---|---|
| 证明什么 | 本次任务有没有做成 | 仓库是否处于可交接状态 |
| 关注点 | 业务验证、证据、状态 | 构建、测试、清洁度、启动路径 |
| 执行顺序 | 先执行 | 后执行 |
| DONE 要求 | 必须通过 | 必须通过 |
| 任一未通过 | 不得 DONE | 不得 DONE |

一句话：**Agent 只有在"任务跑通 + 仓库干净 + 状态可交接"三者同时满足时，才允许说 DONE。**
