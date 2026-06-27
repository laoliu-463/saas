# GitHub Issues Index (Mirror)

> 本文件是 GitHub Issues 的本地镜像，用于 Matt Pocock engineering skills 与 harness 任务路由。
> 最后更新：2026-06-27（#72 达人数据范围 E2E 关闭后）

## 同步规则

- Issue 状态变更后，用 `gh issue list --state open --limit 100` 复核并同步本文件。
- 本文件只记录当前 open 总账和最近关闭的执行项；完整历史以 GitHub 为准。
- 不要把本地阶段判断写成 GitHub 已关闭事实。

## 当前 Open Issues

| # | Title | Labels | Link |
| --- | --- | --- | --- |
| 3 | PRD: DDD 渐进式迁移到 100%（DDD-MIGRATION-100） | ready-for-agent | https://github.com/laoliu-463/saas/issues/3 |
| 73 | [DDD100-SAMPLE-BASELINE] SampleApplicationService 状态机基线 | ready-for-agent | https://github.com/laoliu-463/saas/issues/73 |
| 74 | [DDD100-SAMPLE-COMMAND] 申请、审核、发货、签收 Application 收口 | ready-for-agent | https://github.com/laoliu-463/saas/issues/74 |
| 75 | [DDD100-SAMPLE-EVENT] 订单已同步事件消费与交作业完成 | ready-for-agent | https://github.com/laoliu-463/saas/issues/75 |
| 76 | [DDD100-SAMPLE-PERMISSION] 寄样动作权限和数据范围边界 | ready-for-agent | https://github.com/laoliu-463/saas/issues/76 |
| 78 | [DDD100-SAMPLE-E2E] 幂等、异常分支、real-pre 样本证据 | ready-for-agent | https://github.com/laoliu-463/saas/issues/78 |

## 最近关闭的执行项

| # | Title | Closed Date | Evidence |
| --- | --- | --- | --- |
| 72 | [DDD100-TALENT-E2E] 达人数据范围、越权负例和 E2E | 2026-06-27 | Targeted/E2E/RBAC/data-scope PASS，`harness/reports/2026-06-21/ddd-talent-e2e-072/evidence-20260627-164753-ddd100-talent-scope-e2e.md` |
| 71 | [DDD100-TALENT-GATEWAY] 第三方达人接口真实响应或 BLOCKED 证据 | 2026-06-27 | 第三方真实响应 `BLOCKED`；HTTP/PublicWeb provider real-pre 关闭且 endpoint/token/auth 缺失，`harness/reports/2026-06-21/ddd-talent-gateway-071/evidence-20260627-163647-ddd100-talent-gateway-blocked.md` |
| 70 | [DDD100-TALENT-ADDRESS] 达人地址供寄样域消费边界 | 2026-06-27 | `TalentAddressApplicationService`, `TalentShippingAddressDTO`, `harness/reports/2026-06-21/ddd-talent-address-070/evidence-20260627-163100-ddd100-talent-address-boundary.md` |
| 69 | [DDD100-TALENT-PROFILE] 达人资料、标签、跟进 Application 收口 | 2026-06-27 | `TalentProfileApplicationService`, `TalentFollowApplicationService`, `harness/reports/2026-06-21/ddd-talent-profile-069/evidence-20260627-162100-ddd100-talent-profile-application.md` |
| 68 | [DDD100-TALENT-BASELINE] TalentService 认领/保护期基线 | 2026-06-27 | 达人认领 / 保护期基线 PASS；`harness/reports/2026-06-21/ddd-talent-baseline-068/evidence-20260627-160500-ddd100-talent-baseline.md` |
| 67 | [DDD100-PRODUCT-E2E] 商品库、转链、映射 real-pre E2E | 2026-06-27 | 商品链 PASS；订单正向归因 PENDING，`harness/reports/2026-06-21/ddd-product-e2e-067/evidence-20260627-155300-ddd100-product-real-pre-e2e.md` |
| 66 | [DDD100-PRODUCT-PROMOTION] 转链、归因映射 Port 与事件证据 | 2026-06-27 | `ProductPromotionLinkCompletedEvent`, mapping id evidence, `harness/reports/2026-06-21/ddd-product-promotion-066/evidence-20260627-154100-ddd100-product-promotion-link-event.md` |
| 65 | [DDD100-PRODUCT-BACKFILL] backfill 异步/repair 组件拆分 | 2026-06-27 | `ProductBackfillJobMetadata`, `ProductLibraryRepairPolicy`, `harness/reports/2026-06-21/ddd-product-backfill-065/evidence-20260627-151600-ddd100-product-backfill-repair-components.md` |
| 64 | [DDD100-PRODUCT-SNAPSHOT] 商品快照、read model、query 层 | 2026-06-27 | `ProductSnapshotQueryService`, `harness/reports/2026-06-21/ddd-product-snapshot-064/evidence-20260627-145500-ddd100-product-snapshot-query.md` |
| 63 | [DDD100-PRODUCT-STATUS] 商品业务状态与操作日志收口 | 2026-06-27 | `ProductAuditDecisionPolicy`, `harness/reports/2026-06-21/ddd-product-status-063/evidence-20260627-144200-ddd100-product-status-policy.md` |
| 62 | [DDD100-PRODUCT-DISPLAY] 展示规则 Policy/Application 收口 | 2026-06-27 | `ProductDisplayPolicy.resolveLocalPublishControl`, `harness/reports/2026-06-21/ddd-product-display-062/evidence-20260627-143000-ddd100-product-display-policy.md` |
| 61 | [DDD100-PRODUCT-SYNC] 商品同步 Application 拆分 | 2026-06-27 | `ProductActivitySyncApplicationService`, `harness/reports/2026-06-21/ddd-product-sync-061/evidence-20260627-142000-ddd100-product-sync-application.md` |
| 35 | [DDD100-USER-ASSIGN] 用户分配、渠道、组织归属 Application 收口 | 2026-06-27 | `UserGroupMembershipStore`, `harness/reports/2026-06-21/ddd-user-assign-035/evidence-20260627-134900-user-assign-org-membership.md` |
| 34 | [DDD100-USER-CRUD] SysUser CRUD Application 收口 | 2026-06-27 | `SysUserServiceAssignableBoundaryTest`, `harness/reports/2026-06-21/ddd-user-crud-034/evidence-20260627-123000-sysuser-crud-application.md` |
| 33 | [DDD100-USER-DATASCOPE] 数据范围剩余消费点收口 | 2026-06-27 | `DddUserDataScopeRemainingConsumerGuardTest`, `harness/reports/2026-06-21/ddd-user-datascope-033/evidence-20260627-121500-datascope-consumer-guard.md` |
| 32 | [DDD100-METRIC] DDD 迁移率脚本与 evidence 指标固化 | 2026-06-27 | `harness/scripts/probes/ddd-migration-metrics.ps1`, proxy 26.6% |
| 31 | [DDD100-GUARD] 架构护栏与跨域依赖扫描收口 | 2026-06-27 | `DddArchitectureRedlineGuardTest`, `harness/reports/2026-06-21/ddd-architecture-guard-031/evidence-20260627-115000-architecture-redline-guard.md` |
| 30 | [DDD100-BASELINE] 当前 100% 迁移率与风险基线重算 | 2026-06-27 | `harness/reports/ddd100-baseline-20260627.md`, business proxy 26.3% |
| 24 | [Sprint-4M-W3] DDD-USER-MIGRATION-015 创建 AuthApplication | 2026-06-26 | `AuthServiceTest`, `AuthApplicationTest` |
| 25 | [P1-URGENT] DDD-DATASCOPE-001 加 Feature Flag + 恢复 OrderController 旧 switch | 2026-06-26 | `8e299035`, DataScope/Order targeted tests |
| 26 | [P1-URGENT] [PRODUCT-FIX-001] /product/manage/products 无 query 时 fallback 到 assigned[0] 导致数据归属错位 | 2026-06-25 | `product/manage/products` fallback fix |
| 27 | [P1-URGENT] [PRODUCT-FIX-002] 验证 /product/manage/products fallback 修复端到端行为 | 2026-06-26 | `harness/reports/evidence-20260623-product-manage-fallback-verification.md` |
| 28 | [P1-URGENT] [PRODUCT-FIX-003] DB 快照 total 与抖音实时 total 偏差排查 | 2026-06-26 | `harness/reports/evidence-20260623-db-snapshot-vs-douyin-total.md` |
| 29 | PRD: 代码质量与 DDD 设计合规治理 | 2026-06-26 | `harness/reports/evidence-20260626-173757.md`, `runtime/qa/out/real-pre-p0-20260626-173922/report.md`, `99b4c032` |

## 当前判断

- #3 是 DDD 迁移总 PRD，不能因单个切片完成而关闭。
- 当前 GitHub open leaf issues 为 #73-#76 与 #78；#71、#72、#77、#79-#89 当前不在 open 列表。
- #61 已完成商品同步 Application 收口；#62 已完成本地发布展示规则下沉；#63 已完成人工审核状态与操作日志语义 policy 收口；#64 已完成商品快照基础 query service 收口；#65 已完成 backfill job metadata 与商品库 repair policy 组件拆分；#66 已完成转链 Port 唯一收口、mapping id 证据与转链完成事件证据；#67 已验证商品链 PASS，并把真实订单正向归因样本不足记录为 PENDING；#68 已完成达人认领 / 保护期基线验证；#69 已完成达人资料 / 标签 / 跟进 Application 收口；#70 已完成达人地址供寄样消费边界；上述 issue 均已在 GitHub 关闭。
- #30 给出的 2026-06-27 口径是 raw `domain/` share 20.1%、业务迁移代理 26.3%；它是推进基线，不是 100% 完成证明。
- #31 已新增可重复执行的架构红线 guard，冻结 Controller 直连 Mapper/Gateway 既有债务并阻止新增。
- #32 已新增 `harness/scripts/probes/ddd-migration-metrics.ps1`；当前业务迁移代理指标为 26.6%。
- #33 已冻结非用户域直接 DataScope 消费点，新增复制 self/group/all 规则会由架构测试失败。
- #34 已补 SysUser CRUD 兼容服务委托测试，证明 getById/create/update/delete/resetPassword 进入用户域 Application A/B。
- #35 已将组织成员变更持久化下沉到 `UserGroupMembershipStore`，并用 `updateDeptById` 显式覆盖 `dept_id` 清空。
- #71 已按要求补齐第三方达人真实响应 `BLOCKED` 证据；不能视为真实第三方响应 PASS。
- #72 已完成达人数据范围、越权负例和 E2E 证据；channel 可读达人，biz/ops 访问达人接口返回 403。

## 常用命令

```bash
gh issue list --state open --limit 100
gh issue list --state closed --limit 30
gh issue view <number> --comments
```
