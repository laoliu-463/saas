# Session Exit Gate

本文件定义每次 Agent 会话结束前必须执行的清洁状态检查。
无论本次任务是否 DONE，Agent 都必须留下可交接、可复现、可继续执行的仓库状态。

## 核心原则

会话完成必须同时满足：
1. **Task Gate 通过**：本次任务按对应 Completion Gate 验证完成。
2. **Clean State Gate 通过**：仓库状态干净，下一个 Agent 可以直接接手。

---

## Clean State 六项硬门禁

### 1. Build Clean
- docs-only：确认未修改 Java/Vue/SQL/Docker
- 后端/前端/全栈任务：对应 build 必须通过

### 2. Test Clean
- 跑本次任务相关测试
- 影响核心链路时跑 smoke/E2E
- 无法执行须写明原因并标 PARTIAL/BLOCKED

### 3. Progress Recorded
必须更新：`docs/harness-maintenance/legacy-rules/state/snapshots/01-当前项目状态.md`、`docs/harness-maintenance/legacy-rules/state/snapshots/DOMAIN_STATUS.md`、`docs/harness-maintenance/legacy-rules/changelog.md`
每次记录：做了什么、改了哪些文件、验证了什么、下一步

### 4. Artifacts Clean
- 删除临时 debug、SQL、脚本、日志
- 不留 TODO / console.log / debugger
- 证据文件移到 `runtime/qa/out/`

### 5. Startup Path Clean
- Docker Compose 配置未被破坏
- health check 可执行
- 新 Agent 能按文档启动

### 6. Git 状态 Clean
- `git status --short` 输出已分类
- 所有 dirty 归入十种分类之一
- 当前任务 commit 已推送
- 不能留 unknown dirty

终态：`DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN`

---

## 最终状态规则

| 状态 | 含义 |
|---|---|
| DONE | Completion Gate + Session Exit Gate 全通过 |
| PARTIAL | 代码完成但测试未完整 / 编译通过但容器未验证 |
| BLOCKED_BY_SAMPLE | 真实订单/pick_source样本缺失 |
| BLOCKED_BY_EXTERNAL | 抖音API/Token/服务器/网络阻塞 |
| FAILED | build/test/health/核心链路失败 |

---

## 退出检查模板

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

## Evidence Paths
- ...

## Remaining Risks
- ...

## Next Recommended Task
- ...
```

---

## 禁止事项

1. 没有执行 Session Exit Gate 不得 DONE
2. 没有 evidence/report 路径不得 DONE
3. 修改代码后未验证 build 不得 DONE
4. 未更新 CURRENT_STATE/DOMAIN_STATUS 不得 DONE
5. 工作区存在 unknown dirty 不得 DONE
6. 当前任务 commit 未推送不得 DONE

**一句话：Agent 只有在"任务跑通 + 仓库干净 + 状态可交接"三者同时满足时，才允许说 DONE。**
