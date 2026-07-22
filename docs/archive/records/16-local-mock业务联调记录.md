# 16-Local Mock 业务联调记录

更新时间：2026-04-30

## 1. 文档定位

本文记录当前阶段的本地业务联调结果。

当前阶段只验证本地业务闭环：

- 不接真实抖店 / 抖音 / SDK 接口
- 三方相关能力统一使用本地 Mock / test 工具 / 本地数据
- 每个阶段按“业务理解 -> 页面验证 -> 接口核对 -> 数据库核对 -> 日志核对”的顺序推进

## 2. 联调边界

### 允许

- 登录
- 商品库
- 活动商品
- Mock 转链
- Mock 订单回流
- 订单归因
- 达人本地数据展示
- 寄样申请、审核、发货、签收、待交作业、自动完成
- 系统用户与角色
- 本地 Mock 数据生成
- 数据库状态核对
- 后端日志检查

### 禁止默认执行

- 真实抖店接口联调
- 真实抖音接口联调
- 真实 SDK token 换取
- 真实活动、商品、订单、达人、物流接口调用

## 3. 第 0 阶段：环境确认

### 3.1 测试目的

确认本地项目可以支撑后续业务联调，重点检查：

- 后端是否可用
- 前端是否可用
- PostgreSQL 是否可用
- Redis 是否可用
- 当前是否使用 Mock/test 能力
- Mock 数据初始化入口是否可用
- 登录账号是否可用

### 3.2 实际检查结果

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 后端容器 | `saas-test-backend-1` 运行中，端口 `8080`，状态 healthy | 通过 |
| 前端容器 | `saas-test-frontend-1` 运行中，端口 `3000`，Vite 可访问 | 通过 |
| PostgreSQL | `saas-test-postgres-1` 运行中，端口 `5432` | 通过 |
| Redis | `saas-test-redis-1` 运行中，`redis-cli ping` 返回 `PONG` | 通过 |
| 后端健康检查 | `GET /api/actuator/health` 返回 `UP` | 通过 |
| 前端首页 | `GET http://localhost:3000` 返回 Vite HTML | 通过 |
| 登录账号 | `admin / admin123` 登录成功，返回 JWT 与 `admin` 角色 | 通过 |
| Mock 初始化 | `POST /api/test/seed` 返回 `code=200` | 通过 |
| Mock 开关 | 容器环境变量存在 `APP_TEST_DOUYIN_ENABLED=true`、`DOUYIN_MOCK_ENABLED=true` | 通过 |
| Spring profile | 默认本地联调入口已统一为 `SPRING_PROFILES_ACTIVE=local-mock` | 通过 |

### 3.3 Mock 初始化返回摘要

`POST /api/test/seed` 已返回成功，关键数据如下：

| 数据类型 | 返回摘要 |
| --- | --- |
| 商品 | `10901825`、`10901826`、`10901827` |
| 达人 | `talent_test_a` ~ `talent_test_g` |
| pickSource | `TESTPS01` |
| 标准寄样单 | `TEST-SAMPLE-001` |
| 订单驱动寄样单 | `TEST-SAMPLE-ORDER-001` |
| 拒绝寄样单 | `TEST-SAMPLE-REJECT-001` |
| 关闭寄样单 | `TEST-SAMPLE-CLOSED-001` |
| 发货中寄样单 | `TEST-SAMPLE-SHIP-001` |
| 过期认领达人 | `talent_test_e` |

### 3.4 数据库抽样结果

当前连接库：`colonel_saas`

| 表/对象 | 数量 |
| --- | ---: |
| `sys_user` | 9 |
| `sys_role` | 10 |
| `product` | 3 |
| `colonelsettlement_order` | 8 |
| `sample_request` | 8 |

### 3.5 日志检查

后端日志未显示启动阻塞，业务接口可访问。

当前发现的日志风险：

- `DouyinTalentCrawler` 有外部达人爬取相关 `SSLHandshakeException`
- 该问题属于“测试环境 / Mock 配置风险”
- 当前阶段不做真实达人外部接口联调，因此不作为第 0 阶段阻塞项
- 后续达人模块测试时，需要重点确认页面是否依赖本地数据，而不是依赖该外部爬取任务

### 3.6 第 0 阶段结论

第 0 阶段判定为：通过，但带配置口径风险。

后续本地业务联调入口：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080/api`
- 登录账号：`admin / admin123`
- Mock 数据初始化：`POST /api/test/seed`

### 3.7 当前风险

| 风险 | 类型 | 影响 | 下一步处理 |
| --- | --- | --- | --- |
| 本地默认入口、脚本和文档仍混用 `test` / `dev` / `local-mock` 表述 | 配置问题 | 容易误导接手人与手工启动路径 | 已将默认本地联调入口收口到 `local-mock`，后续继续清理旧文档描述 |
| 达人爬虫仍尝试访问外部接口 | Mock 数据问题 / 测试环境问题 | 可能污染达人模块日志判断 | 达人阶段重点核对页面数据来源与降级展示 |
| 工作区存在大量未提交改动 | 测试环境问题 | 当前结果基于未提交工作区，不是干净基线 | 后续每次记录都保留验证命令与时间 |

## 4. 下一阶段入口

下一阶段为：

> 第 1 阶段：登录与基础页面可达性

测试页面：

- `/login`
- `/product`
- `/product/activity`
- `/orders`
- `/talent`
- `/sample`
- `/system/users`
- `/system/roles`

每个页面需要记录：

- 是否能打开
- 是否报错
- 是否空白
- 是否需要重新登录
- 控制台是否有明显错误
- 后端日志是否有异常

## 5. 第 1 阶段：登录与基础页面可达性

### 5.1 测试目的

确认用户可以进入系统，主要业务页面可以打开，且不会出现：

- 白屏
- 强制回到登录页
- 前端控制台错误
- 后端业务异常

### 5.2 操作入口

- 前端入口：`http://localhost:3000`
- 登录页：`http://localhost:3000/login`
- 登录账号：`admin / admin123`

### 5.3 登录验证

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 登录页打开 | 页面显示“欢迎回来”与用户名、密码输入框 | 通过 |
| 登录接口 | `POST /api/auth/login` 返回 `code=200`、JWT、`admin` 角色 | 通过 |
| 登录后权限 | 后续业务页面可访问，页面顶部显示“系统管理员” | 通过 |
| 控制台错误 | 未发现明显 error | 通过 |

### 5.4 页面可达性结果

| 页面 | 业务作用 | 是否能打开 | 是否空白 | 是否掉登录 | 控制台错误 | 后端异常 | 判断 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `/login` | 用户登录入口 | 能打开 | 否 | 不适用 | 无 | 无 | 通过 |
| `/product` | 商品库主页面，业务链路起点 | 能打开，显示“商品库” | 否 | 否 | 无 | 无 | 通过 |
| `/product/activity` | 团长活动 / 活动商品入口 | 能打开，显示“团长活动” | 否 | 否 | 无 | 无 | 通过 |
| `/orders` | 订单归因工作台 | 能打开，显示“订单归因” | 否 | 否 | 无 | 无 | 通过 |
| `/talent` | 达人 CRM | 能打开，显示“达人 CRM” | 否 | 否 | 无 | 无 | 通过 |
| `/sample` | 寄样台 | 能打开，显示“寄样台”和寄样列表区域 | 否 | 否 | 无 | 无业务异常 | 通过，附观察项 |
| `/system/users` | 用户管理 | 能打开，显示用户管理筛选与列表区域 | 否 | 否 | 无 | 无 | 通过 |
| `/system/roles` | 角色管理 | 能打开，显示“角色管理” | 否 | 否 | 无 | 无 | 通过 |

### 5.5 `/sample` 观察项

首次浏览器 DOM 快照工具对 `/sample` 返回空字符串，但可见截图和页面文本均显示：

- 顶部标题为“寄样台”
- 说明文案为“跟踪寄样申请、审核、发货、签收和交作业完成状态。”
- 存在“刷新列表”“搜索”控件
- 表格区域已渲染
- 后端日志出现 `sample_request` 查询 SQL 和返回行

因此本阶段不把 `/sample` 判定为白屏。

观察项：

- 寄样台页面在当前浏览器视口下存在横向滚动和表格列挤压现象
- 后续第 5 阶段做寄样闭环时，需要重点检查列表字段是否完整可读

### 5.6 后端日志核对

本阶段页面访问期间未发现主要页面对应的 `401`、`403`、`500` 或 Controller 业务异常。

仍存在独立后台任务日志：

- `DouyinTalentCrawler` 周期性出现 `SSLHandshakeException`
- 该日志与当前页面可达性无直接关系
- 当前阶段仍按 Mock / 本地数据联调，不进入真实达人接口

### 5.7 第 1 阶段结论

第 1 阶段判定为：通过。

下一阶段：

> 第 2 阶段：系统管理模块

下一步将验证：

- 用户列表加载
- 用户搜索
- 用户新增
- 用户编辑
- 用户删除
- 用户重置密码
- 角色列表加载
- 角色新增
- 角色编辑
- 角色删除

注意：删除用户、删除角色属于本地数据删除动作，执行到删除步骤前需要单独确认。

## 6. 第 2 阶段：系统管理模块

### 6.1 测试目的

确认系统基础权限和人员管理可用，包括：

- 用户列表
- 用户搜索
- 用户新增
- 用户编辑
- 用户重置密码
- 角色列表
- 角色新增
- 角色编辑
- 数据库真实变化

删除用户、删除角色属于本地数据删除动作，尚未执行。

### 6.2 页面入口

- 用户管理：`/system/users`
- 角色管理：`/system/roles`

### 6.3 页面初步检查

| 页面 | 实际结果 | 判断 |
| --- | --- | --- |
| `/system/users` | 页面可打开，能看到用户筛选、用户列表、新增用户入口 | 通过，附观察项 |
| `/system/roles` | 页面可打开，能看到角色管理、角色列表、新增角色入口 | 通过，附观察项 |

观察项：

- 当前浏览器视口下，系统管理表格列被压缩，文字纵向换行严重
- 这属于“前端布局 / 视口适配问题”
- 为避免误点，本阶段新增、编辑、重置密码先使用页面对应的内部 API 验证业务闭环，并用数据库核对真实落库

### 6.4 用户与角色列表

| 动作 | 操作入口 | 实际结果 | 判断 |
| --- | --- | --- | --- |
| 用户列表加载 | `GET /api/users?page=1&size=10` | 返回 `code=200`，总数为 `8` | 通过 |
| 角色列表加载 | `GET /api/roles?page=1&size=10` | 返回 `code=200`，总数为 `9` | 通过 |

### 6.5 角色新增与编辑

测试角色：

- 角色编码：`qa_local_role_0429192303`
- 初始角色名：`本地联调角色0429192303`
- 编辑后角色名：`本地联调角色0429192303-已编辑`

| 动作 | 实际结果 | 数据库核对 | 判断 |
| --- | --- | --- | --- |
| 新增角色 | `POST /api/roles` 返回 `code=200` | `sys_role` 存在 `qa_local_role_0429192303` | 通过 |
| 编辑角色 | `PUT /api/roles/{id}` 返回 `code=200` | `role_name=本地联调角色0429192303-已编辑`，`data_scope=3`，`remark=local mock integration role edited` | 通过 |

补充记录：

- 第一次角色编辑请求未带 `roleCode`，不满足 `SysRoleUpdateRequest` 要求
- 重试时补齐 `roleCode` 后编辑成功
- 该点属于“测试请求数据问题”，不是后端缺陷

### 6.6 用户新增、搜索、编辑与重置密码

测试用户：

- 用户名：`qa_local_user_0429192303`
- 初始密码：`LocalMock@123`
- 重置后密码：`LocalMock@456`
- 初始姓名：`本地联调用户0429192303`
- 编辑后姓名：`本地联调用户0429192303-已编辑`

| 动作 | 实际结果 | 数据库 / 业务核对 | 判断 |
| --- | --- | --- | --- |
| 新增用户 | `POST /api/users` 返回 `code=200` | `sys_user` 存在该用户，`deleted=0` | 通过 |
| 用户搜索 | `GET /api/users?keyword=qa_local_user_0429192303` 返回 `total=1` | 能精确查到新增用户 | 通过 |
| 编辑用户 | `PUT /api/users/{id}` 返回编辑后姓名 | 数据库姓名、手机、邮箱更新成功 | 通过 |
| 重置密码 | `PUT /api/users/{id}/password` 返回 `code=200` | 使用新密码 `LocalMock@456` 登录成功 | 通过 |

数据库核对结果：

```text
qa_local_user_0429192303|本地联调用户0429192303-已编辑|13900000002|qa_local_user_0429192303.edit@example.com|1|0
qa_local_role_0429192303|本地联调角色0429192303-已编辑|3|1|local mock integration role edited|0
```

### 6.7 后端日志核对

本阶段系统管理接口未发现 `401`、`403`、`500` 或系统管理 Controller 业务异常。

仍存在独立后台任务：

- `DouyinTalentCrawler` 外部 SSL 握手失败日志

该日志与系统用户 / 角色管理无直接关系，不阻塞第 2 阶段。

### 6.8 当前未执行项

以下动作尚未执行：

- 用户删除
- 角色删除

原因：

- 删除本地数据属于需要执行前确认的动作
- 待确认后再使用刚创建的 `qa_local_user_0429192303` 与 `qa_local_role_0429192303` 作为删除验证对象

### 6.9 菜单高亮问题修复记录

问题：

- 访问 `/system/roles` 时，主内容显示“角色管理”，但左侧菜单高亮停留在“用户管理”

问题分类：

- 前端问题
- 具体为侧边栏菜单 active key 与当前路由不同步

原因：

- `frontend/src/views/layout/Sider.vue` 中 `activeMenuKey` 将所有 `/system/*` 路由都映射为 `/system/users`

修复：

- `/system/roles` 映射为 `/system/roles`
- `/system/users` 映射为 `/system/users`
- 同时补正 `/ops/shipping` 与 `/ops/exclusive` 的子路由映射，避免同类问题

验证：

- `npm run build` 通过
- 浏览器访问 `/system/roles` 后，左侧高亮显示在“角色管理”
- 控制台无明显错误

## 7. 第 3 阶段：商品库与活动商品模块

### 7.1 测试目的

商品库是当前本地业务闭环的起点。本阶段验证：

- 活动商品列表能从本地 Mock 数据加载
- 商品详情可打开
- 商品状态、审核状态、负责人、转链状态能展示
- 商品审核、分配招商、Mock 转链可跑通
- 操作结果能写入数据库
- 操作日志可追溯

### 7.2 操作入口

- 商品库页面：`/product`
- 活动商品页面：`/product/activity`
- 主链路接口：`/api/colonel/activities/{activityId}/products`

本阶段使用活动：

- `activityId=100018`
- 活动名：`新品招商-渠道演示活动-18`

### 7.3 页面与接口核对

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 活动列表 | `GET /api/colonel/activities?page=1&pageSize=5` 返回 `code=200`，`total=36` | 通过 |
| 活动商品列表 | `GET /api/colonel/activities/100018/products?count=20&retrieveMode=1` 返回 `code=200`，`total=20` | 通过 |
| 商品详情 | `GET /api/colonel/activities/100018/products/10901823` 返回详情字段与转链状态 | 通过 |
| 页面构建 | `npm run build` 通过 | 通过，附构建警告 |

构建观察项：

- Vite 提示主 chunk 超过 `500 kB`
- 这是构建性能 / 分包优化提示，不影响本地业务联调

### 7.4 商品业务动作验证

测试商品一：

- `productId=10901824`
- 初始状态：`PENDING_AUDIT / 待审核`

| 动作 | 接口 | 实际结果 | 判断 |
| --- | --- | --- | --- |
| 商品审核 | `PUT /api/colonel/activities/100018/products/10901824/audit-result` | 返回 `code=200`，业务状态变为 `APPROVED` | 通过 |
| 分配招商 | `PUT /api/colonel/activities/100018/products/10901824/assignee` | 返回 `code=200`，负责人为 `招商专员测试 (biz_staff)` | 通过 |
| Mock 转链 | `POST /api/colonel/activities/100018/products/10901824/promotion-links` | 生成 Mock 推广链，商品状态变为 `LINKED / 已转链` | 通过 |
| 操作日志 | `GET /api/colonel/activities/100018/products/10901824/operation-logs` | 返回 `total=4`，包含同步、审核、分配、转链记录 | 通过 |

测试商品二：

- `productId=10901823`
- 初始状态：`PENDING_AUDIT / 待审核`
- 使用 `channel_staff` 角色执行 Mock 转链

| 动作 | 实际结果 | 判断 |
| --- | --- | --- |
| 审核通过 | 返回 `code=200` | 通过 |
| 分配招商 | 返回 `code=200` | 通过 |
| 渠道角色 Mock 转链 | 返回 `code=200`，详情中 `promotion.status=READY`、`copyEnabled=true` | 通过 |

### 7.5 数据库核对

商品 `10901823` 数据库落点：

| 表 | 核对结果 | 判断 |
| --- | --- | --- |
| `product_operation_log` | 存在 `AUDIT`、`ASSIGN`、`PROMOTION_LINK`，状态流转为 `PENDING_AUDIT -> APPROVED -> ASSIGNED -> LINKED` | 通过 |
| `promotion_link` | 存在 `channel_user_name=渠道专员测试`、`pick_source=MOCK207112`、短链和推广链均非空 | 通过 |
| `product_operation_state` | `biz_status=LINKED`，存在 Mock 推广链和短链 | 通过 |
| `pick_source_mapping` | 写入 `pick_source=MOCK207112`，关联商品、活动和渠道用户 | 通过 |

商品 `10901824` 数据库落点：

| 表 | 核对结果 | 判断 |
| --- | --- | --- |
| `product_operation_log` | 存在 `AUDIT`、`ASSIGN`、`PROMOTION_LINK` 操作记录 | 通过 |
| `promotion_link` | 存在 Mock 推广链、短链与 `pick_source=MOCK150416` | 通过 |

### 7.6 后端日志核对

本阶段商品主链路接口未发现 `500` 异常。

日志能看到：

- 商品详情查询
- 操作状态查询
- Mock 转链写入 `pick_source_mapping`
- Mock 转链写入 `promotion_link`
- 操作日志写入 `product_operation_log`

仍存在独立后台任务 `DouyinTalentCrawler` 外部 SSL 握手失败日志。当前阶段不接真实三方接口，不作为商品阶段阻塞项。

### 7.7 权限口径待确认

发现一项待确认：

- 后端 `ColonelActivityProductController` 的转链方法标注了渠道角色限制
- 但本地实际测试中，`admin` 角色也能调用转链接口成功
- 这不阻塞本地 Mock 商品链路，但属于“权限口径 / 后端权限拦截规则待确认”

建议后续单独确认：

- `admin` 是否应拥有所有业务动作权限
- 方法级 `@RequireRoles` 是否应该覆盖类级角色，还是与类级角色合并
- 前端按钮展示权限是否需要与后端一致

### 7.8 第 3 阶段结论

第 3 阶段判定为：通过，附权限口径待确认。

已验证本地商品主链路：

```text
活动商品列表
-> 商品详情
-> 商品审核
-> 分配招商
-> Mock 转链
-> 前端详情可展示转链结果
-> 数据库写入推广链、pickSource、操作日志
```

下一阶段：

> 第 4 阶段：订单模块

## 8. 第 4 阶段：订单模块

### 8.1 测试目的

订单模块用于验证 Mock 订单回流后，系统是否能支撑：

- 订单列表展示
- 订单统计展示
- 已归因订单展示
- 未归因订单展示
- 未归因原因展示
- 商品详情 / 看板跳转订单时按商品筛选
- 订单详情诊断

当前阶段订单只使用本地 Mock 数据，不调用真实抖店订单接口。

### 8.2 操作入口

- 订单页面：`/orders`
- 商品筛选入口：`/orders?productId=10901827`
- 订单列表接口：`/api/orders`
- 订单统计接口：`/api/orders/stats`
- 未归因接口：`/api/orders/unattributed`
- 订单详情接口：`/api/orders/{orderId}`

### 8.3 接口验证结果

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 订单列表 | `GET /api/orders?page=1&pageSize=10` 返回 `code=200`，`total=8` | 通过 |
| 订单统计 | `GET /api/orders/stats` 返回 `totalOrders=8` | 通过 |
| 已归因筛选 | `attributionStatus=ATTRIBUTED` 返回 `total=4` | 通过 |
| 未归因筛选 | `attributionStatus=UNATTRIBUTED` 返回 `total=4` | 通过 |
| 未归因专用接口 | `GET /api/orders/unattributed` 返回 `total=4` | 通过 |
| 商品筛选 | `productId=10901827` 返回 `total=2` | 通过 |
| 筛选项 | `GET /api/orders/filter-options` 返回商品选项与未归因原因选项 | 通过 |
| 订单详情 | `GET /api/orders/MOCK_ORD_ATTR_MOCK681039` 返回详情，`promotion.matched=true`、`sample.matched=true` | 通过 |

### 8.4 数据库核对

订单表：`colonelsettlement_order`

| 归因状态 | 原因 | 数量 |
| --- | --- | ---: |
| `ATTRIBUTED` | `ATTRIBUTED` | 4 |
| `UNATTRIBUTED` | `MAPPING_NOT_FOUND` | 2 |
| `UNATTRIBUTED` | `NO_PICK_SOURCE` | 2 |

商品维度：

| 商品 ID | 订单数 |
| --- | ---: |
| `10901825` | 5 |
| `10901826` | 1 |
| `10901827` | 2 |

### 8.5 未归因展示口径

当前 Mock 未归因原因包括：

- `NO_PICK_SOURCE`：订单未携带推广参数
- `MAPPING_NOT_FOUND`：推广参数未匹配到有效归因映射

判断依据：

- 统计接口 `unattributedOrders=4`
- 数据库中未归因原因分布与接口返回一致

### 8.6 页面与前端联调观察项

发现两个前后端契约问题，均属于“前端 / 接口契约问题”，不影响后端订单数据正确性：

| 问题 | 现象 | 影响 | 建议 |
| --- | --- | --- | --- |
| 同步按钮请求体不匹配 | 前端 `同步最新订单` 调用 `POST /orders/sync` 时未提交时间范围，后端要求 JSON body | 页面同步按钮会失败，并在后端日志出现 `Content-Type 'application/x-www-form-urlencoded' is not supported` | 前端应改为调用 `syncOrders(startTime,endTime)`，传最近 30 天时间范围；或后端支持空 body 默认时间窗 |
| 订单号筛选未生效 | 前端发送 `orderId` 参数，但后端 `/orders` 当前未接收 `orderId` | 输入订单 ID 查询仍返回全部订单 | 后端补充 `orderId` 参数，或前端暂时隐藏订单 ID 筛选 |

待确认项：

- 前端分页参数使用 `size`，后端接口文档使用 `pageSize`。当前 Mock 数据仅 8 条，未能充分暴露分页差异，后续数据量超过 20 条时需要专项验证。

### 8.7 后端日志核对

订单列表、统计、详情接口未发现数据查询异常。

点击同步按钮对应的问题在后端日志中表现为：

- `HttpMediaTypeNotSupportedException`
- 原因：请求未按后端 `@RequestBody SyncRequest` 要求提交 JSON

### 8.8 第 4 阶段结论

第 4 阶段判定为：核心数据链路通过，前端同步按钮与订单号筛选存在待修复问题。

已验证本地订单链路：

```text
Mock 订单数据
-> 订单列表
-> 归因统计
-> 已归因 / 未归因筛选
-> 未归因原因展示
-> 商品 ID 筛选
-> 订单详情诊断
```

下一阶段：

> 第 5 阶段：寄样模块

## 9. 第 5 阶段：寄样模块

### 9.1 测试目的

寄样模块用于验证达人样品从申请到完成的业务闭环。本阶段验证：

- 新建寄样申请
- 待审核进入待发货
- Mock 发货
- Mock 签收
- 进入待交作业
- 手动完成
- Mock 订单归因驱动自动完成
- 状态日志与数据库一致

### 9.2 操作入口

- 页面入口：`/sample`
- 寄样列表：`GET /api/samples`
- 新建寄样：`POST /api/samples`
- 状态流转：`PUT /api/samples/{id}/status`
- Mock 发货：`POST /api/test/logistics/ship/{sampleRequestId}`
- Mock 签收：`POST /api/test/logistics/sign/{sampleRequestId}`
- Mock 已归因订单：`POST /api/test/orders/generate-attributed`

### 9.3 候选数据验证

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 商品候选 | `GET /api/samples/product-candidates` 返回 `total=3` | 通过 |
| 达人候选 | `GET /api/samples/talent-candidates` 返回 `total=7` | 通过 |
| 管理员寄样列表 | `GET /api/samples?page=1&size=20` 返回 `total=8` | 通过 |

观察项：

- `channel_staff` 登录后寄样列表为 `0`，与数据权限有关；管理员可看到标准 Mock 寄样数据。

### 9.4 新建寄样完整状态流转

本次新建寄样：

- 寄样单号：`SM2026042930509B39`
- 商品：`10901826`
- 达人：`talent_test_g`
- 执行用户：`channel_staff`

| 步骤 | 操作 | 实际结果 | 判断 |
| --- | --- | --- | --- |
| 新建申请 | `POST /api/samples` | 返回 `PENDING_AUDIT` | 通过 |
| 审核通过 | `PUT /api/samples/{id}/status`，`action=PENDING_SHIP` | 返回 `PENDING_SHIP` | 通过 |
| Mock 发货 | `POST /api/test/logistics/ship/{id}` | 返回状态码 `3`，生成物流单号 | 通过 |
| Mock 签收 | `POST /api/test/logistics/sign/{id}` | 返回状态码 `5`，进入待交作业 | 通过 |
| 完成 | `PUT /api/samples/{id}/status`，`action=COMPLETED` | 返回 `FINISHED` | 通过 |
| 刷新详情 | `GET /api/samples/{id}` | 最终状态 `FINISHED`，存在物流单号与完成时间 | 通过 |

### 9.5 Mock 订单归因驱动自动完成

使用标准 Mock 待交作业寄样单：

- 寄样单号：`TEST-SAMPLE-001`
- 商品：`10901825`
- 达人：`talent_test_a`
- 初始状态：`PENDING_TASK`

触发：

- `POST /api/test/orders/generate-attributed`

结果：

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| Mock 订单生成 | 返回订单号 `MOCK_GEN_ATTR_1777465486371` | 通过 |
| 订单归因 | 返回 `ATTRIBUTED` | 通过 |
| 寄样自动完成 | 返回 `sampleStatus=6`，刷新后寄样状态为 `FINISHED` | 通过 |
| 完成时间 | 寄样详情存在 `completeTime` | 通过 |

### 9.6 数据库核对

寄样主表：`sample_request`

| 寄样单号 | 达人 | 状态 | 物流单号 | 完成时间 | 判断 |
| --- | --- | ---: | --- | --- | --- |
| `SM2026042930509B39` | `talent_test_g` | `6` | 非空 | 非空 | 通过 |
| `TEST-SAMPLE-001` | `talent_test_a` | `6` | 非空 | 非空 | 通过 |

寄样状态日志：`sample_status_log`

新建寄样 `SM2026042930509B39` 状态日志：

```text
null -> 1  create sample request
1 -> 2     Local mock 审核通过
2 -> 3     test logistics ship
3 -> 4     test logistics delivered
4 -> 5     test logistics sign -> pending homework
5 -> 6     Local mock 手动完成验证
```

订单驱动完成日志：

```text
TEST-SAMPLE-001: 5 -> 6 auto complete by order: MOCK_GEN_ATTR_1777465486371
```

### 9.7 观察项

发现一个数据一致性观察项：

- `TEST-SAMPLE-001` 在触发本次订单自动完成前，数据库中状态为 `5`，但 `complete_time` 已非空
- 触发 Mock 已归因订单后，状态变为 `6`
- 这说明标准 Mock 数据里该样例曾存在“待交作业状态但已有完成时间”的不一致

分类：

- Mock 数据问题

影响：

- 不阻塞自动完成链路验证
- 但会影响后续用 `complete_time` 判断是否已完成的统计口径

### 9.8 后端日志核对

本阶段未发现寄样状态流转接口 `500` 异常。

日志能看到：

- 寄样创建
- 状态流转
- Mock 物流发货
- Mock 物流签收
- 订单归因后触发 `SampleLifecycleService.completePendingHomeworkByOrder`

### 9.9 第 5 阶段结论

第 5 阶段判定为：通过，附 Mock 数据一致性观察项。

已验证寄样闭环：

```text
新建寄样申请
-> 待审核
-> 审核通过
-> 待发货
-> Mock 发货
-> 已发货
-> Mock 签收
-> 待交作业
-> 手动完成 / Mock 订单归因自动完成
-> 已完成
```

下一阶段：

> 第 6 阶段：达人模块

## 10. 第 6 阶段：达人模块

### 10.1 测试目的

达人模块在本地业务闭环中的作用是支撑：

- 商品跟进对象理解
- 寄样申请对象理解
- 订单归因中的达人维度解释
- 公海 / 私海归属判断
- 达人详情中关联寄样和订单记录

当前阶段不做真实达人授权，不触发真实达人资料刷新，只验证本地 Mock / 本地库展示。

### 10.2 操作入口

- 页面入口：`/talent`
- 达人列表：`GET /api/talents`
- 达人详情：`GET /api/talents/{id}`
- 公海筛选：`poolStatus=PUBLIC`
- 私海筛选：`poolStatus=PRIVATE`

### 10.3 接口验证结果

管理员视角：

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 达人列表 | `GET /api/talents?page=1&size=20` 返回 `total=7` | 通过 |
| 公海筛选 | `poolStatus=PUBLIC` 返回 `total=5` | 通过 |
| 私海筛选 | `poolStatus=PRIVATE` 返回 `total=2` | 通过 |
| 关键字搜索 | `keyword=达人A` 返回 `total=1` | 通过 |
| 达人详情 | `talent_test_a` 详情返回达人基础信息、寄样记录、订单记录 | 通过 |

渠道专员视角：

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 达人列表 | `channel_staff` 视角返回 `total=1` | 通过 |
| 私海数据 | 返回达人 `达人C-他人已认领`，`poolStatus=PRIVATE` | 通过 |
| 详情关联 | 详情中存在寄样记录与订单记录 | 通过 |

### 10.4 达人详情关联验证

目标达人：

- 昵称：`达人A-寄样待交作业`
- UID：`talent_test_a`

验证结果：

| 字段 | 实际结果 | 判断 |
| --- | --- | --- |
| 寄样数 | 列表 `sampleCount=1`，详情 `samples=1` | 通过 |
| 订单数 | 列表 `orderCount=2`，详情 `orders=2` | 通过 |
| 服务费贡献 | `serviceFeeContribution=5200` | 通过 |
| 池状态 | `PUBLIC` | 通过 |

### 10.5 数据库核对

| 表 / 维度 | 实际结果 | 判断 |
| --- | --- | --- |
| `talent` | `deleted=0` 的达人共 `7` 个 | 通过 |
| `talent_claim` | 活跃认领 `2` 条，过期认领 `1` 条 | 通过 |
| `colonelsettlement_order` 达人维度 | `talent_test_a/b/c/d` 均存在订单记录 | 通过 |
| `sample_request` 达人维度 | 达人详情能关联寄样记录 | 通过 |

### 10.6 观察项

| 观察项 | 分类 | 说明 |
| --- | --- | --- |
| 后台 `DouyinTalentCrawler` 仍有外部 SSL 握手失败日志 | 测试环境 / Mock 配置问题 | 当前达人页面和接口使用本地数据可通过，不阻塞本地展示联调 |
| `TalentController` 类级角色只声明渠道角色，但本地 admin 可访问 | 权限口径待确认 | 与商品转链 admin 权限现象类似，建议后续统一确认权限注解语义 |

### 10.7 第 6 阶段结论

第 6 阶段判定为：通过，附权限口径与后台爬虫配置观察项。

已验证达人支撑关系：

```text
达人列表
-> 公海 / 私海归属
-> 搜索筛选
-> 达人详情
-> 关联寄样
-> 关联订单
-> 服务费 / 订单表现展示
```

下一阶段：

> 第 7 阶段：整体业务闭环复盘

## 11. 第 7 阶段：整体业务闭环复盘

### 11.1 已验证本地业务闭环

```text
登录系统
-> 商品进入商品库
-> 商品审核
-> 商品分配招商
-> Mock 转链
-> 前端详情展示转链结果
-> Mock 订单回流
-> 订单归因
-> 寄样申请
-> 寄样审核
-> Mock 发货
-> Mock 签收
-> 待交作业
-> Mock 订单驱动自动完成
-> 达人详情 / 订单列表 / 寄样列表展示业务结果
```

### 11.2 当前通过项

| 阶段 | 结论 |
| --- | --- |
| 第 0 阶段：环境确认 | 通过，附 profile 口径风险 |
| 第 1 阶段：登录与页面可达性 | 通过 |
| 第 2 阶段：系统管理 | 通过，用户 / 角色删除补测已完成 |
| 第 3 阶段：商品库与活动商品 | 通过，附权限口径待确认 |
| 第 4 阶段：订单模块 | 通过，订单同步按钮与订单号筛选已回归 |
| 第 5 阶段：寄样模块 | 通过，附 Mock 数据一致性观察项 |
| 第 6 阶段：达人模块 | 通过，附权限口径与爬虫配置观察项 |

### 11.3 当前问题清单

| 问题 | 分类 | 优先级 | 建议处理 |
| --- | --- | --- | --- |
| 商品转链和达人接口 admin 权限实际可用，但代码注解看似限制角色 | 权限口径待确认 | P2 | 统一确认 `admin` 是否天然超级权限，以及方法级注解是否覆盖类级注解 |
| 仍有旧文档残留 `dev` / `test` 口径 | 配置问题 | P2 | 继续清理历史描述，避免回到双重真相 |
| `DouyinTalentCrawler` 仍尝试外部访问并报 SSL | 测试环境 / Mock 配置问题 | P2 | 本地 Mock 环境关闭外部爬虫或替换为 Mock 数据刷新 |
| `TEST-SAMPLE-001` 曾出现状态 `5` 但 `complete_time` 非空 | Mock 数据问题 | P2 | 修正 seed 数据，避免统计口径混乱 |

### 11.4 下一步建议

建议下一轮按以下顺序处理：

1. 统一本地 Mock profile 与文档口径。
2. 关闭本地 Mock 环境中的真实达人爬虫外部访问。
3. 清理 Mock seed 中寄样状态与完成时间不一致的问题。
4. 统一确认 `admin` 超级权限与方法级 / 类级角色注解口径。
5. 对照真实 SDK 联调准备清单进入下一阶段准备。

## 12. 第 4 阶段问题修复回归

更新时间：2026-04-29

### 12.1 修复项

本轮已修复两个订单页问题：

1. 订单页“同步最新订单”按钮
2. 订单页订单号筛选

### 12.2 实际修改

前端：

- [frontend/src/views/orders/index.vue](../../../frontend/src/views/orders/index.vue)
  - 同步按钮改为调用 `syncOrders(startTime, endTime)`
  - 默认同步最近 30 天
  - 若页面已选择日期范围，则优先使用页面日期范围
  - 列表查询参数从 `size` 调整为 `pageSize`
  - 列表查询补充时间范围参数格式化

后端：

- [backend/src/main/java/com/colonel/saas/controller/OrderController.java](../../../backend/src/main/java/com/colonel/saas/controller/OrderController.java)
  - `/orders` 新增 `orderId` 查询条件
  - `/orders/unattributed` 同步支持 `orderId`
  - `/orders/sync` 支持空 JSON body 时使用默认最近 30 天时间窗

测试：

- [backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java](../../../backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java)
  - 补充同步时间范围测试
  - 补充默认时间窗测试

### 12.3 回归结果

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 前端构建 | `npm run build` 通过 | 通过 |
| 后端测试 | `mvn -Dtest=OrderControllerTest test` 通过，`3/3` 成功 | 通过 |
| 订单号筛选 | `GET /api/orders?page=1&pageSize=10&orderId=MOCK_ORD_ATTR_MOCK681039` 返回 `total=1` | 通过 |
| 同步接口（前端同口径 JSON） | `POST /api/orders/sync` 携带 JSON 时间范围返回 `code=200` | 通过 |

### 12.4 剩余说明

虽然控制器已支持“空 JSON body”默认时间窗，但直接发送一个完全空的 `POST /api/orders/sync` 时，当前运行环境仍可能以 `application/x-www-form-urlencoded` 进入 Spring 并触发 `415/500` 风格异常。

因此当前结论是：

- 页面按钮已修复，可正常使用
- 后端对“空 JSON body”已具备兜底
- 若要让“完全无 body 且无 content-type 的裸请求”也稳定成功，还需要再做一轮接口兼容性处理

## 13. P0 本地收口最终结论

更新时间：2026-04-30

### 13.1 本轮补充验证

| 检查项 | 实际结果 | 判断 |
| --- | --- | --- |
| 订单同步按钮页面路径 | `/orders` 点击“同步最新订单”后出现同步提示，列表保持稳定，`排查摘要` 列正常展示 | 通过 |
| 商品卡片 hover 展开 | `/product` hover 第一张商品卡片后，类名进入 `expanded`，宽度约从 `364px` 扩到 `743px`，业务快照在同卡片内展示 | 通过 |
| 用户删除补测 | 创建临时用户 `p0_delete_user_20260430164345` 后删除，再按用户名查询结果为 `0` | 通过 |
| 角色删除补测 | 创建临时角色 `p0_delete_role_20260430164345` 后删除，再按角色编码查询结果为 `0` | 通过 |

### 13.2 当前最终通过项

本地 Mock 阶段已验证通过：

- 环境可启动、登录可用、核心页面可访问
- 商品审核、分配、Mock 转链和推广映射写入
- Mock 订单回流、已归因 / 未归因展示、订单详情排查
- 寄样申请、审核、发货、签收、待交作业和订单驱动自动完成
- 达人列表、详情、公海 / 私海、寄样与订单关联
- 系统用户和角色的新增、编辑、删除、重置密码相关本地链路
- 商品推进判断、推进时间线、风险提示和列表筛选等本地业务可读性增强
- 订单列表排查摘要和订单详情四段式排查路径

### 13.3 当前观察项

| 观察项 | 分类 | 下一步 |
| --- | --- | --- |
| `test` / `dev` / `local-mock` 的历史表述仍需继续收口 | 环境口径 | 继续推进 P1 环境治理 |
| `DouyinTalentCrawler` 在本地 Mock 阶段仍可能产生外部 SSL 日志 | 测试环境噪音 | 进入 P1 日志与外部依赖治理 |
| `TEST-SAMPLE-001` 等 seed 数据仍建议继续核对状态与完成时间一致性 | Mock 数据质量 | 进入 P1 Mock 数据治理 |
| `admin` 实际具备超级权限，但部分类 / 方法注解语义仍需统一说明 | 权限口径 | 进入 P2 权限与契约统一 |
| 真实 SDK token、限流、空数据、错误码尚未验证 | 真实联调风险 | 进入 P3 真实 SDK 联调准备 |

### 13.4 本地 Mock 收口结论

当前 P0 本地收口判定为：通过。

更准确的阶段表述为：

> 本地 Mock 核心业务闭环已完成并完成 P0 收口；下一阶段应进入 P1 环境与数据治理、P2 权限与契约统一，以及 P3 真实 SDK 联调准备。

## 14. P1 环境与数据治理记录

更新时间：2026-04-30

### 14.1 P1-1 Profile 口径统一审计

#### 14.1.1 审计范围

本轮只做环境口径审计和文档收口，不开发新业务功能，不接真实抖店 API，不新增文档。

已检查范围：

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-test.yml`
- `docker-compose.yml`
- `docker-compose.test.yml`
- `frontend/.env.development`
- 根目录 `.env`
- 根目录 `.env.test`
- 抖音 Gateway 条件装配与达人爬虫相关代码

#### 14.1.2 当前配置事实

| 项目 | 当前事实 | 判断 |
| --- | --- | --- |
| 默认后端 profile | `application.yml` 默认 `SPRING_PROFILES_ACTIVE=local-mock` | 与本地 Mock 标准口径一致 |
| 本地 Docker Compose | `docker-compose.yml` 固定 `SPRING_PROFILES_ACTIVE: local-mock`，同时打开多项 `APP_TEST_*` Mock 能力 | 作为本地人工联调标准入口 |
| Test Compose | `docker-compose.test.yml` 使用 `SPRING_PROFILES_ACTIVE: test`、`DB_NAME=colonel_saas_test`、`REDIS_DATABASE=1` | 更接近自动化测试 / 隔离测试环境 |
| local-mock profile 文件 | 已存在 `application-local-mock.yml` | 本地业务联调已有独立 profile 承载 |
| 前端本地环境 | `frontend/.env.development` 仅保留 `VITE_ENV_LABEL=本地 Mock`，请求统一走 `/api/**` 并通过 Vite 代理转发到后端 | 前端 API 口径已与本地 Mock 标准入口一致 |
| Douyin Gateway | `douyin.test.enabled=true` 时启用 Test Gateway，Real Gateway 被排除 | Test Gateway 开关清晰 |
| 根目录 `.env` | 默认切到 `SPRING_PROFILES_ACTIVE=local-mock` | 与本地人工联调入口一致 |
| 根目录 `.env.test` | 使用 `SPRING_PROFILES_ACTIVE=test`、`DOUYIN_MOCK_ENABLED=true`、`TALENT_ENRICH_MODE=mock` | Mock-first 口径较清晰，但命名更适合自动化测试 |
| 达人爬虫 | `CrawlerScheduler` / `DouyinTalentCrawler` 当前没有按 local-mock profile 禁用的独立开关 | 本地 Mock 阶段仍可能产生外部请求噪音 |

#### 14.1.3 建议环境边界

| 环境 | 标准用途 | 是否连真实三方 | 是否自动造数据 | 标准场景 |
| --- | --- | ---: | ---: | --- |
| `test` | 自动化测试 / CI / 后端测试 | 否 | 是 | `mvn test`、接口自动化测试 |
| `local-mock` | 本地完整业务联调 | 否 | 是 | 前后端联调、演示前自测、P0/P1 回归 |
| `dev` | 开发调试 / 未来真实三方开发准备 | 可选 | 否或手动 | 真实配置排查、SDK 联调前开发调试 |

本项目后续建议统一采用：

> 本地业务联调以 `local-mock` 为标准；`test` 只服务自动化测试；`dev` 才允许未来真实三方配置。

#### 14.1.4 P1-1 审计结论

当前环境口径冲突的默认入口已完成首轮收口：

1. 本地联调默认入口固定为 `docker-compose.yml -> local-mock`。
2. `application.yml` 默认兜底已切到 `local-mock`。
3. `.env.test` 与 `docker-compose.test.yml` 继续只服务自动化测试 / 隔离测试环境。
4. `application-dev.yml` 保留为兼容 profile，不再作为推荐本地联调入口。
5. 达人爬虫与真实三方噪音仍需在 local-mock 下继续降噪。

#### 14.1.5 `/api/test/**` 当前治理口径

当前 `/api/test/**` 的环境边界更新为：

1. `TestController` 仅在 `local-mock` / `test` profile 下加载。
2. `OPTIONS` 预检请求不再要求 `Authorization`。
3. `POST /api/test/**` 继续要求登录态，避免 reset / seed / 造数接口在本地被无边界裸放。

#### 14.1.5 本轮不修改配置文件的原因

本轮已从“仅审计”进入“最小配置收口”。

原因：

- 已具备 `application-local-mock.yml`
- 默认入口已能统一到 `local-mock`
- 后续重点从“是否存在 local-mock”转向“如何继续清理旧文档、旧脚本和日志噪音”

#### 14.1.6 下一步最小配置落地建议

P1-1 下一小步建议：

1. 继续清理文档中残留的 `dev + mock enabled` 旧结论。（2026-05-03 复核：主干文档已完成本轮清理，此处保留为当时待办记录）
2. 保留 `docker-compose.test.yml` 给自动化测试使用。
3. 继续收口前端 API 口径，统一使用 `/api/**`。
4. 在 `local-mock` 下继续治理真实达人爬虫外呼、真实 Douyin SDK 请求和 token 自动刷新噪音。
5. 对当前运行实例做一次重启确认，确保 `/api/test/**` 的 `OPTIONS` 预检放行策略真正生效。

P1-2 再进入 Mock 数据一致性治理。

## 15. P1-2 real-pre 真实链路联调记录

更新时间：2026-05-10

### 15.1 本节定位

本节是 `real-pre` 真实三方链路补记，不改变本文前序 `local-mock` / `test` Mock 联调结论。

本轮执行口径：

- 不删除 `test` 环境容器和 volume
- 不把 `test` 改成真实接口环境
- `real-pre` 使用真实上游链路，`test` 继续保留为 Mock 回归兜底
- 真实接口返回空数据或失败时，如实记录，不用 Mock 数据伪装通过

证据目录：

- `runtime/qa/out/real-pre-p1-2-20260510-020545`
- `runtime/qa/out/real-pre-p1-2-product-chain-20260510-020840`

### 15.2 环境隔离检查

| 检查项 | 操作 | 结果 | 问题 | 下一步 |
| --- | --- | --- | --- | --- |
| profile 隔离 | 核对 `.env.real-pre`、`.env.test` 与运行中后端容器环境变量 | `test=SPRING_PROFILES_ACTIVE=test`；`real-pre=SPRING_PROFILES_ACTIVE=real` | 无 | 后续继续禁止把 `test` 切到真实接口 |
| 数据库隔离 | 分别查询容器当前数据库 | `test=colonel_saas_test`；`real-pre=colonel_saas_real` | 无 | 保持 `5432/5433` 分离 |
| Redis 隔离 | 分别 ping Redis DB | `test` 使用 Redis DB `1`；`real-pre` 使用 Redis DB `0`；均 `PONG` | 无 | Token 与业务缓存继续只写 real-pre Redis DB 0 |
| 抖音配置隔离 | 核对 `DOUYIN_TEST_ENABLED`、`APP_TEST_ENABLED`、`DOUYIN_REAL_UPSTREAM_MODE` | `test` 为 Mock：`DOUYIN_TEST_ENABLED=true`、`APP_TEST_ENABLED=true`；`real-pre` 为真实：`DOUYIN_TEST_ENABLED=false`、`APP_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live` | 无 | 后续真实联调只走 `8081` |
| 启动与健康检查 | `docker compose --env-file .env.real-pre --project-name saas -f docker-compose.real-pre.yml up -d --build backend-real-pre frontend-real-pre`；随后访问 `/api/actuator/health` | real-pre 后端 `UP`，`3001/8081` 拓扑检查通过 | compose 提示存在旧 `saas-*` orphan 容器，但本轮未删除 | 后续如需清理旧残留，单独评估，不在本轮执行 |

### 15.3 模块联调结果

| 模块 | 操作 | 结果 | 问题分类 / 问题 | 下一步 |
| --- | --- | --- | --- | --- |
| Token 获取 / 刷新 | 管理员登录后调用 `GET /api/douyin/tokens`、`POST /api/douyin/token-refreshes`、`GET /api/douyin/institution-info` | Token 状态存在 `access_token / refresh_token`；刷新返回 `code=200`；授权主体上游返回 `10000 / success` | 无 | 继续使用现有 real-pre token；若后续 token 失效，再走授权码初始化 |
| 活动列表 | 调用 `GET /api/douyin/activities` 与 `GET /api/colonel/activities` | 联调接口返回首批 `20` 条、总数 `21`；业务接口同样返回 `20` 条，首个活动 `3916506` | 无 | 后续优先使用活动 `3916506` 继续链路验证 |
| 活动商品 | 调用 `GET /api/douyin/activity-product-list?activityId=3916506&count=20` 与 `GET /api/colonel/activities/3916506/products?count=20&refresh=true` | 联调接口返回 `20` 条真实商品；业务刷新返回 `20` 条，活动商品业务总数 `56` | 无 | 继续保留 `refresh=true` 作为真实样本刷新入口 |
| 商品审核 | 首轮 `biz_staff/admin123` 登录失败；确认用户存在且启用后，用管理员接口重置 `biz_staff` 本地 real-pre 测试账号密码，再以 `biz_staff` 审核商品 `3810699728333702016` | 审核返回 `bizStatus=APPROVED`、`selectedToLibrary=true`、`auditStatus=2` | Token/账号问题：real-pre 本地测试账号密码漂移，已按文档约定修复为 `admin123` | 后续若角色流验收失败，先核账号密码是否漂移 |
| 商品入库 | 审核后调用 `POST /api/colonel/activities/3916506/products/3810699728333702016/library-entry` 做幂等确认 | 返回 `selectedToLibrary=true`，说明审核通过自动入库与显式入库确认均成立 | 无 | 继续以“审核通过自动入库”为主链路口径 |
| 商品分配 | `biz_leader` 查询 `/api/users/assignable?keyword=biz_staff` 后分配给招商专员 | 返回 `bizStatus=ASSIGNED`，负责人为招商专员 | 首轮失败为前序未入库导致，分类为业务前置未满足；账号修复并审核入库后已通过 | 分配前必须先确认 `selectedToLibrary=true` |
| 真实转链 | `channel_staff` 调用 `POST /api/colonel/activities/3916506/products/3810699728333702016/promotion-links` | 返回 `code=200`，`pickSource=v.MxZLIw`、`pickExtra=channel_channelstaff`、`shortId` 非空、`promoteLink` 非空；详情回读为 `bizStatus=LINKED`、`promotionLinkStatus=READY`、`copyEnabled=true` | 无；上游本次未返回短链，不阻塞主推广链接复制 | 继续以 `buyin.instPickSourceConvert -> promoteLink/product_url` 为主转链入口 |
| `pick_source_mapping` | 查询 real-pre 数据库 `pick_source_mapping` 与 `promotion_link` | `3916506 / 3810699728333702016` 已写入 `pick_source_mapping`，同时存在 `promotion_link` 记录；校正证据见 `db-mapping-corrected.json` | 证据脚本首版 SQL 引号写法误报 `0`，已用修正 SQL 复核存在 | 后续脚本化复核需避免 shell/SQL 双层引号误判 |
| 订单同步 | 调用 `POST /api/orders/sync`，时间窗为最近 `7` 天 | 返回 `totalFetched=100 / created=5 / updated=95 / attributed=95 / unattributed=5 / failed=0` | 无同步失败；真实接口不是空数据 | 后续继续观察新增订单的未归因原因分布 |
| 订单归因 | 调用 `GET /api/orders/stats?timeField=createTime` 与 `POST /api/orders/replay-attribution` dry-run | 订单同步后全库 `totalOrders=1802 / attributedOrders=1665 / unattributedOrders=137 / syncFailedOrders=0`；dry-run 扫描 `50` 单，`attributed=0 / unattributed=50`；最终收口复核更新为 `totalOrders=1821 / attributedOrders=1683 / unattributedOrders=138` | 数据为空/映射缺口问题：未归因仍集中在缺少本地活动/商品/mapping，不能判成代码同步失败 | 下一步补齐缺失活动与 `activity_id + product_id` 映射后再重放归因 |
| Dashboard 展示 | 调用 `GET /api/dashboard/metrics?timeField=createTime` 与 `GET /api/dashboard/summary` | 同步后 `todayOrderCount=617`、`todayGmv=12081.57`、`serviceFee=181.55`、`grossProfit=127.07`；最终收口复核为 `todayOrderCount=636`、`todayGmv=12448.27`；Summary 可返回 | 无 | 后续 M1.6 继续做看板真实化字段口径细化 |

### 15.4 本轮结论

P1-2 `real-pre` 真实链路本轮已覆盖 Token、活动、活动商品、商品审核入库、分配、真实转链、`pick_source_mapping`、订单同步、订单归因统计与 Dashboard 展示。

本轮没有把 `test` 改成真实接口环境，也没有删除 `test` 容器或 volume。

剩余问题不是“订单同步入口失败”，而是部分真实订单仍缺本地活动 / 商品 / 映射组合，导致 `COLONEL_MAPPING_NOT_FOUND` 无法通过 dry-run 重算归因。下一步应围绕缺失映射补齐，而不是用 Mock 订单覆盖真实未归因结果。
