# 抖音团长 SaaS 核心 API 集成指南

> 本文档为技术基准（Source of Truth），记录核心 API 的集成逻辑与开发注意事项。

---

## 1. 鉴权与安全模块 (Auth & Security)

**核心接口：** `/token/create`

| 项目 | 说明 |
|------|------|
| **用途** | 生成/获取业务 API 所需的 `access_token`（无需授权，免费API） |
| **有效期** | 接口返回 `expires_in`（秒级时间戳），建议提前 1 小时调用 `/token/refresh` 刷新 |
| **Refresh Token** | 有效期 14 天 |
| **限流规则** | 应用限流：**60次/秒**，接口总限流：**600次/秒** |
| **并发红线** | 严禁多进程同时刷新，会触发 **31012** 错误 |
| **SDK推荐** | 强烈推荐使用官方 SDK 自动处理 token 缓存和刷新获取 |

### 公共参数

| 参数名称 | 类型 | 必须 | 说明 |
|----------|--------|------|------|
| `method` | String | 是 | `token.create` |
| `app_key` | String | 是 | 应用创建完成后被分配的key |
| `param_json`| String | 是 | 业务参数按字母排序的JSON字符串 |
| `timestamp` | String | 是 | 请求时间戳（如：`2020-09-15 14:48:13`GMT+8） |
| `v` | String | 是 | API协议版本，当前为 `2` |
| `sign` | String | 是 | 签名结果 |
| `sign_method`| String | 否 | 推荐 `hmac-sha256`（默认 `md5`，后续下线） |

### 请求参数

| 参数名 | 类型 | 必须 | 示例值 | 说明 |
|--------|------|------|--------|------|
| `code` | String | 否 | `82bdc687...` | **工具型必传**（传code），**自用型**（传`""`） |
| `grant_type` | String | 是 | `authorization_code` | 授权类型：工具型`authorization_code`，自用型`authorization_self`（若自用有code则传code类型） |
| `test_shop` | String | 否 | `2` | 测试店铺标识，新增测试店铺传1 |
| `shop_id` | String | 否 | `17239` | 店铺ID，抖店自研应用使用（有`auth_subject_type`勿传） |
| `auth_id` | String | 否 | `112334` | 授权iD，配合`auth_subject_type`使用 |
| `auth_subject_type`| String | 否 | `WuLiuShang` | 授权主体类型：`YunCang`,`WuLiuShang`,`MiniApp`,`MCN`,`DouKe`,`Colonel`等 |

### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `access_token` | String | 授权 Token 值 |
| `expires_in` | Int64 | 过期时间（秒级时间戳） |
| `refresh_token` | String | 刷新令牌（有效期 14 天） |
| `scope` | String | 授权范围 |
| `shop_id` | Int64 | 店铺ID |
| `shop_name` | String | 店铺名称 |
| `authority_id` | String | 授权ID |
| `auth_subject_type`| String | 授权主体类型 |
| `encrypt_operator` | String | 操作账号加密（不支持解密） |
| `operator_name` | String | 操作账号昵称（脱敏） |
| `shop_biz_type` | Int64 | 店铺业务类型：0=普通，1=即时零售连锁，2=即时零售个体 |
| `toutiao_id` | String | Token对应的头条账号ID |
| `token_type` | Int64 | **0**=主账号生成，**1**=子账号生成 |

### 关键错误码

| 错误码 | 子返回码 | 发生情况与解决方案 |
|--------|----------|--------------------|
| **10000** | - | 请求成功 |
| **50002** | `31012` | **Token并发请求冲突**，请减少并发或使用 Redis 分布式锁 |
| **50002** | `31007` | 生成token失败，code已经失效（有效期10分钟），请重新获取 |
| **50002** | `31021` | authCode失效/不存在，需在商家后台重新点击获取 |
| **50002** | `31006` | 店铺授权已失效，请重新引导商家完成授权 |
| **50002** | `31020` / `31005` | AuthCode对应的授权不存在，或未找到授权记录 |
| **50002** | `31003` | `grant_type` 参数取值不正确 |
| **50002** | `4` | 非自用型应用code不能为空 |
| **50002** | `31011` | 当前应用不存在（请校验 `app_key`） |

### 开发实现

```python
# Token 管理策略
class DouyinTokenManager:
    """抖音 Token 管理器"""

    TOKEN_KEY = "douyin_access_token"
    REFRESH_TOKEN_KEY = "douyin_refresh_token"
    LOCK_KEY = "douyin_token_refresh_lock"

    def __init__(self, redis_client):
        self.redis = redis_client

    def get_token(self) -> Optional[str]:
        """获取 Token，优先从 Redis 读取"""
        token = self.redis.get(self.TOKEN_KEY)
        if token:
            return token
        return None

    def refresh_token(self, app_key: str, app_secret: str, shop_id: str):
        """
        刷新 Token - 使用分布式锁防止并发
        """
        lock = self.redis.lock(self.LOCK_KEY, timeout=300)

        if lock.acquire(blocking=True, blocking_timeout=10):
            try:
                # 重新检查，可能其他进程已刷新
                existing = self.redis.get(self.TOKEN_KEY)
                if existing:
                    return existing

                # 调用 token/create 接口
                token_data = self._call_token_api(app_key, app_secret, shop_id)

                # 存入 Redis，过期时间比实际少 1 小时
                expires_in = token_data['expires_in']
                self.redis.setex(self.TOKEN_KEY, expires_in - 3600, token_data['access_token'])
                self.redis.setex(self.REFRESH_TOKEN_KEY, 14 * 86400, token_data['refresh_token'])

                return token_data['access_token']
            finally:
                lock.release()
        else:
            # 获取锁失败，等待后重试
            time.sleep(1)
            return self.redis.get(self.TOKEN_KEY)

    def _call_token_api(self, app_key: str, app_secret: str, shop_id: str) -> dict:
        """调用抖音 token/create 接口"""
        # 使用 SDK 或直接调用
        pass
```

### Token 刷新时机判断

```python
def should_refresh_token(redis_client, threshold_seconds: int = 3600) -> bool:
    """
    判断是否需要刷新 Token

    规则：
    - Token 不存在 → 需要刷新
    - Token 剩余有效期 < threshold_seconds → 需要刷新
    """
    ttl = redis_client.ttl(DouyinTokenManager.TOKEN_KEY)

    if ttl < 0:  # Key 不存在或已过期
        return True

    if ttl < threshold_seconds:  # 剩余有效期不足 1 小时
        return True

    return False
```

---

## 2. 归因与转链模块 (Attribution & Link)

**核心接口：** `buyin.instPickSourceConvert`（商品选品来源转链）

| 项目 | 说明 |
|------|------|
| **接口名称** | 商品选品来源转链 |
| **method** | `buyin.instPickSourceConvert` |
| **用途** | 将普通商品链接转换为带 `pick_source` 参数的推广链接 |
| **授权要求** | 无需授权 |
| **费用** | 免费 API |
| **API工具** | 可在开放平台 API 工具中联调 |
| **更新时间** | 2023-04-23 17:49:01 |
| **访问次数** | 3710 |
| **归因有效期** | `pick_source` 参数有效期为 **3 个月** |
| **限流规则** | 应用限流：10次/秒，接口总限流：100次/秒 |

### API权限包

| 权限组 | 权限包 |
|--------|--------|
| 精选联盟 | 精选联盟选品GMV |
| 精选联盟 | 联盟选品统计 |

### 公共参数

| 参数名称 | 类型 | 必须 | 示例值 | 说明 |
|----------|------|------|--------|------|
| `method` | String | 是 | `buyin.instPickSourceConvert` | 调用的 API 接口名称 |
| `app_key` | String | 是 | `6839996111118329223` | 应用创建完成后被分配的 key |
| `access_token` | String | 否 | `edae7c30-8386-443b-88a1-031111596fdd` | 用于调用 API 的 access_token |
| `param_json` | String | 是 | `{"pick_extra":"ch_123","product_url":"https://..."}` | 标准 JSON 类型，业务参数按参数名字符串大小排序 |
| `timestamp` | String | 是 | `2020-09-15 14:48:13` | 时间戳，格式 `yyyy-MM-dd HH:mm:ss`，时区 GMT+8 |
| `v` | String | 是 | `2` | API 协议版本，当前版本为 2 |
| `sign` | String | 是 | `796559d40beb08a1a1113c456c5c5a62` | 输入参数签名结果，签名算法参照 [API调用指南](https://op.jinritemai.com/docs/guide-docs/10/23) |
| `sign_method` | String | 否 | `hmac-sha256` | 签名算法类型，推荐 `hmac-sha256`（后续会下线 md5）。不传默认 `md5`。可选值：`md5` / `hmac-sha256` |

### 请求参数

| 参数名 | 类型 | 必须 | 示例值 | 说明 |
|--------|------|------|--------|------|
| `product_url` | String | 是 | `https://haohuo.jinritemai.com/views/product/item2?id=35119804196108` | 商品链接 |
| `pick_extra` | String | 否 | `dsds_2ew` | 自定义参数（只允许**数字、字母和 `_`**，限制长度 **≤20**） |

### 请求示例

```text
https://openapi-fxg.jinritemai.com/buyin/instPickSourceConvert?app_key=your_appkey_here&method=buyin.instPickSourceConvert&access_token=your_accesstoken_here&param_json={}&timestamp=2018-06-19%2016:06:59&v=2&sign=your_sign_here
```

### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `data` | Struct | 结果 |
| `data.product_url` | String | 转链后的商品链接，带有 `pick_source` 参数，链接后 pick_source 参数有效期为 3 个月 |

### 响应示例

```json
{
  "data": {
    "data": {
      "product_url": "https://haohuo.jinritemai.com/views/product/item2?id=35119804196108&pick_source=R9AhDER"
    }
  },
  "code": 10000,
  "msg": "success",
  "sub_code": "",
  "sub_msg": ""
}
```

### 完整错误码（业务返回码）

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **20000** | 系统错误 | `isp.service-error:256` | 服务打瞌睡了，请稍后再试 | 稍后重试 |
| **40004** | 非法的参数 | `isv.parameter-invalid:257` | 参数校验失败 | 检查参数格式 |
| **40004** | 非法的参数 | `isv.parameter-invalid:1056` | 无效商品URL | 检查 `product_url` 是否正确 |
| **40004** | 非法的参数 | `isv.parameter-invalid:4133` | 商品没有加入精选联盟 | 商品未加入精选联盟，无法转链 |
| **40004** | 非法的参数 | `isv.parameter-invalid:4125` | 获取商品信息失败 | 商品信息获取异常，稍后重试 |

### 归因逻辑

由于订单接口不回传 `pick_extra`，**必须**在本地建立 `pick_source_mapping` 表，记录返回的 `pick_source` 与 `channel_id` 的对应关系。

```
pick_extra 设计规范：channel_{user_id}
示例：channel_12345
长度检查：user_id 长度必须 ≤ 13 位（确保 channel_ + user_id ≤ 20）
```

### 开发实现

```python
from sqlalchemy import Column, String, Integer, DateTime, Index
from sqlalchemy.sql import func
import re


class PickSourceMapping(Base):
    """推广链接归因映射表"""
    __tablename__ = "pick_source_mapping"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    pick_source = Column(String(128), nullable=False, unique=True, index=True)
    channel_id = Column(Integer, nullable=False, index=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    expires_at = Column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        Index('ix_pick_source_created', 'pick_source', 'created_at'),
    )


def convert_product_link(product_url: str, user_id: int, douyin_client) -> dict:
    """
    商品转链并记录归因映射

    Args:
        product_url: 原始商品链接
        user_id: 渠道用户ID

    Returns:
        {"success": True, "converted_url": "...", "pick_source": "..."}
    """
    # 1. 构造 pick_extra（必须 ≤ 20 字符）
    pick_extra = f"ch_{user_id}"  # ch_ + 数字ID，确保 ≤ 20

    # 2. 调用转链接口
    response = douyin_client.call_api(
        method="buyin.instPickSourceConvert",
        params={"product_url": product_url, "pick_extra": pick_extra}
    )

    if response.get("code") != 10000:
        return {"success": False, "error": response.get("msg")}

    # 3. 提取转链后的 URL 和 pick_source
    converted_url = response["data"]["product_url"]
    pick_source = extract_pick_source(converted_url)

    # 4. 存入映射表
    expires_at = datetime.now() + timedelta(days=90)
    mapping = PickSourceMapping(
        pick_source=pick_source,
        channel_id=user_id,
        expires_at=expires_at
    )
    db.add(mapping)
    db.commit()

    return {"success": True, "converted_url": converted_url, "pick_source": pick_source}


def extract_pick_source(url: str) -> str:
    """从转链 URL 中提取 pick_source 参数值"""
    match = re.search(r'pick_source=([^&]+)', url)
    return match.group(1) if match else ""
```

### 归因流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                      推广链接生成环节                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  渠道商务在商品库点击"一键复制"                                   │
│       ↓                                                         │
│  系统调用 /buyin/instPickSourceConvert                           │
│       ├── product_url: 商品原始链接                             │
│       └── pick_extra: ch_{user_id}（限制20字符）               │
│       ↓                                                         │
│  抖音返回转链链接                                                │
│       └── product_url: ...&pick_source={抖音生成的归因码}       │
│       ↓                                                         │
│  系统存储映射关系：pick_source → channel_id                      │
│       ↓                                                         │
│  渠道商务复制链接发给达人                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      订单归因环节                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  用户通过链接下单                                                │
│       ↓                                                         │
│  抖音订单接口返回订单数据                                        │
│       └── 包含 pick_source 参数                                 │
│       ↓                                                         │
│  系统查询映射表                                                  │
│       └── pick_source → channel_id                              │
│       ↓                                                         │
│  业绩归属该渠道商务                                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 商品与活动管理 (Activity & Product)

**核心接口：** `/alliance/colonelActivityCreateOrUpdate` (团长活动创建/更新)

| 项目 | 说明 |
|------|------|
| **用途** | 创建或更新团长活动，设置佣金率、服务费率、独家保护期 |
| **权限包** | 精选联盟 - 团长活动管理 |
| **限流规则** | 应用限流：10次/秒，接口总限流：100次/秒 |

### 请求参数

| 参数名 | 类型 | 必须 | 说明 |
|--------|------|------|------|
| `activity_id` | String | 否 | 活动ID，**不传**=新建，**传值**=更新 |
| `activity_name` | String | 是 | 活动名称 |
| `activity_desc` | String | 否 | 活动描述 |
| `commission_rate` | String | 是 | 佣金率（如 `"0.15"` = 15%），格式为字符串 |
| `service_rate` | String | 是 | 服务费率（如 `"0.30"` = 30%），格式为字符串 |
| `months_of_protection` | Integer | 否 | 独家保护期月数（默认 0） |
| `start_time` | Integer | 否 | 活动开始时间（Unix 时间戳，秒级） |
| `end_time` | Integer | 否 | 活动结束时间（Unix 时间戳，秒级） |

### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `activity_id` | String | 活动ID（新建时返回） |
| `create_time` | Integer | 创建时间（Unix 时间戳） |
| `update_time` | Integer | 更新时间（Unix 时间戳） |

### 关键约束

| 规则 | 说明 |
|------|------|
| **佣金率 + 服务费率** | 必须 ≤ 90%，否则报错 |
| **服务费率** | 必须 ≤ 40%，否则报错 |
| **activity_name** | 必填，新建/更新均需传入 |
| **更新规则** | `activity_id` 存在时为更新，需同时传入 `commission_rate` 和 `service_rate` |

### 关键错误码

| 错误码 | 说明 | 解决方案 |
|--------|------|----------|
| **10000** | 成功 | - |
| **40004** | 参数校验失败 | 检查佣金率/服务费率是否超限 |
| **40004** | 佣金率+服务费率超过90% | 降低佣金率或服务费率 |
| **40004** | 服务费率超过40% | 降低服务费率 |
| **20000** | 系统错误 | 稍后重试 |

### 开发实现

```python
from typing import Optional
from dataclasses import dataclass


@dataclass
class ActivityCreateParams:
    """活动创建/更新参数"""
    activity_name: str
    commission_rate: float      # 如 0.15 = 15%
    service_rate: float         # 如 0.30 = 30%
    activity_id: Optional[str] = None
    activity_desc: Optional[str] = None
    months_of_protection: int = 0
    start_time: Optional[int] = None
    end_time: Optional[int] = None

    def validate(self) -> tuple[bool, str]:
        """校验参数合法性"""
        total = self.commission_rate + self.service_rate
        if total > 0.9:
            return False, f"佣金率+服务费率={total*100}% 超过90%上限"
        if self.service_rate > 0.4:
            return False, f"服务费率={self.service_rate*100}% 超过40%上限"
        if not self.activity_name:
            return False, "活动名称不能为空"
        return True, ""


def create_activity(params: ActivityCreateParams, douyin_client) -> dict:
    """
    创建团长活动

    Args:
        params: 活动参数
        douyin_client: 抖音API客户端

    Returns:
        {"success": True, "activity_id": "..."}
    """
    # 校验参数
    valid, error_msg = params.validate()
    if not valid:
        return {"success": False, "error": error_msg}

    # 构造请求参数（佣金率/服务费率传字符串格式）
    request_params = {
        "activity_name": params.activity_name,
        "commission_rate": str(params.commission_rate),
        "service_rate": str(params.service_rate),
    }

    if params.activity_desc:
        request_params["activity_desc"] = params.activity_desc
    if params.months_of_protection:
        request_params["months_of_protection"] = params.months_of_protection
    if params.start_time:
        request_params["start_time"] = params.start_time
    if params.end_time:
        request_params["end_time"] = params.end_time

    # 调用接口
    response = douyin_client.call_api(
        method="alliance.colonelActivityCreateOrUpdate",
        params=request_params
    )

    if response.get("code") != 10000:
        return {"success": False, "error": response.get("msg")}

    data = response.get("data", {})
    return {
        "success": True,
        "activity_id": data.get("activity_id"),
        "create_time": data.get("create_time"),
    }


def update_activity(activity_id: str, params: ActivityCreateParams, douyin_client) -> dict:
    """
    更新团长活动（复用 create_activity，传入 activity_id 即为更新）

    注意：更新时必须同时传入 commission_rate 和 service_rate
    """
    params.activity_id = activity_id
    return create_activity(params, douyin_client)
```

**辅助接口：** `/buyin/colonelActivityDetail` (活动详情)

- 用途：同步团长活动的门槛、服务费率、佣金率及独家保护期
- 关键字段：`months_of_protection`（独家保护期月份数）

**辅助接口：** `/alliance/colonelActivityProduct` (活动商品查询)

| 项目 | 说明 |
|------|------|
| **用途** | 获取专属团长活动商品列表，需团长百应授权 |
| **授权要求** | 需获取**机构授权、团长授权** |
| **接口属性** | 免费API |
| **API工具** | 可在开放平台 API 工具中联调 |
| **更新时间** | 2024-11-22 16:24:47 |
| **访问次数** | 4929 |
| **权限包** | 精选联盟 - 精选联盟机构团长 / 团长活动管理 |
| **限流规则** | 应用限流：60次/秒，接口总限流：900次/秒 |

### 公共参数

| 参数名 | 类型 | 必须 | 示例值 | 说明 |
|--------|------|------|--------|------|
| `method` | String | 是 | `alliance.colonelActivityProduct` | 调用的 API 接口名称 |
| `app_key` | String | 是 | `6839996111118329223` | 应用创建完成后分配的 key |
| `access_token` | String | 是 | `edae7c30-8386-443b-88a1-031111596fdd` | 用于调用 API 的 access_token |
| `param_json` | String | 是 | `{"activity_id":11111,"count":20}` | 标准 JSON 字符串，业务参数需按参数名字符串排序 |
| `timestamp` | String | 是 | `2020-09-15 14:48:13` | 时间戳，格式 `yyyy-MM-dd HH:mm:ss`，时区 GMT+8 |
| `v` | String | 是 | `2` | API 协议版本，当前版本为 2 |
| `sign` | String | 是 | `796559d40beb08a1a1113c456c5c5a62` | 入参签名结果，签名算法参考 [API调用指南](https://op.jinritemai.com/docs/guide-docs/10/23) |
| `sign_method` | String | 否 | `hmac-sha256` | 推荐 `hmac-sha256`（不传默认为 `md5`，后续建议下线） |

### 请求参数

| 参数名 | 类型 | 必须 | 示例值 | 说明 |
|--------|------|------|--------|------|
| `activity_id` | Number | 是 | `11111` | 活动 ID |
| `search_type` | Number | 否 | `4` | 召回结果排序条件：`0`=报名时间；`1`=活动价格；`2`=活动佣金比例；`4`=更新时间（推荐） |
| `sort_type` | Number | 否 | `0` | 排序顺序：`0`=升序；`1`=降序 |
| `page` | Number | 否 | `1` | 页码，从 1 开始。分页模式下生效，超过 10000 条会被限制，推荐游标模式 |
| `count` | Number | 是 | `20` | 每页数量（最多 20） |
| `cooperation_info` | String | 否 | `23412312` | 商家 ID / 商家名称 / 一级团长名称 / 一级团长 ID |
| `cooperation_type` | Number | 否 | `0` | 合作类型：`0`=不限；`1`=商家；`2`=团长 |
| `product_info` | String | 否 | `一个商品` | 商品 ID/名称（ID 精确匹配，名称模糊匹配） |
| `status` | Number | 否 | `0` | 活动商品状态：`0`=待审核；`1`=推广中；`2`=申请未通过；`3`=合作已终止；`6`=合作已到期 |
| `retrieve_mode` | Number | 否 | `1` | 获取模式：`0`=分页模式（默认，最多返回 10000 条）；`1`=游标模式（推荐，无条数限制） |
| `cursor` | String | 否 | `cefe2r3r2332dcd2/ddf32` | 游标。首页不传，下一页传入上次响应的 `next_cursor` |

### 请求示例

```java
AllianceColonelActivityProductRequest request = new AllianceColonelActivityProductRequest();
AllianceColonelActivityProductParam param = request.getParam();
param.setActivityId(11111L);
param.setSearchType(4L);
param.setSortType(0L);
param.setPage(1L);
param.setCount(20L);
param.setCooperationInfo("23412312");
param.setCooperationType(0);
param.setProductInfo("一个商品");
```

### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `data` | List | 活动商品列表 |
| `exclusion_duration` | Number | 团长保护期（单位：月） |
| `can_promote` | Bool | 能否推广（`true`=可推广） |
| `product_id` | Number | 商品ID |
| `title` | String | 商品名称 |
| `price` | Number | 商品售价（**单位：分**） |
| `cos_ratio` | Double | 佣金比例（`10` = 10%） |
| `cos_fee` | Number | 佣金金额（**单位：分**） |
| `first_cid` | Number | 商品一级类目 |
| `second_cid` | Number | 商品二级类目 |
| `third_cid` | Number | 商品三级类目 |
| `activity_cos_ratio` | Double | 活动佣金比例（`1000` = 10%） |
| `activity_price` | Double | 活动售价（单位元，保留两位小数，废弃） |
| `activity_cos_fee` | Double | 活动佣金金额（单位元，保留两位小数，废弃） |
| `service_ratio` | String | 招商团长服务费率 |
| `status` | Number | 商品审核状态：0=待审核，1=推广中，2=申请未通过，3=合作已终止，6=合作已到期 |
| `reason` | String | 审核原因 |
| `shop_id` | Number | 店铺ID |
| `shop_name` | String | 店铺名称 |
| `in_stock` | Bool | 是否有库存 |
| `sales` | Number | 销量 |
| `cover` | String | 商品主图 |
| `detail_url` | String | 商品团长活动链接（含 `pick_source`） |
| `shop_contact` | String | 商家联系方式（密文，不支持解密） |
| `apply_id` | Number | 审核ID（status=0 时返回） |
| `activity_start_time` | String | 活动开始时间 |
| `activity_end_time` | String | 活动结束时间 |
| `promotion_start_time` | String | 推广开始时间 |
| `promotion_end_time` | String | 推广结束时间 |
| `months_of_protection` | Number | 团长保护期月数 |
| `category_id` | Number | 行业类目 ID |
| `category_name` | String | 行业类目名称 |
| `product_stock` | String | 商品库存 |
| `is_new_shop` | Bool | 是否新手店铺 |
| `shop_score` | String | 店铺评分 |
| `colonel_coupon_info` | String | 团长优惠券信息 |
| `promotion_stock` | String | 活动库存（废弃） |
| `gift_info` | String | 赠品信息（废弃） |
| `promotion_step` | String | 活动实现方式（废弃） |
| `origin_institution_id` | Number | 原始团长 ID |
| `origin_institution_name` | String | 原始团长名称 |
| `origin_institution_phone` | String | 原始团长电话 |
| `origin_activity_start_time` | String | 原始团长活动有效期开始时间 |
| `origin_activity_end_time` | String | 原始团长活动有效期结束时间 |
| `origin_activity_id` | Number | 原始活动 ID |
| `is_trusteeship_product` | Bool | 托管商品标识（仅团长报团长场景） |
| `update_time` | String | 更新时间 |
| `origin_colonel_buyin_id` | Number | 团长百应 ID |
| `total` | Number | 活动商品条数（游标模式下不返回） |
| `institution_id` | Number | 机构 ID |
| `next_cursor` | String | 下一页游标（游标模式下返回，最后一页返回空） |

### 响应示例

```json
{
  "data": {
    "data": [
      {
        "activity_cos_fee": "1",
        "activity_cos_ratio": "1000",
        "activity_end_time": "2021-09-02",
        "activity_price": "111",
        "activity_start_time": "2021-09-02",
        "apply_id": "11111"
      }
    ],
    "next_cursor": "ee2k23i492njwtio5j435ij34n5n34"
  },
  "code": 10000,
  "msg": "success"
}
```

### 完整错误码（业务返回码）

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **50002** | 业务处理失败 | `isv.business-failed:257` | 参数校验失败 | 检查请求参数 |
| **50002** | 业务处理失败 | `isv.business-failed:4097` | 无效分页大小 | 每页最多20条数据 |
| **50002** | 业务处理失败 | `isv.business-failed:4197` | 需【招商团长】角色授权后访问 | 检查授权 |
| **50002** | 业务处理失败 | `isv.business-failed:8197` | 不允许继续翻页，请使用游标模式 | 改用游标模式 `retrieve_mode=1` |
| **50002** | 业务处理失败 | `isv.business-failed:4200` | 账号状态异常 | 检查账号状态 |
| **20000** | 系统错误 | `isp.service-error:256` | 服务打瞌睡了，请稍后再试 | 稍后重试 |

### 开发实现

```python
def get_activity_products(activity_id: int, count: int = 20) -> list:
    """
    获取活动商品列表（游标模式）

    Args:
        activity_id: 活动ID
        count: 每页数量（最大20）

    Returns:
        [{"product_id": "...", "title": "...", ...}, ...]
    """
    all_products = []
    cursor = None

    while True:
        params = {
            "activity_id": activity_id,
            "count": count,
            "retrieve_mode": 1,  # 游标模式（推荐）
            "search_type": 4,    # 按更新时间排序
            "sort_type": 1,      # 降序
        }
        if cursor:
            params["cursor"] = cursor

        response = douyin_client.call_api(
            method="alliance.colonelActivityProduct",
            params=params
        )

        if response.get("code") != 10000:
            logger.error(f"获取活动商品失败: {response}")
            break

        data = response.get("data", {})
        products = data.get("data", [])
        all_products.extend(products)

        cursor = data.get("next_cursor", "")
        if not cursor:  # 最后一页
            break

    return all_products
```

**辅助接口：** `/buyin/colonelActivityDetail` (团长活动详情)

| 项目 | 说明 |
|------|------|
| **用途** | 查询指定团长活动的活动详情，支持查询自建和可参与报名的活动 |
| **授权要求** | 需获取**团长授权** |
| **权限包** | 精选联盟 - 精选联盟机构团长 / 团长活动管理 |
| **限流规则** | 应用限流：200次/秒，接口总限流：600次/秒 |

### 请求参数

| 参数名 | 类型 | 必须 | 说明 |
|--------|------|------|------|
| `activity_id` | Int64 | 是 | 活动ID |

### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `institution_id` | Int64 | 机构ID |
| `activity_id` | Int64 | 活动ID |
| `activity_name` | String | 活动名称 |
| `activity_desc` | String | 活动描述 |
| `apply_start_time` | String | 活动报名开始时间（格式：`yyyy-MM-dd`） |
| `apply_end_time` | String | 活动报名结束时间（格式：`yyyy-MM-dd`） |
| `commission_rate` | String | 活动要求的**最低佣金率**（如 `"500"` = 5%） |
| `service_rate` | String | 活动要求的**最低服务费率**（如 `"1000"` = 10%） |
| `is_new_shop` | Bool | 报名门槛是否限制新手店铺 |
| `shop_type` | List | 报名门槛店铺类型：`0`=普通店，`1`=专营店，`2`=旗舰店，`3`=专卖店，`4`=商场店，`5`=专卖型旗舰店，`6`=官方旗舰店，`7`=企业店，`8`=个体店 |
| `activity_type` | Int64 | 活动类型：`1`=全部商家，`2`=指定商家 |
| `specified_shop_ids` | String | 指定商家ID |
| `categories` | List | 招商类目 |
| `shop_score` | Int32 | 报名门槛-店铺体验分（`0`表示不限） |
| `min_promotion_days` | Int64 | 最短推广周期：`0`=不限，`15`/`30`/`60`/`90`=具体天数 |
| `threshold_cross_border` | Int64 | 跨境店铺：`0`=可报名，`1`=不可报名 |
| `has_threshold` | Bool | 是否有报名门槛 |
| `colonel_buyin_id` | Int64 | 团长百应ID |
| `colonel_name` | String | 团长名称 |
| `months_of_protection` | Int64 | 限时独家期限（0-6个月） |
| `wechat_id` | String | 微信号（密文，需解密） |
| `phone_num` | String | 联系电话（密文，需解密） |
| `estimated_single_sale` | String | 预估销售额 |

### 完整错误码

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **50002** | 业务处理失败 | `isv.business-failed:500` | 活动不存在 | 检查 activity_id 是否正确 |
| **40004** | 非法的参数 | `isv.parameter-invalid:257` | 参数校验失败 | 检查参数格式 |
| **20000** | 系统错误 | `isp.service-error:256` | 服务打瞌睡了，请稍后再试 | 稍后重试 |

### 开发实现

```python
def get_activity_detail(activity_id: int) -> dict:
    """
    获取团长活动详情

    Args:
        activity_id: 活动ID

    Returns:
        {"success": True, "activity": {...}}
    """
    response = douyin_client.call_api(
        method="buyin.colonelActivityDetail",
        params={"activity_id": activity_id}
    )

    if response.get("code") != 10000:
        return {"success": False, "error": response.get("msg")}

    data = response.get("data", {})
    return {
        "success": True,
        "activity": {
            "activity_id": data.get("activity_id"),
            "activity_name": data.get("activity_name"),
            "commission_rate": int(data.get("commission_rate", 0)) / 10000,  # 转换为百分比
            "service_rate": int(data.get("service_rate", 0)) / 10000,
            "min_promotion_days": data.get("min_promotion_days"),
            "months_of_protection": data.get("months_of_protection"),
            "apply_start_time": data.get("apply_start_time"),
            "apply_end_time": data.get("apply_end_time"),
        }
    }
```

**辅助接口：** `/buyin/applyActivities` (商品团长活动提报)

| 项目 | 说明 |
|------|------|
| **用途** | 支持店铺授权将抖店商品申请提报到团长活动中 |
| **授权要求** | 需获取**店铺授权** |
| **权限包** | 精选联盟 - 精选联盟商品推广 / 商家联盟推广 |
| **限流规则** | 接口总限流：50次/秒 |

### 请求参数

| 参数名 | 类型 | 必须 | 说明 |
|--------|------|------|------|
| `activity_id` | Int64 | 是 | 团长活动ID |
| `phone_number` | String | 是 | 店铺联系电话 |
| `products` | List | 是 | 商品列表 |
| `service_ratio` | String | 否 | 招商服务费率，**必须为两位小数**（如 `"20.50"` = 20.50%），服务费率+佣金率需小于 90% |

### 响应参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `total` | Int64 | 操作的商品数量 |
| `result` | List | 结果列表 |
| `result[].product_id` | Int64 | 商品ID |
| `result[].is_success` | Bool | 操作结果：`true`=成功，`false`=失败 |
| `result[].error_code` | Int64 | 错误码，成功为 `0` |
| `result[].error_msg` | String | 错误原因，成功为 `success` |

### 响应示例

```json
{
  "data": {
    "total": 2,
    "result": [
      {
        "product_id": "123123123123",
        "is_success": true,
        "error_code": "0",
        "error_msg": "success"
      },
      {
        "product_id": "456456456456",
        "is_success": false,
        "error_code": "500",
        "error_msg": "商品已在活动中"
      }
    ]
  },
  "code": 10000,
  "msg": "success"
}
```

### 完整错误码

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **20000** | 系统错误 | `isp.service-error:256` | 服务打瞌睡了，请稍后再试 | 稍后重试 |
| **50002** | 业务处理失败 | `isv.business-failed:110110` | 活动报名时间已结束，不支持继续报名 | 检查活动有效期 |
| **50002** | 业务处理失败 | `isv.business-failed:8192` | 小数服务费率不合法，必须为两位小数 | service_ratio 必须为两位小数，如 `"20.50"` |
| **50002** | 业务处理失败 | `isv.business-failed:500` | 服务打瞌睡了，请稍后再试 | 稍后重试 |
| **50002** | 业务处理失败 | `isv.business-failed:10001` | 请检查输入参数 | 检查必填参数 |

### 开发实现

```python
def apply_to_activity(
    activity_id: int,
    product_ids: list[str],
    phone_number: str,
    service_ratio: str = None
) -> dict:
    """
    提报商品到团长活动

    Args:
        activity_id: 团长活动ID
        product_ids: 商品ID列表
        phone_number: 店铺联系电话
        service_ratio: 招商服务费率（两位小数，如 "20.50"）

    Returns:
        {"success": True, "total": 2, "results": [...]}
    """
    # 校验服务费率格式
    if service_ratio:
        if len(service_ratio.split('.')[-1]) != 2:
            return {"success": False, "error": "服务费率必须为两位小数"}

    response = douyin_client.call_api(
        method="buyin.applyActivities",
        params={
            "activity_id": activity_id,
            "phone_number": phone_number,
            "products": product_ids,
            "service_ratio": service_ratio,
        }
    )

    if response.get("code") != 10000:
        return {"success": False, "error": response.get("msg")}

    data = response.get("data", {})
    results = data.get("result", [])

    # 统计成功/失败
    success_count = sum(1 for r in results if r.get("is_success") == "true")
    failed_items = [r for r in results if r.get("is_success") != "true"]

    return {
        "success": True,
        "total": data.get("total", 0),
        "success_count": success_count,
        "failed_items": failed_items,
    }
```

---

## 3.2 营销活动接口 (Marketing)

**核心接口：** `instantShopping.marketing/listActivities`（查询活动列表）

| 项目 | 说明 |
|------|------|
| **接口名称** | 查询活动列表 |
| **method** | `instantShopping.marketing/listActivities` |
| **用途** | 查询限时限量购、店铺券、商品券等活动列表 |
| **授权要求** | 需获取**店铺授权** |
| **限流规则** | 应用限流：20次/秒，接口总限流：100次/秒 |

### API权限包

| 权限组 | 权限包 |
|--------|--------|
| 营销 | 营销管理（即时零售） |

### 公共参数

| 参数名称 | 类型 | 必须 | 示例值 | 说明 |
|----------|------|------|--------|------|
| `method` | String | 是 | `instantShopping.marketing/listActivities` | 调用的 API 接口名称 |
| `app_key` | String | 是 | `6839996111118329223` | 应用创建完成后被分配的 key |
| `access_token` | String | 是 | `edae7c30-8386-443b-88a1-031111596fdd` | 用于调用 API 的 access_token |
| `param_json` | String | 是 | `{"page":1,"size":20}` | 标准 JSON 类型，业务参数按参数名字符串大小排序 |
| `timestamp` | String | 是 | `2020-09-15 14:48:13` | 时间戳，格式 `yyyy-MM-dd HH:mm:ss`，时区 GMT+8 |
| `v` | String | 是 | `2` | API 协议版本，当前版本为 2 |
| `sign` | String | 是 | `796559d40beb08a1a1113c456c5c5a62` | 输入参数签名结果，签名算法参照 [API调用指南](https://op.jinritemai.com/docs/guide-docs/10/23) |
| `sign_method` | String | 否 | `hmac-sha256` | 签名算法类型，推荐 `hmac-sha256`（后续会下线 md5）。不传默认 `md5`。可选值：`md5` / `hmac-sha256` |

### 请求参数

| 参数名 | 类型 | 必须 | 示例值 | 说明 |
|--------|------|------|--------|------|
| `page` | Number | 是 | `1` | 翻页页码 |
| `size` | Number | 是 | `20` | 每页条数 |
| `status` | Number | 否 | `90` | 活动状态 |
| `activity_type` | Number | 否 | `10` | 活动类型：`10`=限时限量购，`20`=店铺券，`30`=商品券 |
| `activity_sub_type` | Number | 否 | `101` | 活动子类型：`101`=限时秒杀，`102`=普通降价促销 |
| `activity_id_list` | List | 否 | `[123,124]` | 活动ID列表 |

### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `data` | List | 结果列表 |
| `data[].activity_id` | String | 活动ID |
| `data[].activity_title` | String | 活动标题 |
| `data[].activity_type` | Number | 活动类型：10=限时限量购，20=店铺券，30=商品券 |
| `data[].activity_sub_type` | Number | 活动子类型：101=限时秒杀，102=普通降价促销 |
| `data[].activity_status` | Number | 活动状态 |
| `data[].activity_base_info` | Struct | 活动基础信息 |
| `data[].activity_base_info.activity_start_time` | Number | 活动开始时间（Unix 时间戳，秒级） |
| `data[].activity_base_info.activity_end_time` | Number | 活动结束时间（Unix 时间戳，秒级） |
| `data[].activity_base_info.order_cancel_time` | Number | 订单取消时间（秒） |
| `data[].activity_base_info.limit_activity_stock` | Number | 是否限制活动库存：1=不限活动库存，2=限活动库存 |
| `data[].activity_base_info.activity_stock_sold_setting` | Number | 活动库存售罄策略：1=恢复为原价继续售卖，2=停止售卖（标示"已抢光"） |
| `data[].activity_base_info.warm_up` | Bool | 是否预热（目前仅支持不预热） |
| `total` | Number | 总数 |

### 响应示例

```json
{
  "data": {
    "data": [
      {
        "activity_base_info": {
          "activity_end_time": "1711265959",
          "activity_start_time": "1711265959",
          "activity_stock_sold_setting": "1",
          "limit_activity_stock": "1",
          "order_cancel_time": "300",
          "warm_up": false
        },
        "activity_id": "123",
        "activity_title": "测试活动",
        "activity_type": 10,
        "activity_sub_type": 101,
        "activity_status": 90
      }
    ],
    "total": 100
  },
  "code": 10000,
  "msg": "success",
  "sub_code": "",
  "sub_msg": ""
}
```

### 完整错误码

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **50002** | 业务处理失败 | `isv.business-failed:-40000` | 参数不合法 | 检查请求参数 |
| **40004** | 非法的参数 | `isv.parameter-invalid:-50001` | 越权错误 | 检查当前用户是否有权限操作 |
| **20001** | 内部服务超时 | `isp.service-timeout-error:-10000` | 系统繁忙，请稍后重试 | 系统繁忙，请稍后重试 |
| **20000** | 系统错误 | `isp.service-error:-11450` | 系统异常，请联系技术人员排查 | 系统异常，请联系技术人员排查 |

### 开发实现

```python
def list_marketing_activities(
    page: int = 1,
    size: int = 20,
    activity_type: int = None,
    status: int = None
) -> dict:
    """
    查询营销活动列表

    Args:
        page: 页码
        size: 每页条数
        activity_type: 活动类型（10=限时限量购, 20=店铺券, 30=商品券）
        status: 活动状态

    Returns:
        {"success": True, "total": 100, "activities": [...]}
    """
    params = {
        "page": page,
        "size": size,
    }
    if activity_type:
        params["activity_type"] = activity_type
    if status:
        params["status"] = status

    response = douyin_client.call_api(
        method="instantShopping.marketing/listActivities",
        params=params
    )

    if response.get("code") != 10000:
        return {"success": False, "error": response.get("msg")}

    data = response.get("data", {})
    return {
        "success": True,
        "total": data.get("total", 0),
        "activities": data.get("data", []),
    }
```

---

## 3.3 商品详情模块 (Product Detail)

**核心接口：** `/product/detail` (商品详情查询)

| 项目 | 说明 |
|------|------|
| **用途** | 查询抖店商品详情信息，支持商品ID和外部编码查询 |
| **授权要求** | 需获取**店铺授权** |
| **权限包** | 商品 - 商品信息查询 |
| **限流规则** | 应用限流：1000次/秒，接口总限流：8000次/秒 |

### 公共参数

| 参数名称 | 类型 | 必须 | 示例值 | 说明 |
|----------|------|------|--------|------|
| `method` | String | 是 | `product.detail` | 调用的API接口名称 |
| `app_key` | String | 是 | `6839996111118329223` | 应用创建完成后被分配的key |
| `access_token` | String | 是 | `edae7c30-8386-443b-88a1-031111596fdd` | 用于调用API的access_token |
| `param_json` | String | 是 | `{"product_id":"3558192687276554544"}` | 标准JSON类型，业务参数按参数名字符串大小排序 |
| `timestamp` | String | 是 | `2020-09-15 14:48:13` | 时间戳，格式`yyyy-MM-dd HH:mm:ss`，时区GMT+8 |
| `v` | String | 是 | `2` | API协议版本，当前版本为2 |
| `sign` | String | 是 | `796559d40beb08a1a1113c456c5c5a62` | 输入参数签名结果 |
| `sign_method` | String | 否 | `hmac-sha256` | 签名算法类型，推荐`hmac-sha256` |

### 请求参数

| 参数名 | 类型 | 必须 | 示例值 | 说明 |
|--------|------|------|--------|------|
| `product_id` | String | 否 | `3558192687276554544` | 商品ID，抖店系统生成，店铺下唯一；长度19位 |
| `out_product_id` | String | 否 | `dy001` | 外部商家编码，商家自定义字段 |
| `show_draft` | String | 否 | `false` | `true`：读取草稿数据；`false`：读取线上数据；不传默认为`false` |
| `store_id` | String | 否 | `1111420330` | 门店ID（即时零售单店版无需使用） |

### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `product_id` | Number | 商品ID，抖店系统生成，店铺下唯一；长度19位 |
| `product_id_str` | String | 商品ID（字符串类型） |
| `outer_product_id` | String | 外部商家编码，支持最多255个字符 |
| `name` | String | 商品标题（至少8个字，最多30个字） |
| `description` | String | 商品详情（HTML格式，最多50张图） |
| `status` | Number | 商品状态：0-在线，1-下线，2-删除 |
| `check_status` | Number | 审核状态：1-未提交，2-待审核，3-审核通过，4-审核未通过，5-封禁，7-审核通过待上架 |
| `pic` | List | 商品主图（最多5张，仅支持png/jpg/jpeg，1:1比例至少600*600px） |
| `market_price` | Number | 划线价，**单位：分** |
| `discount_price` | Number | 售卖价，**单位：分** |
| `spec_prices` | List | 商品SKU详情列表 |
| `spec_prices[].sku_id` | Number | 规格ID |
| `spec_prices[].outer_sku_id` | String | 外部SKU编码 |
| `spec_prices[].spec_detail_ids` | List | 规格ID列表 |
| `spec_prices[].stock_num` | Number | 可售库存 |
| `spec_prices[].price` | Number | SKU价格，**单位：分** |
| `spec_prices[].sku_status` | Bool | SKU状态，`true`上架，`false`下架 |
| `category_detail` | Struct | 类目详情 |
| `category_detail.first_cid` | Number | 一级类目ID |
| `category_detail.second_cid` | Number | 二级类目ID |
| `category_detail.third_cid` | Number | 三级类目ID |
| `category_detail.fourth_cid` | Number | 四级类目ID |
| `category_detail.is_leaf` | Bool | 是否叶子类目 |
| `standard_brand_id` | Number | 品牌库brand id |
| `presell_type` | Number | 预售类型：0-非预售，1-全款预售，2-阶梯库存 |
| `delivery_method` | Number | 承诺发货时间（单位：天） |
| `create_time` | String | 商品创建时间（格式：`yyyy-MM-dd HH:mm:ss`） |
| `update_time` | String | 商品更新时间 |

### 完整错误码

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **40004** | 非法的参数 | `isv.parameter-invalid:4` | out_product_id未找到或商品已删除 | 请检查参数后重试 |
| **40004** | 非法的参数 | `isv.parameter-invalid:2010058` | 商品不存在或已彻底删除 | 请检查参数后重试 |
| **50002** | 业务处理失败 | `isv.business-failed:2010401` | 触发商品操作限流,请稍后再试 | 请稍后再试 |
| **50002** | 业务处理失败 | `isv.business-failed:2010004` | 商品参数错误 | 请检查参数后重试 |
| **50002** | 业务处理失败 | `isv.business-failed:2010203` | 获取商品信息失败 | 请检查参数后重试 |
| **20000** | 系统错误 | `isp.service-error:7` | 请求失败，请稍后再试 | 请检查参数后重试 |
| **20000** | 系统错误 | `isp.service-error:2010001` | 系统异常,请重试 | 请稍后再试 |
| **20000** | 系统错误 | `isp.service-error:2010327` | 销售属性查询失败 | 请稍后再试 |
| **20000** | 系统错误 | `isp.service-error:2010013` | 数据查询异常 | 请稍后再试 |
| **20000** | 系统错误 | `isp.service-error:2010321` | 类目属性查询异常 | 请稍后再试 |

### 开发实现

```python
from dataclasses import dataclass
from typing import Optional


@dataclass
class ProductDetail:
    """商品详情数据结构"""
    product_id: str
    name: str
    status: int           # 0-在线, 1-下线, 2-删除
    check_status: int     # 1-未提交, 2-待审核, 3-审核通过, 4-审核未通过, 5-封禁, 7-待上架
    market_price: int      # 单位：分
    discount_price: int   # 单位：分
    stock: int
    pics: list[str]
    category_ids: dict     # {"first_cid": ..., "second_cid": ..., "third_cid": ..., "fourth_cid": ...}
    skus: list[dict]      # SKU列表


def get_product_detail(product_id: str = None, outer_product_id: str = None) -> dict:
    """
    获取商品详情

    Args:
        product_id: 抖店商品ID（19位）
        outer_product_id: 外部商家编码

    Returns:
        {"success": True, "product": {...}}
    """
    params = {}
    if product_id:
        params["product_id"] = product_id
    if outer_product_id:
        params["out_product_id"] = outer_product_id

    response = douyin_client.call_api(
        method="product.detail",
        params=params
    )

    if response.get("code") != 10000:
        return {"success": False, "error": response.get("msg")}

    data = response.get("data", {})

    # 提取关键信息
    product = ProductDetail(
        product_id=str(data.get("product_id", "")),
        name=data.get("name", ""),
        status=data.get("status", 0),
        check_status=data.get("check_status", 1),
        market_price=int(data.get("market_price", 0)),
        discount_price=int(data.get("discount_price", 0)),
        stock=_calc_total_stock(data.get("spec_prices", [])),
        pics=data.get("pic", []),
        category_ids=_extract_category(data.get("category_detail", {})),
        skus=_normalize_skus(data.get("spec_prices", [])),
    )

    return {"success": True, "product": product}


def _calc_total_stock(spec_prices: list) -> int:
    """计算商品总库存"""
    return sum(sku.get("stock_num", 0) for sku in spec_prices)


def _extract_category(category_detail: dict) -> dict:
    """提取类目信息"""
    return {
        "first_cid": category_detail.get("first_cid"),
        "second_cid": category_detail.get("second_cid"),
        "third_cid": category_detail.get("third_cid"),
        "fourth_cid": category_detail.get("fourth_cid"),
    }


def _normalize_skus(spec_prices: list) -> list:
    """标准化SKU数据"""
    return [
        {
            "sku_id": str(sku.get("sku_id", "")),
            "outer_sku_id": sku.get("outer_sku_id", ""),
            "price": sku.get("price", 0),
            "stock": sku.get("stock_num", 0),
            "status": sku.get("sku_status", False),
        }
        for sku in spec_prices
    ]
```

### 商品状态流转

```
审核状态: 未提交(1) → 待审核(2) → 审核通过(3) / 审核未通过(4) → 封禁(5)
上下架: 审核通过(3) → 待上架(7) → 上线(0)
        上线(0) → 下线(1) → 删除(2)
```

---

## 4.1 订单敏感数据解密 (Order Batch Decrypt)

**核心接口：** `/order/batchDecrypt` (批量解密接口)

| 项目 | 说明 |
|------|------|
| **用途** | 批量解密订单中的敏感数据（收件人电话、姓名、详细地址等） |
| **授权要求** | 需获取**店铺授权、物流商授权** |
| **权限包** | 订单 - 订单管理 / 消费者敏感数据解密 |
| **限流规则** | 应用限流：100次/秒，接口总限流：1500次/秒 |
| **批次限制** | 最大支持一次解密 **50 条**数据 |

### 公共参数

| 参数名称 | 类型 | 必须 | 示例值 | 说明 |
|----------|------|------|--------|------|
| `method` | String | 是 | `order.batchDecrypt` | 调用的API接口名称 |
| `app_key` | String | 是 | `6839996111118329223` | 应用创建完成后被分配的key |
| `access_token` | String | 是 | `edae7c30-8386-443b-88a1-031111596fdd` | 用于调用API的access_token |
| `param_json` | String | 是 | `{"cipher_infos":[...]}` | 标准JSON类型，业务参数按参数名字符串大小排序 |
| `timestamp` | String | 是 | `2020-09-15 14:48:13` | 时间戳，格式`yyyy-MM-dd HH:mm:ss`，时区GMT+8 |
| `v` | String | 是 | `2` | API协议版本，当前版本为2 |
| `sign` | String | 是 | `796559d40beb08a1a1113c456c5c5a62` | 输入参数签名结果 |
| `sign_method` | String | 否 | `hmac-sha256` | 签名算法类型，推荐`hmac-sha256` |

### 请求参数

| 参数名 | 类型 | 必须 | 示例值 | 说明 |
|--------|------|------|--------|------|
| `cipher_infos` | List | 是 | `[{"auth_id":"4933609365066313446","cipher_text":"..."}]` | 待解密值集合，最多50条 |
| `cipher_infos[].auth_id` | String | 是 | `4933609365066313446` | 正向解密使用**订单号**，逆向退款解密使用**售后单号** |
| `cipher_infos[].cipher_text` | String | 是 | `#ML3B#0BB1W4adLHYf...` | 待解密值（信封加密格式） |
| `account_id` | String | 否 | `dy1001` | 服务商账号体系中商户的账户ID |
| `account_type` | String | 否 | `main_account` | 商户账户ID类型：`main_account`=主账号，`sub_account`=子账号 |
| `need_virtual_phone` | Int64 | 否 | `0` | 仅代拍场景使用：`0`=使用代拍虚拟号逻辑，`1`=不使用虚拟号 |

### 响应参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `decrypt_infos` | List | 解密列表 |
| `decrypt_infos[].auth_id` | String | 业务标识（订单号或自定义标识） |
| `decrypt_infos[].cipher_text` | String | 密文值 |
| `decrypt_infos[].decrypt_text` | String | 解密后明文 |
| `decrypt_infos[].data_type` | Int64 | 加密类型：`1`=地址，`2`=姓名，`3`=手机号，`4`=身份证 |
| `decrypt_infos[].is_virtual_tel` | Bool | 手机号描述：`false`=真实手机号，`true`=虚拟手机号 |
| `decrypt_infos[].phone_no_a` | String | 虚拟号主机号（`is_virtual_tel=true`时有值） |
| `decrypt_infos[].phone_no_b` | String | 虚拟号分机号（`is_virtual_tel=true`时有值） |
| `decrypt_infos[].expire_time` | Int64 | 虚拟号过期时间（时间戳） |
| `decrypt_infos[].err_no` | Int64 | 错误码，`0`=成功 |
| `decrypt_infos[].err_msg` | String | 错误描述 |
| `decrypt_infos[].custom_err` | Struct | 业务错误信息 |

### 响应示例

```json
{
  "code": 10000,
  "msg": "success",
  "data": {
    "decrypt_infos": [
      {
        "auth_id": "4933609365066313446",
        "cipher_text": "$c0qBj0QrZm9qae50eoBTInPj8PWvCjUyafxWt5cELI0=...",
        "decrypt_text": "18400913965-7576",
        "data_type": 3,
        "is_virtual_tel": true,
        "phone_no_a": "18400913965",
        "phone_no_b": "7576",
        "expire_time": 1653396024,
        "err_no": 0,
        "err_msg": ""
      }
    ]
  }
}
```

### 完整错误码

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **50002** | 业务处理失败 | `isv.business-failed:300003` | 业务处理失败,详情请看sub_msg | 检查sub_msg |
| **50002** | 业务处理失败 | `isv.business-failed:300008` | 解密接口存在部分失败 | 部分数据解密失败 |
| **50002** | 业务处理失败 | `isv.business-failed:300001` | 解密错误: 批量解密存在部分失败 | 检查密文格式 |
| **50002** | 业务处理失败 | `isv.business-failed:300007` | 解密错误: 批量解密存在部分失败 | 检查密文格式 |

### 虚拟号处理规则

| 场景 | `is_virtual_tel` | `decrypt_text` 返回值 |
|------|------------------|----------------------|
| 真实手机号 | `false` | 明文手机号（如 `13800138000`） |
| 虚拟手机号 | `true` | 虚拟号主机-分机号（如 `18400913965-7576`） |

**虚拟号有效期：** 通过 `expire_time` 字段获取过期时间，过期后需重新请求解密。

### 开发实现

```python
from dataclasses import dataclass
from typing import Optional


@dataclass
class DecryptResult:
    """解密结果"""
    auth_id: str
    success: bool
    plain_text: Optional[str] = None
    data_type: Optional[int] = None
    is_virtual_tel: bool = False
    virtual_phone_a: Optional[str] = None
    virtual_phone_b: Optional[str] = None
    expire_time: Optional[int] = None
    error_msg: Optional[str] = None


def batch_decrypt(cipher_infos: list[dict], account_id: str = None) -> dict:
    """
    批量解密订单敏感数据

    Args:
        cipher_infos: 待解密列表，格式：[{"auth_id": "订单号", "cipher_text": "密文"}, ...]
        account_id: 服务商账号（可选）

    Returns:
        {"success": True, "results": [DecryptResult, ...]}
    """
    # 限制每次最多50条
    if len(cipher_infos) > 50:
        return {"success": False, "error": "每次最多解密50条数据"}

    params = {"cipher_infos": cipher_infos}
    if account_id:
        params["account_id"] = account_id

    response = douyin_client.call_api(
        method="order.batchDecrypt",
        params=params
    )

    if response.get("code") != 10000:
        return {"success": False, "error": response.get("msg")}

    data = response.get("data", {})
    decrypt_infos = data.get("decrypt_infos", [])

    results = []
    for item in decrypt_infos:
        err_no = item.get("err_no", 0)
        is_success = err_no == 0

        result = DecryptResult(
            auth_id=item.get("auth_id", ""),
            success=is_success,
            plain_text=item.get("decrypt_text") if is_success else None,
            data_type=item.get("data_type"),
            is_virtual_tel=item.get("is_virtual_tel", False),
            virtual_phone_a=item.get("phone_no_a"),
            virtual_phone_b=item.get("phone_no_b"),
            expire_time=item.get("expire_time"),
            error_msg=item.get("err_msg") if not is_success else None,
        )
        results.append(result)

    return {"success": True, "results": results}


def decrypt_order_phone(order_id: str, phone_cipher: str) -> dict:
    """
    解密订单手机号（单条解密示例）

    Args:
        order_id: 订单号
        phone_cipher: 手机号密文

    Returns:
        {"success": True, "phone": "...", "is_virtual": False}
    """
    cipher_infos = [{"auth_id": order_id, "cipher_text": phone_cipher}]
    result = batch_decrypt(cipher_infos)

    if not result.get("success"):
        return result

    decrypt_result = result["results"][0]
    if not decrypt_result.success:
        return {"success": False, "error": decrypt_result.error_msg}

    return {
        "success": True,
        "phone": decrypt_result.plain_text,
        "is_virtual": decrypt_result.is_virtual_tel,
        "virtual_phone_a": decrypt_result.virtual_phone_a,
        "virtual_phone_b": decrypt_result.virtual_phone_b,
    }
```

### 敏感字段标记（订单接口中红色标识字段）

| 字段类型 | 加密格式 | 解密后用途 |
|----------|----------|-----------|
| 收件人电话 | 信封加密 | 发货联系用户 |
| 收件人姓名 | 信封加密 | 发货填单 |
| 详细地址 | 信封加密 | 发货配送 |
| 账号值 | 信封加密 | 虚拟商品发货 |
| 证件号 | 信封加密 | 实名认证 |

---

## 4.2 分次结算订单查询

| 项目 | 说明 |
|------|------|
| **用途** | 查询团长分次结算订单，用于业绩对账和财务核算 |
| **授权要求** | 需获取**团长授权** |
| **权限包** | 精选联盟 - 精选联盟机构团长 / 团长订单 |
| **限流规则** | 接口总限流：100次/秒 |
| **时间范围** | 开始与结束时间最大间隔为 **90 天** |

### 请求参数

| 参数名 | 类型 | 必须 | 说明 |
|--------|------|------|------|
| `time_type` | String | 否 | 查询时间类型：`settle`=结算时间，`update`=更新时间（默认） |
| `start_time` | String | 否 | 开始时间，格式：`yyyy-MM-dd HH:mm:ss` |
| `end_time` | String | 否 | 结束时间，格式：`yyyy-MM-dd HH:mm:ss` |
| `size` | Int64 | 否 | 每页订单记录数目，最大 **100**，默认 50 |
| `cursor` | String | 否 | 下一页索引，默认传 `0` |
| `order_ids` | String | 否 | 订单号列表，逗号分隔，最多 **100 个** |

### 响应参数

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `order_id` | String | 订单号（唯一主键，用于 Upsert） |
| `product_id` | String | 商品ID |
| `product_name` | String | 商品名称 |
| `shop_id` | Int64 | 店铺ID |
| `colonel_buyin_id` | String | 团长百应ID |
| `colonel_activity_id` | Int64 | 团长活动ID |
| `settle_colonel_commission` | Int64 | **一级团长服务费，单位：分** |
| `settle_colonel_tech_service_fee` | Int64 | **一级团长平台技术服务费，单位：分** |
| `second_colonel_buyin_id` | String | 二级团长百应ID |
| `second_colonel_activity_id` | String | 二级团长活动ID |
| `settle_second_colonel_commission` | Int64 | **二级团长服务费，单位：分** |
| `phase_id` | Int64 | 第 N 次结算（分次结算标识） |
| `settle_time` | String | 结算时间，格式：`yyyy-MM-dd HH:mm:ss` |
| `update_time` | String | 记录更新时间，格式：`yyyy-MM-dd HH:mm:ss` |
| `cursor` | String | 下一页索引，最后一页返回空 |

### 完整错误码

| 主码 | 主描述 | 子码 | 子描述 | 解决方案 |
|------|--------|------|--------|----------|
| **10000** | success | - | - | - |
| **40004** | 非法的参数 | `isv.parameter-invalid:257` | 参数校验失败 | 检查参数格式 |
| **40004** | 非法的参数 | `isv.parameter-invalid:1031` | 无效size | size 超过最大限制 100 |
| **40004** | 非法的参数 | `isv.parameter-invalid:282` | 无效订单查询时间类型 | time_type 只能为 `settle` 或 `update` |
| **40004** | 非法的参数 | `isv.parameter-invalid:1032` | 无效order_ids | 订单号格式错误或超过 100 个 |
| **40004** | 非法的参数 | `isv.parameter-invalid:1033` | 请指定时间范围或订单号 | 必须传入时间范围或 order_ids |
| **40004** | 非法的参数 | `isv.parameter-invalid:1034` | 无效开始时间 | 检查 start_time 格式 |
| **40004** | 非法的参数 | `isv.parameter-invalid:1035` | 无效结束时间 | 检查 end_time 格式 |
| **40004** | 非法的参数 | `isv.parameter-invalid:1036` | 无效时间区间 | 开始时间不能大于结束时间，且不能小于 t-90d |
| **40004** | 非法的参数 | `isv.parameter-invalid:284` | 无效cursor | cursor 值无效 |
| **20000** | 系统错误 | `isp.service-error:256` | 服务打瞌睡了，请稍后再试 | 稍后重试 |

### 对账逻辑

1. 通过订单中的 `colonel_buyin_id` 匹配本地团长账户
2. 监控分次结算的 `phase_id`，累加 `settle_colonel_commission` 计算实际服务费
3. 定时任务采用"滑动窗口"拉取，每次拉取范围覆盖过去 **10 分钟**
4. 使用 `order_id` 作为唯一主键进行 Upsert 防止重复写入

### 开发实现

```python
def sync_colonel_orders(start_time: str, end_time: str, size: int = 50):
    """
    同步团长订单（滑动窗口拉取）

    Args:
        start_time: 开始时间 "yyyy-MM-dd HH:mm:ss"
        end_time: 结束时间 "yyyy-MM-dd HH:mm:ss"
        size: 每页大小，最大100
    """
    cursor = "0"
    all_orders = []

    while True:
        response = douyin_client.call_api(
            method="buyin.colonelMultiSettlementOrders",
            params={
                "time_type": "update",
                "start_time": start_time,
                "end_time": end_time,
                "size": size,
                "cursor": cursor,
            }
        )

        if response.get("code") != 10000:
            logger.error(f"订单同步失败: {response}")
            break

        data = response.get("data", {}).get("data", {})
        orders = data.get("orders", [])
        all_orders.extend(orders)

        cursor = data.get("cursor", "")
        if not cursor:  # 最后一页
            break

    # Upsert 订单
    for order in all_orders:
        upsert_order(order)

    logger.info(f"同步订单完成，共 {len(all_orders)} 条")
    return all_orders


def upsert_order(order_data: dict):
    """Upsert 订单记录"""
    order = Order(
        order_id=order_data["order_id"],
        product_id=order_data.get("product_id"),
        product_name=order_data.get("product_name"),
        shop_id=str(order_data.get("shop_id", "")),
        colonel_buyin_id=order_data.get("colonel_buyin_id"),
        colonel_activity_id=str(order_data.get("colonel_activity_id", "")),
        settle_colonel_commission=order_data.get("settle_colonel_commission", 0),
        settle_colonel_tech_service_fee=order_data.get("settle_colonel_tech_service_fee", 0),
        phase_id=order_data.get("phase_id", 1),
        settle_time=parse_time(order_data.get("settle_time")),
        update_time=parse_time(order_data.get("update_time")),
    )
    db.merge(order)  # 存在则更新，不存在则插入
    db.commit()
```

---

## 5. 系统数据流转图

```
┌──────────────────────────────────────────────────────────────────────┐
│                         数据流转总览                                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐    │
│  │   鉴权   │────▶│   转链   │────▶│  订单同步 │────▶│  业绩归属 │    │
│  └──────────┘     └──────────┘     └──────────┘     └──────────┘    │
│       │               │               │               │              │
│       ▼               ▼               ▼               ▼              │
│  Token 管理      pick_source      orders 表      提成计算            │
│  (Redis)        mapping 表       (分区表)        财务对账            │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 6. 开发者必读：避坑清单

| 风险点 | 影响 | 防御措施 |
|--------|------|----------|
| **金额单位** | 账目差 100 倍 | 接口返回值为"分"，数据库统一存 `BIGINT`，严禁中间层浮点运算 |
| **ID 长度** | 转链 100% 报错 | 强制限制 `channel_id` 长度 ≤ 20，推荐使用短 8 位随机码或自增 ID |
| **映射表过期** | 数据库膨胀 | 映射有效期 3 个月，需编写 CronJob 定期清理 90 天前的 Mapping 记录 |
| **漏单风险** | 渠道提成纠纷 | 定时任务采用"滑动窗口"拉取，每次拉取范围覆盖过去 10 分钟 |
| **并发刷新** | Token 冲突 | 必须使用 Redis 分布式锁控制 Token 刷新逻辑 |

---

## 7. 核心数据库模型 (SQLAlchemy)

### 7.1 归因映射表 (pick_source_mapping)

```python
from sqlalchemy import Column, String, Integer, DateTime, Index
from sqlalchemy.sql import func
import uuid


class PickSourceMapping(Base):
    """推广链接归因映射表"""
    __tablename__ = "pick_source_mapping"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 抖音返回的归因码（乱码，不可预测）
    pick_source = Column(String(128), nullable=False, unique=True, index=True)

    # 渠道用户 ID
    channel_id = Column(Integer, nullable=False, index=True)

    # 生成时间（用于清理过期记录）
    created_at = Column(DateTime(timezone=True), server_default=func.now())

    # 过期时间（created_at + 3个月）
    expires_at = Column(DateTime(timezone=True), nullable=False)

    # 复合索引加速查询
    __table_args__ = (
        Index('ix_pick_source_created', 'pick_source', 'created_at'),
    )

    def __repr__(self):
        return f"<PickSourceMapping(pick_source={self.pick_source}, channel_id={self.channel_id})>"
```

### 7.2 订单表 (orders) - 分区表设计

```python
from sqlalchemy import Column, String, Integer, BigInteger, DateTime, Index, Text
from sqlalchemy.sql import func


class Order(Base):
    """订单表 - 按月分区"""
    __tablename__ = "orders"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 抖音订单ID（唯一主键，用于 Upsert）
    order_id = Column(String(64), nullable=False, unique=True, index=True)

    # 商品信息
    product_id = Column(String(32), nullable=False, index=True)
    product_name = Column(String(256))

    # 商家信息
    merchant_id = Column(String(32), nullable=False, index=True)
    merchant_name = Column(String(256))

    # 归因信息
    pick_source = Column(String(128), index=True)  # 用于反查渠道

    # 金额信息（单位：分，存储为 BigInteger）
    order_amount = Column(BigInteger, nullable=False)  # 订单金额（分）
    service_fee = Column(BigInteger, nullable=False)   # 服务费（分）

    # 订单状态
    status = Column(Integer, nullable=False)  # 1: 已付款 2: 已结算 3: 已退款等

    # 时间信息
    create_time = Column(DateTime(timezone=True), nullable=False, index=True)
    settle_time = Column(DateTime(timezone=True), index=True)  # 结算时间
    update_time = Column(DateTime(timezone=True), nullable=False)  # 更新时间

    # 关联渠道ID（通过 pick_source 反查填充）
    channel_id = Column(Integer, index=True)

    # 创建/更新时间
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())

    # 索引设计
    __table_args__ = (
        Index('ix_orders_create_time', 'create_time'),
        Index('ix_orders_settle_time', 'settle_time'),
        Index('ix_orders_pick_source', 'pick_source'),
        Index('ix_orders_channel_create', 'channel_id', 'create_time'),
    )
```

### 7.3 用户表 (users) - 含渠道ID长度验证

```python
from sqlalchemy import Column, String, Integer, Boolean, DateTime, Enum as SQLEnum
from sqlalchemy.sql import func


class User(Base):
    """用户表"""
    __tablename__ = "users"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 用户基本信息
    username = Column(String(64), nullable=False, unique=True, index=True)
    password_hash = Column(String(256), nullable=False)

    # 角色
    role = Column(String(32), nullable=False, default='channel')  # admin/attraction_leader/attraction/channel_leader/channel/operator

    # 组别
    team_id = Column(Integer, index=True)

    # 渠道短码（用于 pick_extra，长度必须 ≤ 13 位数字）
    # 格式: channel_{short_code}，确保总长度 ≤ 20
    channel_code = Column(String(20), unique=True, index=True)

    # 状态
    is_active = Column(Boolean, default=True)

    # 时间信息
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    def __repr__(self):
        return f"<User(username={self.username}, role={self.role})>"
```

---

## 8. 关键定时任务设计

### 8.1 Token 刷新任务

```python
@app.task
def refresh_token_task():
    """Token 刷新任务 - 使用分布式锁防止并发刷新"""
    lock_key = "douyin_token_refresh_lock"
    lock = redis_client.lock(lock_key, timeout=300)

    if lock.acquire(blocking=False):
        try:
            new_token = douyin_client.refresh_access_token()
            redis_client.setex("douyin_access_token", 5 * 3600, new_token)
        finally:
            lock.release()
```

### 8.2 订单同步任务

```python
@app.task
def sync_orders_task():
    """订单同步任务 - 滑动窗口拉取"""
    # 获取上次同步时间（使用 Redis 存储）
    last_sync_time = redis_client.get("last_order_sync_time")
    if not last_sync_time:
        last_sync_time = str(int(time.time()) - 600)  # 默认拉取过去 10 分钟

    current_time = int(time.time())

    # 增量拉取（按更新时间）
    orders = douyin_client.get_orders(
        time_type="update",
        start_time=int(last_sync_time),
        end_time=current_time
    )

    # Upsert 订单
    for order in orders:
        upsert_order(order)

        # 反查渠道并更新
        pick_source = order.get('pick_source')
        if pick_source:
            mapping = db.query(PickSourceMapping).filter_by(pick_source=pick_source).first()
            if mapping:
                order.channel_id = mapping.channel_id

    # 更新同步时间
    redis_client.set("last_order_sync_time", str(current_time))
```

### 8.3 映射表清理任务

```python
@app.task
def cleanup_expired_mappings():
    """清理过期的归因映射记录 - 每周执行一次"""
    cutoff_date = datetime.now() - timedelta(days=90)

    deleted_count = db.query(PickSourceMapping).filter(
        PickSourceMapping.created_at < cutoff_date
    ).delete()

    logger.info(f"Cleaned up {deleted_count} expired pick_source mappings")
    return deleted_count
```

---

## 9. 配置文件参考

### .env.example

```bash
# 抖音开放平台配置
DOUYIN_APP_KEY=your_app_key
DOUYIN_APP_SECRET=your_app_secret
DOUYIN_SHOP_ID=your_shop_id

# 数据库配置
DATABASE_URL=postgresql://user:pass@localhost:5432/saas_db

# Redis 配置
REDIS_URL=redis://localhost:6379/0

---

## 10. 扩展数据模型

### 10.1 达人表 (talents)

```python
class Talent(Base):
    """达人表"""
    __tablename__ = "talents"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 抖音信息
    uid = Column(String(64), nullable=False, unique=True, index=True)  # 达人UID
    sec_uid = Column(String(128), unique=True)  # 加密UID
    nickname = Column(String(128))  # 昵称
    short_id = Column(String(32), index=True)  # 抖音号

    # 基础数据
    follower_count = Column(Integer, default=0)  # 粉丝数
    total_favorited = Column(BigInteger, default=0)  # 获赞数
    following_count = Column(Integer, default=0)  # 关注数
    aweme_count = Column(Integer, default=0)  # 作品数

    # IP属地
    ip_location = Column(String(64))

    # 采集状态
    crawl_status = Column(String(16), default='pending')  # pending/success/failed
    crawl_message = Column(String(256))
    last_crawl_at = Column(DateTime(timezone=True))

    # 时间信息
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
```

### 10.2 达人认领表 (talent_claims)

```python
class TalentClaim(Base):
    """达人认领记录表"""
    __tablename__ = "talent_claims"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 关联达人
    talent_id = Column(UUID(as_uuid=True), ForeignKey('talents.id'), nullable=False, index=True)

    # 认领人
    user_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False, index=True)

    # 保护期
    claim_time = Column(DateTime(timezone=True), server_default=func.now())
    protection_end_time = Column(DateTime(timezone=True), nullable=False)  # claim_time + 30天

    # 状态
    status = Column(String(16), default='active')  # active/released

    created_at = Column(DateTime(timezone=True), server_default=func.now())
```

### 10.3 寄样申请表 (sample_requests)

```python
class SampleRequest(Base):
    """寄样申请表"""
    __tablename__ = "sample_requests"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 关联信息
    talent_id = Column(UUID(as_uuid=True), ForeignKey('talents.id'), nullable=False, index=True)
    product_id = Column(String(32), nullable=False, index=True)

    # 申请人
    user_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False, index=True)

    # 收货信息
    recipient_name = Column(String(64))
    recipient_phone = Column(String(32))
    recipient_address = Column(String(512))

    # 物流信息
    tracking_number = Column(String(64))
    shipped_at = Column(DateTime(timezone=True))

    # 状态流转
    status = Column(String(16), nullable=False, default='pending', index=True)
    # pending -> approved -> shipped -> delivered -> completed
    #         -> rejected
    #         -> closed (超时)

    # 审核信息
    reviewer_id = Column(UUID(as_uuid=True), ForeignKey('users.id'))
    reviewed_at = Column(DateTime(timezone=True))
    reject_reason = Column(String(256))

    # 完成时间
    completed_at = Column(DateTime(timezone=True))

    # 时间信息
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    __table_args__ = (
        Index('ix_sample_user_status', 'user_id', 'status'),
        Index('ix_sample_product_status', 'product_id', 'status'),
    )
```

### 10.4 独家达人表 (exclusive_talents)

```python
class ExclusiveTalent(Base):
    """独家达人记录表"""
    __tablename__ = "exclusive_talents"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 关联信息
    talent_id = Column(UUID(as_uuid=True), ForeignKey('talents.id'), nullable=False, index=True)
    channel_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), nullable=False, index=True)

    # 生效月份
    effective_month = Column(String(7), nullable=False, index=True)  # 格式: 2026-01

    # 触发条件（记录快照）
    service_fee_ratio = Column(Numeric(5, 2))  # 服务费占比
    monthly_sample_count = Column(Integer)  # 月寄样数量

    # 状态
    status = Column(String(16), default='active')  # active/inactive

    created_at = Column(DateTime(timezone=True), server_default=func.now())

    __table_args__ = (
        Index('ix_exclusive_talent_talent_month', 'talent_id', 'effective_month', unique=True),
    )
```

### 10.5 活动表 (activities)

```python
class Activity(Base):
    """活动表"""
    __tablename__ = "activities"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 抖音活动ID
    activity_id = Column(String(32), nullable=False, unique=True, index=True)
    activity_name = Column(String(256))

    # 活动时间
    start_time = Column(DateTime(timezone=True))
    end_time = Column(DateTime(timezone=True))

    # 服务费/佣金率
    commission_rate = Column(Numeric(5, 4))  # 佣金率
    service_fee_rate = Column(Numeric(5, 4))  # 服务费率

    # 独家保护期（月数）
    months_of_protection = Column(Integer, default=0)

    # 状态
    status = Column(String(16), default='active')  # active/ended

    # 同步信息
    last_sync_at = Column(DateTime(timezone=True))

    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
```

### 10.6 商品表 (products)

```python
class Product(Base):
    """商品表"""
    __tablename__ = "products"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # 商品信息
    product_id = Column(String(32), nullable=False, unique=True, index=True)
    product_name = Column(String(256))
    product_image = Column(String(512))  # 主图URL

    # 店铺信息
    shop_name = Column(String(128))
    shop_id = Column(String(32), index=True)

    # 价格信息（单位：分）
    price = Column(BigInteger)  # 售价
    commission_rate = Column(Numeric(5, 4))  # 佣金率
    service_fee_rate = Column(Numeric(5, 4))  # 服务费率

    # 关联活动
    activity_id = Column(String(32), ForeignKey('activities.activity_id'), index=True)

    # 招商负责人
    assignee_id = Column(UUID(as_uuid=True), ForeignKey('users.id'), index=True)

    # 补充信息（JSONB）
    extra_info = Column(JSONB, default={})
    # {
    #   "sample_requirements": "近30天销售额≥3W",
    #   "promotion_copy": "推广话术",
    #   "support_ad": true,  # 是否支持投流
    #   "rewards": "奖励说明",
    # }

    # 审核状态
    audit_status = Column(String(16), default='pending')  # pending/approved/rejected

    # 上架状态
    is_listed = Column(Boolean, default=False, index=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
```

---

## 11. 订单分区表配置 (PostgreSQL)

```sql
-- 创建订单分区表（按月分区）
CREATE TABLE orders (
    id UUID DEFAULT gen_random_uuid(),
    order_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    merchant_id VARCHAR(32) NOT NULL,
    pick_source VARCHAR(128),
    order_amount BIGINT NOT NULL,  -- 单位：分
    service_fee BIGINT NOT NULL,   -- 单位：分
    status INTEGER NOT NULL,
    create_time TIMESTAMPTZ NOT NULL,
    settle_time TIMESTAMPTZ,
    update_time TIMESTAMPTZ NOT NULL,
    channel_id INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

-- 创建每月分区
CREATE TABLE orders_2026_01 PARTITION OF orders
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE orders_2026_02 PARTITION OF orders
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

-- 依此类推...

-- 创建索引（分区表索引会自动继承）
CREATE INDEX ix_orders_pick_source ON orders (pick_source);
CREATE INDEX ix_orders_channel_create ON orders (channel_id, create_time);
```

---

## 12. 环境配置清单

| 环境变量 | 说明 | 示例 |
|----------|------|------|
| `DOUYIN_APP_KEY` | 抖音应用 Key | `ttaxxx` |
| `DOUYIN_APP_SECRET` | 抖音应用 Secret | `xxx` |
| `DOUYIN_SHOP_ID` | 团长 ID | `123456` |
| `DATABASE_URL` | PostgreSQL 连接串 | `postgresql://user:pass@host:5432/db` |
| `REDIS_URL` | Redis 连接串 | `redis://localhost:6379/0` |
| `SECRET_KEY` | 应用密钥 | 用于 JWT |
| `LOG_LEVEL` | 日志级别 | `INFO` |

