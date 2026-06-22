# Domain Status

## 作用
本文件记录 DDD 领域优化状态。它不替代 `docs/领域/*.md` 的领域合同，也不代表代码已经完成对应重构。

## 任务结束状态更新规则
每次任务结束前，Agent 必须更新本文件中与本次任务相关的领域状态。更新内容包括：

1. 当前状态摘要（已完成到哪一步）。
2. 报告路径（evidence report 路径）。
3. 当前风险（新发现或已变化）。
4. DDD 优化下一步（下一阶段任务标识）。
5. 标记优先级（P0 / P1 / P2）。

如果本次任务未涉及某个领域，该领域状态保持不变，不得无故修改。

### Session Exit 时的领域状态更新

每次会话退出前，Agent 必须确保：

1. 本次涉及的领域状态已更新到最新。
2. 每个领域条目的"DDD 优化下一步"指向具体任务标识。
3. 新发现的风险已写入"当前风险"。
4. 报告路径已追加到对应领域。
5. 如果模块质量发生变化，同步更新 `harness/QUALITY_LEDGER.md`。

## 状态口径
- `P0`：影响 V1 主链路、权限、归因、业绩、寄样或 real-pre 验收。
- `P1`：影响领域边界、回归风险或主要运维效率。
- `P2`：治理、清理、补文档或体验优化。

## 用户域
- 最新小切片：DDD-USER-TALENT-EXCLUSIVE-DATASCOPE-POLICY 已将 `TalentService.evaluateExclusive` 独家达人评估订单查询的数据范围过滤按灰度双路径收口：`ddd.refactor.data-scope-policy.enabled=false` 默认关闭时保留 Legacy PERSONAL user / DEPT dept 过滤，开启后通过用户域 `DataScopePolicy.contextRequirement` / `decide` 解释数据范围。本轮不改独家评估公式、订单佣金读取、寄样次数统计、默认开关或真实数据。报告：`harness/reports/evidence-20260622-151700.md`。风险变化：`TalentServiceTest` 覆盖源码边界、灰度开启委托用户域 policy、缺 user/dept 上下文 Legacy no-op；103 个相关组合测试 PASS，`agent-do` backend PASS。
- 上一小切片：DDD-USER-TALENT-BLACKLIST-DATASCOPE-POLICY 已将 `TalentService.blacklist/unblacklist` 黑名单操作数据范围校验按灰度双路径收口：`ddd.refactor.data-scope-policy.enabled=false` 默认关闭时保留 Legacy active claim 的 PERSONAL / DEPT 判断，开启后通过用户域 `DataScopePolicy.contextRequirement` / `decide` 解释数据范围，再由达人域继续用 active claim 判断可操作性。本轮不改黑名单状态语义、达人认领、保护期、列表/详情、独家评估、默认开关或真实数据。报告：`harness/reports/evidence-20260622-150007.md`。风险变化：`TalentServiceTest` 覆盖源码边界、灰度开启 claim parity、缺 user/dept 上下文拒绝和无 active claim 放行；101 个相关组合测试 PASS，`agent-do` backend PASS。
- 上一小切片：DDD-USER-TALENT-SERVICE-PAGE-DATASCOPE-POLICY 已将 `TalentService.page` 达人列表数据范围过滤按灰度双路径收口：`ddd.refactor.data-scope-policy.enabled=false` 默认关闭时保留 Legacy 通过 `talent_claim` 按 user/dept 取达人 ID 集合，开启后通过用户域 `DataScopePolicy.contextRequirement` / `decide` 判定 PERSONAL / DEPT 分支，再由达人域继续查询认领事实。本轮不改达人认领、保护期、列表筛选、详情、黑名单、独家评估、默认开关或真实数据。报告：`harness/reports/evidence-20260622-144728.md`。风险变化：`TalentServiceTest` 覆盖源码边界、默认关闭路径、灰度开启 claim parity 和缺上下文 Legacy no-op；98 个相关组合测试 PASS，`agent-do` backend PASS。
- 最新小切片：DDD-USER-TALENT-QUERY-DETAIL-DATASCOPE-POLICY 已将 `TalentQueryService.detail` 达人详情访问范围判断按灰度双路径收口：`ddd.refactor.data-scope-policy.enabled=false` 默认关闭时保留 Legacy claim user/dept 判断，开启后通过用户域 `DataScopePolicy.contextRequirement` / `decide` 判定 PERSONAL / DEPT，缺少 user/dept 上下文仍拒绝。本轮不改达人认领、保护期、标签、地址、第三方接口、默认开关或真实数据。报告：`harness/reports/evidence-20260622-143051.md`。风险变化：`TalentQueryServiceTest` 覆盖源码边界、默认关闭路径、灰度开启 claim parity；49 个相关组合测试 PASS，`agent-do` backend PASS。
- 最新小切片：DDD-USER-ORG-VALIDATION-PERMISSION-POLICY 已将 `OrgValidationPolicy.validateGroupLeader` 负责人角色匹配委托给用户域 `CurrentUserPermissionPolicy.hasAnyRole`，不再维护本地 `roleCodes.stream().noneMatch` matcher。本轮不改负责人候选端口、组织创建 / 更新 / 删除语义、删除约束、Spring 访问注解、数据库结构或真实数据。报告：`harness/reports/evidence-20260622-134547.md`。风险变化：`OrgValidationPolicyTest` 覆盖 `" ADMIN "` 归一化负责人匹配和源码边界守护，`OrgStructureServiceTest` / `OrgStructureApplicationServiceTest` / `OrgUnitWriteApplicationServiceTest` 继续覆盖旧 service parity 与组织写路径。
- 最新小切片：DDD-USER-ACTIVITY-ACCESS-ROLE-NORMALIZATION-BYPASS 已删除 `ActivityAccessService.normalizeRoleCodes` 静态兼容口，活动读权限服务不再直接 `new CurrentUserPermissionPolicy()`，角色集合归一继续通过注入的用户域 `CurrentUserPermissionPolicy.normalizeRoleCodes` 实例路径。本轮不改活动读取权限语义、admin 直通、招商专员 mine 强制、招商组长同部门访问、活动商品同步或真实数据。报告：`harness/reports/evidence-20260622-133356.md`。风险变化：`ActivityAccessServiceTest` 覆盖注入路径归一和源码边界守护，`ColonelActivityControllerTest` 继续覆盖活动控制器消费路径。
- 上一小切片：DDD-USER-PRODUCT-PICK-PAGE-PERMISSION-POLICY 已将废弃兼容 `ProductController.pickPage` 的 BIZ_STAFF 私海限制角色匹配委托给用户域 `CurrentUserPermissionPolicy.hasAnyRole`，不再维护本地 `roleCodes.stream()` / `String::toLowerCase` / `normalized.contains` matcher。本轮不改 `/products/picks` 参数、`ProductService.getPage` 调用、商品查询语义、快速寄样、转链、展示状态、同步或真实数据。报告：`harness/archive/by-date/report-packages/reports-20260622-ddd-datascope-late/evidence-20260622-132516.md`。风险变化：`DddProduct003ProductRoutingTest` 增加源码边界守护，`ProductControllerTest` 继续覆盖 BIZ_STAFF 仅看本人分配商品与 BIZ_LEADER 不受限路径。
- 上一小切片：DDD-USER-ORG-UNIT-WRITE-PERMISSION-POLICY 已将 `OrgUnitWriteApplicationService` 组织更新 / 删除路径的 admin 与渠道组长角色匹配委托给用户域 `CurrentUserPermissionPolicy.hasAnyRole`，不再维护本地 `containsNormalized` / `toLowerCase(Locale.ROOT)` matcher。本轮不改组织创建、更新字段、删除约束、负责人校验、审计日志、`SysDeptController` 访问注解或 `Collection<?> roleCodes` 传参契约。报告：`harness/archive/by-date/report-packages/reports-20260622-ddd-datascope-late/evidence-20260622-131318.md`。风险变化：`OrgUnitWriteApplicationServiceTest` 覆盖渠道组长仅可修改自己负责组织、非本人拒绝、admin 删除、空格大写角色兼容和源码边界守护。
- 上一小切片：DDD-SAMPLE-LOGISTICS-IMPORT-PERMISSION-POLICY 已将 `SampleLogisticsImportService` 物流导入/覆盖动作权限委托给寄样域 `SampleActionPermissionPolicy`；该 policy 继续消费用户域 `CurrentUserPermissionPolicy.hasAnyRole` 解释角色编码集合，服务内本地 matcher 已清零；报告：`harness/reports/reports-20260621-2206-20260622-1233.zip.archive` 内 `evidence-20260621-220643.md`。
- 最新小切片：DDD-USER-PERMISSION-POLICY-TALENT-QUERY 已将 `TalentQueryService.assertCanOperate` 中管理员、渠道组长、渠道专员角色编码集合匹配委托给用户域 `CurrentUserPermissionPolicy.hasAnyRole`，达人查询服务不再维护本地 `hasRole` 归一化实现。本轮不改达人认领归属、达人池可见性、数据范围、列表 / 详情业务筛选、寄样 / 订单补充展示或真实数据。
- 最新报告路径：`harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-2115-2207/evidence-20260621-214308.md`。
- 风险变化：`TalentQueryServiceTest` 覆盖渠道专员非本人拒绝、渠道组长同部门放行和 `" CHANNEL_STAFF "` 归一化本人认领路径；`DddUserFacadeTalentQueryBoundaryTest` 防止达人查询服务重新引入本地 `hasRole`，并约束其继续消费用户域 policy。`TalentService.release` 已在后续小切片完成角色 matcher 收口。
- 最新小切片：DDD-USER-PERMISSION-POLICY-PRODUCT-QUICK-SAMPLE 已将 `ProductQuickSampleService` 快速寄样入口的渠道 / 管理员角色编码集合匹配委托给用户域 `CurrentUserPermissionPolicy.hasAnyRole`，商品域不再维护本地 `hasAnyRole`、`roleCodes.toString()` 或 `Collection` 分支解析。本轮不改快速寄样业务规则、商品展示状态校验、寄样端口委托、外部抖店快速寄样开关或真实数据。
- 最新报告路径：`harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-2115-2207/evidence-20260621-213011.md`。
- 风险变化：`QuickSampleApplyTest` 覆盖非渠道拒绝、管理员放行、渠道专员放行、逗号字符串角色输入兼容、商品上下文校验和端口委托；`DddProduct003ProductRoutingTest` 新增源码边界约束，防止快速寄样入口重新引入本地角色 matcher。`TalentService.release` 已在后续小切片完成角色 matcher 收口。
- 最新小切片：DDD-USER-PERMISSION-POLICY-PERFORMANCE-ACCESS 已将 `PerformanceAccessScope` 中 `admin`、运营、招商 / 渠道组长与专员判断使用的角色编码集合匹配委托给用户域 `CurrentUserPermissionPolicy.hasAnyRole`，业绩域不再维护本地 `hasAnyRole` 归一化实现。本轮不改业绩访问语义、SQL 数据范围条件、提成/归属/服务费公式或真实数据。
- 最新报告路径：`harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-2115-2207/evidence-20260621-212247.md`。
- 风险变化：`PerformanceAccessScopeTest` 继续覆盖 `" ADMIN "` 兼容匹配、导出、月度重算、筛选越权、逐条访问和 SQL 条件拼接；`DddPerformanceAccessPolicyBoundaryTest` 新增约束，防止业绩域重新引入本地 `hasAnyRole` / `toLowerCase(Locale.ROOT)` 角色匹配。`TalentService.release` 已在后续小切片完成角色 matcher 收口。
- 最新小切片：DDD-USER-PERMISSION-POLICY-ACTIVITY-ACCESS 已将 `ActivityAccessService` 中活动读取 / 同步入口使用的角色编码归一与 `admin`、招商角色、招商组长判断委托给用户域 `CurrentUserPermissionPolicy`；`ColonelActivityController` 改为通过活动访问服务消费该 policy，旧静态归一入口保留为兼容旁路。本轮不改活动分配、活动商品同步、活动可读业务规则或真实数据。
- 最新报告路径：`harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-2115-2207/evidence-20260621-211521.md`。
- 风险变化：活动访问服务不再维护本地角色编码解析规则；`ActivityAccessServiceTest` 与 `ColonelActivityControllerTest` 覆盖 lower-case 既有角色路径、字符串角色归一、admin 直通、招商专员强制 mine、招商组长同部门访问和未分配拒绝。剩余风险是其它业务域仍存在局部 `roleCodes` 处理，需要按 U-6/U-10~U-13 分批收口。
- 最新小切片：DDD-USER-DATASCOPE-DATA-APPLICATION 已将 `DataApplicationService` 订单列表、订单汇总、导出、核心指标和独家达人/商家运营监控的数据范围过滤委托给用户域 `DataScopePolicy.applyTo` / `contextRequirement`，数据页服务不再维护本地 `switch(dataScope)` 分支。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/datascope-next/evidence-20260621-192500-data-application-datascope-policy.md`。
- 风险变化：`OrderAttributionService`、`PerformanceMetricsQueryService`、`DashboardService` 与 `DataApplicationService` 的本地 `switch(dataScope)` 已清零；本轮保留 `DataApplicationService` 缺少 user/dept 上下文时抛 `BusinessException` 的既有行为，未改订单事实、业绩公式、导出列或历史数据。
- 最新小切片：DDD-USER-DATASCOPE-DASHBOARD 已将 `DashboardService` 看板 summary fallback 查询、诊断/活动商品下钻 SQL 上下文和 `QueryWrapper` 数据范围过滤委托给用户域 `DataScopePolicy.decide` / `requiresFilter`，看板服务不再维护本地 `switch(dataScope)` 分支。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/datascope-next/evidence-20260621-190800-dashboard-datascope-policy.md`。
- 风险变化：`OrderAttributionService`、`PerformanceMetricsQueryService` 与 `DashboardService` 已消费用户域数据范围 policy；`DataApplicationService` 仍有 5 处 `switch(dataScope)` 需继续收口；本轮未改看板指标公式、订单归因、业绩归属或历史数据。
- 最新小切片：DDD-USER-DATASCOPE-PERFORMANCE-METRICS 已将 `PerformanceMetricsQueryService` 汇总、趋势和 dashboard 业绩指标查询的数据范围过滤委托给用户域 `DataScopePolicy.decide`，业绩查询服务不再维护本地 `switch(dataScope)` 分支。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-185200-performance-metrics-datascope-policy.md`。
- 风险变化：`PerformanceMetricsQueryService` 已消费用户域数据范围 policy；`DashboardService`、`DataApplicationService`、`TalentQueryService.detail`、`TalentService.page`、`TalentService.blacklist/unblacklist` 与 `TalentService.evaluateExclusive` 已完成子路径收口；后续风险转为跨业务域 `DataScopeResolver` / `PermissionChecker` 消费方式尚未统一。
- 最新小切片：DDD-USER-DATASCOPE-ORDER-ATTRIBUTION 已将 `OrderAttributionService` 未归因分页 `co.user_id/co.dept_id` 与摘要查询 `user_id/dept_id` 数据范围过滤委托给用户域 `DataScopePolicy.applyTo`，订单归因查询侧不再维护本地 `switch(dataScope)` 分支。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-175800-order-attribution-datascope-policy.md`。
- 风险变化：订单归因查询侧已消费用户域数据范围 policy；`DashboardService`、`DataApplicationService`、`PerformanceMetricsQueryService`、`TalentQueryService.detail`、`TalentService.page`、`TalentService.blacklist/unblacklist` 与 `TalentService.evaluateExclusive` 已在后续切片收口；后续继续推进跨业务域 `DataScopeResolver` / `PermissionChecker` 消费统一。
- 最新小切片：DDD-SAMPLE-ACTION-PERMISSION-POLICY 已新增 `SampleActionPermissionPolicy`，将 `SampleApplicationService` 中寄样申请、删除、审核、物流推进、物流同步、导出、七天重复申请豁免、私海认领校验和纯运营判断的动作权限语义集中到寄样域 policy；该 policy 继续消费用户域 `CurrentUserPermissionPolicy.hasAnyRole` 解释角色编码集合，应用服务不再直接依赖用户域角色 matcher。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-160300-sample-action-permission-policy.md`。
- 风险变化：用户域仍只负责解释角色编码集合；寄样域已拥有动作权限语义。待确认风险：`SampleApplicationService` 类级注释中曾描述招商组长可创建寄样，但现有代码 / policy 仍只允许管理员、渠道组长和渠道专员发起寄样，本轮未拍板业务规则。
- 最新小切片：U-7 / DDD-USER-PERMISSION-POLICY-SAMPLE-SERVICE 已将 `SampleApplicationService` 中寄样查询默认状态、访问校验、申请 / 删除 / 审核 / 物流 / 导出权限、达人私海校验、七天重复申请豁免和纯运营判断的角色编码集合匹配委托给 `CurrentUserPermissionPolicy.hasAnyRole`，不再维护本地 `roleCodes` 字符串 / 集合解析规则。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-154500-sample-service-role-policy.md`。
- 风险变化：`SampleApplicationService` 已退出本地 roleCodes 解析；寄样动作权限分支已在后续切片集中到寄样域 `SampleActionPermissionPolicy`，未改动寄样状态机。
- 最新小切片：U-7 / DDD-USER-PERMISSION-POLICY-SAMPLE-PORT 已将 `SampleApplicationPortImpl` 的角色编码集合匹配委托给 `CurrentUserPermissionPolicy.hasAnyRole`，业务域 quick sample 入口不再维护本地 `roleCodes` 字符串 / 集合解析规则。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-151700-sample-port-role-policy.md`。
- 风险变化：用户域已提供可复用的角色编码集合解析出口；业务域入口和寄样应用服务均已迁移到该出口，动作权限语义仍需按寄样域策略独立分类。
- 最新小切片：U-7 / DDD-USER-PRODUCT-SERVICE-FACADE 已将 `ProductService` 商品负责人展示、活动/商品负责人归属、转链 `pick_extra` 渠道编码构造和跨部门绑定校验改为消费用户域标量 / 归属引用出口，不再读取完整 `UserOptionResponse`。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-145100-product-service-facade.md`。
- 风险变化：跨业务域 `userDomainFacade.getUserById/getUsersByIds` 生产调用扫描已清零；`UserOptionResponse` 仍保留在用户域主数据下拉 / 兼容门面，不代表跨域泄漏。
- 最新小切片：U-7 / DDD-USER-SAMPLE-APPLICATION-FACADE 已将 `SampleApplicationService` 中寄样创建部门解析改为消费 `UserDomainFacade.loadUserOwnershipReferencesByIds`，将状态日志、导出、详情和看板中的用户展示改为消费 `loadUserDisplayLabelsByIds`，不再读取完整 `UserOptionResponse`。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-142200-sample-application-facade.md`。
- 风险变化：`SampleApplicationService` 已从剩余完整用户 DTO 消费者中移除；剩余 `ProductService` 5 处仍需继续按 U-7 分类收口。
- 最新小切片：U-7 / DDD-USER-EXCLUSIVE-MERCHANT-APPLICATION-FACADE 已将 `ExclusiveMerchantApplicationService` 独家商家评估中的招商负责人部门解析改为消费 `UserDomainFacade.loadUserOwnershipReferencesByIds`，不再为 `recruiterUserId -> deptId` 映射读取完整 `UserOptionResponse`。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-135500-exclusive-merchant-application-facade.md`。
- 风险变化：`ExclusiveMerchantApplicationService` 已从剩余完整用户 DTO 消费者中移除；剩余 `ProductService` 5 处与 `SampleApplicationService` 2 处仍需继续按 U-7 分类收口。
- 最新小切片：U-7 / DDD-USER-OWNERSHIP-REFERENCE 已新增 `UserOwnershipReference` 与 `UserDomainFacade.loadUserOwnershipReferencesByIds`，用于跨域归属覆盖时只读取目标负责人存在性与主组织单元，不再向 `TalentService` / `MerchantService` 泄漏完整 `UserOptionResponse`。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-134600-ownership-reference-facade.md`。
- 风险变化：`TalentService` 与 `MerchantService` 已从剩余完整用户 DTO 消费者中移除；剩余 Product / SampleApplication / ExclusiveMerchantApplicationService 等跨域消费者仍需继续按 U-7 分类收口。
- 最新小切片：U-7 / DDD-USER-COLONEL-ACTIVITY-FACADE 已将 `ColonelActivityController` 活动负责人展示名查询改为消费 `UserDomainFacade.loadUserDisplayNamesByIds` 标量出口，不再为活动列表负责人展示读取完整 `UserOptionResponse`。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-133700-colonel-activity-facade.md`。
- 风险变化：`ColonelActivityController` 已从剩余完整用户 DTO 消费者中移除；剩余 Product / TalentService / Merchant / SampleApplication / ExclusiveMerchantApplicationService 等跨域消费者仍需继续按 U-7 分类收口。
- 当前状态：U-1 现状盘点、U-2 表结构与领域模型对齐、U-2.5-A/B dept_type 统一与最小修复已完成；2026-06-20 已完成当前用户应用服务收口、`CurrentUserPermissionPolicy`、`UserAccessPolicy`、`UserCredentialPolicy` 与 `LegacyUserDomainFacade` 读模型小切片；U-14 已用 Testcontainers 集成测试验证成功改密后的 `sys_user` 状态与 `operation_log` 审计持久化；U-7 已启动跨域出口收口，已防止 `SampleController` 直接导入持久化 Mapper，并将 `UserMasterDataController` 迁移到 `UserMasterDataApplicationService` 出口；`UserChannelCodePolicy`、`UserAccessPolicy`、`UserCredentialPolicy`、`OrgValidationPolicy`、`OrgAssignmentPolicy`、`OrgEnrichmentPolicy` 已通过用户域端口或读模型隔离持久化 Mapper / Entity，`CurrentUserPermissionPolicy` 已通过 `RolePermission` 读模型隔离 `SysRole`，`LegacyUserDomainFacade` 已通过 `UserBasicLookup` 隔离 `SysUserMapper` / `SysUser`，并通过 `DepartmentOptionLookup` 隔离 `SysDeptService` / `SysDept`，`OrgStructureApplicationService` 已改为注入组织 policy，不再直接导入 mapper/entity/infrastructure adapter；`SysDeptApplicationService` 已将负责人展示名用户查询改为 `OrgLeaderDisplayLookup` 端口，并通过 `OrgDepartmentRepository` 隔离 `SysDeptMapper` / `SysDept` CRUD 持久化访问；真实 HTTP 路由使用的 `auth.service.SysDeptService.findAll/findTree/getById/findGroupsByParent/getStats/findMembers` 已迁移到 `OrgUnitDirectoryApplicationService`，`create/update/delete` 已迁移到 `OrgUnitWriteApplicationService`；目录与统计查询通过 `OrgUnitDirectoryLookup` 端口读取，成员分页通过 `OrgUnitMemberLookup` 端口转接，写路径通过 `OrgDepartmentRepository` 与 `OrgValidationPolicy` 保留现有负责人角色校验、修改权限和删除约束，不再由 true-route service 直接调用 `SysDeptMapper` / `SysUserService.findDeptMembers()` / `OrgStructureService`；`auth.service.SysUserService.findAssignableUsers/assertAssignableUser/assertRecruiterUser` 已迁移到 `UserAssignableApplicationService`，`assignRoles` 已迁移到 `SysUserRoleAssignmentApplicationService`，`findPage/findDeptMembers` 已迁移到 `SysUserQueryApplicationService`；`SysUserService` 已压缩为兼容委托入口，不再直接导入 Mapper / Entity / password / audit / event / cache 依赖；`UserAssignmentPolicy` 已通过 `UserAssignmentLookup` 隔离目标用户、角色关系和角色元数据读取，不再直接导入 Mapper / Entity；`UserAssignableApplicationService` 已通过 `UserAssignableCandidateLookup` 隔离候选负责人列表查询与 VO 组装，不再直接导入 MyBatis / Mapper / Entity；`SysUserRoleAssignmentApplicationService` 已通过 `UserRoleAssignmentStore` 隔离用户、角色和用户角色关系读写，不再直接导入 Mapper / Entity；`SysUserQueryApplicationService` 已通过 `UserQueryLookup` 隔离分页 SQL wrapper 与角色关系读取，不再直接导入 QueryWrapper / Mapper / Entity；`SysUserCRUDApplicationA/B` 已通过 `UserCrudMutationStore` 隔离 getById/create/update/delete/resetPassword 路径中的 `SysUserMapper`、`SysRoleMapper`、`SysUserRoleMapper` 和持久化实体；`UserDomainFacade` 已新增登录账号查询出口 `getUsername`、用户显示标签批量出口 `loadUserDisplayLabelsByIds`、用户展示名称批量出口 `loadUserDisplayNamesByIds` 与负责人归属引用出口 `loadUserOwnershipReferencesByIds`，`OperationLogService` 已改为只消费操作人账号，不再为审计日志读取完整 `UserOptionResponse`；`ExclusiveMerchantQueryService` 已改为只消费招商负责人账号，不再为独家商家展示读取完整 `UserOptionResponse`；`SampleFilterOptionsService` 已改为只消费渠道/招商用户显示标签，不再为寄样筛选下拉读取完整 `UserOptionResponse`；`TalentQueryService` 已改为只消费达人认领人显示标签，不再为达人列表/详情认领展示读取完整 `UserOptionResponse`；`DataApplicationService` 已改为只消费订单明细负责人用户展示名称，不再为分析模块订单明细展示读取完整 `UserOptionResponse`；`ExclusiveMerchantApplicationService` 已改为只消费招商负责人归属组织单元，不再为独家商家评估读取完整 `UserOptionResponse`；`SampleApplicationService` 已改为只消费用户归属引用和显示标签，并委托寄样域 `SampleActionPermissionPolicy` 承载动作权限语义，不再为寄样创建、状态日志、导出、详情、看板和权限判断读取完整 `UserOptionResponse` 或直接依赖用户域 role matcher；`OrderAttributionService` 未归因分页与摘要查询已委托 `DataScopePolicy` 处理数据范围过滤；用户域 policy 包持久化 import 扫描已清零，Mapper / Entity 调用下沉到过渡边界。
- 报告路径：U-1 `harness/reports/user-domain-u1-inventory-20260603-120000.md`；U-2 `harness/reports/user-domain-u2-model-schema-alignment-20260603-150000.md`；U-2.5-A `harness/reports/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md`；U-2.5-B `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md`；TEST-1 `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md`；DDD-USER-20260620 `harness/archive/reports-20260620-ddd-user-policy-115705.md`；U-14 `harness/archive/reports-20260620-ddd-user-u14-121830.md`；U-7 `harness/archive/by-date/2026-06-20/evidence-20260620-123319.md`、`harness/archive/reports-20260620-ddd-user-masterdata-124420.md`、`harness/archive/by-date/report-packages/reports-20260620-ddd-user-basic-lookup/evidence-20260620-125715.md`、`harness/archive/by-date/report-packages/reports-20260620-ddd-user-basic-lookup/evidence-20260620-131201.md`、`harness/archive/by-date/report-packages/reports-20260620-ddd-user-basic-lookup/evidence-20260620-132146.md`、`harness/archive/by-date/report-packages/reports-20260620-132255-local-memory-continuation.zip`、`harness/reports/2026-06-20/evidence-20260620-133727.md`、`harness/reports/2026-06-20/evidence-20260620-135130.md`、`harness/reports/2026-06-20/evidence-20260620-141400.md`、`harness/reports/2026-06-20/evidence-20260620-142809.md`、`harness/archive/by-date/report-packages/reports-20260620-ddd-user-basic-lookup/evidence-20260620-195737.md`、`harness/archive/by-date/report-packages/reports-20260620-ddd-user-basic-lookup/evidence-20260620-201847.md`、`harness/reports/2026-06-20/ddd-user/evidence-20260620-203632.md`、`harness/reports/2026-06-20/ddd-user/evidence-20260620-210045.md`、`harness/reports/2026-06-20/ddd-user/evidence-20260620-230204.md`、`harness/reports/evidence-20260621-114910.md`、`harness/reports/evidence-20260621-115909.md`、`harness/reports/evidence-20260621-121159.md`、`harness/reports/2026-06-21/ddd-user/evidence-20260621-122929-operation-log-facade.md`、`harness/reports/2026-06-21/ddd-user/evidence-20260621-124230-exclusive-merchant-facade.md`、`harness/reports/2026-06-21/ddd-user/evidence-20260621-125452-sample-filter-facade.md`、`harness/reports/2026-06-21/ddd-user/evidence-20260621-130900-talent-query-facade.md`、`harness/reports/2026-06-21/ddd-user/evidence-20260621-132400-data-application-facade.md`、`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-135500-exclusive-merchant-application-facade.md`、`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-142200-sample-application-facade.md`。
- U-2.5-B 处理结果：`DeptType.java` 作为唯一 Java 标准，标准值为 `department/recruiter_group/channel_group/ops_group`；`DeptTypes.java` 已删除；`service.SysDeptService` 已迁移到 `DeptType.java`；seed/init 和既有 dept_type 幂等脚本不再写入 `recruiter/channel/dept` 作为新标准。
- 当前补充：`OrderAttributionService`、`PerformanceMetricsQueryService`、`DashboardService` 与 `DataApplicationService` 已委托 `DataScopePolicy` 处理数据范围过滤；`OrderService`、`OrderQueryService`、`TalentQueryService.detail`、`TalentService.blacklist/unblacklist` 与 `TalentService.evaluateExclusive` 保留默认关闭 Legacy 路径，灰度开启后消费用户域 `DataScopePolicy`。
- 当前风险：跨域 Mapper 仍未整体清零，`auth/service/*` 中的 Mapper / Entity 依赖仍需分类；真实 HTTP 路由 `auth.service.SysDeptService` 已完成读写路径收口，`auth.service.SysUserService` 已完成可分配负责人、角色分配、分页查询和部门成员查询委派；`UserAssignableApplicationService` 已清除候选负责人查询与 VO 组装上的 MyBatis / Mapper / Entity 直接依赖，`SysUserRoleAssignmentApplicationService` 已清除用户、角色和关系读写上的 Mapper / Entity 直接依赖，`SysUserQueryApplicationService` 已清除分页 SQL wrapper 和角色关系读取上的 QueryWrapper / Mapper / Entity 直接依赖，`SysUserCRUDApplicationA/B` 已清除 CRUD 过渡应用服务上的 Mapper / Entity 直接依赖；`SysDeptApplicationService` 已通过 `OrgLeaderDisplayLookup` 和 `OrgDepartmentRepository` 清除应用层对 `SysUserMapper` / `SysUser`、`SysDeptMapper` / `SysDept` 的直接依赖；`LegacyUserDomainFacade` 已清除 `SysUserMapper` / `SysUser`、`SysDeptService` / `SysDept` 直接依赖，`OrgStructureApplicationService` 已清除 mapper/entity/infrastructure import；`PerformanceAccessScope` 与 `PerformanceAccessContext` 已迁入 `domain.performance.policy`，不再从 `service.performance` 被消费；`OperationLogService`、`ExclusiveMerchantQueryService`、`SampleFilterOptionsService`、`TalentQueryService`、`DataApplicationService`、`ExclusiveMerchantApplicationService`、`SampleApplicationService` 与 `ProductService` 已退出跨域 `UserOptionResponse` 依赖，跨业务域 `userDomainFacade.getUserById/getUsersByIds` 生产调用扫描已清零；`OrderAttributionService` 与 `PerformanceMetricsQueryService` 已消费 `DataScopePolicy`，`SampleApplicationService` 本地 roleCodes 字符串 / 集合解析已清零，寄样动作权限已集中到 `SampleActionPermissionPolicy`；`UserOptionResponse` 仍保留在用户域主数据下拉 / 兼容门面，不代表跨域泄漏；跨业务域 `DataScopeResolver` / `PermissionChecker` 消费方式尚未完全统一；`UserDomainFacade` 尚未最终收口；`sys_role_menu` FK CASCADE 尚未处理；real-pre 历史 `sys_dept.dept_type` 当前只读对账仍为 `department=3`，如需修复必须单独 DB 任务执行；`/users/current/password` 已补 Controller API 边界、未登录 401 smoke 与成功改密审计持久化集成测试，但 authenticated real-pre 成功路径仍未执行，避免无授权修改真实账号凭证。
- 待优化能力：CurrentUser record、PermissionContext 多角色并集、DataScopeResolver 统一、跨业务域 PermissionChecker 消费统一、UserDomainFacade、改密/审计 API 验证和越权负例补齐。
- DDD 优化下一步：U-7 转入 `UserDomainFacade` 自身兼容 DTO 出口分层：保留下拉 / 主数据 DTO，继续拆分业务域需要的标量出口；并推进 `DataScopeResolver` / `PermissionChecker` 消费统一。U-14 在指定测试账号或授权窗口内补 authenticated real-pre 改密 API/E2E。
- 标记：P0。

## 配置域
- 当前状态：配置读取、变更和审计主链路已具备。
- 已完成能力：配置 API、规则参数、配置变更日志。
- 待优化能力：配置域只出参数的边界审查、配置消费方梳理、异常分支和审计证据补齐。
- DDD 优化下一步：C-1 盘点配置域代码、接口、表和测试。
- 标记：P1。

## 订单域
- 最新边界变化：`OrderQueryService.getOrderDetail` 详情访问数据范围新增灰度开启的用户域 `DataScopePolicy` 路径，默认关闭仍走 Legacy 判断；本轮未改订单事实、归因规则、订单同步、业绩事件、接口参数或历史数据。
- 最新报告路径：`harness/reports/evidence-20260622-141704.md`。
- 当前状态：订单事实、退款事实、同步日志和归因输入已具备；P0-ORDER-001 PAY_RECENT 6h 补拉与同步日志增强已完成（2026-06-03，代码 + 运行态）；ORDER-P0-DUAL-SOURCE-SYNC 已在本地 real-pre 接入 1603 事实订单源并验证入库；ORDER-P0-DUAL-SOURCE-REMOTE-VERIFY 已完成远端部署验证，远端 commit 对齐 `77b723b6`，1603 入库与管理员可见通过；订单明细表字段对齐已完成本地 real-pre 验证（2026-06-04，commit `abf3f9eb`）；ORDER-DETAIL-TAB-FIX-001 已完成前端 16 列扩展与“渠道”文案统一（2026-06-05，commit `db934d99`）；ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001 已完成本地 real-pre 修复验证（2026-06-06），订单已同步事件改为事务提交后发布。
- 已完成能力：订单同步（默认 1603 `INSTITUTE_SETTLEMENT` 结算口径 + PAY_RECENT 6h 30min 兜底回扫 + 1603 INSTITUTE_RECENT 24h 事实订单源）、订单入库、退款事实、归因输入保存、双轨独立 Redis 水位 + 独立锁、同步日志含 `api/mode/timeType/inserted/updated/attributed/unattributed/noPickSource/noMapping/failed` 维度。
- P0-ORDER-001 报告路径：`harness/reports/p0-order-001-real-order-visible-20260603-180450.md`、`harness/reports/p0-order-001-diagnosis-20260603-173500.md`、`harness/reports/p0-order-001-intake-20260603-172923.md`。
- P0-ORDER-001 修改文件：`backend/src/main/java/com/colonel/saas/job/JobLockKeys.java`、`OrderSyncJob.java`、`backend/src/main/java/com/colonel/saas/service/OrderSyncService.java`、`backend/src/main/resources/application.yml`，新增/增强测试 3 个文件 13 用例。
- P0-ORDER-001 运行态对账：本地 backend-real-pre 重启后 health=UP，scheduler-2 立即执行 INCREMENTAL（窗口 628s ≈ 10min+overlap），scheduler-4 立即执行 PAY_RECENT（窗口 21600s = 6h），两 Redis key `order:sync:last_time` + `order:sync:pay_recent_last_time` 共存独立。
- ORDER-P0-DUAL-SOURCE-SYNC 本地证据：`harness/reports/evidence-20260603-202253.md`；1603 事实同步日志 `fetched=100 inserted=100`，订单表 `count(*)=100`，100 条均有 `order_amount/pay_time/estimate_*`，`settle_amount/effective_*` 为 0 条有值，管理员订单列表和未归因列表均返回 `total=100`。
- ORDER-P0-DUAL-SOURCE-REMOTE-VERIFY 远端证据：`harness/reports/order-p0-dual-source-remote-verify-20260603-205719.md`；远端 `backend-real-pre` health=UP，1603 事实同步日志 `fetched=100 inserted=100 updated=0`，2704 对照日志 `fetched=0`，远端订单表 `count(*)=100`，管理员 `/api/orders total=100`、`/api/orders/unattributed total=100`。
- ORDER-DETAIL-FIELD-ALIGN 本地 real-pre 证据：`harness/reports/evidence-20260604-191102.md`；页面 smoke `runtime/qa/out/order-detail-page-smoke-20260604-191711/result.json`。`/api/data/orders/detail` 200、total=738、records=20；订单明细页面 16 个目标列可见，商品信息可见，未结算样本 `6953418877360936032` 显示 `结算：-`，页面不含“媒介”和“毛利”。
- ORDER-DETAIL-TAB-FIX-001 前端展示证据：`harness/reports/order-detail-tab-fix-001-20260605-100305.md`；`harness/reports/evidence-20260604-221838.md`；页面 smoke `runtime/qa/out/order-detail-tab-fix-001-20260605-020048/result.json`。`/api/data/orders/detail` 200、total=933、records=20；订单明细页面 16 个最终表头可见，页面不含“媒介”和“毛利”，金额列显示人民币，未结算 null 显示 `-`。
- ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001 证据：`harness/reports/order-performance-event-after-commit-fix-001-20260606-121000.md`；Harness evidence `harness/reports/evidence-20260606-120829.md`；real-pre preflight `runtime/qa/out/real-pre-preflight-20260606-120648/report.md`。本地后端全量测试 1730/0/0，`backend-real-pre` rebuild/recreate 后 healthy；SQL anti-join 显示历史缺口仍为 `missing_performance=15`。
- 待优化能力：`pick_source` 返回样本与 mapping 命中后的渠道正向可见性验证；Dashboard summary 双轨聚合另开 `ANALYTICS-DUAL-TRACK-SUMMARY-FIX`；如需远端同步展示订单明细新表头，另起远端部署任务。
- 当前风险：真实订单 PAY_RECENT 周期最坏 30 分钟可见延迟，如商务要求更短可调小 cron；抖音 update_time 上限延迟超 6h 时 PAY_RECENT 也会丢，6h 为当前观测经验值；1603 是否稳定返回 `settle_time` / `effective_*` / `flow_point` 仍需 raw probe + dry-run 取证；渠道可见性正向样本仍 PENDING；订单事件时序根因已修复本地 real-pre，但历史 `performance_records` 缺口仍需 backfill，当前本地 anti-join 为 15。
- DDD 优化下一步：先执行 `ORDER-PERFORMANCE-BACKFILL-001` 清理历史缺口；有 `pick_source` 样本后做渠道可见性验证；之后进入 P0-SAMPLE-001 寄样流转，Dashboard summary 双轨单独处理；远端订单明细展示按部署需求单独执行。
- 标记：P0。

## 业绩域
- 最新边界变化：`PerformanceAccessScope` 继续承载业绩域导出、重算、筛选越权、逐条访问和 SQL 范围拼接语义，但角色编码集合匹配已委托用户域 `CurrentUserPermissionPolicy.hasAnyRole`；本轮未改业绩归属、提成、冲正、服务费双轨公式、SQL 条件语义或历史数据。
- 最新报告路径：`harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-2115-2207/evidence-20260621-212247.md`。
- 最新边界变化：`PerformanceMetricsQueryService` 汇总、趋势和 dashboard 业绩指标查询的数据范围决策已委托用户域 `DataScopePolicy`，本轮未改业绩归属、提成、冲正、服务费双轨公式或历史数据。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-185200-performance-metrics-datascope-policy.md`。
- 当前状态：最终归属、提成、冲正和汇总主链路已具备；订单明细 BFF 已按 orderId 批量读取 `performance_records` 补全招商提成、渠道提成、服务费支出和服务费收益展示字段；ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001 已验证新订单事件在提交后发布，业绩 Listener 正常走 `upsertFromOrder`，重复事件仍走 upsert 幂等路径；SERVICE-FEE-INCOME-FORMULA-CODE-001 已按 2026-06-06 用户口径补齐服务费收入公式解析、结算轨不重复扣技术服务费和后端单元验证；`PerformanceAccessScope` / `PerformanceAccessContext` 已从 `service.performance` 迁入 `domain.performance.policy`，业绩访问策略进入业绩域 policy 包；独家商家评估应用服务已通过用户域负责人归属引用获取招商负责人组织单元，不改变独家覆盖规则、订单金额归集或服务费比例计算。
- 报告路径：DDD-PERFORMANCE-ACCESS-POLICY `harness/reports/evidence-20260621-121159.md`；DDD-USER-EXCLUSIVE-MERCHANT-APPLICATION-FACADE `harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-135500-exclusive-merchant-application-facade.md`。
- 已完成能力：`performance_records`、最终归属、提成、冲正、汇总刷新。
- 待优化能力：输入追溯、重复消费幂等、冲正证据、权限数据范围和 dashboard 对账补齐；继续修复 DASH-MONEY-P0-001 结算轨污染问题，避免业绩表历史数据影响看板结算口径；执行 `ORDER-PERFORMANCE-BACKFILL-001` 将当前本地 15 条历史缺口补齐并重新 anti-join；补充 real-pre API / SQL / 页面级服务费收入与收益双轨验收。
- DDD 优化下一步：先执行 `ORDER-PERFORMANCE-BACKFILL-001`；之后进入 Y-1 盘点业绩域代码、接口、表、任务和测试。
- 标记：P0。

## 分析模块
- 最新边界变化：`DataApplicationService` 的数据页订单明细、订单汇总、导出、核心指标和运营监控查询已消费用户域 `DataScopePolicy`；本轮只收口可见性，不改变订单事实、业绩补全、导出列、服务费双轨公式或历史数据。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/datascope-next/evidence-20260621-192500-data-application-datascope-policy.md`。
- 最新边界变化：`DashboardService` 看板 summary fallback 查询、诊断/活动商品下钻 SQL 上下文和 `QueryWrapper` 过滤已消费用户域 `DataScopePolicy.decide` / `requiresFilter`；本轮只收口可见性，不改变指标公式、订单归因或业绩归属。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/datascope-next/evidence-20260621-190800-dashboard-datascope-policy.md`。
- 当前状态：dashboard、报表和只读汇总主链路已具备；数据平台订单页已保留汇总模块，并新增/收口订单明细 Tab 与 16 列订单级明细导出；ORDER-DETAIL-TAB-FIX-001 已补齐订单明细 Tab 前端 16 列展示、人民币金额格式与“渠道”文案统一；SERVICE-FEE-INCOME-FORMULA-CODE-001 已更新经营指标矩阵服务费收入 / 收益双轨公式口径与后端展示层单测；`DataApplicationService` 订单明细负责人展示已改为消费用户域 `loadUserDisplayNamesByIds`，分析模块不再为该展示读取完整用户 DTO。
- 报告路径：DDD-USER-DATA-APPLICATION-FACADE `harness/reports/2026-06-21/ddd-user/evidence-20260621-132400-data-application-facade.md`。
- 已完成能力：看板汇总、报表查询、导出能力。
- 待优化能力：只读边界审查、dashboard API/SQL 对账、admin/group/self 差异验证；Dashboard summary 双轨聚合和历史结算轨污染仍需专项处理；补充 dashboard / 前端页面级服务费收入与收益双轨验收。
- DDD 优化下一步：进入 A-1 盘点分析模块代码、接口、表和测试。
- 标记：P0。

## 商品域
- 最新边界变化：`ProductQuickSampleService` 快速寄样入口继续负责商品存在性、展示中状态、商品库入库状态、商品快照/主表上下文和寄样域端口委托，但角色编码集合匹配已委托用户域 `CurrentUserPermissionPolicy.hasAnyRole`；本轮未改商品状态机、转链规则、`pick_source` 归因语义、寄样状态机或真实数据。
- 最新报告路径：`harness/archive/by-date/report-packages/reports-20260621-ddd-role-policy-2115-2207/evidence-20260621-213011.md`。
- 最新边界变化：活动列表负责人展示已通过用户域 `loadUserDisplayNamesByIds` 出口读取，只消费展示名称标量，不改变活动分配、活动商品同步或商品库展示规则。
- 最新边界变化：`ProductService` 已通过用户域显示标签、归属引用、渠道编码出口完成负责人展示、分配、转链 `pick_extra` 和跨部门绑定校验，不改变商品状态机、转链规则或 `pick_source` 归因语义。
- 最新报告路径：`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-145100-product-service-facade.md`。
- 当前状态：商品库、活动商品同步、转链和映射主链路已具备。FUNC-001 卡片改造已完成。P-FIX-001C 分页弱化已完成。P-DIAG-002 商品库数量不足排查已完成。P-FIX-002A 同步任务 5 分钟周期配置已完成。P-FIX-002 代码与配置准备已完成。P-FIX-002D 本地运行态验证已完成。P-FIX-002D-REMOTE 远端部署验证已完成（2026-06-03），远端 commit=dea06e4c，同步参数生效，两个周期零冲突。GIT-BATCH-2 frontend-product-ui 已于 2026-06-03 14:08 提交并部署（commit=5fe6ba23）。PRODUCT-LIBRARY-FULL-BACKFILL-FIX-001：Phase 4-1.5B PASS。单活动 3859423 真实 backfill 与幂等复跑已验证通过；允许进入 RECENT_30D maxActivities=20 小批量回补。当前工作区历史未提交改动已在 Phase 4-2 前置门禁中复查，Git 视角仅发现两个无效临时 dry-run 文件并已清理。
- 已完成能力：商品库、活动商品、转链、`pick_source_mapping`；FUNC-001 卡片 UI；P-FIX-001C 分页优化；P-FIX-002A 同步周期配置；P-FIX-002B 唯一索引冲突修复（两遍处理）。
- P-FIX-002 修复结论：`applyNormalDisplayDedup` 改为三阶段持久化（先降级旧 DISPLAYING→HIDDEN，再处理其他非 DISPLAYING，最后升级新 winner→DISPLAYING），避免 `uk_pos_one_displaying_per_product` partial unique index 冲突。新增/补齐 4 个相关测试覆盖严格调用顺序、切换顺序、幂等性和多候选场景。
- P-FIX-002 报告路径：`harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`。
- P-FIX-002D 报告路径：`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`。
- P-FIX-002D-REMOTE 报告路径：`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md`。
- P-FIX-002 修改文件：`ProductActivitySyncJob.java`、`ProductDisplayRuleService.java`、`ProductDisplayRuleServiceTest.java`、`application.yml`、`docker-compose.real-pre.yml`、`.env.real-pre.example`、`remote-deploy.md`、`deploy-remote.ps1`。
- P-FIX-002 只读对账：本地 7284 快照 / 7284 运营状态 / 1963 DISPLAYING / 4566 HIDDEN / 755 PENDING / 无重复 DISPLAYING / 716 推广中但未展示；商品库 API total=1963 与 SQL DISPLAYING 一致。
- P-FIX-002D 运行态对账：重启后同步执行，本地 7323 快照 / 2377 DISPLAYING / 4575 HIDDEN / 371 PENDING / 无重复；API total=2377 与 SQL 一致。
- P-FIX-002D-REMOTE 远端对账：远端 3846 快照 / 604 DISPLAYING / 1114 HIDDEN / 2128 PENDING / 无重复；API total=604 与 SQL 一致。远端 DISPLAYING 从 420 增长到 604（+184）。
- P-DIAG-002 报告路径：`harness/reports/p-diag-002-product-library-count-sync-remote-20260603-114742.md`。
- P-FIX-002A 报告路径：`harness/reports/p-fix-002a-product-sync-5min-config-20260603-120100.md`。
- P-FIX-001C 报告路径：`harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md`。
- FUNC-001 报告路径：`harness/reports/func-001-product-card-hover-ui-20260603-111451.md`。
- GIT-BATCH-2 报告路径：`harness/reports/git-batch-2-frontend-product-ui-20260603-140800.md`。
- PRODUCT-LIBRARY-FULL-BACKFILL-FIX-001 报告路径：`harness/reports/evidence-20260615-114620.md`；`harness/reports/product-library-full-backfill-evidence-20260615.md`；`harness/reports/product-library-backfill-observability-fix-final-20260616-0832.md`。
- 当前风险：1) 本地 PENDING 总量 371，远端 PENDING 总量 2128，均需后续区分过期活动、未选中和待重算来源；2) 远端 env 手工补齐了 `PRODUCT_ACTIVITY_SYNC_ENABLED` 和 `PRODUCT_ACTIVITY_SYNC_CRON`，需纳入部署文档；3) Gitee 双 remote 同步流程需确保每次推送同时 push 到 gitee；4) Phase 4-2 仍未全量补齐，成功标准是 20 个活动小批量补数稳定：无死锁、无重复、无锁残留、展示口径不乱、失败可定位。
- 待优化能力：活动商品状态断链 repair、远端部署对齐、推广中商品自动入库。
- DDD 优化下一步：Phase 4-2 先执行 RECENT_30D、maxActivities=20、dryRun=true 预评估；dry-run PASS 后再执行 RECENT_30D、maxActivities=20、dryRun=false、confirm=true 受控真实回补，并验证 job log 无 RUNNING、Redis lock 无残留、duplicate=0、DISPLAYING total 与 `/api/products` total 稳定、`/api/products/admin/counts` 全量口径增长合理、backend health=UP。
- 标记：P0。

## 达人域
- 最新边界变化：`TalentService.evaluateExclusive` 独家达人评估订单查询数据范围新增灰度开启的用户域 `DataScopePolicy` 路径，默认关闭仍走 Legacy PERSONAL user / DEPT dept 过滤；本轮未改独家评估公式、订单佣金读取、寄样次数统计、达人认领规则、黑名单、第三方接口或真实数据。报告：`harness/reports/evidence-20260622-151700.md`。
- 近期边界变化：`TalentService.blacklist/unblacklist`、`TalentService.page` 和 `TalentQueryService.detail` 已新增灰度开启的用户域 `DataScopePolicy` 路径；`TalentService.release` 管理员角色编码匹配、`TalentQueryService.assertCanOperate` 操作访问角色匹配已委托用户域 policy；达人归属覆盖已通过用户域 `loadUserOwnershipReferencesByIds` 校验目标负责人存在，不再读取完整用户 DTO，既有达人认领记录 `deptId` 写入行为不变。
- 当前状态：达人资料、标签、地址和跟进主链路已具备。
- 已完成能力：达人列表 / 详情、标签、地址、跟进。
- 待优化能力：认领、保护期、第三方接口证据、`gender` 筛选缺口和权限负例补齐。
- DDD 优化下一步：T-1 盘点达人域代码、接口、表和测试。
- 标记：P1。

## 寄样域
- 最新边界变化：`SampleLogisticsImportService` 继续负责物流 Excel 解析、行级校验、状态流转到已发货、事件发布与物流订阅，但物流导入/覆盖动作权限已委托 `SampleActionPermissionPolicy`，该 policy 消费用户域 `CurrentUserPermissionPolicy.hasAnyRole`；本轮未改寄样状态机、物流 API、事件语义或真实数据。报告：`harness/reports/reports-20260621-2206-20260622-1233.zip.archive` 内 `evidence-20260621-220643.md`。
- 当前状态：申请、审批、发货和订单事件自动完成链路已具备。TALENT-ADDRESS-SAMPLE-DEFAULT 达人寄样地址默认保存已完成（2026-06-03）；2026-06-20 已补 `SampleController` 架构测试，防止 HTTP 入口重新直接导入持久化 Mapper；`SampleApplicationService` 已通过用户域归属引用读取创建人部门、通过用户显示标签读取状态日志/导出/详情/看板展示名，并通过寄样域 `SampleActionPermissionPolicy` 承载寄样动作权限；该策略消费用户域 `CurrentUserPermissionPolicy.hasAnyRole` 匹配角色编码集合，不改变寄样状态机或历史数据；`SampleApplicationPortImpl` quick sample 入口、`SampleFilterOptionsService` 寄样筛选下拉和 `SampleLogisticsImportService` 物流导入入口均已收口到 policy / 用户域角色解释，不再本地解析 `roleCodes`；real-pre 仍依赖真实归因订单样本。
- 已完成能力：寄样申请、审批、发货、状态日志、订单事件消费；**地址默认保存**（寄样成功后回写 `talent_claim`，下次选达人自动带入，修改后更新，历史快照不变，多渠道隔离）。
- TALENT-ADDRESS-SAMPLE-DEFAULT 报告路径：`harness/reports/talent-address-sample-default-20260603-224000.md`。
- DDD-USER-SAMPLE-APPLICATION-FACADE 报告路径：`harness/reports/2026-06-21/ddd-user/facade-next/evidence-20260621-142200-sample-application-facade.md`。
- DDD-USER-PERMISSION-POLICY-SAMPLE-PORT 报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-151700-sample-port-role-policy.md`。
- DDD-USER-PERMISSION-POLICY-SAMPLE-SERVICE 报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-154500-sample-service-role-policy.md`。
- DDD-SAMPLE-ACTION-PERMISSION-POLICY 报告路径：`harness/reports/2026-06-21/ddd-user/permission-next/evidence-20260621-160300-sample-action-permission-policy.md`。
- TALENT-ADDRESS-SAMPLE-DEFAULT 修改文件：`ProductQuickSampleService.java`、`SampleApplicationService.java`（后端回写）；`QuickSampleModal.vue`、`SampleCreateModal.vue`（前端加载+提交）；测试 4 文件 8 用例。
- 待优化能力：状态机完整验证、交作业命中条件、重复消费幂等和真实样本证据补齐。
- DDD 优化下一步：S-1 盘点寄样域代码、接口、表、状态机和测试。
- 标记：P0。

## Harness
- 当前状态：GIT-HARNESS-001 工作区治理完成（2026-06-03）。
- 已完成能力：Completion Gate (G0-G4)、Session Exit Gate、Quality Ledger、Git Intake / Exit Gate、Dirty Classification (10 种分类)、Allowed Change Set、Staged Scope Gate、Commit / Push / Deploy Commit Gate、批次提交流程 (GIT-BATCH-N)、Unknown Dirty Policy、Rollback Policy。
- 当前风险：需要把所有任务落地到 Git Intake / Exit Gate；P-FIX-002 同步配置残留 (application-real-pre.yml) 仍需独立任务收口。
- DDD 优化下一步：所有未来任务必须按 git-change-control.md 执行 12 条 Git 强约束。
- 标记：P0。
