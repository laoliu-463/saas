# 前端需求整理

## 一、当前前端状态总览
### 1.1 页面与 API 连接情况
*   **登录页 Login.vue**: /login，✅ 已对接 (JWT 登录，store 存储)
*   **商品库 product/index.vue**: /product，✅ 已对接 (getProductPage)
*   **活动列表 product/ActivityList.vue**: /product/activity，✅ 已对接 (getActivityPage)
*   **达人 CRM talent/index.vue**: /talent，✅ 已对接 (getTalentPublic/Private)
*   **寄样台 sample/index.vue**: /sample，✅ 已对接 (getSamplePage + 7 状态 tab)
*   **数据看板 data/index.vue**: /data，✅ 已对接
*   **订单明细 data/OrderList.vue**: /data/orders，✅ 已对接 (getOrderPage)
*   **用户管理 system/UserList.vue**: /system/users，✅ 已对接 (增删改查全链路)
*   **角色管理 system/RoleList.vue**: /system/roles，✅ 已对接

### 1.2 已完成的基础设施
*   Router (router/index.ts): 9 条路由，懒加载，嵌套 layout
*   侧边栏 (layout/Sider.vue): 6 角色菜单可见性控制
*   Auth Store (stores/auth.ts): isAdmin/isLeader/dataScope getters
*   请求封装 (utils/request.ts): Axios + Bearer Token + 401 重定向
*   API 层 (api/): 6 个文件，共 20+ 接口定义
*   导出 composable(composables/useExportCSV.ts): 角色权限校验

## 二、M1.5 前端改动清单（寄样台接入真实数据）
*   **Apply.vue 达人选择器 enrichment**: (已完成) 调用 /api/samples/talents，下拉展示粉丝数/信用分。
*   **sample/index.vue 列展示增强**: 增加展示达人粉丝数、商品价格。
*   **SampleDetail.vue 增加达人信息卡片**: 显示 talentName + fansCount + creditScore + mainCategory。

## 三、M1.6 前端改动清单（数据大盘接入真实数据）
*注意：由于后端已移除部门模块，且通过 JWT 解析用户身份，前端无需在 API 请求中显式传递 userId, deptId 或 dataScope。数据隔离由后端 DataScope AOP 自动拦截处理。*

## 四、缺失的类型定义
目前需在 `src/types/index.ts` 补充以下类型定义：
*   UserItem, RoleItem
*   ProductItem, ActivityItem
*   TalentItem, SampleItem, OrderItem
*   MetricsData, PageResult<T>

## 五、其余待优化项
*   **P1 Login.vue**: 表单未校验（空用户名可提交），需增加 rules。
*   **P1 ActivityList.vue**: 活动详情弹窗接入真实 API。
*   **P2 talent/index.vue**: 列表列信息太少，缺等级/类目/信用分，需增加共享列。
