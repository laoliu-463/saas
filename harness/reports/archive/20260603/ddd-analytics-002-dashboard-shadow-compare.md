# DDD-ANALYTICS-002: Dashboard Shadow Compare — Evidence Report

## 基本信息

- **时间**: 2026-06-10 19:45 CST
- **环境**: local real-pre
- **分支**: 当前开发分支
- **工作区状态**: clean（仅本次变更）

## 目标

新增 Dashboard 新旧路径影子对账。用户仍看到旧 Dashboard 结果，后台同时计算新汇总结果并记录 diff。

## 变更文件

### 新增文件

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/.../service/DashboardShadowCompareService.java` | 影子对账服务（181 行） |
| `backend/src/test/java/.../service/DashboardShadowCompareTest.java` | 9 个单元测试（224 行） |

### 修改文件

| 文件 | 变更说明 |
|------|---------|
| `backend/src/main/java/.../service/DashboardService.java` | 新增 `@Autowired(required=false)` 影子服务字段 + getSummary 尾部调用 |

## 配置开关

- 属性: `ddd.refactor.analytics-shadow.enabled`
- 默认值: `false`（关闭时完全不执行新路径）
- 开启后: 接口仍返回旧结果，后台执行新路径，输出 `[SHADOW-COMPARE]` diff 日志

## 对比指标（10 项 × 2 轨）

| # | 指标 | 旧路径可比 | 预估轨 | 结算轨 |
|---|------|-----------|--------|--------|
| 1 | 总订单数 | ✅ | new-path-only | compare |
| 2 | 订单额 | ✅ | new-path-only | compare |
| 3 | 服务费收入 | ✅（元→分） | new-path-only | compare |
| 4 | 技术服务费 | ❌ | new-path-only | new-path-only |
| 5 | 服务费支出 | ❌ | new-path-only | new-path-only |
| 6 | 服务费收益 | ❌ | new-path-only | new-path-only |
| 7 | 招商提成 | ❌ | new-path-only | new-path-only |
| 8 | 渠道提成 | ❌ | new-path-only | new-path-only |
| 9 | 毛利 | ❌ | new-path-only | new-path-only |

## 测试结果

### DashboardShadowCompareTest（新增，9/9 通过）

- `isEnabled_returnsFalse_whenConfigFalse` ✅
- `isEnabled_returnsTrue_whenConfigTrue` ✅
- `compare_returnsNull_whenDisabled` ✅
- `compare_allPass_whenSettleValuesMatch` ✅
- `compare_detectsDiff_whenSettleValuesDiffer` ✅
- `compare_handlesNullSummaryFields` ✅
- `compare_returnsNull_whenNewPathThrows` ✅
- `compare_passesCorrectScopeParameters` ✅
- `dashboardService_getSummary_works_whenShadowServiceIsNull` ✅

### 回归测试

| 测试类 | 结果 |
|--------|------|
| DashboardServiceTest | 20/20 ✅ |
| DashboardShadowCompareTest | 9/9 ✅ |
| PerformanceMetricsQueryServiceTest | 5/5 ✅ |
| DashboardPerformanceSummaryServiceTest | 2/2 ✅ |
| DddCrossDomainMapperGuardTest | 2/2 (1 skipped) ✅ |

### 预存失败（与本次无关）

- `ColonelSaasApplicationTests`: sampleController/LegacySampleQueryService 循环依赖（DDD-SAMPLE-005 遗留）
- `DddConfig003ConfigRoutingTest`: config routing 断言失败（预存）

## 禁止事项检查

- [x] 未切换前端看板数据源
- [x] 未隐藏 diff（全部通过 SLF4J 日志输出）
- [x] 未修改 Dashboard 响应字段（Summary 类结构未变）
- [x] 未重算业务归属

## 结论

**PASS** — 影子对账功能已实现，开关默认关闭，开启后不影响接口响应，diff 日志完整输出。
