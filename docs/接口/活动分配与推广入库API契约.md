# 活动分配与推广入库 API 契约

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`
> 更新日期：2026-05-29
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
1. 校验目标用户存在、启用、且拥有 `biz_leader`、`biz_staff`、`colonel_leader` 角色之一
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

**复用现有接口**，返回拥有招商角色（`biz_leader`、`biz_staff`、`colonel_leader`）的用户列表，供前端填充分配弹窗下拉框。

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

---

### 2.5 推广中活动自动入库（内部）

**触发函数**：`ProductService.autoAddToLibraryIfPromoting`

**规则**：
1. 判断条件：`colonel_activity.status == 5` 或 `statusText` 包含"推广中"
2. 操作：设置 `product_operation_state.selected_to_library = true`、`selected_at = NOW()`
3. 设置 `display_status = DISPLAYING` 作为推广中活动的持久可见事实
4. 触发：`ProductDisplayRuleService.applyForActivityId(activityId)` 重算展示规则，推广中活动记录不参与同 `product_id` 去重隐藏

---

### 2.6 推广中活动展示规则（ProductDisplayRuleService）

**规则调整**（V1 必做）：
- 推广中活动的商品必须进入商品库展示候选
- 同商品多活动冲突时，保证当前活动下推广中的商品可见
- 去重规则不能把"可推广商品"完全隐藏（`HIDDEN`）
- 若某 product_id 在推广中活动中已标记入库，但被去重规则选为非展示候选：强制设为 `DISPLAYING`
- 未推广活动的商品仍沿用现有去重规则

**display_status 枚举**（复用现有）：
- `DISPLAYING`：展示中
- `HIDDEN`：隐藏
- `PENDING`：待审核

---

## 三、前端字段契约

### 3.1 活动列表页面

| 字段 | 来源 | 说明 |
|------|------|------|
| `recruiterUserId` / `recruiterUserName` | API 返回 | 活动级招商组长，优先展示 |
| 分配操作 | `PUT /colonel/activities/{id}/assignee` | 弹窗候选人来自 `GET /users/master-data/recruiters` |

### 3.2 商品管理 / 商品库页面

| 字段 | 来源 | 说明 |
|------|------|------|
| `assigneeName` | `product_operation_state.assignee_id` JOIN user | 招商组长（文案统一） |
| 联盟推广状态筛选 | `product_snapshot.statusText` | 筛选条件 |
| 活动状态筛选 | `colonel_activity.statusText` | 筛选条件 |

**文案统一**（V1 必做）：
- 所有展示"负责人"、"招商经理"、"招商负责人"的位置统一改为"招商组长"
- 包括商品卡片、操作列、详情页等

---

## 四、边界约定（V1 不做）

- 不实现单品级别的 assignee 覆盖独立归因规则
- 不扩展到独家商家、独家达人的分配逻辑
- 不实现保护期内的覆盖优先级重算
- 推广中判断以活动维度为准，不以商品维度代替

---

## 五、变更清单

| 序号 | 文件 | 变更类型 |
|------|------|---------|
| 1 | `db/migrate/V*__alter-colonel-activity-add-recruiter-fields.sql` | 新增 |
| 2 | `ColonelsettlementActivity.java` | 新增字段 |
| 3 | `ColonelsettlementActivityMapper.java` + `.xml` | 新增 mapper 方法 |
| 4 | `ColonelActivityController.java` | 新增分配 API |
| 5 | `ProductService.java` | 继承 assignee + 自动入库 |
| 6 | `ProductDisplayRuleService.java` | 推广中展示规则 |
| 7 | 前端活动列表 + 分配弹窗 | 新增入口 |
| 8 | 前端商品库 + 展示逻辑 | 文案 + 状态 |
