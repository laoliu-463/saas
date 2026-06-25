# Postmortem: agent-do 自动 commit 根因排查

## 时间
2026-06-21 21:44:27 Asia/Shanghai

## 现象
从我（Hermes）开始 commit `ac52b8ec` (19:52) 起，每 2-5 分钟就有 commit 落到 `feature/ddd/DDD-VERIFY-001` 分支，commit message 来自 harness evidence/retro 自动化（如 `docs: update harness folder limits to 50`）。

## 排查过程

### 1. PowerShell Scheduled Task
```
Get-ScheduledTask | Where-Object TaskName like *agent*
→ 仅 SpaceAgentTask（Windows 系统任务，无关）
```

### 2. Git Hooks
```
.git/hooks/pre-commit: 仅 code-review-graph update（无害）
无 post-commit / post-merge hook
```

### 3. Process 链路追踪
最终通过 `Get-CimInstance Win32_Process` 找到正在运行的进程：

```
PID 29244 Codex.exe --remote-debugging-port=...     ← 用户桌面 Codex 主进程
  └─ PID 23284 codex.exe (CLI worker)               ← 启动 21:54:02
       └─ PID 20176 pwsh.exe -Command "powershell ...agent-do.ps1 ..." ← 21:42:10
            └─ PID 34828 powershell.exe -File agent-do.ps1 -Env real-pre -Scope backend ...
                 -ContentMaintenance off -Message "refactor(user-domain): route talent query roles through policy"
```

## 根因

**OpenAI Codex CLI (26.616.5445) 在持续运行**一个自主循环任务：
- 启动时间 2026-06-20 21:54:02（昨天启动，今日仍在跑）
- CPU 累计 6506+10857+6802+4208 秒（数小时 runtime）
- 工作的就是 `feature/ddd/DDD-VERIFY-001` 分支
- 每个循环调用：
  ```
  agent-do.ps1 -Env real-pre -Scope backend
              -ContentMaintenance off
              -Message "refactor(user-domain): route talent query roles through policy"
  ```
  → agent-do → mvn compile (silent) → collect-evidence → git-push-safe
  → 自动 commit + push 整个 working tree（包含所有未跟踪 + 我刚 commit 的）

## Codex 工作 vs 我的工作
- 我（Hermes/M3）：在 `feature/ddd/DDD-VERIFY-001` 做 DDD 切片收口
- Codex (OpenAI)：在 `feature/ddd/DDD-VERIFY-001` 做 DDD 切片（user-domain / talent / performance / order）
- **冲突点**：同分支、同时改 working tree、双方 commit 都靠 git-push-safe
- Codex 不感知我的存在，我无法控制 Codex

## 当前状态（21:44）
- HEAD = `4ddd7551 refactor(user-domain): route talent query roles through policy`（Codex 21:43 commit）
- 领先 origin 1 commit
- 多个 Codex worktrees 共享此分支族（DDD-PRODUCT-004-copy-promotion-port, SPRINT-1-P0, DDD-PRODUCT-001, integration-safe-merge-20260618）

## 我的处理策略
1. **不杀 Codex**（无用户授权）
2. **不切 worktree**（Codex 会在 DDD-VERIFY-001 上持续 commit，跨 worktree 同步困难）
3. **错峰提交**：观察 Codex commit 节奏（每 2-5 分钟），在间隔期 commit
4. **同步推送**：每次 commit 后用 `git pull --rebase --autostash` 同步 Codex 的 commit
5. **不与 Codex 竞争切片**：今日剩余时间专注本地操作，不再开新 DDD issue

## 需要用户决策
请用户决定：
- A. **关闭 Codex**（最稳妥，独享分支）
- B. **保留 Codex 协作**（继续错峰，可能丢 commit 或出现 race condition）
- C. **切到独立 worktree**（如 `worktree/hermes-only` 新分支，避免冲突但需协调 merge）

## 给下一位 agent 的提示
如果 Codex 还在跑，请先 ps 检查 PID 链再决定 commit 策略。详见本文件。
