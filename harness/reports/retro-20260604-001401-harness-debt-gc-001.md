# HARNESS-DEBT-GC-001 — Retro Summary

> **任务**：HARNESS-DEBT-GC-001 — harness 安全清理、归档、瘦身
> **时间**：2026-06-04 00:14 +08:00
> **类型**：retro（任务后复盘）
> **状态**：DONE（docs-only / harness-cleanup）

---

## 1. 本轮清理收益

### 1.1 工作区清理

- 删除 1 个 Windows 设备文件残留（`nul`，0 bytes）。
- 删除 1 个 playwright 测试结果目录（`test-results/playwright/`）。
- 删除 1 个 playwright HTML 报告目录（`playwright-report/`，含 528 KB `index.html`）。
- 释放磁盘空间：约 528 KB（playwright HTML 报告为主）。
- 工作区状态：清理前 0 dirty，清理后 5 个 untracked（全部为本任务新生成的 manifest / 报告）。

### 1.2 DEBT 收敛

- **DEBT-013**（12 个 ad-hoc log 未 .gitignore）：deferred → **fixed**
  - 实际盘点：只有 1 个 `nul` 设备文件 + 2 个 playwright 生成物目录，共 3 项；全部已 `.gitignore` 覆盖；本任务全部已删除。
- **DEBT-014**（reports/ 72 份未触发归档）：deferred → **wontfix**
  - 实际盘点：76 份 reports 全部被 evidence/retro/git-batch/state 文件交叉引用（最少 1 处、最多 9 处）。归档会破坏证据链引用，不可执行。
  - 重新分类理由：reports 目录在 GC 政策下受保护，不需要轮转。"未触发归档"不是债务，是 GC 政策正确执行的结果。

### 1.3 状态同步

- `harness/CURRENT_STATE.md`：追加 HARNESS-DEBT-GC-001 完成段，路径指针清晰。
- `harness/HARNESS_CHANGELOG.md`：新增 v0.6.1 条目，含完整变更清单。
- `harness/state/HARNESS_DEBT.md`：DEBT 状态明确更新（013 fixed / 014 wontfix）。
- `harness/QUALITY_LEDGER.md`：Harness 等级 A- → A（垃圾清理与归档已明确口径）。

---

## 2. 发现的问题

### 2.1 `retire-content.ps1` 不支持 Windows 设备名

- **问题**：`Test-Path -LiteralPath 'D:\Projects\SAAS\nul'` 返回 `False`，`Resolve-Path` 也无法解析 — PowerShell 把 `nul` 视为保留设备名。
- **影响**：retire-content.ps1 走 manifest 路径无法删除 `nul`；必须单独用 git bash `rm -f nul` 绕过。
- **解决**：本任务已绕过（git bash `rm`）。**建议**在 HARNESS-AGENT-DO-HARDEN 中加 fallback：检测 Windows 设备名（`nul` / `con` / `prn` / `aux` / `com1`-`com9` / `lpt1`-`lpt9`），用 `[System.IO.File]::Delete('\\?\D:\...')` 强制删除；或转 git bash `rm`。

### 2.2 reports 目录引用链是 1:N 模式

- **问题**：每份 evidence 报告都会显式引用 5-10 份前序报告；导致 76 份 reports 全部被"硬链接"。
- **影响**：删除 / 归档任何 reports 都会导致其他报告中"X 报告路径"链接 404。
- **结论**：当前设计是**有意为之**（证据链 = 可审计），不是 debt。但需要明确告知 Agent：reports 目录受保护。
- **建议**：在 `garbage-collection-policy.md` 中显式增加一句："reports/*.md 之间的交叉引用是 evidence chain；不允许删除或归档被其他 report 引用的文件。"

### 2.3 "12 个 ad-hoc log" 假设与实际不符

- **问题**：DEBT-013 描述"12 个 ad-hoc log 未 .gitignore 排除"，但实际盘点只有 1 个 `nul` + 2 个 playwright 目录。
- **可能原因**：DEBT-013 是在 HARNESS-DEBT-GOVERNANCE-ITERATION 创建时凭印象登记，未做实际盘点。
- **修正**：DEBT-013 关闭（fixed），同时记录实际清理范围。
- **建议**：所有 DEBT 创建前必须先盘点实际数量，**禁止凭印象登记**。

### 2.4 content-retire 报告本身也是 evidence 链的一部分

- **发现**：7 份 content-retire Plan 报告被 1-9 份其他报告交叉引用。
- **结论**：content-retire 报告在 retire-content.ps1 生成后成为 evidence；不应在 GC 中自动删除。
- **建议**：可考虑在 retire-content.ps1 中加 `--skip-content-retire` 选项，或在 GC policy 中明确 content-retire 报告也受保护（与 evidence/retro 同等）。

---

## 3. 是否需要更新 garbage-collection-policy

**需要**：在 `harness/feedback/garbage-collection-policy.md` 中显式增加以下约束：

1. **Windows 设备名**：`nul` / `con` / `prn` / `aux` / `com1`-`com9` / `lpt1`-`lpt9` 是 Windows 保留设备名；PowerShell `Test-Path` / `Remove-Item` 无法解析。git bash `rm -f` 可绕过；建议在 retire-content.ps1 中加设备名 fallback。
2. **reports 引用链**：reports/*.md 之间的交叉引用是 evidence chain；不允许删除或归档被其他 report 引用的文件。归档前必须 `grep -l` 验证。
3. **content-retire 报告**：由 retire-content.ps1 生成的 reports/content-retire-*.md 报告本身也是 evidence 链一部分；与 evidence/retro 同等受保护。
4. **deferred → wontfix 决策路径**：当盘点发现某 DEBT 描述与实际不符，或 DEBT 描述的对象不存在 / 不可执行时，应重新分类为 wontfix 而不是继续保持 deferred。

**建议** 在 HARNESS-AGENT-DO-HARDEN 任务中实施（不是本任务范围）。

---

## 4. 是否需要后续自动轮转策略

**当前**：76 份 reports，861 KB — 完全可承受。**不需要立即轮转**。

**未来触发条件**（如达到则启动轮转）：

- reports 总数 > 200 份，或
- reports 目录总大小 > 5 MB，或
- 出现 6 个月前的 evidence 报告（无审计必要性）。

**轮转策略建议**（未来任务设计）：

- 保留期：reports 保留 6 个月（180 天）。
- 过期后归档到 `harness/archive/retired-content/<batch>/reports/`，manifest 记录。
- 关键 evidence（如涉及 release tag、远端部署、订单归因）永久保留，不轮转。
- 关键 evidence 由"是否被 KNOWN_ISSUES.md / CURRENT_STATE.md / HARNESS_CHANGELOG.md 显式引用"判定。

**实施** 不在本任务范围（仅记录建议）。

---

## 5. 下一步建议

### 5.1 推荐下一步

**HARNESS-AGENT-DO-HARDEN**（已在 CURRENT_STATE.md 下一步列表中）：

- 扩展 `retire-content.ps1`：增加 Windows 设备名 fallback、设备名检测。
- 扩展 `agent-do.ps1`：增加 `-Scope harness` 选项（DEBT-020）。
- 扩展 `safety-check.ps1`：增加 `harness` / `code` scope（DEBT-022）。
- 扩展 `garbage-collection-policy.md`：增加本 retro 第 3 节列出的 4 条约束。
- 提升 Harness quality：A 已达，下一步向 A+ 推进需要 scripts / commands 全面硬化。

### 5.2 备选下一步

- **HARNESS-ENV-CHEATSHEET-V2**：扩展 `environment/CHEATSHEET.md`，加入端口冲突排查、容器名清单、env 变量矩阵（不在优先列表）。
- **DDD 领域优化任务**：用户域 U-3 CurrentUser / PermissionContext 统一（业务任务，本任务为 docs-only 不阻塞业务）。

### 5.3 业务侧

- 等待真实渠道订单样本（RISK-007 BLOCKED_BY_SAMPLE）。
- 1-2 小时后 P-VERIFY-002 远端商品库数量复核（已部署远端 P-FIX-002D-REMOTE）。

---

## 6. 经验教训

1. **DEBT 登记必须有事实基础**：DEBT-013 描述"12 个 ad-hoc log"是凭印象登记，实际只有 3 项。所有 DEBT 必须在登记前做最小盘点。
2. **evidence 链 = 可审计 = 不可删**：reports 目录的"硬链接"是 evidence 链设计；不允许为了清理而删除。
3. **Windows 设备名是 PowerShell 盲区**：retire-content.ps1 应该加 fallback（git bash `rm` 或 `cmd /c del`）。
4. **工作区 clean = 起步干净 ≠ 任务轻量**：本次任务"只删 3 项"看似简单，但实际承担了 76 份 reports 引用审计、DEBT 重新分类、状态同步多项工作。
5. **manifest-driven delete 优于 rm -rf**：retire-content.ps1 的 manifest 机制保证了 (a) 保护路径检查 (b) 显式 audit trail (c) 跨平台一致；本任务已使用。

---

## 7. 状态

- **DONE**（docs-only / harness-cleanup）
- 工作区干净（本任务新增 5 个 untracked 待 commit）
- DEBT-013 fixed / DEBT-014 wontfix
- Harness 质量 A- → A
- 下一步：HARNESS-AGENT-DO-HARDEN（推荐）
