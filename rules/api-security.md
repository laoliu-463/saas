# 规则：API 安全约束

**版本**：V1.0
**状态**：强制执行
**适用范围**：所有抖音 API 调用、敏感数据处理

---

## 一、敏感数据处理

### 1.1 订单解密规范

```java
// ✅ 正确：调用官方解密接口，不存储明文
@Service
public class OrderDecryptService {

    public OrderDetailVO decryptAndShow(String orderId) {
        // 1. 检查解密有效期
        if (isDecryptExpired(orderId)) {
            throw new BusinessException("订单已过解密有效期");
        }

        // 2. 调用官方解密接口
        DecryptResponse response = douyinApi.decryptOrder(orderId);

        // 3. 仅返回给前端，不存储
        return OrderDetailVO.builder()
            .phone(response.getPhone()) // 仅展示
            .build();
    }
}

// ❌ 错误：存储解密明文
public void wrongSaveDecryptedData(String orderId, DecryptResponse response) {
    Order order = new Order();
    order.setPhone(response.getPhone()); // 禁止！存储明文
    orderMapper.updateById(order);
}
```

### 1.2 禁止清单

- [ ] **禁止**：将解密后的手机号存储到数据库
- [ ] **禁止**：将解密后的身份证号存储到数据库
- [ ] **禁止**：在日志中打印解密后的敏感字段
- [ ] **禁止**：将解密明文写入文件

---

## 二、Token 安全

### 2.1 Token 获取

```java
// ✅ 正确：通过 TokenService 获取
@Service
public class OrderSyncService {

    public void syncOrders() {
        String token = tokenService.getValidToken(appId);
        // 使用 token...
    }
}

// ❌ 错误：直接查询数据库获取 token
public void wrongSync() {
    String token = tokenMapper.selectByAppId(appId).getAccessToken(); // 禁止！
}
```

### 2.2 Token 缓存

- [ ] 使用 Redis 缓存 access_token
- [ ] refresh_token 单独缓存，30 天过期
- [ ] Token 即将过期（< 5 分钟）时自动刷新

---

## 三、API 错误处理

### 3.1 错误码处理

```java
public class DouyinApiException extends RuntimeException {
    private int errorCode;
    private String errorMsg;
    private String requestId;

    public static boolean isTokenExpired(int errorCode) {
        return errorCode == 10009 || errorCode == 10008;
    }

    public static boolean isRateLimited(int errorCode) {
        return errorCode == 40017;
    }
}

// ✅ 正确：区分处理错误
public void callApi() {
    try {
        douyinApi.getOrderList(request);
    } catch (DouyinApiException e) {
        if (DouyinApiException.isTokenExpired(e.getErrorCode())) {
            tokenService.refreshToken(appId);
            // 重试
        } else if (DouyinApiException.isRateLimited(e.getErrorCode())) {
            // 等待后重试
            Thread.sleep(60_000);
        } else {
            throw e;
        }
    }
}
```

---

## 四、接口调用限制

| 接口类型 | 超时时间 | 重试次数 | 特殊限制 |
|----------|----------|----------|----------|
| 订单同步 | 30s | 3 | 需 rate limit 处理 |
| 活动列表 | 15s | 2 | - |
| 商品列表 | 15s | 2 | - |
| 达人转链 | 10s | 1 | pick_extra ≤ 20 字符 |
| 订单解密 | 10s | 2 | 需有效期检查 |

---

## 五、请求日志规范

### 5.1 必须记录的日志

```java
// ✅ 正确：记录 request_id
log.info("API call: buyin.settlement.order.list, request_id: {}, params: {}",
    response.getRequestId(), params);
```

### 5.2 禁止日志内容

- [ ] **禁止**：在日志中打印 access_token
- [ ] **禁止**：在日志中打印解密后的手机号/身份证
- [ ] **禁止**：在日志中打印 Cookie 内容

---

## 六、相关文件索引

| 文件 | 说明 |
|------|------|
| `DouyinApiClient.java` | API 客户端 |
| `DouyinTokenService.java` | Token 服务 |
| `OrderDecryptService.java` | 解密服务 |
| 需求入口 | `requirements/03-api-specs.md` |
