# 每日开发进度记录

**项目**：抖音团长 SaaS 系统
**起始日期**：2026-04-20
**当前分支**：`feature/auth-system`

---

## 2026-04-20（周一）

### 完成内容

| 模块 | 状态 | 说明 |
|------|------|------|
| M0.3 登录认证 | **已完成** | 修复 UUID 主键映射 + sys_user_role.deleted 列 |
| 后端登录验证 | **已完成** | `POST /api/auth/login` 返回真实 JWT（admin/admin123） |
| Apifox 配置 | **已完成** | Base URL: `http://localhost:8080/api` |

### 修复的问题

1. **`UUIDTypeHandler` NPE 崩溃**
   - 根因：PostgreSQL UUID → Java UUID 无 TypeHandler 映射
   - 修复：创建 `common/handler/UUIDTypeHandler.java`，注册到 `type-handlers-package`

2. **`sys_user_role.deleted` 列缺失**
   - 根因：`SysRoleMapper.findByUserId` XML 查询了 `ur.deleted` 但表无此列
   - 修复：`init-db.sql` 新增 `deleted SMALLINT NOT NULL DEFAULT 0`

---

## 2026-04-21（周二）

### 上午：后端用户 CRUD

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | 创建 SysUser DTO（分页/创建/更新） | `auth/dto/` | 有 Page/Create/Update/ResetPassword |
| 2 | 创建 SysUserService（CRUD + 密码加密） | `auth/service/SysUserService.java` | BCrypt 加密、新增关联角色 |
| 3 | 创建 SysUserController（分页/详情/新增/修改/删除） | `controller/SysUserController.java` | 7 个接口，路径 `/sys/users` |
| 4 | 创建 SysRoleService + Controller | `auth/service/SysRoleService.java` + `controller/SysRoleController.java` | 角色 CRUD，路径 `/sys/roles` |
| 5 | 创建 SysDeptService + Controller | `auth/service/SysDeptService.java` + `controller/SysDeptController.java` | 部门树形，路径 `/sys/depts` |
| 6 | 创建角色分配接口 | `controller/SysUserController.java` | `PUT /sys/users/{id}/roles` |

### 下午：前端用户管理页面

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | Axios 封装（request 拦截器 + token） | `frontend/src/utils/request.ts` | 请求自动带 JWT，响应统一处理 |
| 2 | Pinia auth store | `frontend/src/stores/auth.ts` | 存 token/role/userInfo |
| 3 | 路由守卫 | `frontend/src/router/index.ts` | 未登录跳转 /login，带 token 才能访问 |
| 4 | 布局页完善（Header 显示用户名/退出） | `views/layout/Header.vue` | 右上角显示 admin + 退出按钮 |
| 5 | 用户管理页面 | `frontend/src/views/system/UserList.vue` | 表格 + 分页 + 新增/编辑/删除弹窗 |
| 6 | 角色管理页面 | `frontend/src/views/system/RoleList.vue` | 表格 + 新增/编辑弹窗 |
| 7 | 部门管理页面 | `frontend/src/views/system/DeptTree.vue` | 树形组件 + 新增/编辑 |

### 验收命令
```bash
# 后端
curl -s http://localhost:8080/api/sys/users/page?page=1&size=10

# 前端
# 打开 http://localhost:3000 应自动跳转登录页
# 登录后访问 http://localhost:3000/system/users
```

---

## 2026-04-22（周三）

### 上午：抖音 SDK 封装（M1.2）

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | TokenManager（Redis 锁 + 自动刷新） | `douyin/token/TokenManager.java` | 分布式锁防并发，5 分钟刷新阈值 |
| 2 | DouyinTokenService 重构 | `douyin/DouyinTokenService.java` | 调用 TokenManager，异常抛业务异常 |
| 3 | OrderApi（滑窗同步方法） | `douyin/api/OrderApi.java` | 支持时间范围参数，返回订单列表 |
| 4 | ProductApi（活动/商品同步） | `douyin/api/ProductApi.java` | 团长活动列表 + 商品列表 |
| 5 | PromotionAPI（ShortID 生成） | `douyin/api/PromotionApi.java` | 转链接口，提取 short_id |

### 下午：订单同步定时任务（M1.3 前置）

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | 创建 colonelsettlement_order Entity | `entity/ColonelsettlementOrder.java` | 继承 BaseEntity，UUID 主键 |
| 2 | 创建 OrderMapper + XML | `mapper/ColonelsettlementOrderMapper.java` | 按 create_time 范围查询 |
| 3 | 创建 OrderSyncService | `douyin/service/OrderSyncService.java` | 滑窗 10 分钟窗口，Redis 记录同步点 |
| 4 | 创建定时任务配置 | `config/ScheduleConfig.java` | 启用调度，OrderSync 每 10 分钟 |
| 5 | 创建 AttributionService | `colonel/service/AttributionService.java` | 归因逻辑（独家达人优先 → pick_source） |

### 验收命令
```bash
# 手动触发同步
curl -s -X POST http://localhost:8080/api/order/sync/trigger

# 查看 Redis 同步点
docker exec redis redis-cli GET order:sync:last_time
```

---

## 2026-04-23（周四）

### 上午：商品库完善（M0.4 → M1.5）

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | 创建 Product/ColonelActivity Entity | `entity/Product.java` + `entity/ColonelsettlementActivity.java` | UUID 主键，含商品/活动字段 |
| 2 | 创建 ProductService + Controller | `product/service/ProductService.java` | CRUD + 审核上下架 |
| 3 | 商品列表页面完善 | `views/product/index.vue` | 搜索 + 状态筛选 + 分页 |
| 4 | 商品详情弹窗 | `views/product/ProductDetail.vue` | 查看/编辑商品信息 |
| 5 | 活动管理页面 | `views/product/ActivityList.vue` | 活动列表 + 关联商品 |
| 6 | 活动同步（对接抖音 API） | `douyin/service/ActivitySyncService.java` | 从抖音拉取活动列表 |

### 下午：达人 CRM（M0.5 → M1.5）

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | 创建 Talent/TalentClaim Entity | `entity/Talent.java` + `entity/TalentClaim.java` | 含达人字段 + 认领关系 |
| 2 | 创建 TalentService + Controller | `talent/service/TalentService.java` | 公海私海分离，保护期判断 |
| 3 | 达人列表页面 | `views/talent/index.vue` | 公海/私海 Tab + 认领按钮 |
| 4 | 认领接口 | `TalentService.claim()` | 保护期冲突校验 |
| 5 | Mock 达人数据填充 | `data/mock/talents.json` | 20 条模拟达人数据 |

### 验收命令
```bash
# 商品列表
curl -s http://localhost:8080/api/products?page=1&size=10

# 达人列表
curl -s http://localhost:8080/api/talents/public?page=1&size=10
```

---

## 2026-04-24（周五）

### 上午：寄样台（M0.6）

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | 创建 SampleRequest Entity | `entity/SampleRequest.java` | 含状态机字段 |
| 2 | 创建 SampleStatusLog Entity | `entity/SampleStatusLog.java` | 状态变更记录 |
| 3 | 创建 SampleService | `sample/service/SampleService.java` | 状态机流转 + 7 天限制校验 |
| 4 | 状态机测试用例 | `test/java/.../SampleServiceTest.java` | PENDING→APPROVED→SHIPPED→SIGNED 流程 |
| 5 | 寄样列表页面 | `views/sample/index.vue` | 筛选 + 详情弹窗 + 状态标签 |
| 6 | 寄样申请页面 | `views/sample/Apply.vue` | 选达人 + 选商品 + 提交 |

### 下午：数据看板（M0.7）

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | 创建 DataPlatformService | `data/service/DataPlatformService.java` | 核心指标计算（按 DataScope） |
| 2 | 指标 Controller | `controller/DataPlatformController.java` | `/data/metrics` 指标接口 |
| 3 | 指标页面 | `views/data/index.vue` | 4 个指标卡片（订单数/GMV/服务费/样品数） |
| 4 | 订单明细页面 | `views/data/OrderList.vue` | Mock 数据 + 分页 |
| 5 | Mock 数据填充 | `data/mock/orders.json` | 100 条模拟订单 |

### 验收命令
```bash
# 指标接口
curl -s http://localhost:8080/api/data/metrics

# 寄样列表
curl -s http://localhost:8080/api/samples?page=1&size=10
```

---

## 2026-04-25（周六）

### 全天：单元测试（M0.8）

| # | 任务 | 文件路径 | 验收标准 |
|---|------|----------|----------|
| 1 | DataScope 切面测试 | `test/java/.../DataScopeAspectTest.java` | PERSONAL/DEPT/ALL 三种过滤 |
| 2 | 寄样 7 天限制测试 | `test/java/.../SampleServiceTest.java` | 7天内重复申请拦截 |
| 3 | 状态机流转测试 | `test/java/.../SampleStatusTransitionTest.java` | 非法流转被拒绝 |
| 4 | 达人认领测试 | `test/java/.../TalentClaimTest.java` | 保护期内无法认领 |
| 5 | 归因逻辑测试 | `test/java/.../AttributionServiceTest.java` | 独家达人优先 pick_source |
| 6 | JWT 认证测试 | `test/java/.../AuthServiceTest.java` | 正确密码/错误密码 |
| 7 | 覆盖率报告 | `target/site/jacoco/index.html` | 整体 ≥ 80% |

### 验收命令
```bash
cd backend
./mvnw test jacoco:report
# 打开 target/site/jacoco/index.html 检查覆盖率
```

---

## 2026-04-28（周一）

### 上午：爬虫模块（M1.4）

| # | 任务 | 路径 | 验收标准 |
|---|------|------|----------|
| 1 | CrawlerBase（间隔 + UA 轮换） | `crawler/base.py` | 3-6 秒随机间隔，UA 池轮换 |
| 2 | 达人信息采集器 | `crawler/talent_spider.py` | 昵称/粉丝/等级/月销 |
| 3 | Docker 配置 | `docker-compose.yml` 添加 python 服务 | 爬虫独立容器运行 |
| 4 | 失败重试 + 日志 | `crawler/retry.py` | 3 次重试，失败写日志 |
| 5 | Talent 数据更新接口 | `talent/service/TalentSyncService.java` | 爬虫数据写入数据库 |

### 下午：V0.5 联调 + Bug 修复

| # | 任务 | 说明 |
|---|------|------|
| 1 | 前端 Mock 数据替换 | 将所有 Mock 数据替换为真实接口调用 |
| 2 | 接口鉴权验证 | 所有接口带 token 才能访问 |
| 3 | DataScope 前端过滤 | 前端根据 userInfo.dataScope 过滤数据 |
| 4 | Bug 修复 | 根据测试结果修复问题 |

---

## 里程碑对照表

| 里程碑 | 目标日期 | 实际完成 | 状态 |
|--------|----------|----------|------|
| M0.1 项目骨架 | 04-19 | 04-20 | ✅ |
| M0.2 数据库初始化 | 04-19 | 04-20 | ✅ |
| M0.3 登录认证 | 04-20 | 04-20 | ✅ |
| M0.3 用户 CRUD | 04-21 | - | 🔄 |
| M0.4 商品库 | 04-22 | - | ⏳ |
| M0.5 达人 CRM | 04-23 | - | ⏳ |
| M0.6 寄样台 | 04-24 | - | ⏳ |
| M0.7 数据看板 | 04-24 | - | ⏳ |
| M0.8 单元测试 | 04-25 | - | ⏳ |
| M1.1 数据库脚本 | 04-20 | 04-20 | ✅ |
| M1.2 抖音 SDK | 04-26 | - | ⏳ |
| M1.3 订单同步 | 04-27 | - | ⏳ |
| M1.4 爬虫模块 | 04-28 | - | ⏳ |
| M1.5 真实数据接入 | 04-28 | - | ⏳ |
| M1.6 数据看板真实数据 | 04-29 | - | ⏳ |
| M1.7 Docker 部署 | 04-29 | - | ⏳ |
| M1.8 性能测试 | 04-30 | - | ⏳ |
| M1.9 上线部署 | 05-01 | - | ⏳ |

---

## 每日记录格式

完成当日工作后，在顶部插入新记录：

```markdown
## YYYY-MM-DD（周X）

### 完成内容
| 模块 | 状态 | 说明 |

### 修复的问题
1. **问题描述**
   - 根因：
   - 修复：

### 验收命令
```bash
# 在此记录验证命令
```

### 明日计划（参考上方详细排期调整）
```
```
