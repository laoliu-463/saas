# 15-real-pre 后续联调执行清单

更新时间：2026-05-08
适用环境：`real-pre`
目标：把当前真实上游联调状态、核心卡点和下一步动作固化为可执行清单。

## 一、当前项目进展

### 1. 联调状态

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| Auth 授权 | 已完成 | 新 OAuth 授权码已验证，Token 初始化 / 状态 / 刷新均可用 |
| Activity 活动接口 | 已完成 | 活动列表、活动详情已返回上游 `10000 / success` |
| Product 商品接口 | 部分覆盖 | 活动商品列表和业务刷新已通；商品详情 / SKU 缺商品详情权限包 |
| Order 订单接口 | 部分覆盖 | 团长侧订单同步已通；真实订单已入库但归因未闭环 |
| Webhook 验签 | 已完成接收层 | 接收、验签、快速返回和日志脱敏已覆盖；业务消费未完成 |
| 归因逻辑 | Mock / 本地通，真实未闭环 | 真实订单目前没有 `pick_source / pick_extra`，无法形成真实归因成功样本 |
| 权限包申请 | 等待平台审批 | 商品详情、店铺侧订单、敏感数据解密依赖抖店平台权限包 |

### 2. 订单数量口径

当前文档里存在两个时间点的订单数量，后续引用时必须带时间：

| 时间点 | 口径 | 结论 |
| --- | --- | --- |
| 2026-05-08 13:30 左右 | 商品级闭环复核 | 真实订单约 `52` 笔，其中真实订单未形成归因闭环 |
| 2026-05-08 14:02 ~ 14:04 | 三方接口矩阵复测 | 当前真实订单总量 `68`；`UNATTRIBUTED=67`，`NO_PICK_SOURCE=67`；另有 `1` 笔历史种子归因样本 |

后续默认采用较新的 `2026-05-08 14:04` 口径，除非执行人明确说明是在复核 13:30 前后的历史样本。

### 3. 核心卡点

当前核心卡点不是“主代码完全没通”，而是“真实业务数据尚未形成闭环”：

- 真实订单没有 `pick_source / pick_extra`，说明用户购买时没有走当前系统生成的推广链接，或上游未在团长订单中回传来源。
- 已真实转链的商品，在转链完成后尚未产生带来源的新真实订单。
- 商品详情 / SKU、店铺侧订单和敏感数据解密权限包尚未审批完成。
- 多个真实转链样本曾返回同一个 `pickSource=v.MxZLIw`，本地 `pick_source_mapping` 当前按 `pick_source` 唯一索引保存，存在后写覆盖先写的归因风险。

## 二、立即可做（不依赖权限包）

### 1. 重启 real-pre 容器

优先使用 Docker Compose v2 命令：

```powershell
docker compose -f docker-compose.real-pre.yml --env-file .env.real-pre down
docker compose -f docker-compose.real-pre.yml --env-file .env.real-pre up -d
```

如果本机只支持旧版命令，再使用：

```powershell
docker-compose -f docker-compose.real-pre.yml --env-file .env.real-pre down
docker-compose -f docker-compose.real-pre.yml --env-file .env.real-pre up -d
```

### 2. 确认容器健康

```powershell
docker compose -f docker-compose.real-pre.yml --env-file .env.real-pre ps
```

通过标准：

- `backend-real-pre` healthy
- `frontend-real-pre` healthy
- `postgres-real-pre` healthy
- `redis-real-pre` healthy

### 3. 确认后端日志

推荐按服务名看日志，避免容器名前缀因 compose project 改变而失效：

```powershell
docker compose -f docker-compose.real-pre.yml --env-file .env.real-pre logs --tail 300 backend-real-pre |
  Select-String -Pattern 'profile|started|error|RealDouyin|upstreamMode|Douyin API call'
```

也可先查实际容器名：

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | Select-String -Pattern 'real-pre'
```

通过标准：

- 日志出现 `The following 1 profile is active: "real"`
- 日志出现 `gateway=RealDouyinOrderGateway, upstreamMode=live`
- 最近日志没有持续启动失败、数据库连接失败或 Redis 连接失败
- 日志不应出现 `access_token=`、`refresh_token=`、`sign=`、`app_secret`

### 4. 执行接口 09：商品素材状态

接口：`POST /api/douyin/product-material-status-checks`
上游能力：`buyin.materialsProductStatus`
目的：补齐逐接口联调文档中的接口 09 当前证据。

前置：

1. 登录 real-pre 后端拿管理员 JWT。
2. 准备 1 到 2 个真实商品 ID。

PowerShell 示例：

```powershell
$base = "http://localhost:8081/api"
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType "application/json" `
  -Body (@{ username = "admin"; password = "admin123" } | ConvertTo-Json)
$token = $login.data.token

$body = @{
  products = @("3811489772686409810", "3793873371549270033")
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "$base/douyin/product-material-status-checks" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
```

记录要求：

- HTTP 状态码
- 统一响应 `code`
- `data.status`
- 上游 `remoteResponse.code / msg / log_id`
- 是否有权限、参数或空数据错误

### 5. 执行转链 fallback：接口 11 / 12

接口入口：`POST /api/douyin/promotion-link-probes/raw`
目的：验证 fallback 方法真实返回结构，不改变当前“主转链已走 `buyin.instPickSourceConvert` 成功”的结论。

#### 接口 11：`buyin.kolProductShare`

```powershell
$payload = @{
  method = "buyin.kolProductShare"
  product_id = "3811489772686409810"
  pick_extra = "fallback_11_probe"
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "$base/douyin/promotion-link-probes/raw" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $payload
```

#### 接口 12：`buyin.getProductShareMaterial`

```powershell
$payload = @{
  method = "buyin.getProductShareMaterial"
  product_id = "3811489772686409810"
  pick_extra = "fallback_12_probe"
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "$base/douyin/promotion-link-probes/raw" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $payload
```

记录要求：

- 方法名
- 真实返回字段
- 是否返回链接字段
- 是否返回 `pick_source` 或等价字段
- 错误码、子码、`logId`
- 是否有副作用或需避免重复调用

## 三、等待权限包审批后执行

### 1. 验证商品详情接口

接口能力：`product.detail`
当前状态：代码已有 `RealDouyinProductGateway.queryProductDetail`，但 raw probe 返回 `30001 / isv.app-permissions-insufficient`。

通过标准：

- 上游返回成功。
- 能拿到商品标题、图片、价格、店铺、详情字段。
- SKU 或 `spec_prices` 字段可解析。
- 业务详情页不能只依赖活动商品快照冒充商品详情成功。

### 2. 验证店铺侧订单接口

接口能力：

- `order.searchList`
- `order.orderDetail`

当前状态：均到达上游，但返回 `30001 / isv.app-permissions-insufficient`。

通过标准：

- 当前应用具备订单管理接口权限包。
- 当前授权主体与店铺订单主体匹配。
- 能拿到当前授权主体下真实订单号。
- 若返回敏感字段，应确认是否是密文字段，不能写入日志。

### 3. 验证订单解密成功样本

正式口径：

- 上游方法：`order.batchDecrypt`
- 入参：`cipher_infos=[{auth_id,cipher_text}]`

当前状态：

- 本地入口 `/api/orders/phone-decryptions` 可达。
- 真实负向证据已命中上游。
- 代码仍需对齐正式 `order.batchDecrypt` 契约。

通过标准：

- 只传同一授权主体下的真实 `auth_id / cipher_text`。
- 返回成功解密结果。
- 操作日志记录“谁解密了哪些订单”，不记录手机号明文。

## 四、推动真实归因闭环

目标不是“再同步几单”，而是证明：

> 真实转链 -> 用户通过推广链接下单 -> 订单回流携带 `pick_source / pick_extra` -> 本地归因到正确商品和渠道。

建议路径：

1. 选择已有真实订单的爆款商品。
2. 先在系统内推进到可转链状态：审核通过、入库、分配、生成推广链接。
3. 投放系统生成的推广链接。
4. 等待新订单回流。
5. 按 `archive/runbooks/13-real-pre首单回流复核清单.md` 复核：
   - 新订单时间是否晚于转链时间
   - 商品 ID 是否匹配
   - 是否带 `pick_source / pick_extra`
   - 是否命中正确 `pick_source_mapping`
   - 是否出现 `pickSource` 复用覆盖

## 五、执行完成后必须回写

每执行一个接口或一次真实订单复核，都要同步更新：

1. `docs/archive/records/14-抖店SDK全量梳理与逐接口联调规划.md`
2. `docs/archive/records/20-2026-05-08-新授权码三方全流程联调报告.md` 或新建后续联调记录
3. `docs/09-真实SDK联调准备清单.md`
4. 如影响阶段结论，再更新 `docs/04-开发进度.md`

## 六、一句话结论

当前 real-pre 已经不是“没有真实 SDK”的状态；它已经完成真实上游主链路前半段。下一步不要泛泛重测全系统，而是按“不依赖权限包的接口 09 / 11 / 12”和“依赖权限包的商品详情 / 店铺订单 / 解密”分两条线推进，同时等待真实推广链接产生新订单来验证最终归因闭环。
