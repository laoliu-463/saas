# 规则：数据范围过滤约束

**版本**：V1.0
**状态**：强制执行
**适用范围**：所有涉及多角色数据访问的 Service 方法

---

## 一、数据范围类型（DataScope）

| 枚举值 | 说明 | SQL 条件 |
|--------|------|----------|
| `PERSONAL` (1) | 本人数据 | `user_id = #{currentUserId}` |
| `DEPT` (2) | 本组数据 | `dept_id = #{currentUserDeptId}` |
| `ALL` (3) | 全部数据 | 无条件 |

---

## 二、Service 方法约束

### 2.1 必须传入用户上下文

```java
// ✅ 正确：方法必须包含 userId 和 deptId 参数
public List<Order> listOrders(UUID userId, UUID deptId, DataScope scope,
                                LocalDateTime start, LocalDateTime end) {
    LambdaQueryWrapper<Order> wrapper = Wrappers.<Order>lambdaQuery()
        .between(Order::getCreateTime, start, end);

    switch (scope) {
        case PERSONAL:
            wrapper.eq(Order::getUserId, userId);
            break;
        case DEPT:
            wrapper.eq(Order::getDeptId, deptId);
            break;
        case ALL:
            // 无过滤
            break;
    }
    return orderMapper.selectList(wrapper);
}

// ❌ 错误：方法不包含用户上下文
public List<Order> wrongListOrders() {
    return orderMapper.selectList(null); // 禁止！不经过数据范围过滤
}
```

### 2.2 Controller 层必须传递上下文

```java
// ✅ 正确
@GetMapping("/orders")
public ApiResult<List<OrderVO>> listOrders(
    @RequestAttribute UUID userId,
    @RequestAttribute UUID deptId,
    @RequestAttribute DataScope dataScope,
    @RequestParam LocalDateTime start,
    @RequestParam LocalDateTime end) {

    List<Order> orders = orderService.listOrders(
        userId, deptId, dataScope, start, end);
    return success(OrderConvert.toVOList(orders));
}

// ❌ 错误
@GetMapping("/orders")
public ApiResult<List<OrderVO>> wrongListOrders() {
    // 禁止！不传递用户上下文
    return success(orderService.listAll());
}
```

---

## 三、数据范围注解

### 3.1 使用 @DataScope 注解

```java
// ✅ 推荐：使用 @DataScope 注解自动处理
@DataScope(type = "order", scopeField = "channelId")
@GetMapping("/orders")
public ApiResult<List<OrderVO>> listOrders(OrderQuery query) {
    // 框架自动注入数据范围过滤条件
    List<Order> orders = orderService.listByQuery(query);
    return success(OrderConvert.toVOList(orders));
}
```

### 3.2 @DataScope 切面逻辑

```java
@Aspect
@Component
public class DataScopeAspect {
    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint point, DataScope dataScope) {
        // 1. 获取当前用户上下文
        UserContext ctx = getCurrentUserContext();

        // 2. 构建数据范围条件
        String scopeField = dataScope.scopeField();
        switch (ctx.getDataScope()) {
            case PERSONAL:
                addCondition(point, scopeField + "_id", ctx.getUserId());
                break;
            case DEPT:
                addCondition(point, "dept_id", ctx.getDeptId());
                break;
            case ALL:
                // 无条件
                break;
        }

        // 3. 继续执行
        return point.proceed();
    }
}
```

---

## 四、禁止做法

- [ ] **禁止**：在 Controller 层直接返回不过滤的数据集
- [ ] **禁止**：Service 方法不接收用户上下文参数
- [ ] **禁止**：硬编码用户 ID 或部门 ID
- [ ] **禁止**：在内部方法中绕过数据范围检查

---

## 五、配置化数据范围

### 5.1 运营角色自定义范围

```java
// 运营角色可配置自定义数据范围
@DataScope(type = "custom", customScope = "#{'operation'}")
public ApiResult<List<OrderVO>> listOrdersForOperation(
    @RequestAttribute List<UUID> operationProductIds) {

    return success(orderService.listByProductIds(operationProductIds));
}
```

---

## 六、验收测试

```java
@Test
void shouldFilterByPersonalScope() {
    // given
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000100");
    when(getCurrentUserId()).thenReturn(userId);
    when(getDataScope()).thenReturn(DataScope.PERSONAL);

    // when
    List<Order> orders = orderService.listOrders(start, end);

    // then
    assertThat(orders).allMatch(o -> o.getUserId().equals(userId));
}

@Test
void shouldFilterByDeptScope() {
    // given
    UUID deptId = UUID.fromString("00000000-0000-0000-0000-000000000050");
    when(getCurrentUserId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001")); // 非本人
    when(getDataScope()).thenReturn(DataScope.DEPT);
    when(getCurrentUserDeptId()).thenReturn(deptId);

    // when
    List<Order> orders = orderService.listOrders(start, end);

    // then
    assertThat(orders).allMatch(o -> o.getDeptId().equals(deptId));
}
```

---

## 七、相关文件索引

| 文件 | 说明 |
|------|------|
| `DataScopeService.java` | 数据范围服务 |
| `DataScope.java` | 枚举类 |
| `DataScopeAspect.java` | 切面类 |
| `OrderController.java` | 订单控制器 |
| 需求入口 | `requirements/01-roles-permissions.md` |
