# Evidence: DDD100-CONFIG-READ (Issue #39) — 配置读取、缓存与参数出口收口

## 基本信息

- Time: 2026-06-27 12:13 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #39 [DDD100-CONFIG-READ] 配置读取、缓存与参数出口收口
- 类型: 配置读路径 + 缓存 + 参数出口
- 阻塞: #31 (DDD100-GUARD)

## 现有测试覆盖 (不重复造轮子)

### 配置读取 Service
- **SysConfigServiceTest (4/4 PASS)** - 守护 getConfigValue / 分组 (douyin=******, talent=30)
- **BusinessRuleConfigServiceTest (11/11 PASS)** - 守护 getPromotionPickExtraRule + fallback
- **LegacyConfigDomainFacadeTest (22/22 nested)** - aggregateDtos 11 + rawAccessors 11

### 缓存守护
- **ShortTtlCacheServiceTest (5/5 PASS)** - 守护 TTL 缓存
- **ShortTtlCacheRedisInvalidationConfigTest (2/2 PASS)** - Redis 失效配置
  - `RedisMessageListenerContainer` + `EVICT_CHANNEL` 守护
- **DataApplicationServiceOrderSummaryCacheTest** - 真实 ShortTtlCacheService + Duration.ZERO (5 次重载)
- **UserPermissionCacheServiceTest** - 缓存使用

### 架构护栏
- DddConfig003ConfigRoutingTest (7/7, productCopyTemplate_shouldReadFromFacade + pickExtraRule_shouldReadFromFacade)
- DddUserSysConfigAdminRolePolicyBoundaryTest (1/1)

### 其他 consumer 测试
- SysConfigServiceEventTest (DDD-CONFIG-004, 集成测试)
- RuleCenterServiceTest (10/10)
- RuleCenterControllerTest (10/10)
- DddUserSysConfigAdminRolePolicyBoundaryTest

## 验证证据

- mvn test -Dtest="ShortTtlCacheServiceTest,ShortTtlCacheRedisInvalidationConfigTest,SysConfigServiceTest,BusinessRuleConfigServiceTest,LegacyConfigDomainFacadeTest":
  - **44/44 PASS** (5+2+4+11+22 nested)
  - Total time: 15.2s

## 配置域只读消费边界

- **缓存层**: ShortTtlCacheService (TTL + Redis EVICT_CHANNEL 失效广播)
- **服务层**: BusinessRuleConfigService / SysConfigService
- **Facade 层**: ConfigDomainFacade (DDD-CONFIG-001/002/004)
- **不执行业务规则**: DddConfig003ConfigRoutingTest 守护只读路径

## 边界确认

- ✅ Config 域只读 facade 单向输出
- ✅ TTL 缓存 + Redis 失效广播
- ✅ 业务规则不执行 (DDD-CONFIG-001 raw + aggregate 两条只读路径)
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ GUARD 守门 (DddConfig003ConfigRoutingTest 7/7)

## 与 #31 关系

- #31 DDD100-GUARD: 架构护栏 + 跨域依赖扫描
- #39 是配置读路径收口, 与 #31 独立
- 现有 baseline 已覆盖大部分, 待 #31 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (44/44 tests PASS)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #31)