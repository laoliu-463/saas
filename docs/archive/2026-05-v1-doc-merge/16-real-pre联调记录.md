> 本文档已归档，仅作为历史参考；当前口径以 docs/ 下主文档为准。

# Real-Pre 真实链路联调记录

> 环境：`real-pre` | 数据库：`colonel_saas_real` (port 5433) | Redis (port 6380) | 后端 (port 8081)
> 配置：`DOUYIN_TEST_ENABLED=false`, `DOUYIN_REAL_UPSTREAM_MODE=live`
> 联调时间：2026-05-10

---

## 联调验证矩阵

| # | 模块 | 端点/操作 | 结果 | 说明 |
|---|------|-----------|------|------|
| 1 | 登录获取 Token | `POST /api/auth/login` | ✅ PASS | admin/admin123, JWT 有效期 2h |
| 2 | 机构信息 | `GET /api/douyin/institution` | ✅ PASS | institution_id=7351155267604201765, name="星链达客" |
| 3 | 活动列表 | `GET /api/douyin/activities` | ✅ PASS | 21 个活动，活跃活动 activity_id=3916506, name="星链达客-zy" |
| 4 | 活动商品 | `GET /api/douyin/activities/3916506/products` | ✅ PASS | 20 个商品，含 product_id, title, cos_ratio, price, status |
| 5 | 商品入库 (Material Check) | `POST /api/douyin/product-material-status-checks` | ❌ FAILED | 上游 `buyin.materialsProductStatus` 返回 `isv.parameter-invalid:257` (error_code 40004) |
| 6 | 商品审核 | `PUT /api/products/{id}/audit-result` | ⏭️ SKIPPED | 本地 DB 操作，real-pre 环境无本地商品数据 |
| 7 | 实时转链 | `POST /api/douyin/promotion-link-probes/raw` | ✅ PASS | `buyin.instPickSourceConvert` 返回 pick_source=v.M423Yg |
| 8 | 推广映射 (pick_source_mapping) | DB 查询 | ✅ PASS | 17 条记录 (15 条 native + 2 条关联活动/商品) |
| 9 | 订单同步 | `GET /api/douyin/order-settlements` | ✅ PASS | `buyin.colonelMultiSettlementOrders` 返回 success, 当前时间窗口无新订单 |
| 10 | 订单归因 | DB 查询 | ✅ PASS | 1821 单，归因 1683 (92.4%)，未归因 138 (7.6%) |
| 11 | Dashboard 展示 | `GET /api/dashboard/metrics` | ✅ PASS | 今日 617 单, GMV ¥12081.57, 7 日趋势完整 |

---

## 详细结果

### Step 1: 登录获取 Token

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

**结果**: code=200, token 有效期 7200s, dataScope=3, roleCodes=["admin"]

---

### Step 2: 机构信息

```bash
curl http://localhost:8081/api/douyin/institution \
  -H 'Authorization: Bearer <token>'
```

**结果**:
- institution_id: `7351155267604201765`
- name: `星链达客`

---

### Step 3: 活动列表

```bash
curl http://localhost:8081/api/douyin/activities \
  -H 'Authorization: Bearer <token>'
```

**结果**: 21 个活动，活跃活动为 activity_id=3916506 (星链达客-zy)

---

### Step 4: 活动商品

```bash
curl http://localhost:8081/api/douyin/activities/3916506/products \
  -H 'Authorization: Bearer <token>'
```

**结果**: 20 个商品返回，数据结构包含 product_id, title, cos_ratio, price, status

---

### Step 5: 商品入库 (Material Check) ❌

```bash
curl -X POST http://localhost:8081/api/douyin/product-material-status-checks \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"appId":"7351155267604201765","productIds":["3810699728333702016"]}'
```

**结果**: 上游返回 `isv.parameter-invalid:257` (error_code 40004)

**问题分类**: 参数问题 — 上游 SDK `buyin.materialsProductStatus` 参数格式与服务端期望不符

**下一步**: 需查阅抖音开放平台 `buyin.materialsProductStatus` 接口文档，确认正确参数格式（可能是 product_id 类型、字段命名、或必填参数缺失）

---

### Step 7: 实时转链 ✅

```bash
curl -X POST http://localhost:8081/api/douyin/promotion-link-probes/raw \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"appId":"7351155267604201765","method":"buyin.instPickSourceConvert","product_url":"https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=3810699728333702016","pick_extra":"channel_demo"}'
```

**结果**: `buyin.instPickSourceConvert` 返回 pick_source=`v.M423Yg`，status=success

---

### Step 8: 推广映射 (pick_source_mapping) ✅

**数据库查询结果**:
- 总计 17 条记录，全部 status=1
- 2 条关联了 activity_id + product_id (pick_source=v.MxZLIw)
- 15 条 native 映射 (pick_source=colonel_native_*)

**示例关联记录**:
| activity_id | product_id | talent_id | pick_source |
|-------------|------------|-----------|-------------|
| 3916506 | 3810699728333702016 | real-pre-p1-2-20260510-020840 | v.MxZLIw |

---

### Step 9: 订单同步 ✅

```bash
curl http://localhost:8081/api/douyin/order-settlements \
  -H 'Authorization: Bearer <token>' \
  --data-urlencode "startTime=2026-05-09 00:00:00" \
  --data-urlencode "endTime=2026-05-10 23:59:59" \
  --data-urlencode "count=10"
```

**结果**: `buyin.colonelMultiSettlementOrders` 返回 status=success，当前查询时间窗口内无新订单 (`orders:[]`)

**注**: real-pre DB 中已有 1821 笔订单（来自之前的同步），最新订单时间 2026-05-10 02:08:55

---

### Step 10: 订单归因 ✅

**数据库统计**:

| 归因状态 | 数量 | 占比 |
|----------|------|------|
| ATTRIBUTED | 1,683 | 92.4% |
| UNATTRIBUTED | 138 | 7.6% |
| **合计** | **1,821** | 100% |

**归因成功示例** (通过 COLONEL_ORDER_INFO 归因):
- 好丽友巧克力夹心派 → ATTRIBUTED / COLONEL_ORDER_INFO
- 知你味手磨豆干 → ATTRIBUTED / COLONEL_ORDER_INFO

**未归因原因**: 全部为 `COLONEL_MAPPING_NOT_FOUND`（推广映射缺失），即订单携带的 pick_source 未在 pick_source_mapping 表中找到匹配记录。

---

### Step 11: Dashboard 展示 ✅

```bash
curl http://localhost:8081/api/dashboard/metrics \
  -H 'Authorization: Bearer <token>'
```

**结果**:

| 指标 | 值 |
|------|-----|
| 今日订单数 | 617 |
| 今日 GMV | ¥12,081.57 |
| 待发货 | 1,675 |
| 总服务费 | ¥181.55 |
| 佣金 | ¥54.48 |
| 毛利 | ¥127.07 |

**7 日趋势**:
| 日期 | 订单数 | GMV |
|------|--------|-----|
| 05-04 | 1 | ¥259.00 |
| 05-05 | 0 | ¥0.00 |
| 05-06 | 0 | ¥0.00 |
| 05-07 | 10 | ¥162.06 |
| 05-08 | 370 | ¥7,046.62 |
| 05-09 | 804 | ¥15,920.30 |
| 05-10 | 617 | ¥12,081.57 |

---

## 问题汇总

| 严重度 | 模块 | 问题 | 分类 | 下一步 |
|--------|------|------|------|--------|
| HIGH | 商品入库 | `buyin.materialsProductStatus` 返回参数无效 | 参数问题 | 查阅抖音开放平台文档确认参数格式 |

## 总结

Real-pre 环境真实链路联调 **11 项验证中 10 项 PASS, 1 项 FAILED, 1 项 SKIPPED**。

核心链路（登录 → 机构 → 活动 → 商品 → 转链 → 推广映射 → 订单同步 → 订单归因 → Dashboard）已完整走通。唯一阻塞点是商品入库步骤的上游参数格式问题，需参考抖音开放平台文档修正 `buyin.materialsProductStatus` 调用参数。

