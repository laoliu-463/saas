# 需求：抖音 API 对接规范

**文档版本**：V1.0
**状态**：已定稿
**智能体入口**：直接读取此文件

---

## 一、API 对接概览

| API 类型 | 接口示例 | 用途 |
|----------|----------|------|
| **授权获取 Token** | `buyin.oauth.token` | 获取 access_token |
| **活动管理** | `buyin.colonel.activity.list` | 团长活动列表 |
| **活动商品** | `buyin.colonel.product.list` | 活动商品列表 |
| **订单同步** | `buyin.settlement.order.list` | 结算订单列表 |
| **订单解密** | `order.batchSensitiveDataRequest` | 订单敏感数据解密 |
| **达人转链** | `buyin.instPickSourceConvert` | 生成归因短链 |
| **商品转链** | `buyin.productLink` | 商品推广链接 |

---

## 二、SDK 封装要求

### 2.1 目录结构

```
com.colonel.saas
└── douyin
    └── sdk
        ├── DouyinApiClient.java      # API 客户端（单例）
        ├── DouyinTokenService.java  # Token 管理
        └── api
            ├── ActivityApi.java      # 活动相关 API
            ├── OrderApi.java         # 订单相关 API
            ├── ProductApi.java       # 商品相关 API
            └── TalentApi.java        # 达人相关 API
```

### 2.2 Token 管理规范

```java
// DouyinTokenService 职责
public interface DouyinTokenService {
    /**
     * 获取有效 Token（自动刷新过期 Token）
     * @param appId 抖音应用 ID
     * @return 有效的 access_token
     */
    String getValidToken(String appId);

    /**
     * 刷新 Token
     * @param appId 抖音应用 ID
     */
    void refreshToken(String appId);

    /**
     * 检查 Token 是否即将过期（< 5 分钟）
     */
    boolean isTokenExpiringSoon(String appId);
}
```

### 2.3 Token 缓存策略

| 缓存键 | 过期时间 | 说明 |
|--------|----------|------|
| `douyin:token:{appId}` | 实际过期时间 | access_token |
| `douyin:refresh:{appId}` | 30天 | refresh_token |

---

## 三、API 调用规范

### 3.1 标准请求流程

```java
public ApiResponse<OrderListResponse> getOrderList(String appId, OrderListRequest request) {
    // 1. 获取有效 Token（自动刷新）
    String token = tokenService.getValidToken(appId);

    // 2. 构建请求参数
    Map<String, Object> params = new HashMap<>();
    params.put("access_token", token);
    params.put("app_id", appId);
    params.put("start_time", request.getStartTime());
    params.put("end_time", request.getEndTime());
    params.put("page_size", request.getPageSize());
    params.put("cursor", request.getCursor());

    // 3. 发送请求（带重试）
    return httpClient.post(API_ORDER_LIST)
        .header("Content-Type", "application/json")
        .body(params)
        .timeout(30_000)  // 30 秒超时
        .retry(3)         // 最多重试 3 次
        .execute()
        .as(OrderListResponse.class);
}
```

### 3.2 请求超时配置

| 接口类型 | 超时时间 | 重试次数 |
|----------|----------|----------|
| 订单同步 | 30s | 3 |
| 活动列表 | 15s | 2 |
| 商品列表 | 15s | 2 |
| 达人转链 | 10s | 1 |

### 3.3 错误处理

```java
// 错误码处理
public class DouyinApiException extends RuntimeException {
    private int errorCode;
    private String errorMsg;
    private String requestId;  // 抖音返回的请求 ID，用于排查

    // 常见错误码处理
    public static boolean isTokenExpired(int errorCode) {
        return errorCode == 10009 || errorCode == 10008; // token 过期/无效
    }

    public static boolean isRateLimited(int errorCode) {
        return errorCode == 40017; // 调用频率超限
    }
}
```

---

## 四、归因参数规范（pick_source）

### 4.1 pick_source 生成规则

```java
/**
 * pick_source 格式：{userId}_{shortId}_{timestamp}
 * 示例：usr_abc123_1712000000
 *
 * 约束：
 * - 总长度 ≤ 64 字符
 * - short_id 必须唯一（通过 PickSourceMapping 表管理）
 */
public String generatePickSource(UUID userId, String productId, String activityId) {
    String shortId = generateShortId(userId);
    return String.format("%s_%s_%d",
        userId.toString().substring(0, 8),
        shortId,
        System.currentTimeMillis() / 1000
    );
}
```

### 4.2 pick_extra 参数（20字符限制）

```java
/**
 * pick_extra 长度限制：≤ 20 字符
 * 用于传递额外归因信息（如商品ID后6位）
 */
public String generatePickExtra(String productId) {
    if (productId != null && productId.length() > 20) {
        return productId.substring(productId.length() - 20);
    }
    return productId;
}
```

---

## 五、订单解密规范

### 5.1 解密接口调用

```java
/**
 * 订单解密必须调用官方接口，严禁本地存储明文
 * 接口：order.batchSensitiveDataRequest
 */
public DecryptResponse decryptOrder(String appId, List<String> orderIds) {
    // 1. 检查订单是否在解密有效期内（expire_time）
    // 2. 调用解密接口
    // 3. 返回明文但不持久化存储
    // 4. 仅返回给前端展示，缓存 5 分钟
}
```

### 5.2 禁止做法

- [ ] **禁止**将解密后的手机号、身份证存储到数据库
- [ ] **禁止**在日志中打印解密后的敏感信息
- [ ] **禁止**将解密接口返回的明文写入文件

---

## 六、爬虫透明化基类

路径：`com.colonel.saas.crawler.base`

```java
/**
 * 爬虫基类，预置安全控制
 * 智能体只需实现 HTML 解析，无需关心请求间隔
 */
public abstract class CrawlerBase {

    // 请求间隔：3-6 秒（随机）
    private static final int MIN_INTERVAL_MS = 3000;
    private static final int MAX_INTERVAL_MS = 6000;

    // User-Agent 轮换
    private String[] userAgents = {...};

    /**
     * 带安全控制的 HTTP GET
     */
    protected String httpGet(String url) {
        // 1. 随机延时
        Thread.sleep(randomInterval());

        // 2. 随机 UA
        HttpRequest request = HttpUtil.createGet(url)
            .header("User-Agent", randomUA());

        // 3. 异常处理
        return executeWithRetry(request);
    }

    /**
     * 子类实现：解析 HTML
     */
    protected abstract TalentInfo parseHtml(String html);
}
```

---

## 七、开发约束

### 7.1 Token 相关

- [ ] **必须**：使用 `DouyinTokenService` 获取 Token，禁止直接查询数据库
- [ ] **必须**：Token 即将过期时自动刷新
- [ ] **必须**：Token 异常时抛出 `DouyinApiException`

### 7.2 API 调用

- [ ] **必须**：所有 API 调用设置超时时间
- [ ] **必须**：实现重试机制（指数退避）
- [ ] **必须**：记录请求日志（含 request_id）

### 7.3 敏感数据

- [ ] **禁止**：本地解密存储订单敏感信息
- [ ] **禁止**：日志打印敏感字段（手机号、身份证）
- [ ] **必须**：解密结果仅展示不存储

### 7.4 爬虫

- [ ] **必须**：请求间隔 3-6 秒
- [ ] **必须**：UA 轮换
- [ ] **禁止**：并发请求同一来源

---

## 八、验收标准

1. **Token 管理**：过期 Token 自动刷新，接口不中断
2. **归因参数**：pick_source 生成正确，pick_extra ≤ 20 字符
3. **订单解密**：解密接口调用成功，明文不落库
4. **爬虫安全**：连续请求间隔符合 3-6 秒要求
5. **错误处理**：API 异常时返回明确错误信息

---

## 九、相关文件索引

| 文件 | 路径 | 说明 |
|------|------|------|
| Token 实体 | `backend/src/main/java/com/colonel/saas/entity/douyin/DouyinToken.java` | Token 存储 |
| SDK 客户端 | `backend/src/main/java/com/colonel/saas/douyin/sdk/` | API 封装 |
| 归因服务 | `backend/src/main/java/com/colonel/saas/service/PickSourceService.java` | 归因管理 |
| 爬虫基类 | `backend/src/main/java/com/colonel/saas/crawler/base/CrawlerBase.java` | 爬虫基类 |
| Lint 规则 | `rules/api-security.md` | API 安全约束 |

---

## 十、详细 API 响应结构

### 10.1 授权 Token 接口（buyin.oauth.token）

**请求参数**：
```
grant_type = authorization_code
code = 授权码
client_key = 应用 client_key
client_secret = 应用 client_secret
```

**成功响应**：
```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "access_token": "REPEATToken...",
    "refresh_token": "REFRESHToken...",
    "expires_in": 7200,
    "refresh_expires_in": 2592000
  }
}
```

**失败响应**：
```json
{
  "err_no": 31012,
  "err_msg": "refresh token invalid",
  "log_id": "20260420xxxx"
}
```

> **错误码 31012**：`refresh_token` 被刷死（多进程并发刷新）。必须使用 Redis 分布式锁防重。

---

### 10.2 活动列表接口（buyin.colonel.activity.list）

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `access_token` | string | ✅ | 有效 access_token |
| `app_id` | integer | ✅ | 应用 app_id |
| `start_time` | integer | ✅ | 开始时间（秒级时间戳） |
| `end_time` | integer | ✅ | 结束时间（秒级时间戳） |
| `page_size` | integer | ❌ | 每页条数，默认20，最大100 |

**成功响应**：
```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "list": [
      {
        "activity_id": "7318828232912345678",
        "title": "团长活动标题",
        "colonel_buyin_id": "7348293728374323456",
        "commission_rate": 85,
        "service_rate": 35,
        "status": 1,
        "start_time": 1711900800,
        "end_time": 1714492800,
        "create_time": 1711897200
      }
    ],
    "page_size": 20,
    "total": 100,
    "has_more": true
  }
}
```

**业务校验**：
- `commission_rate`：招商提成比例，0-100整数，**不得超过 90**
- `service_rate`：服务费比例，0-100整数，**不得超过 40**
- 返回值为百分比整数（如 85 表示 85%）

---

### 10.3 活动商品接口（buyin.colonel.product.list）

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `access_token` | string | ✅ | 有效 access_token |
| `activity_id` | string | ✅ | 团长活动 ID |
| `page_size` | integer | ❌ | 每页条数，默认20，最大100 |
| `cursor` | integer | ❌ | 翻页游标（0表示第一页） |

**成功响应**：
```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "list": [
      {
        "product_id": "7495829108234567890",
        "title": "商品标题",
        "amount": 299900,
        "cos_ratio": 10,
        "cos_fee": 2999,
        "service_ratio": 35,
        "min_refer_amount": 0,
        "status": 1,
        "assignee_id": "7392837498234567890"
      }
    ],
    "page_size": 20,
    "has_more": true,
    "next_cursor": 21
  }
}
```

> **金额单位**：`amount`（商品价格）、`cos_fee`（技术服务费）均为**分**（整数），**禁止**使用 Double。

---

### 10.4 团长订单接口（buyin.settlement.order.list）

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `access_token` | string | ✅ | 有效 access_token |
| `app_id` | integer | ✅ | 应用 app_id |
| `start_time` | integer | ✅ | 开始时间（秒级时间戳） |
| `end_time` | integer | ✅ | 结束时间（秒级时间戳） |
| `phase_id` | string | ❌ | 大促阶段 ID（可不传） |
| `page_size` | integer | ❌ | 每页条数，默认20，最大100 |

**成功响应**：
```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "list": [
      {
        "order_id": "74958291082345678912345678901234",
        "product_id": "7495829108234567890",
        "activity_id": "7318828232912345678",
        "talent_uid": "9876543210987654321",
        "talent_nickname": "达人昵称",
        "pick_source": "usr_abc123_1712000000",
        "pick_extra": "749582",
        "order_amount": 299900,
        "actual_amount": 299900,
        "service_fee": 104965,
        "platform_fee": 10496,
        "talent_commission": 89970,
        "commission_rate": 85,
        "status": 2,
        "create_time": 1712000000,
        "settle_time": 1714684800,
        "phase_id": "20260401_20260430"
      }
    ],
    "page_size": 20,
    "has_more": true
  }
}
```

**关键字段说明**：
- `order_amount`：订单金额（分），**不得**使用 Double
- `actual_amount`：实际支付金额（分）
- `service_fee`：服务费（分），用于业绩计算
- `platform_fee`：平台技术服务费（分）
- `talent_commission`：达人佣金（分）
- `pick_source`：归因参数，**必须**通过映射表解析，禁止字符串解析
- `pick_extra`：透传值（≤20字符），用于辅助归因

---

### 10.5 订单解密接口（order.batchSensitiveDataRequest）

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `access_token` | string | ✅ | 有效 access_token |
| `order_ids` | array[string] | ✅ | 订单 ID 列表（最多50条/次） |
| `type` | integer | ✅ | 解密类型：1=手机号 |

**成功响应（含虚拟号处理）**：
```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": [
    {
      "order_id": "74958291082345678912345678901234",
      "is_virtual_tel": true,
      "phone_no_a": "138****0001",
      "phone_no_b": "138****0002",
      "expire_time": 1717363200
    },
    {
      "order_id": "74958291082345678912345678901235",
      "is_virtual_tel": false,
      "phone": "13812345678"
    }
  ]
}
```

**虚拟号字段说明**：
| 字段 | 类型 | 说明 |
|------|------|------|
| `is_virtual_tel` | boolean | 是否为虚拟号（快递联络号） |
| `phone_no_a` | string | 虚拟号A（达人手机号，脱敏） |
| `phone_no_b` | string | 虚拟号B（快递员手机号，脱敏） |
| `expire_time` | integer | 虚拟号过期时间（秒级时间戳），过期后联络失效 |
| `phone` | string | 非虚拟号时，直接返回真实手机号（脱敏） |

> **虚拟号有效期**：虚拟号在 `expire_time` 之前有效，过期后联络失效，需在页面明确展示过期提示。

**业务约束**：
- [ ] **禁止**：将解密结果持久化到数据库
- [ ] **禁止**：日志打印解密后的手机号
- [ ] **必须**：解密结果仅返回前端展示，缓存不超过 5 分钟
- [ ] **必须**：每次解密请求限制 50 条订单

---

### 10.6 达人转链接口（buyin.instPickSourceConvert）

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `access_token` | string | ✅ | 有效 access_token |
| `account_id` | string | ✅ | 账号 ID（app_id） |
| `product_id` | string | ✅ | 商品 ID |
| `talent_uid` | string | ✅ | 达人 UID |
| `pick_extra` | string | ❌ | 透传值（**≤ 20 字符**） |

**成功响应**：
```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "pick_source": "usr_abc123_1712000000",
    "pick_url": "https://haohuo.snssdk.com/..."
  }
}
```

> **pick_extra 限制**：API 明确要求 `pick_extra` ≤ 20 字符，超长截断（取后20位）。

---

## 十一、方案B归因（ShortID）

### 11.1 方案B设计背景

当 `pick_source` 中 `userId` 部分超过 `pick_extra` 20字符限制时，引入 ShortID 方案：

```
pick_extra = short_id（8位Base36，仅≤10字符）
pick_source_mapping.uuid_seed = 原始UUID（用于反查还原）
```

### 11.2 映射表字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `short_id` | VARCHAR(10) UNIQUE | 8位Base36短码，≤10字符 |
| `uuid_seed` | UUID | 原始UUID，用于反查还原 |
| `pick_extra` | VARCHAR(10) | 实际透传值=short_id |
| `pick_source` | VARCHAR(128) | 完整归因字符串（保留） |
| `expire_time` | TIMESTAMP | 过期时间（默认90天后） |

### 11.3 归因还原流程

```java
public UUID resolveFromShortId(String shortId) {
    PickSourceMapping mapping = pickSourceMapper.selectByShortId(shortId);
    if (mapping == null) {
        return null;
    }
    if (mapping.getExpireTime() != null && mapping.getExpireTime().isBefore(LocalDateTime.now())) {
        return null;  // 已过期
    }
    return mapping.getUuidSeed();
}
```

> **过期清理**：定时任务每30天扫描过期映射，`expire_time < now()` 时物理删除。

---

## 十二、Token 管理（Redis 分布式锁）

### 12.1 并发刷新问题

多个进程同时检测到 Token 即将过期时，会并发调用刷新接口，导致：
- 错误码 31012（refresh token invalid）
- refresh_token 被刷死
- 需重新授权

### 12.2 Redis 分布式锁实现

```java
public String refreshTokenWithLock(String appId) {
    String lockKey = "douyin:token:lock:" + appId;

    // 尝试获取锁（5分钟自动过期）
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", Duration.ofMinutes(5));

    if (Boolean.TRUE.equals(acquired)) {
        try {
            return doRefreshToken(appId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // 等待锁释放
        while (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            Thread.sleep(100);
        }
        // 锁释放后重新获取 Token
        return getValidToken(appId);
    }
}
```

### 12.3 缓存策略

| 缓存键 | 值 | TTL | 说明 |
|--------|----|-----|------|
| `douyin:token:{appId}` | access_token | 2小时（实际过期时间） | 有效 Token |
| `douyin:refresh:{appId}` | refresh_token | 30天 | 刷新 Token |
| `douyin:token:lock:{appId}` | 1 | 5分钟 | 刷新锁 |

---

## 十三、定时任务设计

### 13.1 Token 刷新任务

```sql
-- 每小时执行，检查即将过期的 Token 并刷新
SELECT refresh_token_if_expiring();
```

```java
@Scheduled(cron = "0 0 * * * ?")  // 每小时整点
public void refreshExpiringTokens() {
    List<DouyinToken> expiringTokens = douyinTokenMapper.selectExpiringSoon(300); // 5分钟内过期
    for (DouyinToken token : expiringTokens) {
        try {
            tokenService.refreshTokenWithLock(token.getAppId());
        } catch (Exception e) {
            log.error("Token 刷新失败: appId={}", token.getAppId(), e);
        }
    }
}
```

### 13.2 订单同步任务（滑动窗口）

```java
@Scheduled(fixedDelay = 60000)  // 每分钟执行
public void syncOrders() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime start = now.minusMinutes(11);  // 10分钟前（留1分钟重叠）
    LocalDateTime end = now.minusMinutes(1);     // 1分钟前

    // 使用滑动窗口避免漏单
    orderSyncService.syncByTimeRange(start, end);
}
```

> **滑动窗口策略**：每次同步覆盖最近 10 分钟数据，上一次同步和本次有 1 分钟重叠，确保不漏单。

### 13.3 映射表清理任务

```sql
-- 每周一凌晨3点执行，清理90天前过期的映射记录
SELECT cleanup_expired_mappings();
```

```java
@Scheduled(cron = "0 0 3 ? * MON")  // 每周一凌晨3点
public void cleanupExpiredMappings() {
    LocalDateTime deadline = LocalDateTime.now().minusDays(90);
    int count = pickSourceMapper.deleteExpired(deadline);
    log.info("清理过期映射记录: {} 条", count);
}
```

---

## 十四、关键风险警告

### 14.1 金额单位陷阱

```java
// ❌ 错误：假设 amount 是元
BigDecimal amount = new BigDecimal(order.getAmount()) / 100;

// ✅ 正确：amount 已经是分，直接使用
Long amount = order.getAmount();  // 单位：分
```

> 所有金额字段（`order_amount`、`actual_amount`、`service_fee`、`platform_fee`、`talent_commission`、`cos_fee`、`amount`）单位均为**分**（Long），禁止除以100。

### 14.2 ID 长度限制

- `pick_extra` ≤ 20 字符，超长截断取后20位
- `short_id` 方案B ≤ 10 字符
- 渠道码 `channel_code` 存储短码，非 UUID

### 14.3 映射表过期

- 默认过期时间：90 天
- 过期的 `pick_source` 无法反查，订单归因失败
- 定期清理任务每周执行

### 14.4 并发刷新 31012

- 多进程同时刷新 Token 导致 refresh_token 失效
- **必须**使用 Redis 分布式锁：`douyin:token:lock:{appId}`
- 锁 TTL 5 分钟，防止死锁

### 14.5 虚拟号有效期

- `expire_time` 到达后，虚拟号失效
- 页面展示时需提示"该联络方式已过期"
- 解密请求时检查 `expire_time`，过期不返回虚拟号

---

## 十五、Python 实现参考（Celery 任务）

```python
# backend/tasks/order_sync.py
from celery import Celery
from sqlalchemy import text
import logging

logger = logging.getLogger(__name__)
bp = Celery('order_sync', broker=os.getenv('REDIS_URL'))

@bp.task
def sync_orders(start_time: int, end_time: int):
    """滑动窗口订单同步"""
    page_size = 100
    cursor = 0

    while True:
        resp = douyin_api.colonel_order_list(
            start_time=start_time,
            end_time=end_time,
            page_size=page_size,
            cursor=cursor
        )

        orders = resp.get('data', {}).get('list', [])
        if not orders:
            break

        # 批量 upsert（phase_id 为分区标识）
        with DBSession() as db:
            db.execute(text("""
                INSERT INTO colonelsettlement_order (...)
                VALUES (...)
                ON CONFLICT (id, create_time) DO UPDATE SET
                    phase_id = EXCLUDED.phase_id
            """), orders)

        has_more = resp.get('data', {}).get('has_more', False)
        if not has_more:
            break
        cursor += page_size

    logger.info(f"订单同步完成: {cursor} 条")

@bp.task
def refresh_token_if_expiring():
    """Token 即将过期时刷新"""
    with DBSession() as db:
        tokens = db.execute(text("""
            SELECT app_id FROM douyin_token
            WHERE expire_time < NOW() + INTERVAL '5 minutes'
        """)).fetchall()

    for token in tokens:
        with redis_lock(f"douyin:token:lock:{token['app_id']}"):
            refresh_token(token['app_id'])
