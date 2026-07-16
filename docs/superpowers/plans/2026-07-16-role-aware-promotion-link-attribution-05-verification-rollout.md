# Phase 5：本地交付与远端受控修复

## Task 1：完整本地验证

**Files:**

- Create/Update: `harness/reports/current/latest-role-aware-link-attribution.md`

- [ ] 后端重点回归：

```powershell
mvn -f backend/pom.xml -Dtest=PromotionAttributionOwnerPolicyTest,LegacyUserDomainFacadeTest,ProductServiceColonelBuyinIdTest,PickSourceMappingServiceTest,OrderPickSourceMappingAdapterTest,OrderDefaultAttributionPolicyTest,OrderDefaultAttributionResolverTest,OrderAttributionRouterTest,PerformanceCalculationApplicationServiceTest,PerformanceAccessScopeTest,OrderAttributionReplayServiceTest,AttributionOwnerReconciliationServiceTest,AttributionAdminControllerTest,RealPreMigrationContractTest test
```

- [ ] 架构和 mapper 合同：

```powershell
mvn -f backend/pom.xml -Dtest=DddUserFacadeProductServiceBoundaryTest,DddOrderDefaultAttributionInputContractTest,DddOrder003RoutingTest,DddOrderPerformanceBoundaryTest,DddPerformanceAttributionTraceabilityContractTest,ColonelsettlementOrderMapperXmlTest test
```

- [ ] 前端回归与构建：

```powershell
npm --prefix frontend run test -- --run src/views/product/ProductLibrary.test.ts src/views/product/product-actions.test.ts src/views/data/OrderDetailTab.test.ts src/views/orders/components/OrderDetailModal.test.ts
npm --prefix frontend run build
```

Expected: 全部测试和构建 PASS。

- [ ] 从真实 diff 生成 OwnedFiles，执行唯一 Harness 入口：

```powershell
$ownedFiles = ((git diff --name-only 04629dde...HEAD) -join ';')
if ([string]::IsNullOrWhiteSpace($ownedFiles)) { throw 'OwnedFiles is empty' }
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey role-aware-link-attribution -OwnedFiles $ownedFiles -Message "fix: role-aware promotion link attribution"
```

Expected: 构建通过、本地 real-pre 对应容器实际重启、健康检查通过、业务验证有真实结论、evidence 生成、当前分支提交并推送。阻塞项必须标记 `PARTIAL/BLOCKED`。

- [ ] Harness 分层门禁：

```powershell
powershell -ExecutionPolicy Bypass -File harness/scripts/check-harness-limits.ps1 -BaselineRef HEAD
git status --short
git rev-parse HEAD
git rev-parse '@{upstream}'
```

Expected: 当前任务无新增超限、工作树干净、HEAD 等于上游；历史健康债务保持 `REPOSITORY_HEALTH=PARTIAL`。

## Task 2：远端上线门禁

本任务默认停止在本地验证和分支 push。只有用户明确要求“部署远端并修复这笔订单”后，才继续。

- [ ] 上线前只读确认用户 `1c34b680-30b2-41ec-bdc7-2dde1f37e786`、订单 `6927995582750227729`、活动 `3916506`、商品 `3829804874841849888`、两条链接/映射和当前业绩事实。
- [ ] 用固定脚本部署：

```powershell
$ownedFiles = ((git diff --name-only 04629dde...HEAD) -join ';')
if ([string]::IsNullOrWhiteSpace($ownedFiles)) { throw 'OwnedFiles is empty' }
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey role-aware-link-attribution -OwnedFiles $ownedFiles -DeployRemote true -Message "deploy: role-aware promotion link attribution"
```

Expected: 迁移、远端容器和接口健康；失败即停止数据动作。

## Task 3：受审计修复壮云单据

- [ ] 通过现有用户 API 将壮云从错误渠道角色纠正为业务确认的招商角色；不直接 SQL。
- [ ] 分类 dry-run：

```json
POST /api/order-attribution/admin/mapping-owner-reconcile
{
  "userIds": ["1c34b680-30b2-41ec-bdc7-2dde1f37e786"],
  "limit": 200,
  "dryRun": true,
  "confirm": false
}
```

Expected: proposal 为 `RECRUITER`、冲突数 0；逐条核对 activity/product/time/potential orders。

- [ ] 人工确认后只改 `dryRun=false, confirm=true` 执行分类；再次 dry-run 应无待分类记录。
- [ ] 指定订单重放 dry-run：

```json
{
  "orderIds": ["6927995582750227729"],
  "reason": "role-aware attribution correction for verified order",
  "limit": 1,
  "dryRun": true
}
```

Expected: recruiter 为壮云 UUID，source=`native_unique_link_owner`，safe=true；否则禁止 apply。

- [ ] 人工确认后只改 `dryRun=false`，不扩大 orderIds。
- [ ] 四方验证：DB 订单/业绩事实；API 壮云 PERSONAL 可见且他人不可越权；页面招商为壮云且无团长 fallback；日志无 ambiguous、mapping-after-order、权限或业绩刷新异常。

## 回滚

不删除迁移列。用重放前 evidence 的旧订单/业绩事实做受审计恢复，再纠正角色或映射分类；代码回滚走正常分支部署。禁止清库、删 volume 或无审计覆盖。
