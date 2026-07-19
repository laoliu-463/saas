# GitHub Issues Index (Mirror)

> **状态**：本目录是 Matt Pocock engineering skills 的配置入口。
> 本文件是 GitHub Issues 的**本地镜像**，提供 harness 内的 issue 可见性。
>
> **最后更新**：2026-07-19（GitHub open issues 权威快照）

## 同步规则

- **每次 `/implement` skill 启动前**：读取本文件了解 in-flight issues
- **每次 issue 状态变更后**：手动 `gh issue list > /tmp/issues.txt` 同步到本文件
- **每周自动同步**：CI 任务或本地 cron 跑 `gh issue list --state all`

## 历史 Sprint 2M 计划（2026-06-22 → 2026-08-22）

> 以下排期仅保留历史背景，不代表 GitHub 当前状态。当前 in-flight 以紧随其后的 open issue 快照为准。

详见 `docs/决策/DDD-MIGRATION-SPRINT-2M.md`。

| 周 | Phase | 状态 |
|---|---|---|
| W1 (6/22-6/28) | Phase 0: #14 SysDeptService 修复 + #8 灰度删除 | ⏳ 进行中 |
| W2 (6/29-7/5) | Phase 2.1: SysUserService 拆 5 个 Application (#15-#20) | ⏳ 待开始 |
| W3 (7/6-7/12) | Phase 2.2: SysRole + SysMenu (#21-#23) | ⏳ |
| W4 (7/13-7/19) | Phase 3: 寄样域 (#24-#25) | ⏳ |
| W5 (7/20-7/26) | Phase 4: 达人域 (#26-#27) | ⏳ |
| W6 (7/27-8/2) | Phase 5+6: 业绩域 + 分析 (#28-#30) | ⏳ |
| W7 (8/3-8/9) | Phase 7+8: 配置域 + 商品域 (#31-#33) | ⏳ |
| W8 (8/10-8/16) | Phase 9: 订单域 + Sprint 验收 (#34-#36) | ⏳ |

## 当前 In-flight Issues（GitHub 镜像）

| # | Title | State | Labels | Created | Link |
| --- | --- | --- | --- | --- | --- |
| 166 | 建立 GitHub 协作治理与可执行 CI 门禁（Phase 1） | OPEN | ready-for-agent | 2026-07-19 | https://github.com/laoliu-463/saas/issues/166 |
| 165 | 重构 Harness 为跨平台 Node 验证核心（第一阶段） | OPEN | ready-for-agent | 2026-07-18 | https://github.com/laoliu-463/saas/issues/165 |

## 本地验证补充（2026-06-20）

| Issue | 本地状态 | 证据 |
| --- | --- | --- |
| #16 UserAssignmentPolicy | LOCAL VERIFIED，GitHub 状态待同步 | `harness/reports/evidence-20260620-200000-UserAssignmentPolicy.md` |
| #21 SysUserCRUDApplicationA | LOCAL VERIFIED，GitHub 状态待同步 | `harness/reports/evidence-20260620-223000-SysUserCRUDApplicationA.md` |
| #22 SysUserCRUDApplicationB | LOCAL VERIFIED + 已接入 `SysUserService` live path | `harness/reports/latest-evidence-20260620.md` |
| #18 SysUserAssignmentApplication | LOCAL VERIFIED，已以 `SysUserGroupMembershipApplication` 承接群组成员加入/移除 live path | `harness/reports/latest-evidence-20260620.md` |

## Closed Issues（最近）

| # | Title | State | Closed Date | Link |
| --- | --- | --- | --- | --- |
| 4 | test | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/4 |
| 5 | [DDD-USER-DATASCOPE-003] verify OrderController integration with DataScopePolicy | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/5 |
| 6 | [DDD-USER-DATASCOPE-004] 接入 service/OrderService.applyDataScope | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/6 |
| 7 | [DDD-USER-DATASCOPE-005] 接入 LegacyOrderDomainFacade.applyDataScope | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/7 |
| 9 | [DDD-USER-MIGRATION-001] 迁移 OrgStructureService 到 domain.user.policy/application | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/9 |
| 10 | [DDD-USER-MIGRATION-002] 创建 OrgAssignmentPolicy (resolveAssignment + splitAssignment) | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/10 |
| 11 | [DDD-USER-MIGRATION-003] 创建 OrgValidationPolicy (validateGroupLeader + assertCanDeleteDept) | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/11 |
| 12 | [DDD-USER-MIGRATION-004] 创建 OrgEnrichmentPolicy (enrichUser + enrichUserList + formatOrgChangeRemark) | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/12 |
| 13 | [DDD-USER-MIGRATION-005] 创建 OrgStructureApplicationService + 旧 Service 改造为 Legacy | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/13 |
| 14 | [DDD-USER-MIGRATION-006] 迁移 SysDeptService (439 行拆解) | CLOSED | 2026-06-19 | https://github.com/laoliu-463/saas/issues/14 |

> 备注：#14 是 W1 之前版本，已被 #15 替代。

## 同步命令

```bash
# 列出所有 open issues
gh issue list --state open --limit 100

# 列出所有 closed issues（最近 30 天）
gh issue list --state closed --limit 30

# 查看单个 issue
gh issue view <number> --comments
```

## Issue 生命周期

```
外部需求 / 缺陷
   ↓
gh issue create --label "needs-triage"
   ↓
[maintainer 评估]
   ↓                          ↓                          ↓
needs-info             ready-for-agent          ready-for-human
(等 reporter)          (AFK agent 抓)         (human 实现)
   ↓                          ↓                          ↓
ready-for-agent        /implement 新 session     关闭 issue + 引用 commit
   ↓
/implement 完成后
   ↓
gh issue close --comment "..."
```

## 历史 Sprint 进度记录（2026-06-20）

> 本节是历史快照，不参与当前 issue 状态判断。

- **整体迁移率**：23.3% → 目标 70%+
- **已完成 Phase**：1（DataScope）/ 2 部分（OrgStructure）
- **本 Sprint 已 CLOSED Issues**：9/24
- **本 Sprint OPEN Issues**：1（#3 PRD 持续 OPEN）
- **本 Sprint OPEN Issues**：#3、#17、#20（#18 本地已验证；#17 已被 #21/#22 拆分覆盖一部分）

## 相关文件

- [`harness/engineering/issue-tracker.md`](./issue-tracker.md) —— Issue tracker 配置
- [`harness/engineering/triage-labels.md`](./triage-labels.md) —— 标签映射
- [`harness/engineering/context.md`](./context.md) —— 上下文文档规则
- `docs/决策/DDD-MIGRATION-100.md` —— 完整 PRD
- `docs/决策/DDD-MIGRATION-SPRINT-2M.md` —— Sprint 计划
- `docs/决策/DDD-MIGRATION-STATUS-20260619.md` —— 状态快照

## 变更历史

- **v2.0**（2026-06-19）：Sprint 2M 创建时刷新，废弃旧 #14 用 #15
- **v1.0**（2026-06-19）：初始化，镜像 GitHub Issues #3
