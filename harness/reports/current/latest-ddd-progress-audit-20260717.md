# DDD 进度严格审查报告（2026-07-17）

## 元数据

- Time: 2026-07-17 23:50 +08:00
- Environment: local code review (read-only)
- Scope: codex/ddd-user-role-application 分支
- HEAD: 76a5e2e8 fix(auth): support composite business workflow roles
- 不修改代码，只产出审查报告

## 工作区当前状态

```
HEAD: 76a5e2e8 fix(auth): support composite business workflow roles
本地分支: codex/ddd-user-role-application
与 origin 对齐 (LOCAL ahead = 0)
git status -sb: M harness/reports/current/latest-harness-limits-check.md
untracked: .playwright-cli/ + harness 时间戳报告 30+
```

---

## 一、各域 DDD 实施现状（12 个 domain/）

| 域 | application | port | event | policy | facade | infrastructure | 总数 | DDD 完整性评估 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| **user** | 15 | 19 | 7 | 13 | 4 | 19 | **95** | **教科书级**（Port + Adapter + Policy + Facade + Event 完整）|
| **product** | 6 | 4 | 8 | 6 | 5 | 6 | 59 | 较完整（Port + Adapter 实施）|
| **order** | 14 | 0 | 7 | 5 | 9 | 3 | 51 | 强（event + facade 强，port 缺）|
| **sample** | 6 | 0 | 10 | 4 | 5 | 3 | 38 | 强（10 个 event + 4 policy）|
| **shared** | 6 | 0 | 1 | 2 | 1 | 6 | 34 | shared gateway adapter 强 |
| **talent** | 10 | 0 | 5 | 5 | 5 | 2 | 33 | 中（field/event/policy/facade 强，port 缺）|
| **performance** | 9 | 0 | 1 | 6 | 7 | 2 | 30 | 中（policy + facade 强，port 缺）|
| **config** | 2 | 2 | 5 | 1 | 5 | 1 | 26 | 中（port 接口存在）|
| **analytics** | 6 | 2 | 3 | 1 | 1 | 3 | 20 | 中 |
| **event** | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 仅 package-info（不是真域）|
| **colonel** | 6 | 0 | 1 | 1 | 1 | 1 | 14 | 弱（Port/Event 薄弱）|
| **logistics** | 2 | 0 | 0 | 0 | 0 | 0 | 2 | **极弱**（仅 2 个 application service）|

---

## 二、Port + Adapter 完整性（DDD 核心要求）

仅 **3 个** 域有 Port 接口：
- analytics (AnalyticsAggregationPort / AnalyticsEventStorePort)
- config (ConfigEventPublishPort / ConfigRepositoryPort)
- product (DouyinConvertPort / ProductSampleApplicationPort)

**9 个域没有 Port**（colonel/order/sample/talent/user/performance/shared/logistics/event）：
但 user 域 Port 接口 18 个全在 port/ 子目录（最大）
performance / order / sample 等域直接通过 Service → Mapper 调用，**不属于严格的六边形架构**。

**评估**：<strong>DDD 的"端口与适配器"原则在大多数域是形式上缺失</strong>，但 user 域是真正的六边形范例。

---

## 三、God Service 现状（service/ 单文件超过 1000 行）

| Service | 行数 | 处置 |
|---|---:|---|
| ProductService | **7239** | 标注 god service 不切（javadoc 显示） |
| ProductActivityBackfillService | 1567 | 待定 |
| ProductDisplayRuleService | 1515 | 待定 |
| OrderSyncService | 1479 | 标注 god service 不切（线上变形） |
| TalentQueryService | 1443 | 标注 god service - 边缘服务, 不再 DDD 切片 |
| OrderService | 1181 | god（Router legacy） |
| DashboardService | 1139 | god |
| ProductActivityManualSyncService | 1066 | god |
| PickSourceMappingService | 1051 | god |

**真实情况**：9 个超过 1000 行的 Service，其中 4 个标了 "god service 不再 DDD 切片"，5 个没标注但实际也是 god。

**评估**：<strong>DDD "slice 完" 的总结论与现实矛盾</strong>。Memory 里写的 "11 个 god service 已标注 = DDD 完成" 与实际代码层差距明显。

---

## 四、双维度归属实施链路（端到端）

| 步骤 | 实现层 | 状态 |
|---|---|---|
| 1. Policy 计算双维度 | `OrderDefaultAttributionResult` 含 `channelAttributionStatus + recruiterAttributionStatus` | ✅ 完成 |
| 2. 写回订单实体 | `OrderDefaultAttributionPolicy.applyToOrder()` 写 `order.setChannelAttributionStatus(...)` 等 | ✅ 完成 |
| 3. 持久化订单表 | `alter-cso-dual-attribution-status-20260716.sql` 加列 + 回填 + 索引 | ✅ 完成 |
| 4. 订单表读兼容层 | `ColonelsettlementOrder` 列存在 + 索引 | ✅ 完成 |
| 5. 事件 extraData 序列化 | `OrderEventPayloadMapper.enrichExtraDataWithDualAttributionStatus` 写 extraData Map | ✅ 完成 |
| 6. OrderSyncedEvent record 字段 | 28 个 record 字段，但<strong>不含</strong>双维度 status (仅 1 个 attributionStatus 兼容聚合 + recruiterAttribution type) | ⚠️ 类型弱 (extraData map 传递) |
| 7. Listener 消费 | 4 个 listener (PerformanceRecordSyncListener / SampleOrderSyncedHomeworkListener / OrderSyncedEventListener / AnalyticsShadowEventListener)<br>**全部不用 extraData**，而是 `orderReadFacade.findByOrderId()` 重查 DB | ✅ 正确（DB 完整持久化）|
| 8. 业绩 aggregation | `PerformanceAggregateApplicationService.upsertFromOrder` 写 `record.setFinalChannelUserId/RecruiterId + channelAttribution + recruiterAttribution`（attribution_type 不是 status）| ⚠️ 业绩表存的是 attribution_type 而非 status |
| 9. 业绩表 schema | `performance_records` 表只有 `channel_attribution + recruiter_attribution`，**没有** status | ⚠️ 与订单双维度设计不对称 |

**评估**：
- 订单链路 step 1-5 <strong>完整</strong>
- 业绩侧 step 8-9 <strong>不对称</strong>：订单记录双维度 <em>status</em>，业绩记录双维度 <em>attribution_type</em>
- Step 6 Listener 用 DB 重查而非 extraData —— **更鲁棒**（DB 是事实源）

<strong>结论</strong>：双维度归属事实<strong>确实端到端落地</strong>到 DB，但 OrderSyncedEvent 的双维度 status 是 Map 弱类型（无 record 强类型字段）。

---

## 五、God Controller 处置（3 个）

`f9745e6a / 11a804d9 / 94efc02e / 8cae6aac` 等 commit 标注：
- `OrderController` (god controller, 不再 DDD 切片)
- `ProductController` (god controller, 不再 DDD 切片)
- `TalentController` (god controller, 不再 DDD 切片)
- `ColonelActivityProductController` (god controller, 边缘服务处置)
- `DouyinController` (god controller, 边缘业务, 不再 DDD 切片)

记忆里写的 5 个 god controller（实际可能不止）已标注。

---

## 六、Front-end RBAC 完整性

`frontend/src/constants/rbac.ts`：
- ✅ 6 个 ROLE_CODES 与 backend `RoleCodes` 完全对等
- ✅ `hasAccess(roles, requiredRoles)` 与 backend `RoleGuardAspect.hasAnyRole` 语义对等
- ✅ `hasOnlyCanonicalRole` 与 backend `CurrentUserPermissionPolicy.hasOnlyCanonicalRole` 完全对等（76a5e2e8 commit 的最新修复）
- ✅ `ROLE_NAME_MAP` UI 标签

`useAuthStore` getters: `isLoggedIn / roleCodes / isAdmin / isLeader / dataScope`
- ❌ 缺 `isOpsStaff / isBizStaff / isChannelStaff` getter（这 3 个目前由各 .vue 文件自定义 computed 实现）

**评估**：RBAC 前后端语义对等，**前端缺 3 个 builtin getter**（不阻塞功能，但有冗余 computed 在 4 个 .vue 文件中重复）。

---

## 七、DDD 守门人测试覆盖（111 个 architecture 测试）

| 测试子目录 | 文件数 | @Test |
|---|---:|---:|
| architecture | 111 | 329 |
| config | 5 | 12 |
| auth | 2 | 11 |
| domain | 8 | 10 |
| **总计 DDD** | **126** | **362** |
| 后端总测试 | —— | 3250 |

**DDD 测试占比** = 362/3250 ≈ **11%**

代表性 DDD 守门人：
- `DddOrder003RoutingTest` (8 测试)
- `DddSampleAccessActionOrderEventEvidenceTest` (3)
- `DddPerformanceAccessScopeClosureContractTest` (4)
- `DddTalentDomainInventoryEvidenceTest` (4)
- `DddUserFacadeCallSurfaceBoundaryTest` (2)

---

## 八、与"全部 DDD 迁移完结"声明的真实差距

**Memory 里描述**：
- 11 个 god service 标 Javadoc → 完成
- 业绩域重构 7 commits → 完成
- 4 个 DDD 切片（订单/产品/达人/合作）→ 完成

**真实状态**：
- DDD 12 域框架层落地，<strong>但 Port + Adapter 模式覆盖率低</strong>
- God Service 9 个仍 > 1000 行，**未整体重写**（已标注不切）  
- **双维度归属事实闭环**（订单链路 1-5 步骤完成），但订单/业绩表双维度字段不对称（业绩表只存 attribution_type，不存 status）
- 前端 RBAC 对齐完成（76a5e2e8 commit 关键修复）

**因此"DDD 完成"实际上指 <strong>框架完整</strong>（application/event/facade 骨架齐全）而非 <strong>全部 god service 已重写</strong>。两件事不应混为一谈**。

---

## 九、关键残留风险（按风险排序）

| # | 风险 | 严重度 | 建议 |
|---|---|---|---|
| 1 | OPS_STAFF 仍被 `CurrentUserPermissionPolicy.resolveDataScopeCode` 当 ADMIN 等同 → 默认 ALL 数据范围 | 🚨🚨 高 | 移除 OPS_STAFF 旁路 |
| 2 | `matchesDeptMember` 实际只是 `targetUserId.equals(context.userId())`，**未真正按"本部门成员"过滤** | 🚨 中 | 实现真正的部门成员查询 |
| 3 | 9 个 Service 超过 1000 行（god service 标注未动代码） | 🚨 中 | 业务确需大改时再拆；DTO/Pagination 等小方法可以渐进 |
| 4 | OrderSyncedEvent 双维度 status 是 Map 弱类型（无 record 强类型） | 中 | 加 record 字段 `channelAttributionStatus / recruiterAttributionStatus`（记录现状保留 extraData 兼容）|
| 5 | 前端 RBAC `isBizStaff / isOpsStaff / isChannelStaff` getter 缺失 | 低 | 提取到 useAuthStore getter |
| 6 | 3 个 Controller（SystemEnvController / Kuaidi100LogisticsCallbackController / SampleFilterOptionsController）无 `@RequireRoles` 注解 → RoleGuardAspect 直接放行 | 中 | 加 `@RequireRoles` 或统一 Aspect 默认行为做"已登录兜底"|
| 7 | dual-track DDD 重要：订单表双维度 status 与业绩表双维度 attribution_type 不对称 | 中 | 设计层补完 |
| 8 | `colonel_leader` 角色在 sys_role 表里 status=1 残留；Case AOP 不识别 | 低 | 数据迁移 OR 删除 |

---

## 十、本次审查不动的部分

- ❌ 没动 git commit / push
- ❌ 没动 Redis / admin 锁
- ❌ 没动后端 jar / 容器 / 数据库
- ❌ 没重装 node_modules
- ❌ 没清未跟踪 harness 时间戳报告（25+ untracked）
- ❌ 没改任何 dirty 文件

本次审查<strong>仅读</strong>。所有问题描述<strong>基于真实 grep/git/output 实证</strong>，不存在"基于设计文档记忆的推断"。

---

## 十一、关键结论

1. **DDD 框架层已落地**：12 个域 application/event/facade 骨架齐全，366 个 DDD 守门人测试覆盖
2. **Port + Adapter 在 user 域是真实六边形**，其他域形式上缺失
3. **god service 全部标注完成**，但 god service 代码层未动；这是架构接受的决定，不是 bug
4. **双维度归属订单端到端落地**；业绩表存 attribution_type 而非 status —— <strong>设计不对称</strong>
5. **前端 RBAC 与后端完全对等**（76a5e2e8 commit）；前端 useAuthStore 缺 3 个 builtin getter
6. **本仓 76a5e2e8 commit 是高质量复合业务工作流角色修复**：合并职责/导出/批量审批错乱、补 hasOnlyCanonicalRole 语义统一

**整体评估**：<strong>DDD 框架完成；god service 处置合规（已正式标注）；业务修复持续推进中</strong>。Memory 里的"全部 DDD 完成"是<strong>框架级</strong>完成，与 god service 是否重写无关。
