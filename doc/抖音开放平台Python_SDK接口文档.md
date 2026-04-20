---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: 8b971de8256e7868c408795deb691445
    PropagateID: 8b971de8256e7868c408795deb691445
    ReservedCode1: 304402204456a36ead3f4ced5f764251ef95bea9900dadc54b51cff1f3620b5ab935e83402202a82b47bc46a52ed72e283e0ac56367ed27e0968f4f9e8afa28731aec459032a
    ReservedCode2: 304502210092d796ed7f89d689e97188d0875ba47930cbc886d61e5b805fe9b0b4d26729270220415302f0ceb5936906954c8a262763e2d9db7142b237be604efabf7b0834a29e
---

# 抖音开放平台 Python SDK 接口文档

> **文档版本**: 1.1.0
> **SDK 版本**: doudian-sdk-python-1.1.0
> **最后更新**: 2026-04-07

---

## 1. 概述

### 1.1 SDK 用途

抖音开放平台 Python SDK（`doudian-sdk-python`）是抖音电商开放平台官方提供的 Python 语言开发工具包，用于帮助开发者快速集成抖音电商能力，实现与抖音开放平台的交互。该 SDK 封装了抖音电商开放平台的 API 和 SPI 接口，提供了统一认证、请求签名、响应解析等功能。

### 1.2 主要功能模块

| 模块类型 | 说明 |
|---------|------|
| **API 模块** | 提供抖音电商各类业务接口调用能力，包括商品、订单、联盟、营销、短信等 |
| **SPI 模块** | 提供服务端回调（Server Push Interface）接收处理能力 |
| **Token 管理** | 提供 access_token 的获取、刷新、缓存等功能 |
| **HTTP 通信** | 封装 HTTPS 请求，支持自定义超时和请求头 |

### 1.3 支持的抖音开放平台能力范围

基于 SDK 代码分析，支持以下能力：

| 能力分类 | 具体功能 |
|---------|---------|
| **精选联盟** | 团长活动管理、商品推广、订单结算、佣金查询等 |
| **商品管理** | 商品详情查询、SKU 管理、商品上下架等 |
| **订单处理** | 订单查询、加密解密、敏感信息处理等 |
| **机构服务** | 机构订单、招商活动、团长托管等 |
| **短信服务** | 短信发送、模板管理、签名管理等 |
| **开放能力** | 素材上传、授权信息获取、云服务等 |
| **安全服务** | 订单安全事件上报等 |

---

## 2. 目录结构说明

```
sdk-python/
├── doudian/                          # SDK 主包
│   ├── __init__.py                   # 包导出入口
│   ├── core/                         # 核心组件
│   │   ├── DoudianOpApiClient.py     # API 请求客户端
│   │   ├── DoudianOpApiRequest.py    # API 请求基类
│   │   ├── DoudianOpApiLight.py      # 轻量级 API 执行器
│   │   ├── DoudianOpConfig.py        # 配置类
│   │   ├── DoudianOpResponse.py      # API 响应基类
│   │   ├── DoudianOpSpiRequest.py    # SPI 请求处理类
│   │   ├── DoudianOpSpiContext.py    # SPI 上下文
│   │   ├── DoudianOpSpiParam.py      # SPI 参数类
│   │   ├── DoudianOpSpiResponse.py   # SPI 响应类
│   │   ├── AccessToken.py            # Access Token 封装
│   │   ├── AccessTokenBuilder.py     # Token 构建器
│   │   ├── TokenHolder.py            # Token 缓存管理
│   │   ├── Constant.py               # 常量定义
│   │   └── http/                     # HTTP 通信层
│   │       └── DefaultHttpClient.py  # 默认 HTTP 客户端
│   ├── api/                          # API 接口模块（72个）
│   │   ├── token/                    # Token 相关
│   │   ├── buyin_*/                  # 精选联盟相关
│   │   ├── alliance_*/               # 联盟相关
│   │   ├── open_*/                   # 开放能力相关
│   │   ├── order_*/                  # 订单相关
│   │   ├── product_*/                # 商品相关
│   │   ├── sms_*/                    # 短信相关
│   │   └── security_*/               # 安全相关
│   └── exeception/                   # 异常定义
│       └── DoudianOpException.py     # SDK 异常类
└── example.py                        # 使用示例
```

### 2.1 核心文件职责

| 文件 | 职责 |
|-----|------|
| `DoudianOpApiClient.py` | 负责构建签名、发送 HTTP 请求、解析响应 |
| `DoudianOpApiRequest.py` | 所有 API 请求的基类，定义通用接口 |
| `DoudianOpConfig.py` | 存储 SDK 配置（appKey、appSecret、超时等） |
| `DoudianOpSpiRequest.py` | 处理抖音服务端回调请求 |
| `AccessTokenBuilder.py` | 提供获取和刷新 Token 的静态方法 |
| `TokenHolder.py` | 提供 Token 缓存和自动续期功能 |

---

## 3. 安装与初始化

### 3.1 依赖项

根据 SDK 代码分析，该 SDK 无外部依赖，使用 Python 标准库即可运行：

```python
# 无需安装额外依赖，使用 Python 标准库
import json
import hashlib
import hmac
import threading
import time
import urllib.request
import urllib.parse
```

> ⚠️ **注意**: SDK 使用 Python 3 标准库，建议使用 Python 3.6 或更高版本。

### 3.2 初始化配置

SDK 使用全局配置对象 `GlobalConfig`，需要开发者设置以下参数：

```python
from doudian.core.DoudianOpConfig import GlobalConfig

# 设置应用凭证（必填）
GlobalConfig.appKey = "your_app_key"      # 抖音开放平台分配的 app_key
GlobalConfig.appSecret = "your_app_secret" # 抖音开放平台分配的 app_secret

# 可选配置
GlobalConfig.httpConnectTimeout = 1000      # HTTP 连接超时（毫秒），默认 1000ms
GlobalConfig.httpReadTimeout = 10000       # HTTP 读取超时（毫秒），默认 10000ms
GlobalConfig.useHttps = True               # 是否使用 HTTPS，默认 True
GlobalConfig.openRequestUrl = "openapi-fxg.jinritemai.com"  # API 请求地址

# 自定义 HTTP 请求头（可选）
GlobalConfig.addHttpRequestHeader("Custom-Header", "custom-value")
```

### 3.3 配置参数说明

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|-------|-----|-----|-------|------|
| `appKey` | str | 是 | - | 抖音开放平台分配的 App Key |
| `appSecret` | str | 是 | - | 抖音开放平台分配的 App Secret |
| `httpConnectTimeout` | int | 否 | 1000 | HTTP 连接超时时间（毫秒） |
| `httpReadTimeout` | int | 否 | 10000 | HTTP 读取超时时间（毫秒） |
| `useHttps` | bool | 否 | True | 是否使用 HTTPS 协议 |
| `openRequestUrl` | str | 否 | openapi-fxg.jinritemai.com | API 请求域名 |
| `httpRequestHeader` | dict | 否 | {} | 自定义 HTTP 请求头 |

---

## 4. API 接口文档

### 4.1 Token 模块

#### 4.1.1 CreateTokenRequest - 获取 Access Token（自用型）

```python
from doudian.api.token.CreateTokenRequest import CreateTokenRequest
```

**类说明**: 用于自用型应用获取 Access Token。

**方法列表**:

| 方法 | 说明 |
|-----|------|
| `__init__()` | 初始化请求对象 |
| `getParams()` | 获取请求参数对象 |
| `getUrlPath()` | 返回 API 路径 `/token/create` |
| `execute(accessToken=None)` | 执行请求并返回响应 |

**请求参数 (CreateTokenParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `shop_id` | str | 条件必填 | 店铺 ID（授权类型为自用型时必填） |
| `grant_type` | str | 是 | 授权类型，固定值 `authorization_self` |
| `code` | str | 否 | 授权码（授权类型为工具型时使用） |

**返回值**: `DoudianOpResponse` 对象，包含以下字段：

| 字段 | 类型 | 说明 |
|-----|-----|------|
| `code` | int | 响应码，10000 表示成功 |
| `msg` | str | 响应消息 |
| `data` | dict | 包含 access_token、refresh_token 等 |
| `log_id` | str | 日志追踪 ID |

**调用示例**:

```python
from doudian.api.token.CreateTokenRequest import CreateTokenRequest

# 创建请求对象
request = CreateTokenRequest()

# 设置参数（自用型应用）
request.getParams().shop_id = "123456789"
request.getParams().grant_type = "authorization_self"
request.getParams().code = ""

# 执行请求
response = request.execute()

# 检查结果
if response.isSuccess():
    access_token = response.data.get("access_token")
    refresh_token = response.data.get("refresh_token")
    print(f"Token 获取成功: {access_token}")
else:
    print(f"Token 获取失败: {response.code} - {response.msg}")
```

---

#### 4.1.2 RefreshTokenRequest - 刷新 Access Token

```python
from doudian.api.token.RefreshTokenRequest import RefreshTokenRequest
```

**类说明**: 用于刷新即将过期的 Access Token。

**请求参数 (RefreshTokenParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `grant_type` | str | 是 | 固定值 `refresh_token` |
| `refresh_token` | str | 是 | 刷新令牌 |

**返回值**: `DoudianOpResponse` 对象，包含新的 access_token 和 refresh_token。

**调用示例**:

```python
from doudian.api.token.RefreshTokenRequest import RefreshTokenRequest

# 创建请求对象
request = RefreshTokenRequest()

# 设置参数
request.getParams().grant_type = "refresh_token"
request.getParams().refresh_token = "your_refresh_token"

# 执行请求
response = request.execute()

if response.isSuccess():
    new_access_token = response.data.get("access_token")
    print(f"Token 刷新成功: {new_access_token}")
```

---

### 4.2 精选联盟 - buyin_* 模块

#### 4.2.1 BuyinProductsDetailRequest - 商品详情查询

```python
from doudian.api.buyin_productsDetail.BuyinProductsDetailRequest import BuyinProductsDetailRequest
```

**类说明**: 查询精选联盟商品的详细信息。

**请求参数 (BuyinProductsDetailParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `product_ids` | str | 是 | 商品 ID，多个用逗号分隔 |
| `fields` | str | 否 | 指定返回的字段 |

**返回值**: `DoudianOpResponse` 对象

**调用示例**:

```python
from doudian.api.buyin_productsDetail.BuyinProductsDetailRequest import BuyinProductsDetailRequest

request = BuyinProductsDetailRequest()
request.getParams().product_ids = "123456,789012"
request.getParams().fields = "product_id,title,price"

response = request.execute(access_token)
```

---

#### 4.2.2 BuyinActivityProductListRequest - 活动商品列表

```python
from doudian.api.buyin_activityProductList.BuyinActivityProductListRequest import BuyinActivityProductListRequest
```

**请求参数 (BuyinActivityProductListParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `page` | int | 否 | 页码，默认 1 |
| `size` | int | 否 | 每页数量，默认 20 |
| `activity_id` | str | 否 | 活动 ID |
| `applied_sub_status` | str | 否 | 报名子状态 |
| `product_info` | bool | 否 | 是否返回商品信息 |
| `sort_field` | str | 否 | 排序字段 |
| `sort_direction` | str | 否 | 排序方向（asc/desc） |

---

#### 4.2.3 BuyinShareCommandParseRequest - 分享口令解析

```python
from doudian.api.buyin_shareCommandParse.BuyinShareCommandParseRequest import BuyinShareCommandParseRequest
```

**请求参数 (BuyinShareCommandParseParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `command` | str | 是 | 分享口令内容 |

---

#### 4.2.4 BuyinDecryptContactInfoRequest - 解密联系方式

```python
from doudian.api.buyin_decryptContactInfo.BuyinDecryptContactInfoRequest import BuyinDecryptContactInfoRequest
```

**请求参数**: 包含解密所需的加密参数。

---

#### 4.2.5 其他精选联盟接口

| 接口类 | API 路径 | 说明 |
|-------|---------|------|
| `BuyinProductSkusRequest` | /buyin/productSkus | 商品 SKU 查询 |
| `BuyinProductSkusV2Request` | /buyin/productSkus_v2 | 商品 SKU 查询 V2 |
| `BuyinKolProductsDetailRequest` | /buyin/kolProductsDetail | KOL 商品详情 |
| `BuyinKolMaterialsProductsSearchRequest` | /buyin/kolMaterialsProductsSearch | KOL 素材商品搜索 |
| `BuyinInstGmvRequest` | /buyin/instGmv | 机构 GMV 查询 |
| `BuyinInstGmvDetailRequest` | /buyin/instGmvDetail | 机构 GMV 明细 |
| `BuyinDistributionLiveProductListV2Request` | /buyin/distributionLiveProductList_v2 | 直播分销商品列表 V2 |
| `BuyinColonelActivityDetailRequest` | /buyin/colonelActivityDetail | 团长活动详情 |
| `BuyinColonelTrusteeshipListRequest` | /buyin/colonel_trusteeshipList | 团长托管列表 |
| `BuyinDoukeRewardOrdersRequest` | /buyin/doukeRewardOrders | 抖客奖励订单 |
| `BuyinDoukeCrowdMatchRequest` | /buyin/doukeCrowdMatch | 抖客人群匹配 |
| `BuyinDoukeCommandParseAndShareRequest` | /buyin/doukeCommandParseAndShare | 抖客口令解析分享 |
| `BuyinGetProductShareMaterialRequest` | /buyin/getProductShareMaterial | 获取商品分享素材 |
| `BuyinActivityProductCancelRequest` | /buyin/activityProductCancel | 取消活动报名 |
| `BuyinMaterialsProductStatusRequest` | /buyin/materialsProductStatus | 素材商品状态 |

---

### 4.3 联盟模块 - alliance_* 模块

#### 4.3.1 AllianceColonelActivityCreateOrUpdateRequest - 团长活动创建/更新

```python
from doudian.api.alliance_colonelActivityCreateOrUpdate.AllianceColonelActivityCreateOrUpdateRequest import AllianceColonelActivityCreateOrUpdateRequest
```

**API 路径**: `/alliance/colonelActivity/createOrUpdate`

**请求参数**: 包含活动创建/更新所需的各项参数（具体字段请参考抖音开放平台文档）。

---

#### 4.3.2 其他联盟接口

| 接口类 | API 路径 | 说明 |
|-------|---------|------|
| `AllianceColonelActivityProductRequest` | /alliance/colonelActivityProduct | 团长活动商品 |
| `AllianceColonelActivityProductAuditRequest` | /alliance/colonelActivityProductAudit | 团长活动商品审核 |
| `AllianceColonelActivityProductExtensionRequest` | /alliance/colonelActivityProductExtension | 团长活动商品扩展 |
| `AllianceInstituteColonelActivityListRequest` | /alliance/instituteColonelActivityList | 机构团长活动列表 |
| `AllianceInstituteColonelActivityOperateRequest` | /alliance/instituteColonelActivityOperate | 机构团长活动操作 |
| `AllianceMaterialsProductCategoryRequest` | /alliance/materialsProductCategory | 素材商品分类 |
| `AllianceMaterialsProductsDetailsRequest` | /alliance/materialsProductsDetails | 素材商品详情 |
| `AllianceMaterialsProductsSearchRequest` | /alliance/materialsProductsSearch | 素材商品搜索 |
| `AllianceActivityProductCategoryListRequest` | /alliance/activityProductCategoryList | 活动商品分类列表 |

---

### 4.4 开放能力模块 - open_* 模块

#### 4.4.1 OpenBinaryuploadRequest - 二进制文件上传

```python
from doudian.api.open_binaryupload.OpenBinaryuploadRequest import OpenBinaryuploadRequest
```

**API 路径**: `/open/binaryupload`

**请求参数 (OpenBinaryuploadParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `file_content` | str | 是 | 文件内容（Base64 编码） |

---

#### 4.4.2 OpenGetAuthInfoRequest - 获取授权信息

```python
from doudian.api.open_getAuthInfo.OpenGetAuthInfoRequest import OpenGetAuthInfoRequest
```

**API 路径**: `/open/getAuthInfo`

---

#### 4.4.3 OpenMaterialTokenRequest - 获取素材 Token

```python
from doudian.api.open_materialToken.OpenMaterialTokenRequest import OpenMaterialTokenRequest
```

**API 路径**: `/open/materialToken`

---

#### 4.4.4 OpenCloudV1ImageVersionCreateRequest - 云端图片版本创建

```python
from doudian.api.openCloud_v1_imageVersion_create.OpenCloudV1ImageVersionCreateRequest import OpenCloudV1ImageVersionCreateRequest
```

**API 路径**: `/openCloud/v1/imageVersion/create`

---

### 4.5 订单模块 - order_* 模块

#### 4.5.1 OrderBatchSensitiveRequest - 订单敏感信息批量脱敏

```python
from doudian.api.order_batchSensitive.OrderBatchSensitiveRequest import OrderBatchSensitiveRequest
```

**API 路径**: `/order/batchSensitive`

**请求参数 (OrderBatchSensitiveParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `cipher_infos` | list | 是 | 加密信息列表 |

---

#### 4.5.2 OrderBatchEncryptRequest - 订单信息批量加密

```python
from doudian.api.order_batchEncrypt.OrderBatchEncryptRequest import OrderBatchEncryptRequest
```

**API 路径**: `/order/batchEncrypt`

---

#### 4.5.3 OrderGetMCTokenRequest - 获取 MC Token

```python
from doudian.api.order_getMCToken.OrderGetMCTokenRequest import OrderGetMCTokenRequest
```

**API 路径**: `/order/getMCToken`

---

#### 4.5.4 OrderGetSearchIndexRequest - 获取订单搜索索引

```python
from doudian.api.order_getSearchIndex.OrderGetSearchIndexRequest import OrderGetSearchIndexRequest
```

**API 路径**: `/order/getSearchIndex`

---

### 4.6 商品模块 - product_* 模块

#### 4.6.1 ProductIsvCreateProductFromSupplyPlatformRequest - ISV 从供给平台创建商品

```python
from doudian.api.product_isv_createProductFromSupplyPlatform.ProductIsvCreateProductFromSupplyPlatformRequest import ProductIsvCreateProductFromSupplyPlatformRequest
```

**API 路径**: `/product/isv/createProductFromSupplyPlatform`

---

#### 4.6.2 ProductIsvSaveGoodsSupplyStatusRequest - ISV 保存商品供给状态

```python
from doudian.api.product_isv_saveGoodsSupplyStatus.ProductIsvSaveGoodsSupplyStatusRequest import ProductIsvSaveGoodsSupplyStatusRequest
```

**API 路径**: `/product/isv/saveGoodsSupplyStatus`

---

#### 4.6.3 ProductIsvScanClueRequest - ISV 扫描线索

```python
from doudian.api.product_isv_scanClue.ProductIsvScanClueRequest import ProductIsvScanClueRequest
```

**API 路径**: `/product/isv/scanClue`

---

#### 4.6.4 ProductIsCategoryMigrateObjRequest - 商品类目迁移对象

```python
from doudian.api.product_isCategoryMigrateObj.ProductIsCategoryMigrateObjRequest import ProductIsCategoryMigrateObjRequest
```

**API 路径**: `/product/isCategoryMigrateObj`

---

### 4.7 短信模块 - sms_* 模块

#### 4.7.1 SmsSendRequest - 发送短信

```python
from doudian.api.sms_send.SmsSendRequest import SmsSendRequest
```

**API 路径**: `/sms/send`

**请求参数 (SmsSendParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `sms_account` | str | 是 | 短信账号 |
| `sign` | str | 是 | 短信签名 |
| `template_id` | str | 是 | 模板 ID |
| `template_param` | str | 否 | 模板参数（JSON 格式） |
| `tag` | str | 否 | 标签 |
| `post_tel` | str | 是 | 接收手机号 |
| `user_ext_code` | str | 否 | 用户扩展码 |
| `outbound_id` | str | 否 | 外部 ID |
| `link_id` | str | 否 | 链接 ID |
| `mini_app_link_id` | str | 否 | 小程序链接 ID |
| `douyin_open_id` | str | 否 | 抖音 Open ID |
| `sms_test_verification` | object | 否 | 短信测试验证 |
| `order_id` | str | 否 | 订单 ID |
| `after_sale_id` | str | 否 | 售后 ID |

---

#### 4.7.2 其他短信接口

| 接口类 | API 路径 | 说明 |
|-------|---------|------|
| `SmsBatchSendRequest` | /sms/batchSend | 批量发送短信 |
| `SmsSendResultRequest` | /sms/sendResult | 短信发送结果查询 |
| `SmsTemplateApplyRequest` | /sms/template/apply | 模板申请 |
| `SmsTemplateApplyListRequest` | /sms/template/applyList | 模板申请列表 |
| `SmsTemplateRevokeRequest` | /sms/template/revoke | 模板撤回 |
| `SmsTemplateDeleteRequest` | /sms/template/delete | 模板删除 |
| `SmsTemplateSearchRequest` | /sms/template/search | 模板搜索 |
| `SmsPublicTemplateRequest` | /sms/public/template | 公共模板 |
| `SmsSignApplyRequest` | /sms/sign/apply | 签名申请 |
| `SmsSignApplyListRequest` | /sms/sign/applyList | 签名申请列表 |
| `SmsSignApplyRevokeRequest` | /sms/sign/applyRevoke | 签名申请撤回 |
| `SmsSignDeleteRequest` | /sms/sign/delete | 签名删除 |
| `SmsSignSearchRequest` | /sms/sign/search | 签名搜索 |

---

### 4.8 权益模块 - rights_* 模块

#### 4.8.1 RightsInfoRequest - 权益信息查询

```python
from doudian.api.rights_info.RightsInfoRequest import RightsInfoRequest
```

**API 路径**: `/rights/info`

**请求参数 (RightsInfoParam)**:

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `biz_type` | int | 是 | 业务类型 |
| `outer_biz_id` | str | 是 | 外部业务 ID |
| `service_id` | int | 否 | 服务 ID |

---

### 4.9 安全模块 - security_* 模块

#### 4.9.1 SecurityBatchReportOrderSecurityEventRequest - 批量上报订单安全事件

```python
from doudian.api.security_batchReportOrderSecurityEvent.SecurityBatchReportOrderSecurityEventRequest import SecurityBatchReportOrderSecurityEventRequest
```

**API 路径**: `/security/batchReportOrderSecurityEvent`

---

## 5. SPI 接口说明

### 5.1 SPI 概述

SPI（Server Push Interface）是抖音开放平台服务端主动回调开发者的机制。当特定业务事件发生时，抖音服务器会向开发者的服务端推送通知。

### 5.2 DoudianOpSpiRequest - SPI 请求处理器

```python
from doudian.core.DoudianOpSpiRequest import DoudianOpSpiRequest
```

**类说明**: SPI 请求处理器，用于接收和处理抖音服务端回调。

#### 5.2.1 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|-----|-----|-------|------|
| `__init__()` | - | - | 初始化 SPI 请求对象 |
| `init(appKey, timestamp, sign, signV2, signMethod, paramJson)` | 见下方 | None | 初始化 SPI 参数 |
| `registerHandler(bizHandler)` | bizHandler: callable | None | 注册业务处理函数 |
| `execute(parseAsJsonString=True)` | parseAsJsonString: bool | str/dict | 执行处理并返回响应 |
| `calcSign(...)` | 见下方 | str | 计算签名 |

#### 5.2.2 init() 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `appKey` | str | 是 | 应用 Key |
| `timestamp` | str | 是 | 时间戳 |
| `sign` | str | 是 | 签名（v1） |
| `signV2` | str | 是 | 签名（v2） |
| `signMethod` | str | 是 | 签名方法（md5/hmac-sha256） |
| `paramJson` | str | 是 | 参数字符串（JSON 格式） |

#### 5.2.3 calcSign() 参数说明

| 参数名 | 类型 | 必填 | 说明 |
|-------|-----|-----|------|
| `appKey` | str | 是 | 应用 Key |
| `appSecret` | str | 是 | 应用 Secret |
| `paramJson` | str | 是 | 参数字符串 |
| `signMethod` | str | 是 | 签名方法 |
| `timestamp` | str | 是 | 时间戳 |

---

### 5.3 DoudianOpSpiContext - SPI 上下文

```python
from doudian.core.DoudianOpSpiContext import DoudianOpSpiContext
```

**类说明**: SPI 回调的上下文对象，提供获取参数和设置响应的方法。

#### 5.3.1 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|-----|-----|-------|------|
| `getParamJson()` | - | str | 获取原始参数字符串 |
| `getParamJsonObject()` | - | dict/object | 获取反序列化后的参数对象 |
| `setResponseData(data)` | data: dict | None | 设置响应数据 |
| `wrapSuccess()` | - | None | 标记处理成功（code=0） |
| `wrapError(code, message="")` | code: int, message: str | None | 标记处理失败 |

---

### 5.4 DoudianOpSpiResponse - SPI 响应

```python
from doudian.core.DoudianOpSpiResponse import DoudianOpSpiResponse
```

**类说明**: SPI 回调的响应对象。

#### 5.4.1 属性列表

| 属性 | 类型 | 说明 |
|-----|-----|------|
| `code` | int | 响应码，0 表示成功 |
| `message` | str | 响应消息 |
| `data` | object | 响应数据 |

---

### 5.5 SPI 使用示例

```python
from doudian.core.DoudianOpSpiRequest import DoudianOpSpiRequest
from doudian.core.DoudianOpConfig import GlobalConfig

# 初始化配置
GlobalConfig.appKey = "your_app_key"
GlobalConfig.appSecret = "your_app_secret"

# 定义业务处理器
def bizHandler(context):
    # 获取回调参数
    param = context.getParamJsonObject()
    print(f"收到回调: {param}")

    # 处理业务逻辑
    try:
        # TODO: 处理具体业务
        result = {"status": "success", "data": param}

        # 返回成功响应
        context.wrapSuccess()
        context.setResponseData(result)
    except Exception as e:
        # 返回失败响应
        context.wrapError(10001, f"处理失败: {str(e)}")

# 创建 SPI 请求对象
request = DoudianOpSpiRequest()

# 初始化参数（从抖音服务器回调中获取）
request.init(
    appKey="7037989963426022956",
    sign="a68b72f8ccc9c43acc79b2fbbeac392555e042d0c690e5fba1dc9355c50fb548",
    signV2="xxx",
    signMethod="hmac-sha256",
    timestamp="2022-02-10 10:59:24",
    paramJson='{"order_id": "123456"}'
)

# 注册业务处理器
request.registerHandler(bizHandler)

# 执行处理
response = request.execute(parseAsJsonString=True)
# 将 response 返回给抖音服务器
```

---

## 6. 核心类详解

### 6.1 DoudianOpConfig - 配置类

```python
from doudian.core.DoudianOpConfig import DoudianOpConfig, GlobalConfig
```

**类说明**: SDK 全局配置类。

#### 6.1.1 属性

| 属性 | 类型 | 默认值 | 说明 |
|-----|-----|-------|------|
| `appKey` | str | "" | 抖音开放平台 App Key |
| `appSecret` | str | "" | 抖音开放平台 App Secret |
| `httpConnectTimeout` | int | 1000 | HTTP 连接超时（毫秒） |
| `httpReadTimeout` | int | 10000 | HTTP 读取超时（毫秒） |
| `openRequestUrl` | str | openapi-fxg.jinritemai.com | API 请求域名 |
| `useHttps` | bool | True | 是否使用 HTTPS |
| `httpRequestHeader` | dict | {} | 自定义 HTTP 请求头 |

#### 6.1.2 方法

| 方法 | 参数 | 返回值 | 说明 |
|-----|-----|-------|------|
| `addHttpRequestHeader(key, value)` | key: str, value: str | None | 添加自定义 HTTP 请求头 |

---

### 6.2 AccessToken - Access Token 封装类

```python
from doudian.core.AccessToken import AccessToken
```

**类说明**: 封装 access_token 及其相关信息。

#### 6.2.1 构造函数

| 参数 | 类型 | 说明 |
|-----|-----|------|
| `accessTokenResp` | DoudianOpResponse | Token 创建/刷新接口的响应对象 |

#### 6.2.2 方法

| 方法 | 返回值 | 说明 |
|-----|-------|------|
| `getAccessToken()` | str | 获取 access_token |
| `getRefreshToken()` | str | 获取 refresh_token |
| `getExpiresIn()` | int | 获取有效期（秒） |
| `getScope()` | str | 获取授权范围 |
| `getShopId()` | int | 获取店铺 ID |
| `getShopName()` | str | 获取店铺名称 |
| `getAuthorityId()` | str | 获取权限 ID |
| `getShopBizType()` | str | 获取店铺业务类型 |
| `isSuccess()` | bool | 判断 Token 获取是否成功 |

---

### 6.3 AccessTokenBuilder - Token 构建器

```python
from doudian.core.AccessTokenBuilder import AccessTokenBuilder
```

**类说明**: 提供获取和构建 Access Token 的静态方法。

#### 6.3.1 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|-----|-----|-------|------|
| `buildTokenByShopId(shopId, config=GlobalConfig)` | shopId: str, config: DoudianOpConfig | AccessToken | 自用型应用获取 Token |
| `buildTokenByCode(code, config=GlobalConfig)` | code: str, config: DoudianOpConfig | AccessToken | 工具型应用获取 Token |
| `refreshToken(refreshToken, config=GlobalConfig)` | refreshToken: str, config: DoudianOpConfig | AccessToken | 刷新 Token |
| `parse(accessTokenStr)` | accessTokenStr: str | AccessToken | 从字符串解析 Token |

---

### 6.4 TokenHolder - Token 缓存管理

```python
from doudian.core.TokenHolder import TokenHolder
```

**类说明**: 提供 Token 的缓存和自动续期功能，支持多线程安全访问。

#### 6.4.1 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|-----|-----|-------|------|
| `getToken(authId=None)` | authId: str | str | 获取 Token（自动续期） |
| `isTokenExpired(token)` | token: dict | bool | 判断 Token 是否过期 |
| `generateTokenByRemote(authId)` | authId: str | dict | 从远程获取新 Token |
| `refreshToken(authId=None)` | authId: str | str | 强制刷新 Token |

> ⚠️ **注意**: `authId` 参数为空时会自动从当前线程上下文中获取（需提前设置 `threading.current_thread().__dict__['authId']`）。

---

### 6.5 DoudianOpApiLight - 轻量级 API 执行器

```python
from doudian.core.DoudianOpApiLight import DoudianOpApiLight
```

**类说明**: 提供简化的 API 调用流程，自动处理 Token 续期。

#### 6.5.1 方法列表

| 方法 | 参数 | 返回值 | 说明 |
|-----|-----|-------|------|
| `executeForLight(request)` | request: DoudianOpApiRequest | DoudianOpResponse | 执行请求（自动处理 Token 过期） |
| `resetOpenUrl(request)` | request: DoudianOpApiRequest | None | 从环境变量重置请求 URL |

> 💡 **提示**: `executeForLight()` 方法会自动检测 Token 是否过期，若过期则自动刷新后重试。

---

### 6.6 DoudianOpResponse - API 响应基类

```python
from doudian.core.DoudianOpResponse import DoudianOpResponse
```

**类说明**: 所有 API 响应的基类。

#### 6.6.1 属性

| 属性 | 类型 | 说明 |
|-----|-----|------|
| `data` | object | 响应数据 |
| `log_id` | str | 日志追踪 ID |
| `code` | int | 响应码，10000 表示成功 |
| `msg` | str | 响应消息 |
| `sub_code` | str | 子错误码 |
| `sub_msg` | str | 子错误消息 |

#### 6.6.2 方法

| 方法 | 返回值 | 说明 |
|-----|-------|------|
| `isSuccess()` | bool | 判断请求是否成功（code == 10000） |

---

## 7. 错误码与异常处理

### 7.1 SDK 异常类

```python
from doudian.exeception.DoudianOpException import DoudianOpException
```

**类说明**: SDK 定义的异常类型。

#### 7.1.1 异常错误码

| 错误码 | 常量名 | 说明 |
|-------|-------|------|
| 9999 | `UNKNOWN_ERROR` | 未知错误 |
| 10001 | `HTTP_RESPONSE_STATUS_CODE_NOT_2XX` | HTTP 响应状态码非 2xx |
| 10002 | `HTTP_REQUEST_ERROR` | HTTP 请求错误 |

#### 7.1.2 构造函数

```python
DoudianOpException(code, causeBy=None)
```

| 参数 | 类型 | 说明 |
|-----|-----|------|
| `code` | int | 错误码 |
| `causeBy` | Exception | 原始异常（可选） |

---

### 7.2 API 响应码

抖音开放平台 API 返回的响应码定义在 `DoudianOpResponse` 中：

| 响应码 | 说明 |
|-------|------|
| 10000 | 请求成功 |
| 其他值 | 具体错误（参考 sub_code 和 sub_msg） |

常见 sub_code 示例：
- `isv.access-token-expired` - Access Token 已过期
- `isv.access-token-no-existed` - Access Token 不存在

---

### 7.3 错误处理示例

```python
from doudian.exeception.DoudianOpException import DoudianOpException
from doudian.core.DoudianOpResponse import DoudianOpResponse

# 方式一：捕获 SDK 异常
try:
    response = request.execute(access_token)
except DoudianOpException as e:
    print(f"SDK 异常: code={e.code}")

# 方式二：检查 API 响应
response = request.execute(access_token)

if not response.isSuccess():
    print(f"API 调用失败: {response.code} - {response.msg}")
    print(f"子错误: {response.sub_code} - {response.sub_msg}")
else:
    print(f"API 调用成功: {response.data}")

# 方式三：使用 DoudianOpApiLight 自动处理 Token 过期
from doudian.core.DoudianOpApiLight import DoudianOpApiLight

try:
    response = DoudianOpApiLight.executeForLight(request)
    if response.isSuccess():
        print(f"调用成功: {response.data}")
except DoudianOpException as e:
    print(f"SDK 异常: {e.code}")
```

---

## 8. 快速开始示例

### 8.1 完整示例：查询商品详情

以下示例展示从初始化到调用 API 的完整流程：

```python
# step 1: 导入 SDK 模块
from doudian.core.DoudianOpConfig import GlobalConfig
from doudian.core.AccessTokenBuilder import AccessTokenBuilder
from doudian.api.buyin_productsDetail.BuyinProductsDetailRequest import BuyinProductsDetailRequest

# step 2: 配置应用凭证
GlobalConfig.appKey = "your_app_key"
GlobalConfig.appSecret = "your_app_secret"

# step 3: 获取 Access Token（自用型应用）
try:
    access_token_obj = AccessTokenBuilder.buildTokenByShopId("your_shop_id")
    if access_token_obj.isSuccess():
        access_token = access_token_obj.getAccessToken()
        print(f"Token 获取成功: {access_token[:20]}...")
    else:
        print("Token 获取失败")
        exit(1)
except Exception as e:
    print(f"获取 Token 异常: {e}")
    exit(1)

# step 4: 创建 API 请求
request = BuyinProductsDetailRequest()
request.getParams().product_ids = "123456789,987654321"
request.getParams().fields = "product_id,title,price,main_pic"

# step 5: 执行请求
try:
    response = request.execute(access_token)

    # step 6: 处理响应
    if response.isSuccess():
        print(f"请求成功!")
        print(f"商品数据: {response.data}")
    else:
        print(f"请求失败: {response.code} - {response.msg}")
        print(f"子错误: {response.sub_code} - {response.sub_msg}")
except Exception as e:
    print(f"请求异常: {e}")
```

### 8.2 使用 TokenHolder 自动管理 Token

```python
import threading
from doudian.core.DoudianOpConfig import GlobalConfig
from doudian.core.TokenHolder import TokenHolder
from doudian.api.buyin_productsDetail.BuyinProductsDetailRequest import BuyinProductsDetailRequest

# 配置
GlobalConfig.appKey = "your_app_key"
GlobalConfig.appSecret = "your_app_secret"

# 设置当前线程的 authId（用于 TokenHolder 自动获取）
threading.current_thread().__dict__['authId'] = "your_shop_id"

# 后续调用无需手动管理 Token
request = BuyinProductsDetailRequest()
request.getParams().product_ids = "123456789"

# 自动获取/刷新 Token 并执行请求
from doudian.core.DoudianOpApiLight import DoudianOpApiLight
response = DoudianOpApiLight.executeForLight(request)

if response.isSuccess():
    print(f"成功: {response.data}")
```

### 8.3 接收 SPI 回调示例

```python
from doudian.core.DoudianOpSpiRequest import DoudianOpSpiRequest
from doudian.core.DoudianOpConfig import GlobalConfig

# 配置
GlobalConfig.appKey = "your_app_key"
GlobalConfig.appSecret = "your_app_secret"

# 业务处理函数
def handle_order_notify(context):
    params = context.getParamJsonObject()
    print(f"收到订单通知: {params}")

    # 处理订单...
    context.wrapSuccess()
    context.setResponseData({"received": True})

# 初始化 SPI 请求
request = DoudianOpSpiRequest()
request.init(
    appKey=request.args.get('app_key'),
    timestamp=request.args.get('timestamp'),
    sign=request.args.get('sign'),
    signV2=request.args.get('signV2', ''),
    signMethod=request.args.get('signMethod', 'md5'),
    paramJson=request.args.get('paramJson', '{}')
)

# 注册处理器
request.registerHandler(handle_order_notify)

# 执行并返回响应（用于 Web 框架的响应）
response = request.execute(parseAsJsonString=True)
return response
```

---

## 9. 附录：公开接口清单

### 9.1 核心模块导出 (`doudian.core`)

| 类名 | 文件 | 说明 |
|-----|-----|------|
| `DoudianOpApiClient` | DoudianOpApiClient.py | API 请求客户端 |
| `DoudianOpApiRequest` | DoudianOpApiRequest.py | API 请求基类 |
| `DoudianOpApiLight` | DoudianOpApiLight.py | 轻量级 API 执行器 |
| `DoudianOpConfig` / `GlobalConfig` | DoudianOpConfig.py | 全局配置类 |
| `DoudianOpResponse` | DoudianOpResponse.py | API 响应基类 |
| `DoudianOpSpiRequest` | DoudianOpSpiRequest.py | SPI 请求处理器 |
| `DoudianOpSpiContext` | DoudianOpSpiContext.py | SPI 上下文 |
| `DoudianOpSpiParam` | DoudianOpSpiParam.py | SPI 参数类 |
| `DoudianOpSpiResponse` | DoudianOpSpiResponse.py | SPI 响应类 |
| `AccessToken` | AccessToken.py | Access Token 封装 |
| `AccessTokenBuilder` | AccessTokenBuilder.py | Token 构建器 |
| `TokenHolder` | TokenHolder.py | Token 缓存管理 |
| `DefaultHttpClient` | DefaultHttpClient.py | HTTP 客户端 |
| `DOUDIAN_SDK_VERSION` | Constant.py | SDK 版本常量 |

### 9.2 异常模块 (`doudian.exeception`)

| 类名 | 文件 | 说明 |
|-----|-----|------|
| `DoudianOpException` | DoudianOpException.py | SDK 异常类 |

### 9.3 API 模块清单（共 72 个）

#### Token 模块（3 个）
| 类名 | API 路径 |
|-----|---------|
| `CreateTokenRequest` | /token/create |
| `RefreshTokenRequest` | /token/refresh |
| `token` 模块目录 | - |

#### 精选联盟模块（38 个）
| 类名 | API 路径 |
|-----|---------|
| `BuyinProductsDetailRequest` | /buyin/productsDetail |
| `BuyinProductSkusRequest` | /buyin/productSkus |
| `BuyinProductSkusV2Request` | /buyin/productSkus_v2 |
| `BuyinKolProductsDetailRequest` | /buyin/kolProductsDetail |
| `BuyinKolMaterialsProductsDetailsRequest` | /buyin/kolMaterialsProductsDetails |
| `BuyinKolMaterialsProductsSearchRequest` | /buyin/kolMaterialsProductsSearch |
| `BuyinActivityProductListRequest` | /buyin/activityProductList |
| `BuyinActivityProductCancelRequest` | /buyin/activityProductCancel |
| `BuyinColonelActivityDetailRequest` | /buyin/colonelActivityDetail |
| `BuyinColonelTrusteeshipListRequest` | /buyin/colonel_trusteeshipList |
| `BuyinColonelMultiSettlementOrdersRequest` | /buyin/colonelMultiSettlementOrders |
| `BuyinColonelSpecialApplyListRequest` | /buyin/colonel_specialApplyList |
| `BuyinColonelSpecialApplyDealRequest` | /buyin/colonel_specialApplyDeal |
| `BuyinColonelExtendApplyListRequest` | /buyin/colonelExtendApplyList |
| `BuyinColonelAwardTaskListRequest` | /buyin/colonelAwardTaskList |
| `BuyinColonelMultiParseActivityUrlRequest` | /buyin/colonelMultiParseActivityUrl |
| `BuyinInstGmvRequest` | /buyin/instGmv |
| `BuyinInstGmvDetailRequest` | /buyin/instGmvDetail |
| `BuyinInstPickSourceConvertRequest` | /buyin/instPickSourceConvert |
| `BuyinDistributionLiveProductListV2Request` | /buyin/distributionLiveProductList_v2 |
| `BuyinShareCommandParseRequest` | /buyin/shareCommandParse |
| `BuyinShareCommandParseV2Request` | /buyin/shareCommandParse_v2 |
| `BuyinCommonShareCommandParseRequest` | /buyin/commonShareCommandParse |
| `BuyinDecryptContactInfoRequest` | /buyin/decryptContactInfo |
| `BuyinDoukeRewardOrdersRequest` | /buyin/doukeRewardOrders |
| `BuyinDoukeCrowdMatchRequest` | /buyin/doukeCrowdMatch |
| `BuyinDoukeCommandParseAndShareRequest` | /buyin/doukeCommandParseAndShare |
| `BuyinGetProductShareMaterialRequest` | /buyin/getProductShareMaterial |
| `BuyinMaterialsProductStatusRequest` | /buyin/materialsProductStatus |
| `BuyinOriginColonelApplyActivitiesRequest` | /buyin/originColonelApplyActivities |
| `BuyinOriginColonelEnrollableActivityListRequest` | /buyin/originColonelEnrollableActivityList |
| `BuyinOriginColonelUnappliedProductListRequest` | /buyin/originColonelUnappliedProductList |
| `BuyinInstituteOrderColonelRequest` | /buyin/instituteOrderColonel |
| `BuyinInstituteOrderPickRequest` | /buyin/instituteOrderPick |
| `BuyinInstitutionInfoRequest` | /buyin/institutionInfo |
| `BuyinMHandleTrusteeshipApplyRequest` | /buyin/mHandleTrusteeshipApply |

#### 联盟模块（10 个）
| 类名 | API 路径 |
|-----|---------|
| `AllianceColonelActivityCreateOrUpdateRequest` | /alliance/colonelActivity/createOrUpdate |
| `AllianceColonelActivityProductRequest` | /alliance/colonelActivityProduct |
| `AllianceColonelActivityProductAuditRequest` | /alliance/colonelActivityProductAudit |
| `AllianceColonelActivityProductExtensionRequest` | /alliance/colonelActivityProductExtension |
| `AllianceInstituteColonelActivityListRequest` | /alliance/instituteColonelActivityList |
| `AllianceInstituteColonelActivityOperateRequest` | /alliance/instituteColonelActivityOperate |
| `AllianceMaterialsProductCategoryRequest` | /alliance/materialsProductCategory |
| `AllianceMaterialsProductsDetailsRequest` | /alliance/materialsProductsDetails |
| `AllianceMaterialsProductsSearchRequest` | /alliance/materialsProductsSearch |
| `AllianceActivityProductCategoryListRequest` | /alliance/activityProductCategoryList |

#### 开放能力模块（4 个）
| 类名 | API 路径 |
|-----|---------|
| `OpenBinaryuploadRequest` | /open/binaryupload |
| `OpenGetAuthInfoRequest` | /open/getAuthInfo |
| `OpenMaterialTokenRequest` | /open/materialToken |
| `OpenCloudV1ImageVersionCreateRequest` | /openCloud/v1/imageVersion/create |

#### 订单模块（4 个）
| 类名 | API 路径 |
|-----|---------|
| `OrderBatchSensitiveRequest` | /order/batchSensitive |
| `OrderBatchEncryptRequest` | /order/batchEncrypt |
| `OrderGetMCTokenRequest` | /order/getMCToken |
| `OrderGetSearchIndexRequest` | /order/getSearchIndex |

#### 商品模块（4 个）
| 类名 | API 路径 |
|-----|---------|
| `ProductIsvCreateProductFromSupplyPlatformRequest` | /product/isv/createProductFromSupplyPlatform |
| `ProductIsvSaveGoodsSupplyStatusRequest` | /product/isv/saveGoodsSupplyStatus |
| `ProductIsvScanClueRequest` | /product/isv/scanClue |
| `ProductIsCategoryMigrateObjRequest` | /product/isCategoryMigrateObj |

#### 短信模块（11 个）
| 类名 | API 路径 |
|-----|---------|
| `SmsSendRequest` | /sms/send |
| `SmsBatchSendRequest` | /sms/batchSend |
| `SmsSendResultRequest` | /sms/sendResult |
| `SmsTemplateApplyRequest` | /sms/template/apply |
| `SmsTemplateApplyListRequest` | /sms/template/applyList |
| `SmsTemplateRevokeRequest` | /sms/template/revoke |
| `SmsTemplateDeleteRequest` | /sms/template/delete |
| `SmsTemplateSearchRequest` | /sms/template/search |
| `SmsPublicTemplateRequest` | /sms/public/template |
| `SmsSignApplyRequest` | /sms/sign/apply |
| `SmsSignApplyListRequest` | /sms/sign/applyList |
| `SmsSignApplyRevokeRequest` | /sms/sign/applyRevoke |
| `SmsSignDeleteRequest` | /sms/sign/delete |
| `SmsSignSearchRequest` | /sms/sign/search |

#### 权益模块（1 个）
| 类名 | API 路径 |
|-----|---------|
| `RightsInfoRequest` | /rights/info |

#### 安全模块（1 个）
| 类名 | API 路径 |
|-----|---------|
| `SecurityBatchReportOrderSecurityEventRequest` | /security/batchReportOrderSecurityEvent |

---

## 10. 版本历史

| 版本 | 日期 | 说明 |
|-----|-----|------|
| 1.1.0 | - | 当前版本 |

---

> **文档生成说明**: 本文档基于抖音开放平台 Python SDK 代码自动生成。如有疑问，请参考 [抖音开放平台官方文档](https://open.jinritemai.com/)。
