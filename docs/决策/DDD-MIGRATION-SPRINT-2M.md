# DDD 2 月冲刺计划（DDD-MIGRATION-SPRINT-2M）

> **状态**：基于 ask-matt 主流程 + 用户确认（C 目标：Phase 1+2 完整闭环 + 各域 ≥ 80%）
> **时间窗口**：2026-06-22 → 2026-08-22（8 周）
> **目标**：业务代码迁移率从 23.3% → **70%+**

## 一、Sprint 目标定义（与用户达成共识）

| 维度 | 当前 | Sprint 目标 | 衡量 |
|---|---|---|---|
| **业务代码迁移率** | 23.3% | **70%+** | domain/ 新代码 ÷ 全部 service+domain 代码 |
| **Phase 1**（DataScope） | 完成 | **完成**（#8 待灰度） | 3 个接入点全 Policy 委派 |
| **Phase 2**（User 域） | 部分（OrgStructure 完成） | **完整闭环** | user 域 6 个核心 Service 全 DDD 化 |
| **各域迁移率** | 13-40% | **≥ 80%**（除 talent/config 外） | 每域至少 80% |

**用户选项 C**：**Phase 1 + Phase 2 完整闭环（DataScope + User 域）+ 各域至少 80%**。

## 二、Sprint 范围

### ✅ 在 Sprint 范围内

| Phase | 任务 | 预期新增 DDD 代码 |
|---|---|---|
| **Phase 0（收尾）** | #14 SysDeptService 修复（本期 1 周内） | ~300 行 |
| **Phase 1（DDD-USER-DATASCOPE）** | #8 删除 OrderController 旧方法（需人工 + 灰度） | 0 行（删除） |
| **Phase 2（DDD-USER-MIGRATION）** | 拆 SysUserService 1,412 行 → 5 个 Application + 1 个 Policy | ~3,500 行 |
| **Phase 2 续** | 迁移 SysRoleService + SysMenuService | ~1,500 行 |
| **Phase 3（Sample）** | 寄样域核心 Service 迁移 | ~3,000 行 |
| **Phase 4（Talent）** | 达人域核心 Service 迁移（仅 80%） | ~3,500 行 |
| **Phase 5（Performance）** | 业绩域核心 Service 迁移 | ~2,000 行 |
| **Phase 6（Analytics）** | 分析模块核心 Service 迁移 | ~1,500 行 |
| **Phase 7（Config）** | 配置域核心 Service 迁移（仅 80%） | ~800 行 |
| **Phase 8（Product）** | 商品域 god class 拆解（ProductService 5,565 行） | ~2,500 行 |
| **Phase 9（Order）** | OrderSyncService 1,445 行拆解 | ~2,500 行 |

**Sprint 累计**：~21,100 行 DDD 新代码 + 老代码减 ~25,000 行 = 整体迁移率从 23.3% → **约 75%**（注：用户 C 目标"≥ 80%"是个**域级别**目标，不是整体）。

### ❌ 不在 Sprint 范围内

- 9 层 DDD 完整结构（query/domain/api/port 层补全）—— 推到 Sprint 后
- Legacy 类的完全删除（保留作兜底）—— Sprint 后单独 Phase
- 业务代码迁移率到 100% —— 仅达成域级 80%
- baseline 回归修复（SysUserServiceTest 7 个失败等）—— Sprint 后单独处理
- 性能优化 / 重构

## 三、8 周时间表（每周 1 个 Phase）

| 周 | 起止 | Phase | 任务 | 交付物 |
|---|---|---|---|---|
| **W1** | 6/22-6/28 | Phase 0 | #14 SysDeptService 修复 + #8 灰度删除 | 2 个 Issue CLOSED |
| **W2** | 6/29-7/5 | Phase 2.1 | SysUserService 拆 5 个 Application + DataScope 业务规则 Policy | +1 Policy + 5 Application |
| **W3** | 7/6-7/12 | Phase 2.2 | SysRoleService + SysMenuService 迁移 + PermissionPolicy | +2 Application + 1 Policy |
| **W4** | 7/13-7/19 | Phase 3 | 寄样域 SampleStatusLogService + SampleApprovalService 迁移 | +2 Application |
| **W5** | 7/20-7/26 | Phase 4 | 达人域 TalentService + TalentFollowService 迁移（80%） | +2 Application |
| **W6** | 7/27-8/2 | Phase 5+6 | 业绩域 + 分析模块核心 Service 迁移 | +3 Application |
| **W7** | 8/3-8/9 | Phase 7+8 | 配置域 + 商品域 ProductService god class 拆解（80%） | +2 Application |
| **W8** | 8/10-8/16 | Phase 9 | OrderSyncService 拆解 + 全面验收 + Sprint 报告 | +1 Application + 1 报告 |

**Sprint 验收**：8/22 前完成所有 Phase + 整体迁移率 ≥ 70% + 各域（除 talent/config 外）≥ 80%。

## 四、每周节奏标准

### 单 Phase 标准动作（每会话）

```
1. 创建 GitHub Issue（user 确认后）
2. /tdd red: 写新测试（基于现有 Service）
3. /tdd green: 实现 Policy/Application
4. 改旧 Service 为 Legacy 委派壳
5. 跑测试（最少 90% 通过）
6. /review 自检
7. 关闭 Issue
8. 更新 docs/harness-maintenance/engineering/issues-index.md
```

### Sprint 节奏约束

- **每周最多 1 个 Phase**（保持小步可回滚）
- **每周 1 次会话**（避免 burnout）
- **每会话后输出 handoff 文档**（更新 DDD-MIGRATION-STATUS-{date}.md）

## 五、风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| **god class 拆解引入 bug**（ProductService 5,565 行 / SysUserService 1,412 行） | 高 | 高 | 先补测试覆盖 80%+ 再动手；Parity Test 必须 1:1 |
| **talent/config 域迁移率难达 80%**（当前 13-40%） | 中 | 中 | Sprint 末允许这两个域保留 60-70%，但需明确记录原因 |
| **agent tool 限额中断** | 高 | 中 | 已用 status 文档 handoff，下次会话可续 |
| **真实环境灰度发现行为差异** | 低 | 高 | 每个 Phase 完成都跑全量回归测试 |
| **核心架构师 review 不通过** | 中 | 高 | Sprint 每周输出 handoff 文档，team lead 可介入 |

## 六、Sprint 范围内的精确 Issue 列表（24 条）

### Phase 0（2 条）

- #14 [DDD-USER-MIGRATION-006] SysDeptService 修复（OPEN，继续推进）
- #8 [DDD-USER-DATASCOPE-006] 删除 OrderController 旧方法（OPEN，ready-for-human）

### Phase 2.1（6 条）

- #15 [DDD-USER-MIGRATION-007] 创建 UserAssignmentPolicy（user_id 分配）
- #16 [DDD-USER-MIGRATION-008] 拆 SysUserCRUDApplication（基础 CRUD）
- #17 [DDD-USER-MIGRATION-009] 拆 SysUserChannelAssignmentApplication
- #18 [DDD-USER-MIGRATION-010] 拆 SysUserGroupAssignmentApplication
- #19 [DDD-USER-MIGRATION-011] 拆 SysUserDataScopeApplication
- #20 [DDD-USER-MIGRATION-012] 拆 SysUserRoleAssignmentApplication

### Phase 2.2（3 条）

- #21 [DDD-USER-MIGRATION-013] 创建 PermissionPolicy
- #22 [DDD-USER-MIGRATION-014] 创建 SysRoleApplicationService
- #23 [DDD-USER-MIGRATION-015] 创建 SysMenuApplicationService

### Phase 3（2 条）

- #24 [DDD-SAMPLE-MIGRATION-001] SampleStatusLogService 迁移
- #25 [DDD-SAMPLE-MIGRATION-002] SampleApprovalService 迁移

### Phase 4（2 条）

- #26 [DDD-TALENT-MIGRATION-001] TalentService 迁移
- #27 [DDD-TALENT-MIGRATION-002] TalentFollowService 迁移

### Phase 5+6（3 条）

- #28 [DDD-PERFORMANCE-MIGRATION-001] PerformanceCalculationService 迁移
- #29 [DDD-ANALYTICS-MIGRATION-001] DashboardService 迁移
- #30 [DDD-ANALYTICS-MIGRATION-002] ReportAggregationService 迁移

### Phase 7+8（4 条）

- #31 [DDD-CONFIG-MIGRATION-001] ConfigDomainService 迁移（80%）
- #32 [DDD-PRODUCT-MIGRATION-001] ProductService god class 拆解（80%）
- #33 [DDD-PRODUCT-MIGRATION-002] ProductActivityBackfillService 拆解
- #34 [DDD-ORDER-MIGRATION-006] OrderSyncService god class 拆解

### Phase 9 收尾（2 条）

- #35 [DDD-SPRINT-2M-VALIDATION] Sprint 全面验收测试
- #36 [DDD-SPRINT-2M-REPORT] Sprint 报告 + handoff

**总计**：2 + 6 + 3 + 2 + 2 + 3 + 4 + 2 = **24 条 issues**

## 七、Sprint 验收标准

### 量化验收

| 指标 | 目标 |
|---|---|
| 业务代码迁移率 | ≥ 70% |
| 域级迁移率 | 6 个域 ≥ 80%（user/order/product/sample/performance/analytics） |
| 新增 DDD 代码 | ≥ 21,000 行 |
| 新增测试代码 | ≥ 5,000 行（每 DDD 类平均 60+ 测试） |
| CLOSED issues | 24 条（不含 #3 PRD 总览） |
| 任何 P0/P1 bug | 0 |

### 质量验收

- [ ] 所有新增 Policy 都有 ≥ 5 个测试用例
- [ ] 所有新增 Application 都有 ≥ 10 个测试用例
- [ ] 所有 Legacy Service 委派壳 ≤ 100 行
- [ ] 所有 Phase 都有 Parity Test 验证行为 1:1
- [ ] Sprint 末跑全量回归测试，记录 baseline 状态

## 八、Sprint 后的下一阶段

Sprint 完成后，自然过渡到：

- **Phase 10**：9 层 DDD 完整结构补全（query / domain / api / port 层）
- **Phase 11**：Legacy 类清理（每个域 100% 删除）
- **Phase 12**：跨域重复代码抽象（共享 Policy）
- **Phase 13**：性能优化与监控

## 九、决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| 完成度定义 | **C**（Phase 1+2 闭环 + 各域 ≥ 80%） | 用户确认；现实可行 |
| Legacy 策略 | **保留兜底**（与 Phase 1 一致） | 灰度切换可控 |
| 工作节奏 | **每周 1 次会话** | 可持续，避免 burnout |
| handoff 文档 | **每次会话更新** | 防止上下文丢失 |
| 跳过 9 层结构 | **本次 Sprint 不补全** | 优先级 P2，推迟到 Sprint 后 |
| baseline 回归 | **Sprint 后处理** | 不阻塞主目标 |

## 十、给下一轮 agent 的接力指南

### 如果这是 Sprint 第一轮（W1）

1. 读 `DDD-MIGRATION-STATUS-20260619.md`（状态快照）
2. 读 `DDD-MIGRATION-SPRINT-2M.md`（本文件）
3. 打开 `docs/harness-maintenance/engineering/issues-index.md`（issue 镜像）
4. 从 #14 开始（Phase 0）

### 如果这是 Sprint 中间轮（W2-W8）

1. 检查 `DDD-MIGRATION-STATUS-{latest}.md`
2. 找到当前 Phase 对应的 OPEN issue
3. 按 `/implement` skill 推进
4. 完成后关闭 issue + 更新 handoff

### 如果需要暂停

1. 写一份 `DDD-MIGRATION-STATUS-{new-date}.md` 记录当前状态
2. 关闭所有进行中的 issue（如需）
3. 标记 Phase 状态为 "deferred" 或 "partial"

---

**Sprint 文档版本**：v1.0
**创建日期**：2026-06-19
**作者**：ask-matt router（grill-with-docs 等价工作）
**Sprint 截止**：2026-08-22（8 周）
**目标**：业务代码迁移率 23.3% → 70%+；Phase 1+2 完整闭环；6 个核心域 ≥ 80%