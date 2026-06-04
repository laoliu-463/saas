# Evidence: 订单明细表复刻与前后端字段对齐

**任务**: 数据平台订单明细Tab新增（16列逐单明细 + 双轨金额）
**日期**: 2026-06-04 18:15
**终态**: PARTIAL (代码完成 + 构建通过 + 容器重启，smoke 受沙箱网络限制未验证)

---

## 1. 后端变更

### 新增文件
- `backend/src/main/java/com/colonel/saas/vo/data/OrderDetailVO.java` — 16列订单明细VO（133行）

### 修改文件
- `backend/src/main/java/com/colonel/saas/mapper/PerformanceRecordMapper.java` — 新增 `findByOrderIds(List<String>)`
- `backend/src/main/resources/mapper/PerformanceRecordMapper.xml` — 新增 `<select id="findByOrderIds">` + 空列表守卫
- `backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java`:
  - 新增 `PerformanceRecordMapper` + `SysUserMapper` 依赖注入
  - 新增 `getOrderDetailPage()` — 订单明细分页查询（~80行）
  - 新增辅助方法: `loadPerformanceMap`, `loadActivityNameMap`, `loadUserNameMap`, `toOrderDetailVO`, `safeCentToYuan`, `safeAdd`, `safeSubtract`
  - 新增 `exportOrderDetail()` — 订单明细CSV导出（~90行）
  - 恢复 `exportOrders` 简化重载（11参数委托19参数）
- `backend/src/main/java/com/colonel/saas/controller/DataController.java`:
  - 构造函数新增 `PerformanceRecordMapper` + `SysUserMapper`
  - 新增 `@GetMapping("/data/orders/detail")` 端点
  - 新增 `@GetMapping("/orders/exports/detail")` 端点

### 测试文件
- `backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java` — 新增 5 个测试方法（40/40 PASS）
- `backend/src/test/java/com/colonel/saas/mapper/PerformanceRecordMapperTest.java` — 新增 `findByOrderIds` 3 个集成测试

## 2. 前端变更

### 新增文件
- `frontend/src/views/data/OrderDetailTab.vue` — 订单明细Tab子组件（16列+分页+自定义表头+导出按钮）

### 修改文件
- `frontend/src/api/data.ts` — 新增 `getOrderDetailPage` + `exportOrderDetail`
- `frontend/src/views/data/order-list-query.ts` — 新增 `buildOrderDetailPageParams`
- `frontend/src/views/data/OrderList.vue`:
  - 新增Tab切换器（汇总 / 订单明细）
  - 汇总面板 + 维度行仅summary tab显示
  - 导出按钮根据activeTab调用不同API
  - 新增CSS样式（.data-tab-bar, .data-tab）

### 测试文件
- `frontend/src/views/data/OrderList.test.ts` — 新增 4 个测试（Tab渲染、切换、明细组件、导出调度）

## 3. 构建验证

| 检查 | 结果 | 备注 |
|---|---|---|
| `mvn compile` | PASS | BUILD SUCCESS |
| `mvn test-compile` | PASS | BUILD SUCCESS |
| `mvn test -Dtest=DataControllerTest` | PASS | 40/40 tests, 0 errors |
| `npx vitest run OrderList.test.ts` | PASS | 8/8 tests |
| `npx vite build` | PASS | built in 1.77s |
| `mvn -DskipTests package` | PASS | colonel-saas.jar built |
| `docker compose build` (real-pre) | PASS | backend + frontend images built |
| `docker compose restart` (real-pre) | PASS | 4 containers healthy |
| HTTP smoke (sandbox) | SKIP | 沙箱网络限制，容器日志确认健康 |

## 4. 关键设计决策

- **批量关联**: 用 `findByOrderIds` 批量查业绩记录 + 内存Map关联，避免N+1
- **活动名称**: 用已有 `selectNamesByActivityIds` 纯MyBatis方法（非MyBatis-Plus）
- **用户姓名**: 通过 `SysUserMapper(BaseMapper)` 的 `selectList(LambdaQueryWrapper)` 批量查
- **双轨金额**: `safeCentToYuan(null)` → null（非0），前端显示 "-"
- **服务费计算**: 支出=招商提成+渠道提成，收益=服务费收入-技术服务费（双轨分别算）
- **Tab架构**: 抽取 OrderDetailTab.vue 子组件，避免OrderList.vue过度膨胀

## 5. 未验证项

- 容器内真实API调用（沙箱网络限制）
- 前端页面真实渲染（需浏览器验证）
- PerformanceRecordMapperTest 集成测试（需Docker PostgreSQL）
- E2E 测试

## 6. 下一步

- 浏览器打开 http://localhost:3001/data/orders 验证Tab切换和明细表渲染
- 验证导出CSV内容正确性
- 运行完整后端测试套件（含Testcontainers）
