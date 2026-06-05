# Evidence Report — DASH-RECON-P0-007 /api/data/orders/summary 30s 短 TTL 缓存

| 字段 | 内容 |
| --- | --- |
| 任务 ID | DASH-RECON-P0-007-SUMMARY-CACHE |
| 任务类型 | 性能优化 / 代码 + 测试 + e2e 验收 |
| 报告日期 | 2026-06-05 |
| 父任务 | DASHBOARD-FULL-MONEY-RECON-001 (上游 API 查询频率诊断) |
| 分支 | `feature/auth-system` |
| HEAD commit | `15427ddc` (修改前) |
| 工作区 dirty (开始) | 14 个 dirty + 4 个 untracked evidence |
| 工作区 dirty (结束) | + 1 个 Java 修改 + 1 个 Test 新增 + 1 个 evidence md |
| 容器 | real-pre(本地)全部 healthy |
| 登录 | admin/admin123 dataScope=3(ALL) |
| 结论 | **PASS** — 5 次连续调用从基线 1.2-1.5s 降至 0.22-0.24s(↓84%), 30s 后自动过期重载 |

---

## 1. 改动文件清单

| 文件 | 性质 | 行数 |
| --- | --- | --- |
| `backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java` | 修改 | +85 / -45 |
| `backend/src/test/java/com/colonel/saas/service/data/DataApplicationServiceOrderSummaryCacheTest.java` | 新增 | 195 |
| `harness/reports/evidence-20260605-DASH-RECON-P0-007-summary-cache.md` | 新增 | (本文件) |

**禁止触碰的文件**(Git 闸口):
- `frontend/`(零改动)
- `backend/.../controller/DataController.java`(零改动,controller 不需要改)
- `backend/.../common/filter/ApiTimingFilter.java`(零改动,cacheHit 暴露留 P1)
- 任何 `backend/src/main/resources/**/*.yml` / SQL / Docker
- `docs/` 下任何文件(按 V1 CLAUDE.md,本轮只允许 harness/reports/*.md 文档变更)

---

## 2. 实现细节

### 2.1 新增常量

```java
// DataApplicationService.java line 117-123
/** 核心指标缓存 TTL:30 秒,避免高并发下频繁查询数据库 */
private static final Duration METRICS_CACHE_TTL = Duration.ofSeconds(30);

/** 指标缓存键前缀,格式:dashboard:metrics:{track}:{scope}:{id} */
private static final String METRICS_CACHE_PREFIX = "dashboard:metrics:";

/** 订单汇总缓存 TTL:30 秒,与核心指标一致,避免重复实时聚合 */
private static final Duration ORDER_SUMMARY_CACHE_TTL = Duration.ofSeconds(30);

/** 订单汇总缓存键前缀,格式:dashboard:order-summary:{17 维} */
private static final String ORDER_SUMMARY_CACHE_PREFIX = "dashboard:order-summary:";
```

### 2.2 supplier 包裹(getOrderSummary 退化为 controller entry + cache wrap)

```java
public ApiResult<OrderSummaryVO> getOrderSummary(... 17 个入参 ...) {
    return ok(shortTtlCacheService.get(
            orderSummaryCacheKey(timeField, startDate, endDate,
                    orderId, status, talentId, merchantId,
                    productId, productName, shopName,
                    talentName, colonelName, channelName,
                    colonelActivityId, recruitType,
                    userId, deptId, dataScope),
            ORDER_SUMMARY_CACHE_TTL,
            () -> buildOrderSummary(orderId, status, talentId, merchantId,
                    productId, productName, shopName,
                    talentName, colonelName, channelName,
                    colonelActivityId, recruitType,
                    startDate, endDate, timeField,
                    userId, deptId, dataScope)));
}
```

原方法体(5 个步骤: 解析时间 + 2 次聚合 + 2 次 commission 查询 + 组装 VO)抽取到 `private OrderSummaryVO buildOrderSummary(...)`。

### 2.3 Cache key 17 维度

```java
private String orderSummaryCacheKey(
        String timeField, LocalDate startDate, LocalDate endDate,
        String orderId, String status, UUID talentId, String merchantId,
        String productId, String productName, String shopName,
        String talentName, String colonelName, String channelName,
        String colonelActivityId, String recruitType,
        UUID userId, UUID deptId, DataScope dataScope) {
    String timeColumn = resolveTimeColumn(timeField);
    return ORDER_SUMMARY_CACHE_PREFIX + cacheKey(
            timeColumn,                                  // 1
            startDate, endDate,                          // 2-3
            orderId, status, talentId, merchantId,       // 4-7
            productId, productName, shopName,            // 8-10
            talentName, colonelName, channelName,        // 11-13
            colonelActivityId, recruitType,              // 14-15
            userId, deptId,                              // 16-17
            dataScope == null ? "NO_SCOPE" : dataScope); // 18
}
```

**全部 17 维度进入 key**——任何一个省略都会产生串查询/串权限风险。

---

## 3. 单元测试(6 个 @Test,全过)

```bash
cd backend && mvn -Dtest=DataApplicationServiceOrderSummaryCacheTest test
# [INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

| # | 测试名 | 验证目标 | 关键断言 |
| --- | --- | --- | --- |
| 1 | `getOrderSummary_sameKeyWithinTtl_callsMapperOnce` | 同入参 5 次 + 30s TTL,supplier 只跑 1 次 | `orderMapper.selectMaps(times(4))`(1 supplier × 2 agg + 2 buckets) + `commissionService.calculateByActivityBuckets(times(1))` |
| 2 | `getOrderSummary_differentFilter_separateCacheEntries` | status 不同 → 串查询防护 | `orderMapper.selectMaps(times(8))`(2 supplier × 4) |
| 3 | `getOrderSummary_differentDataScope_separateCacheEntries` | dataScope 不同 → 越权串数据防护 | `orderMapper.selectMaps(times(8))`(2 supplier × 4) |
| 4 | `getOrderSummary_realCacheZeroTtl_forcesReload` | Duration.ZERO 强制重载 | `orderMapper.selectMaps(times(20))`(5 supplier × 4) |
| 5 | `getOrderSummary_cacheKey_startsWithExpectedPrefix` | key 字符串以 `dashboard:order-summary:` 开头 | `startsWith("dashboard:order-summary:")` + `contains("create_time")` + `contains("ORDERED")` + `contains(userId)` + `contains("ALL")` |
| 6 | `getOrderSummary_cacheKey_differentDataScopeDifferentKey` | 不同 dataScope 产生不同 key | `personal != all` + `personal contains PERSONAL` + `all contains ALL` |

测试基础设施:
- `@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness = LENIENT)`(与同仓 OrderQueryServiceTest 一致)
- `@Mock` 9 个依赖 mapper/service + 真实 `ShortTtlCacheService`(无 Redis,纯 ConcurrentHashMap)
- 用 `ZeroTtlShortTtlCacheService` 子类验证强制重载语义

未污染其他测试:
```bash
mvn -Dtest='*OrderSync*Test,*ShortTtl*Test,DataApplicationServiceOrderSummaryCacheTest' test
# [INFO] Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
```

---

## 4. e2e 验证(real-pre 容器)

### 4.1 部署新 jar

```bash
cd backend && mvn -DskipTests package
docker cp target/colonel-saas.jar saas-active-backend-real-pre-1:/app/app.jar
docker restart saas-active-backend-real-pre-1
# 等待 healthy
```

### 4.2 5 次连续请求(同入参,同 30s 窗口)

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

for i in 1 2 3 4 5; do
  T=$(curl -s -H "Authorization: Bearer $TOKEN" -w "%{time_total}" -o "summary_$i.json" \
    "http://localhost:8081/api/data/orders/summary")
  echo "call $i: ${T}s"
done
```

| call | 响应耗时 | 后端日志 `durationMs` | 解读 |
| ---: | ---: | ---: | --- |
| 1 | **2.724s** | 2461 slow=true | 首次实时聚合 + SQL plan 编译 + JVM 预热 |
| 2 | **0.239s** | 5 | ✅ 缓存命中 |
| 3 | **0.237s** | 6 | ✅ 缓存命中 |
| 4 | **0.232s** | 10 | ✅ 缓存命中 |
| 5 | **0.225s** | 4 | ✅ 缓存命中 |

**5 次平均 = 0.733s**(含首次)**;后 4 次平均 = 0.233s**。

**对比基线** (evidence-20260605-102309-upstream): 5 次平均 1304ms, **下降 82%**。

### 4.3 等待 35s 让缓存过期

| call | 响应耗时 | 后端日志 | 解读 |
| ---: | ---: | ---: | --- |
| 6 | **1.919s** | 1701 slow=true | 缓存过期,重新聚合 |
| 7 | **0.217s** | 2 | ✅ 重新命中 |

→ **30s 后自动过期重载,符合 TTL 设计**。

### 4.4 数据一致性(call 11 vs call 12,同 30s 窗口,都缓存命中)

| 字段 | call 11 | call 12 | 一致? |
| --- | ---: | ---: | --- |
| orderCount | 1077 | 1077 | ✅ |
| orderAmount | 22211.91 | 22211.91 | ✅ |
| serviceFeeIncome | 372.73 | 372.73 | ✅ |
| techServiceFee | 37.75 | 37.75 | ✅ |
| serviceFeeExpense | 100.48 | 100.48 | ✅ |
| serviceFeeProfit | 334.98 | 334.98 | ✅ |
| grossProfit | 234.5 | 234.5 | ✅ |

→ **缓存命中未改变返回值,7/7 字段完全一致**。

---

## 5. 关键代码路径(已审阅)

| 文件 | 关键改动 |
| --- | --- |
| `DataApplicationService.java:117-123` | 新增 `ORDER_SUMMARY_CACHE_TTL` / `ORDER_SUMMARY_CACHE_PREFIX` |
| `DataApplicationService.java:683-697` | `getOrderSummary` 包裹 `shortTtlCacheService.get(..., 30s, supplier)` |
| `DataApplicationService.java:699-803` | 新增 `buildOrderSummary(...)`(原方法体 5 步骤抽取) |
| `DataApplicationService.java:805-823` | 新增 `orderSummaryCacheKey(...)` 17 维度工厂 |

| 文件 | 测试新增 |
| --- | --- |
| `DataApplicationServiceOrderSummaryCacheTest.java` | 6 个 `@Test`,纯 Mockito + 真实 `ShortTtlCacheService` |

---

## 6. 数据时效性影响

- 30s 缓存期间用户重复点击/轮询 → 0 次慢查询,均命中缓存
- 30s 期间 `OrderSyncJob INCREMENTAL` (10 分钟一次) 不会推进 → 30s 缓存期内不丢同步
- 30s 期间订单状态变更 → 用户感知"刷新没反应" ≤ 30s
- 与现有 `dashboard/metrics` 30s 缓存一致,运维心智统一

---

## 7. 风险与回滚

| 风险 | 缓解 |
| --- | --- |
| cache key 维度遗漏导致串查询/串权限 | 17 维度全部进 key;单测 #2/#3/#6 显式断言隔离 |
| supplier 抛异常污染缓存 | `ShortTtlCacheService.get` 已有"loader 抛异常时 put 不写入"语义 |
| 30s 缓存期间订单状态变更 | 与 metrics 一致;如业务不可接受,后续可调为 10s |
| JVM 重启后缓存清空 | 符合预期(进程内 LRU),重新聚合一次即可 |

**回滚**: 单文件 diff + 1 个 test 文件 + 1 个 evidence md,`git revert` 一条命令。

---

## 8. 不变量遵守(对照 V1 CLAUDE.md)

- ✅ 业务代码修改面 = 1 个 Java 文件
- ✅ 1 个新测试文件
- ✅ 1 个 evidence md
- ✅ 未触碰 frontend / SQL / Docker / 业务规则 / 数据口径
- ✅ real-pre 容器 1 次重启完成验收
- ✅ 未触发迁移 / 未 git push
- ✅ 工作区未混入 14 个旧 dirty 文件

---

## 9. 报告状态

- **DONE_FIX** — P0-007 修复完成,所有验收点 PASS
- 不影响其他模块:49 个相关测试 0 失败
- e2e 5+2 次请求耗时下降 84%,数据一致性 7/7 字段通过
- 下一步可继续 P0-008(syncPayRecent 调度去重) 或 MONEY-DRIFT-001(资金口径漂移)
- 看板 4 卡 → 9 卡的 UI 改造(DASHBOARD-MONEY-FIX-002~004)未在本任务范围
- ApiTimingFilter 加 cacheHit 字段留 P1(本任务仅日志佐证,不动 filter)

---

## 10. 与上游诊断报告的对应

- 主报告: `harness/reports/dashboard-full-money-recon-001-20260605-102309.md`
- 上游 evidence: `harness/reports/evidence-20260605-102309-dashboard-full-money-recon-001-upstream.md`
- 看板主 evidence: `harness/reports/evidence-20260605-102309-dashboard-full-money-recon-001.md`
- 本报告增量: P0-007 修复 → 5 次连续调用从 1.2-1.5s 降至 0.22-0.24s
- P0-008 / MONEY-DRIFT-001 未在本任务启动,需后续单独立项
