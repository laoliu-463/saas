# 活动分配与商品状态独立 API 契约

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`
> 更新日期：2026-06-01
> 状态：**契约锁定**，后端和前端均以此为准

---

## 一、数据库字段变更

### 1.1 `colonel_activity` 表新增字段

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `recruiter_user_id` | UUID | NULL | 当前活动分配的招商组长用户 ID，null 表示未分配 |
| `recruiter_dept_id` | UUID | NULL | 招商用户所属部门 ID |
| `assigned_at` | TIMESTAMP | NULL | 分配时间 |
| `assigned_by` | UUID | NULL | 操作用户 ID（管理员） |

SQL 变更通过 Flyway migration 文件执行：
- 文件名：`V*__alter-colonel-activity-add-recruiter-fields.sql`
- 使用 `ADD COLUMN IF NOT EXISTS` 兼容已有环境

---

## 二、接口契约

### 2.1 分配/变更活动招商组长

```
PUT /colonel/activities/{activityId}/assignee
```

**权限**：仅 `admin` 角色可调用

**请求体**：
```json
{
  "assigneeId": "UUID-string"
}
```
- `assigneeId` 为 `null` 时表示清除分配

**业务规则**：
1. 校验目标用户存在、启用、且拥有 `biz_leader`、`biz_staff` 角色之一
2. 更新 `colonel_activity.recruiter_user_id`、`recruiter_dept_id`、`assigned_at`、`assigned_by`
3. 级联更新该活动下已有 `product_operation_state` 的 `assignee_id`

**成功响应**（200）：
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "activityId": "20260428001",
    "recruiterUserId": "550e8400-e29b-41d4-a716-446655440000",
    "recruiterUserName": "张三",
    "recruiterDeptId": "660e8400-e29b-41d4-a716-446655440000",
    "recruiterDeptName": "招商部",
    "assignedAt": "2026-05-29T16:52:00",
    "assignedBy": "770e8400-e29b-41d4-a716-446655440000"
  }
}
```

**错误响应**：
- `400`：目标用户不存在或未启用
- `403`：目标用户不具备招商角色
- `404`：活动不存在

---

### 2.2 查询招商用户列表（分配候选人）

```
GET /users/master-data/recruiters
```

**复用现有接口**，返回拥有招商角色（`biz_leader`、`biz_staff`）的用户列表，供前端填充分配弹窗下拉框。

**响应字段约定**（部分字段）：
```json
{
  "data": [
    {
      "userId": "UUID-string",
      "userName": "张三",
      "deptId": "UUID-string",
      "deptName": "招商部",
      "roles": ["biz_leader"]
    }
  ]
}
```

---

### 2.3 活动列表 GET /colonel/activities（兼容扩展）

**新增查询参数**（V1 必做）：

| 参数 | 取值 | 说明 |
|------|------|------|
| `assignmentFilter` | `all`（默认）/ `assigned` / `unassigned` / `mine` | 按活动级招商组长分配筛选；`mine` 依赖当前登录用户 |

**数据范围与分页规则**（V1 必做，2026-05-30）：

| 角色 | 有效 filter | 数据源 |
|------|-------------|--------|
| `admin` + `all` | `all` | 上游 Gateway 分页 + 本地 enrich |
| `admin` + `assigned/unassigned/mine` | 请求值 | 本地 `colonel_activity` 分页 |
| `biz_leader` / `biz_staff`（非 admin） | **强制 `mine`** | 本地 `colonel_activity` 按 `recruiter_user_id = 当前用户` 分页 |

- 非 admin 传 `assignmentFilter=all` 仍按 `mine` 处理，禁止越权查看他人或未分配活动。
- `GET /api/colonel/activities/{activityId}/products` 对非 admin 招商角色校验活动 `recruiter_user_id`，未分配给自己返回 403。

**新增返回字段**（V1 必做）：
```json
{
  "activityId": "20260428001",
  "activityName": "4月精选联盟活动",
  "statusText": "推广中",
  "status": 5,
  "recruiterUserId": "550e8400-...",
  "recruiterUserName": "张三",
  "recruiterDeptId": "660e8400-...",
  "recruiterDeptName": "招商部",
  "assignedAt": "2026-05-29T16:52:00",
  "assignedBy": "770e8400-...",
  "lastSyncAt": "2026-05-29T16:52:00",
  // 兼容旧字段
  "assigneeId": "550e8400-...",
  "assigneeName": "张三"
}
```

---

### 2.4 商品快照同步继承逻辑（内部）

**触发函数**：`ProductService.upsertSnapshotsWithStats`

**规则**：
1. 同步前查询 `colonel_activity.recruiter_user_id`
2. 若活动已有 `recruiter_user_id`，对新商品（`product_operation_state` 不存在）和已有商品（状态记录中 `assignee_id` 为 null）均设置 `assignee_id = recruiter_user_id`
3. 不覆盖手动分配过的 `assignee_id`
4. 不因活动 `status/statusText` 设置 `selected_to_library`、`biz_status`、`audit_status` 或 `display_status`
5. 若上游商品自身 `product_snapshot.status = 1` / `statusText = 推广中`，且本地未拒绝、未暂停发布，则同步时自动设置 `selected_to_library = true`，空/待审核状态自动写为 `biz_status = APPROVED`、`audit_status = 2`、`audit_remark = 上游状态为推广中，系统自动入库展示`
6. 若上游商品自身不是推广中，则同步时隐藏并移出商品库展示，`hidden_reason = UPSTREAM_NOT_PROMOTING`
7. 本地 `audit_status = 3` / `biz_status = REJECTED` 优先隐藏，`hidden_reason = LOCAL_REJECTED`；本地 `manual_disabled = true` 视为暂停发布，`hidden_reason = LOCAL_PAUSED`，优先隐藏且不自动恢复发布

---

### 2.5 活动状态与商品状态独立（内部）

**触发函数**：
- `ProductService.upsertSnapshotsWithStats`
- `ProductService.refreshActivitySnapshots`
- `ProductService.assignActivity`
- `ColonelsettlementActivityService.syncFromGatewayItem`

**独立规则**：
1. 活动状态只写入 `colonel_activity.activity_status_code` / `activity_status_text`
2. 商品同步、活动商品全量刷新、管理员分配招商、活动列表落库均不得因活动「推广中」自动设置商品入库、审核通过或展示中
3. 历史数据不自动回滚；已有 `selected_to_library`、`biz_status`、`audit_status`、`display_status` 按既有事实保留
4. `GET /api/colonel/activities/{activityId}/products?refresh=true` 继续返回 `syncStats` 兼容旧前端，其中 `libraryEntryCount` 为本次上游推广中商品自动入库数量，`autoLibraryEligible = libraryEntryCount > 0`
5. `ProductService.refreshActivitySnapshots` 在活动商品全量刷新后执行 `repairLibraryStateForActivity(activityId, false, 10000)`，再执行 `applyForActivityId(activityId)`；历史 `status=1` 但仍停留在 `PENDING_AUDIT/selected_to_library=false` 的数据必须通过统一规则入口修复，不允许裸 SQL 直改。

**商品状态来源**：
1. 商品是否入库来自商品域操作：上游商品自身推广中自动入库、手动加入商品库、商品审核/运营状态写入
2. 商品是否展示来自商品自身联盟状态、本地拒绝/暂停状态、推广期、去重优先级和强制展示标记
3. 活动 `status == 5` 或状态文案包含「推广中」不能绕过商品自身展示规则

---

### 2.6 商品展示规则（ProductDisplayRuleService）

**规则调整**（V1 必做）：
- 只按 `product_snapshot.status/statusText`、`product_operation_state.selected_to_library`、本地拒绝/暂停状态、推广期、手动禁用、去重优先级和强制展示标记计算展示状态
- 上游商品自身 `status=1/推广中` 且本地未拒绝、未暂停时可以进入商品库展示竞争，不要求人工审核通过
- 商品自身联盟状态未满足展示规则时，即使所属活动为「推广中」也不得展示
- 同商品多活动冲突时，按商品域去重优先级选择展示候选，活动状态不提供展示保护
- 已入库商品在刷新活动商品后仍可重新运行展示规则，但规则输入只能来自商品自身事实

**display_status 枚举**（复用现有）：
- `DISPLAYING`：展示中
- `HIDDEN`：隐藏
- `PENDING`：待审核

### 2.7 商品库展示状态 repair / health 运维接口

```
POST /colonel/activities/{activityId}/products/repair-library-state
```

**权限**：仅 `admin` 角色可调用。

**请求体**：
```json
{
  "dryRun": true,
  "limit": 1000
}
```

**规则**：
1. `dryRun=true` 只返回差异，不写库。
2. `dryRun=false` 仅在人工确认 real-pre 窗口后执行，写入后触发商品展示规则重算。
3. `status=1/推广中` 且本地未拒绝、未暂停、未过期时，自动设置 `selected_to_library=true`、`audit_status=2`，空/待审核业务状态转为 `APPROVED`。
4. 非推广中、本地拒绝、本地暂停或推广期过期的商品不得进入商品库展示。

```
GET /colonel/products/library/health
```

**权限**：仅 `admin` 角色可调用。

**返回指标**：`snapshotTotal`、`promotingTotal`、`promotingNotSelected`、`promotingNotDisplaying`、`displayingWithHiddenReason`、`selectedButNotPromoting`、`upstreamNotPromoting`、`localRejected`、`localPaused`、`lastSyncTime`、`lastSyncError`。

---

## 三、前端字段契约

### 3.1 活动列表页面

| 字段 | 来源 | 说明 |
|------|------|------|
| `recruiterUserId` / `recruiterUserName` | API 返回 | 活动级招商组长，优先展示 |
| 分配操作 | `PUT /colonel/activities/{id}/assignee` | 弹窗候选人来自 `GET /users/master-data/recruiters` |
| 获取同步商品 | `POST /colonel/activities/{id}/products/sync` | 前端只触发后台同步并提示“后台同步中”，接口立即返回 `syncStatus=ACCEPTED/RUNNING`；同步结果通过稍后刷新列表、日志和 `lastSyncAt` 验证 |
| 强制刷新兼容 | `GET /colonel/activities/{id}/products?refresh=true` | 兼容旧链路的同步并返回视图；上游商品自身推广中自动进入商品库；兼容返回 `syncStats.libraryEntryCount`、`syncStats.autoLibraryEligible` |

### 3.2 商品管理 / 商品库页面

| 字段 | 来源 | 说明 |
|------|------|------|
| `assigneeName` | `product_operation_state.assignee_id` JOIN user | 招商组长（文案统一） |
| 联盟推广状态筛选 | `product_snapshot.statusText` | 商品状态筛选，只匹配商品自身联盟状态 |
| 活动状态筛选 | `colonel_activity.statusText` | 活动状态筛选，只匹配活动自身状态 |

**文案统一**（V1 必做）：
- 所有展示"负责人"、"招商经理"、"招商负责人"的位置统一改为"招商组长"
- 包括商品卡片、操作列、详情页等

---

## 四、边界约定（V1 不做）

- 不实现单品级别的 assignee 覆盖独立归因规则
- 不扩展到独家商家、独家达人的分配逻辑
- 不实现保护期内的覆盖优先级重算
- 活动推广中只用于活动维度展示/筛选，不作为商品入库或展示依据；商品推广中只来自商品自身联盟状态

---

## 五、变更清单

| 序号 | 文件 | 变更类型 |
|------|------|---------|
| 1 | `db/migrate/V*__alter-colonel-activity-add-recruiter-fields.sql` | 新增 |
| 2 | `ColonelsettlementActivity.java` | 新增字段 |
| 3 | `ColonelsettlementActivityMapper.java` + `.xml` | 新增 mapper 方法 |
| 4 | `ColonelActivityController.java` | 新增分配 API |
| 5 | `ProductService.java` | 继承 assignee + 状态独立同步 |
| 6 | `ProductDisplayRuleService.java` | 商品自身状态展示规则 |
| 7 | 前端活动列表 + 分配弹窗 | 新增入口 |
| 8 | 前端商品库 + 展示逻辑 | 文案 + 商品状态筛选 |
