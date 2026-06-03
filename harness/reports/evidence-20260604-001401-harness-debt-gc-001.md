# HARNESS-DEBT-GC-001 — Evidence Report

> **任务**：HARNESS-DEBT-GC-001 — harness 安全清理、归档、瘦身
> **时间**：2026-06-04 00:14:01 +08:00
> **环境**：本地工作区（非 test / 非 real-pre；本任务为 docs-only 清理）
> **分支**：feature/auth-system
> **commit hash（清理前）**：c76986f5 docs(harness): HARNESS-DEBT-GOVERNANCE-ITERATION governance pass
> **Scope**：docs / harness-cleanup

---

## 1. 基本信息（Git Intake）

| 字段 | 值 |
| --- | --- |
| `git status --short` 数量 | 0（工作区 clean，Intake 阶段） |
| `git branch --show-current` | feature/auth-system |
| `git log -1 --oneline` | c76986f5 docs(harness): HARNESS-DEBT-GOVERNANCE-ITERATION governance pass |
| 工作区是否干净 | ✅ Clean |
| 任务类型 | docs-only / harness-cleanup |

---

## 2. 执行命令与输出摘要

### 2.1 Intake / 盘点阶段

```text
$ git status --short
(0 行)
$ git branch --show-current
feature/auth-system
$ git log -1 --oneline
c76986f5 docs(harness): HARNESS-DEBT-GOVERNANCE-ITERATION governance pass
$ ls harness/reports/*.md | wc -l
76
$ ls harness/archive/retired-content/
20260602-153913
20260603-reports-archive
$ ls harness/archive/retired-content/20260603-reports-archive | wc -l
52
```

### 2.2 候选对象扫描

```text
$ ls nul
-rw-r--r-- 1 caojianing 197121 0 Jun  3 10:43 nul     [存在，0 bytes]
$ ls *.log / snap*.txt / args.json / env.json / health.json
ls: cannot access ...  No such file or directory     [全部不存在]
$ ls out/ tmp/ .codex-run/ runtime/tmp/ .nyc_output/ coverage/
ls: cannot access ...  No such file or directory     [全部不存在]
$ ls -la test-results/ playwright-report/
drwxr-xr-x ...  test-results/playwright/
drwxr-xr-x ...  playwright-report/data/
-rw-r--r-- ...  527672 Jun  3 11:13 playwright-report/index.html
$ git check-ignore test-results/ playwright-report/ nul
test-results/                                     [已 gitignore]
playwright-report/                                [已 gitignore]
nul                                               [已 gitignore]
```

### 2.3 重复报告交叉引用检查

| 旧报告 | 引用数 | 引用方 |
| --- | --- | --- |
| user-domain-u1-inventory-120000 (旧) | 4 | DOMAIN_STATUS.md / u2-schema-150000 / u2_5 plan / u2_5b |
| user-domain-u2-schema-093000 (旧) | 3 | HARNESS_CHANGELOG / evidence-093411 / evidence-095000 |
| p-fix-001c-112740 (旧) | 8 | evidence-113632 / evidence-122021 / batch-3 / batch-4 / git-harness-001 / p-diag-002 / p-fix-001c-113616 / sync-plan-001 |

**结论**：3 对"重复"报告全部被证据链引用，必须 keep。

### 2.4 content-retire 报告引用检查

| 文件 | 跨引用数 |
| --- | --- |
| content-retire-093347 | 2 |
| content-retire-095000 | 1 |
| content-retire-103207 | 9 |
| content-retire-111343 | 8 |
| content-retire-113617 | 7 |
| content-retire-213739 | 2 |
| content-retire-214340 | 1 |

**结论**：7 份 content-retire 全部被引用，必须 keep。

### 2.5 Manifest 准备

```text
$ cat harness/archive/manifests/manifest-20260604-001052-harness-debt-gc-001-delete.json
{
  "items": [
    { "path": "test-results", "action": "delete", "category": "generated-output-directory",
      "reason": "playwright test results directory; .gitignore'd; regenerated on every playwright run; no business value",
      "allowRecursive": true },
    { "path": "playwright-report", "action": "delete", "category": "generated-output-directory",
      "reason": "playwright HTML report (528 KB index.html); .gitignore'd; regenerated on every playwright run; no business value",
      "allowRecursive": true }
  ]
}
```

> **注**：`nul` 不在 manifest 内（Windows 设备名，PowerShell Resolve-Path 无法解析），单独用 git bash `rm` 删除。

### 2.6 retire-content.ps1 DryRun

```text
$ powershell -NoProfile -ExecutionPolicy Bypass -File ./harness/commands/retire-content.ps1 \
    -Action Delete \
    -Manifest harness/archive/manifests/manifest-20260604-001052-harness-debt-gc-001-delete.json \
    -Reason "HARNESS-DEBT-GC-001 cleanup ad-hoc artifacts" \
    -DryRun
=== Content retirement ===
DRY-RUN content retirement report:
# Content Retirement Report
## Metadata
- Time: 2026-06-04 00:13:38 +08:00
- Action: Delete
- DryRun: True
- Manifest: harness/archive/manifests/manifest-20260604-001052-harness-debt-gc-001-delete.json
## Planned / Applied Operations
DELETE test-results
DELETE playwright-report
Content retirement report: D:\Projects\SAAS\harness\reports\content-retire-20260604-001338.md
```

**DryRun 结果**：保护检查通过，2 个 DELETE 计划已记录。

### 2.7 retire-content.ps1 实际执行

```text
$ powershell -NoProfile -ExecutionPolicy Bypass -File ./harness/commands/retire-content.ps1 \
    -Action Delete \
    -Manifest harness/archive/manifests/manifest-20260604-001052-harness-debt-gc-001-delete.json \
    -Reason "HARNESS-DEBT-GC-001 cleanup ad-hoc artifacts"
=== Content retirement ===
Content retirement report: D:\Projects\SAAS\harness\reports\content-retire-20260604-001401.md
```

### 2.8 nul 单独删除

```text
$ rm -f nul
$ ls -la nul
ls: cannot access 'nul': No such file or directory    [已删除]
```

### 2.9 验证

```text
$ for p in nul test-results playwright-report; do
>   if [ -e "$p" ]; then echo "STILL EXISTS: $p"; else echo "DELETED: $p"; fi
> done
DELETED: nul
DELETED: test-results
DELETED: playwright-report

$ git status --short
?? harness/archive/manifests/
?? harness/reports/content-retire-20260604-001401.md
?? harness/reports/harness-debt-gc-001-inventory-20260604-001052.md
```

**3 个 dirty**：全部为本任务新生成（manifest 目录、content-retire 报告、inventory 报告），全部在 Allowed Change Set 内。

---

## 3. 实际删除对象列表

| # | 路径 | 类型 | 已删除 | 原因 |
| --- | --- | --- | --- | --- |
| 1 | `nul` | Windows 设备文件残留（0 bytes） | ✅ | 设备名已被 Windows 系统占用，git bash `rm` 绕过设备名解析删除；已 gitignore；非业务文件 |
| 2 | `test-results/` | playwright 测试结果目录 | ✅ | 已 gitignore；playwright 重新运行会再生；无业务价值 |
| 3 | `playwright-report/` | playwright HTML 报告（528 KB index.html） | ✅ | 已 gitignore；playwright 重新运行会再生；无业务价值 |

**删除总数**：3 项

---

## 4. 归档对象列表

| # | 路径 | 操作 | 原因 |
| --- | --- | --- | --- |
| — | （无） | — | 76 份 `harness/reports/*.md` 全部被证据链引用，无可安全归档对象 |

**归档总数**：0 项

---

## 5. 未处理对象列表

| # | 路径 | 状态 | 原因 |
| --- | --- | --- | --- |
| 1 | 76 份 `harness/reports/*.md` | keep | 全部被 evidence / retro / git-batch / state 显式引用 |
| 2 | 3 对"重复"早期报告（u1 120000 / u2 093000 / p-fix-001c 112740） | keep | 被主源/批次报告显式引用（4-8 处/份） |
| 3 | 7 份 content-retire Plan 报告 | keep | 被 git-batch / sync-plan / evidence 引用（1-9 处/份） |
| 4 | `harness/archive/retired-content/20260603-reports-archive/`（52 文件） | keep | 已存在归档批次，本任务不动 |
| 5 | `harness/archive/retired-content/20260602-153913/` | keep | 已存在归档批次 |
| 6 | `harness/archive/manifests/` | NEW untracked | 本任务新建（manifest 目录） |
| 7 | `harness/reports/harness-debt-gc-001-inventory-20260604-001052.md` | NEW untracked | 本任务新建（inventory 报告） |
| 8 | `harness/reports/content-retire-20260604-001401.md` | NEW untracked | retire-content.ps1 自动生成 |
| 9 | `harness/reports/evidence-20260604-001401-harness-debt-gc-001.md` | NEW untracked | 本任务新建（evidence） |
| 10 | `harness/reports/retro-20260604-001401-harness-debt-gc-001.md` | NEW untracked | 本任务新建（retro） |

---

## 6. 验证结果

| 检查项 | 命令 | 结果 |
| --- | --- | --- |
| 删除前 | `ls -la nul` | 0 bytes 文件存在 |
| 删除前 | `ls -la test-results/` | 目录存在 |
| 删除前 | `ls -la playwright-report/` | 目录 + index.html 存在 |
| 候选确认 | `git check-ignore` | 3/3 已 gitignore |
| DryRun | `retire-content.ps1 -Action Delete -DryRun` | PASS（保护检查通过，2 个 DELETE 计划） |
| 实际执行 | `retire-content.ps1 -Action Delete` | PASS（2 项删除） |
| nul 删除 | `rm -f nul`（git bash） | PASS（已删除） |
| 工作区状态 | `git status --short` | 3 个 untracked（本任务新生成，全部在 Allowed Change Set） |

---

## 7. 状态文件更新

| 文件 | 更新 |
| --- | --- |
| `harness/CURRENT_STATE.md` | 追加 HARNESS-DEBT-GC-001 完成段 |
| `harness/HARNESS_CHANGELOG.md` | 新增 v0.6.1 条目 |
| `harness/state/HARNESS_DEBT.md` | DEBT-013 deferred → fixed；DEBT-014 deferred → wontfix |
| `harness/QUALITY_LEDGER.md` | Harness A- → A |

---

## 8. 剩余风险

| 风险 | 等级 | 描述 |
| --- | --- | --- |
| nul 设备名 Windows 限制 | LOW | Windows 把 `nul` 视为保留设备名；未来如再产生 `nul` 文件需用 git bash `rm` 绕过 |
| 76 份 reports 不可归档 | LOW | reports 目录膨胀到 76 份无影响：受保护文件 ~861 KB，仓库可承受；如未来超过 200 份，可重新评估 |
| 未自动轮转 reports | LOW | 当前依赖人工 GC；可后续在 HARNESS_ITERATION_ROADMAP 中加 rotation 任务 |

---

## 9. 结论

**Final Status**: **DONE**（docs-only / harness-cleanup）

- 删除 3 项：`nul`、`test-results/`、`playwright-report/`
- 归档 0 项（无符合归档条件对象）
- 保留 76 份 reports（全部被证据链引用）
- 关闭 DEBT-013、重新分类 DEBT-014 为 wontfix
- 工作区新增 5 个 untracked（manifest、inventory、content-retire、evidence、retro）

**未执行**：mvn test / npm run build / docker compose restart / 远端 deploy（docs-only）。

---

## 10. Git Push / Commit 信息

- 本任务 commit 待生成。
- 推送：本地 → gitee / origin → 远端 deploy（如用户明确要求时执行；本任务为 docs-only 不需远端部署）。
