# 需求：商品库管理

**文档版本**：V1.0
**来源**：V2.2 定稿文档 §3.1、§3.2
**智能体入口**：直接读取此文件

---

## 一、功能定位

商品库是渠道商务选品的入口，**全员可见**，也是招商审核商品、配置寄样要求的核心工具。

---

## 二、功能清单

### 2.1 渠道端（选品）

| 功能 | 说明 |
|------|------|
| 卡片展示 | 图片、商品名称、商品ID、店铺、售价、佣金率、服务费率、寄样要求、活动信息 |
| 多维度筛选 | 商品类目、佣金率范围、寄样门槛、支持投流、招商负责人 |
| 一键复制 | 调用抖音官方转链接口，生成带归因参数的推广链接 |
| 商品详情页 | 完整信息：寄样要求、投流信息、推广素材 |
| 排序 | 最新推广、总销量、价格、佣金比例 |
| 分页 | 分页加载 |

### 2.2 招商端（审核）

| 功能 | 说明 |
|------|------|
| 待审核列表 | 查看分配给自己的商品，支持按活动、时间筛选 |
| 审核通过 | 补充信息后上架到商品库 |
| 审核拒绝 | 填写拒绝原因，商品退回，渠道不可见 |
| 寄样要求配置 | 可覆盖全局默认标准 |

#### 审核通过时需填写

- 专属价说明、发货信息
- 商品卖点、推广话术
- 是否支持投流
- 奖励说明、参与要求
- 活动时间、手卡（素材图片/文档）

### 2.3 招商组长（管理）

| 功能 | 说明 |
|------|------|
| 活动绑定 | 输入活动 ID 或从列表选择，触发系统同步商品列表 |
| 商品分配 | 将活动商品批量分配给招商人员，支持均分或指定分配 |

---

## 三、数据来源

| 数据类型 | 来源 | 存储位置 |
|----------|------|----------|
| 团长活动信息 | 抖音 API `buyin.colonel.activity.list` | `colonel_activity` 表 |
| 活动商品列表 | 抖音 API `buyin.colonel.product.list` | `product` 表 |
| 商品补充信息 | 招商审核时填写 | `product.extra_data`（JSONB） |
| 商品详情 | 抖音 API `buyin.product.detail`（按需） | 覆盖 `product` 表 |

---

## 四、审核流程

```
老板在官方后台创建活动
       ↓
招商组长在系统中同步活动列表（或手动绑定活动ID）
       ↓
系统自动同步该活动下的商品列表
       ↓
招商组长将商品批量分配给招商人员
       ↓
招商补充信息（寄样要求、话术等）后审核通过
       ↓
商品正式上架到商品库，对全员可见
```

---

## 五、转链归因

### 5.1 一键复制流程

```java
// Controller：商品转链
public ApiResult<String> generatePromotionLink(String productId, String userId) {
    // 1. 获取商品原始链接
    Product product = productService.getById(productId);
    String productUrl = product.getProductUrl();

    // 2. 调用抖音转链接口（pick_extra ≤ 20字符）
    String pickExtra = "ch_" + userId.substring(0, 16);  // channel_{userId}
    PromotionLinkResponse resp = douyinApi.convertPickSource(productUrl, pickExtra);

    // 3. 存储归因映射（见 rules/attribution-logic.md）
    String pickSource = extractPickSource(resp.getUrl());
    pickSourceMappingService.save(pickSource, userId, productId);

    return ApiResult.ok(resp.getUrl());
}
```

### 5.2 pick_extra 格式

```
pick_extra = "ch_" + userId（前16位）
示例：ch_a1b2c3d4e5f6g7h8
限制：≤ 20 字符，仅数字、字母、下划线
```

---

## 六、业务约束

| 约束 | 文件 | 级别 |
|------|------|------|
| 转链必须带归因参数 | `rules/attribution-logic.md` | **CRITICAL** |
| pick_extra ≤ 20 字符 | `requirements/03-api-specs.md` | **CRITICAL** |
| 商品审核后才能上架 | 业务逻辑 | HIGH |

---

## 七、相关文件索引

| 文件 | 路径 |
|------|------|
| 商品实体 | `backend/src/main/java/com/colonel/saas/entity/Product.java` |
| 活动实体 | `backend/src/main/java/com/colonel/saas/entity/ColonelActivityEntity.java` |
| 归因服务 | `backend/src/main/java/com/colonel/saas/service/PickSourceService.java` |
| 归因约束 | `rules/attribution-logic.md` |
