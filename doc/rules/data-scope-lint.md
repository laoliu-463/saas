# 瑙勫垯锛氭暟鎹寖鍥磋繃婊ょ害鏉?
**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氭墍鏈夋秹鍙婂瑙掕壊鏁版嵁璁块棶鐨?Service 鏂规硶

---

## 涓€銆佹暟鎹寖鍥寸被鍨嬶紙DataScope锛?
| 鏋氫妇鍊?| 璇存槑 | SQL 鏉′欢 |
|--------|------|----------|
| `PERSONAL` (1) | 鏈汉鏁版嵁 | `user_id = #{currentUserId}` |
| `DEPT` (2) | 鏈粍鏁版嵁 | `dept_id = #{currentUserDeptId}` |
| `ALL` (3) | 鍏ㄩ儴鏁版嵁 | 鏃犳潯浠?|

---

## 浜屻€丼ervice 鏂规硶绾︽潫

### 2.1 蹇呴』浼犲叆鐢ㄦ埛涓婁笅鏂?
```java
// 鉁?姝ｇ‘锛氭柟娉曞繀椤诲寘鍚?userId 鍜?deptId 鍙傛暟
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
            // 鏃犺繃婊?            break;
    }
    return orderMapper.selectList(wrapper);
}

// 鉂?閿欒锛氭柟娉曚笉鍖呭惈鐢ㄦ埛涓婁笅鏂?public List<Order> wrongListOrders() {
    return orderMapper.selectList(null); // 绂佹锛佷笉缁忚繃鏁版嵁鑼冨洿杩囨护
}
```

### 2.2 Controller 灞傚繀椤讳紶閫掍笂涓嬫枃

```java
// 鉁?姝ｇ‘
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

// 鉂?閿欒
@GetMapping("/orders")
public ApiResult<List<OrderVO>> wrongListOrders() {
    // 绂佹锛佷笉浼犻€掔敤鎴蜂笂涓嬫枃
    return success(orderService.listAll());
}
```

---

## 涓夈€佹暟鎹寖鍥存敞瑙?
### 3.1 浣跨敤 @DataScope 娉ㄨВ

```java
// 鉁?鎺ㄨ崘锛氫娇鐢?@DataScope 娉ㄨВ鑷姩澶勭悊
@DataScope(type = "order", scopeField = "channelId")
@GetMapping("/orders")
public ApiResult<List<OrderVO>> listOrders(OrderQuery query) {
    // 妗嗘灦鑷姩娉ㄥ叆鏁版嵁鑼冨洿杩囨护鏉′欢
    List<Order> orders = orderService.listByQuery(query);
    return success(OrderConvert.toVOList(orders));
}
```

### 3.2 @DataScope 鍒囬潰閫昏緫

```java
@Aspect
@Component
public class DataScopeAspect {
    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint point, DataScope dataScope) {
        // 1. 鑾峰彇褰撳墠鐢ㄦ埛涓婁笅鏂?        UserContext ctx = getCurrentUserContext();

        // 2. 鏋勫缓鏁版嵁鑼冨洿鏉′欢
        String scopeField = dataScope.scopeField();
        switch (ctx.getDataScope()) {
            case PERSONAL:
                addCondition(point, scopeField + "_id", ctx.getUserId());
                break;
            case DEPT:
                addCondition(point, "dept_id", ctx.getDeptId());
                break;
            case ALL:
                // 鏃犳潯浠?                break;
        }

        // 3. 缁х画鎵ц
        return point.proceed();
    }
}
```

---

## 鍥涖€佺姝㈠仛娉?
- [ ] **绂佹**锛氬湪 Controller 灞傜洿鎺ヨ繑鍥炰笉杩囨护鐨勬暟鎹泦
- [ ] **绂佹**锛歋ervice 鏂规硶涓嶆帴鏀剁敤鎴蜂笂涓嬫枃鍙傛暟
- [ ] **绂佹**锛氱‖缂栫爜鐢ㄦ埛 ID 鎴栭儴闂?ID
- [ ] **绂佹**锛氬湪鍐呴儴鏂规硶涓粫杩囨暟鎹寖鍥存鏌?
---

## 浜斻€侀厤缃寲鏁版嵁鑼冨洿

### 5.1 杩愯惀瑙掕壊鑷畾涔夎寖鍥?
```java
// 杩愯惀瑙掕壊鍙厤缃嚜瀹氫箟鏁版嵁鑼冨洿
@DataScope(type = "custom", customScope = "#{'operation'}")
public ApiResult<List<OrderVO>> listOrdersForOperation(
    @RequestAttribute List<UUID> operationProductIds) {

    return success(orderService.listByProductIds(operationProductIds));
}
```

---

## 鍏€侀獙鏀舵祴璇?
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
    when(getCurrentUserId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001")); // 闈炴湰浜?    when(getDataScope()).thenReturn(DataScope.DEPT);
    when(getCurrentUserDeptId()).thenReturn(deptId);

    // when
    List<Order> orders = orderService.listOrders(start, end);

    // then
    assertThat(orders).allMatch(o -> o.getDeptId().equals(deptId));
}
```

---

## 涓冦€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璇存槑 |
|------|------|
| `DataScopeService.java` | 鏁版嵁鑼冨洿鏈嶅姟 |
| `DataScope.java` | 鏋氫妇绫?|
| `DataScopeAspect.java` | 鍒囬潰绫?|
| `OrderController.java` | 璁㈠崟鎺у埗鍣?|
| 闇€姹傚叆鍙?| `doc/requirements/01-roles-permissions.md` |
