# OpenAPI Controller Inventory

生成时间：2026-07-04

## 结论

- 实际 REST Controller 类：45
- 实际 mapping 注解：295
- 统计方式：只匹配源码中行首真实注解 `@RestController` / `@Controller` / `@ResponseBody`，避免把 Javadoc 示例当成接口。
- 不包含未被 Spring MVC 暴露的普通 Service、DTO、Mapper。

## 清单

| # | Class | Tag | Base mapping | Mapping annotations | File |
|---|---|---|---|---:|---|
| 1 | `AuthController` | 认证中心 | `"/auth")` | 4 | `backend/src/main/java/com/colonel/saas/auth/controller/AuthController.java` |
| 2 | `AdminColonelPartnerController` | - | `"/api/admin/colonel-partners")` | 3 | `backend/src/main/java/com/colonel/saas/controller/AdminColonelPartnerController.java` |
| 3 | `AdminDouyinQuickSampleController` | 抖店快速寄样诊断 | `"/admin/douyin/quick-sample")` | 2 | `backend/src/main/java/com/colonel/saas/controller/AdminDouyinQuickSampleController.java` |
| 4 | `AdminLogisticsGatewayController` | 物流 Gateway 诊断 | `"/admin/logistics/gateway")` | 3 | `backend/src/main/java/com/colonel/saas/controller/AdminLogisticsGatewayController.java` |
| 5 | `AdminProductDisplayController` | - | `"/api/admin/products/display")` | 4 | `backend/src/main/java/com/colonel/saas/controller/AdminProductDisplayController.java` |
| 6 | `AdminSampleLogisticsController` | 寄样物流管理 | `"/admin/samples/logistics")` | 2 | `backend/src/main/java/com/colonel/saas/controller/AdminSampleLogisticsController.java` |
| 7 | `ColonelActivityController` | 团长活动管理 | `"/colonel/activities")` | 6 | `backend/src/main/java/com/colonel/saas/controller/ColonelActivityController.java` |
| 8 | `ColonelActivityProductController` | 活动商品主链路 | `"/colonel/activities/{activityId}/products")` | 17 | `backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java` |
| 9 | `ColonelPartnerController` | 商品域合作方 | `"/colonel/partners")` | 4 | `backend/src/main/java/com/colonel/saas/controller/ColonelPartnerController.java` |
| 10 | `ColonelPartnerMasterDataController` | - | `"/api/colonel-partners")` | 4 | `backend/src/main/java/com/colonel/saas/controller/ColonelPartnerMasterDataController.java` |
| 11 | `ColonelPinnedProductController` | 商品置顶 | `"/colonel/pinned-products")` | 2 | `backend/src/main/java/com/colonel/saas/controller/ColonelPinnedProductController.java` |
| 12 | `ColonelsettlementActivityController` | 活动管理 | `"/activities")` | 3 | `backend/src/main/java/com/colonel/saas/controller/ColonelsettlementActivityController.java` |
| 13 | `CommissionRuleController` | 提成规则 | `"/commission-rules")` | 5 | `backend/src/main/java/com/colonel/saas/controller/CommissionRuleController.java` |
| 14 | `CurrentUserController` | 用户域 | `"/users/current")` | 5 | `backend/src/main/java/com/colonel/saas/controller/CurrentUserController.java` |
| 15 | `DashboardController` | 数据看板 | `"/dashboard")` | 3 | `backend/src/main/java/com/colonel/saas/controller/DashboardController.java` |
| 16 | `DataController` | 数据平台 | `-` | 10 | `backend/src/main/java/com/colonel/saas/controller/DataController.java` |
| 17 | `DouyinController` | 抖音联调 | `"/douyin")` | 18 | `backend/src/main/java/com/colonel/saas/controller/DouyinController.java` |
| 18 | `DouyinOAuthController` | 抖音 OAuth 授权 | `"/douyin/oauth")` | 3 | `backend/src/main/java/com/colonel/saas/controller/DouyinOAuthController.java` |
| 19 | `DouyinWebhookController` | 抖音联调 | `"/douyin/webhooks")` | 2 | `backend/src/main/java/com/colonel/saas/controller/DouyinWebhookController.java` |
| 20 | `ExclusiveMerchantController` | 独家商家 | `"/exclusive-merchants")` | 3 | `backend/src/main/java/com/colonel/saas/controller/ExclusiveMerchantController.java` |
| 21 | `Kuaidi100LogisticsCallbackController` | 物流回调 | `-` | 2 | `backend/src/main/java/com/colonel/saas/controller/Kuaidi100LogisticsCallbackController.java` |
| 22 | `MerchantController` | 商家管理 | `"/merchants")` | 5 | `backend/src/main/java/com/colonel/saas/controller/MerchantController.java` |
| 23 | `OperationLogController` | 操作日志中心 | `"/operation-logs")` | 2 | `backend/src/main/java/com/colonel/saas/controller/OperationLogController.java` |
| 24 | `OrderAttributionController` | 订单回流与归因 | `-` | 3 | `backend/src/main/java/com/colonel/saas/controller/OrderAttributionController.java` |
| 25 | `OrderController` | 订单管理 | `"/orders")` | 16 | `backend/src/main/java/com/colonel/saas/controller/OrderController.java` |
| 26 | `OutboxAdminController` | - | `"/api/admin/outbox-events")` | 3 | `backend/src/main/java/com/colonel/saas/controller/OutboxAdminController.java` |
| 27 | `PerformanceController` | 业绩域 | `"/performance")` | 7 | `backend/src/main/java/com/colonel/saas/controller/PerformanceController.java` |
| 28 | `ProductController` | 商品管理（已废弃） | `"/products")` | 19 | `backend/src/main/java/com/colonel/saas/controller/ProductController.java` |
| 29 | `ProductLibraryRepairController` | 商品库运维修复 | `"/colonel")` | 4 | `backend/src/main/java/com/colonel/saas/controller/ProductLibraryRepairController.java` |
| 30 | `ProductSyncAdminController` | 商品同步管理 | `"/product-sync/admin")` | 4 | `backend/src/main/java/com/colonel/saas/controller/ProductSyncAdminController.java` |
| 31 | `ProductSyncProbeController` | 商品同步只读探针 | `"/product-sync-probes")` | 3 | `backend/src/main/java/com/colonel/saas/controller/ProductSyncProbeController.java` |
| 32 | `RedisProbeController` | 环境探针 | `"/ops")` | 2 | `backend/src/main/java/com/colonel/saas/controller/RedisProbeController.java` |
| 33 | `RuleCenterController` | 规则中心 | `"/rule-center")` | 8 | `backend/src/main/java/com/colonel/saas/controller/RuleCenterController.java` |
| 34 | `SampleController` | 寄样管理 | `"/samples")` | 22 | `backend/src/main/java/com/colonel/saas/controller/SampleController.java` |
| 35 | `SampleFilterOptionsController` | 寄样筛选选项 | `"/samples")` | 2 | `backend/src/main/java/com/colonel/saas/controller/SampleFilterOptionsController.java` |
| 36 | `SysConfigController` | 系统配置 | `"/configs")` | 7 | `backend/src/main/java/com/colonel/saas/controller/SysConfigController.java` |
| 37 | `SysDeptController` | 系统部门 | `{"/depts", "/departments"})` | 12 | `backend/src/main/java/com/colonel/saas/controller/SysDeptController.java` |
| 38 | `SysMenuController` | 系统菜单 | `"/menus")` | 6 | `backend/src/main/java/com/colonel/saas/controller/SysMenuController.java` |
| 39 | `SysRoleController` | 系统角色 | `"/roles")` | 9 | `backend/src/main/java/com/colonel/saas/controller/SysRoleController.java` |
| 40 | `SystemEnvController` | - | `"/system")` | 3 | `backend/src/main/java/com/colonel/saas/controller/SystemEnvController.java` |
| 41 | `SysUserController` | 系统用户 | `"/users")` | 9 | `backend/src/main/java/com/colonel/saas/controller/SysUserController.java` |
| 42 | `TalentController` | 达人CRM | `"/talents")` | 25 | `backend/src/main/java/com/colonel/saas/controller/TalentController.java` |
| 43 | `TalentProfileController` | 达人真实资料 | `"/talents")` | 3 | `backend/src/main/java/com/colonel/saas/controller/TalentProfileController.java` |
| 44 | `TestController` | 测试工具 | `"/test")` | 12 | `backend/src/main/java/com/colonel/saas/controller/TestController.java` |
| 45 | `UserMasterDataController` | 用户域主数据 | `"/users/master-data")` | 4 | `backend/src/main/java/com/colonel/saas/controller/UserMasterDataController.java` |

## 风险

- 部分 Controller 暂未显式 `@Tag`，Apifox 导入后可能进入默认目录或以类名分组。
- `ProductController` 已标记“已废弃”，本次只保留文档说明，不删除 legacy 接口。
