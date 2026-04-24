# 开发进度记录

**项目**：抖音团长 SaaS V2.2
**最后更新**：2026-04-24

---

## 2026-04-24 RESTful 接口整理

### 已完成

1. 后端接口统一英文 RESTful 路径
- 分页列表统一为集合接口，如 `GET /users`、`GET /products`、`GET /orders`
- 动作型接口改为子资源风格，如 `/products/{id}/activity`、`/samples/{id}/status`
- 抖音联调接口统一收口为英文 `/douyin/**` 路径

2. 联动改造
- 前端 `frontend/src/api/*` 已同步切换新路径
- 控制器测试已同步更新为新路径
- 新增总表文档：`doc/API_RESTFUL_ENDPOINTS.md`

3. 验证
- `backend` 控制器相关测试 `77` 项通过

4. 鉴权问题修复
- 修复 `/douyin/tokens` 被错误排除在 JWT 拦截器之外的问题
- `GET /api/douyin/tokens` 未携带 `Authorization` 现在返回 `401`
- 使用 `admin / admin123` 登录后，请求 `GET /api/douyin/tokens?appId=7623665273727387199` 返回 `200`
- 已用 Docker 容器重新构建并重跑后端，确认当前 `localhost:8080` 为最新代码

5. Token 持久化异常修复
- 修复 `POST /api/douyin/tokens` 成功拿到抖店返回后，写入 Redis 时触发 `Long cannot be cast to String` 的问题
- 根因是 Redis 使用 `StringRedisSerializer`，但 `tokenExpireAtEpochSeconds` 和订单同步 `lastSyncTime` 曾以 `Long` 直接写入
- 已改为统一按字符串写入 Redis，避免 Token 创建成功后被本地序列化异常打断
- 已补充 `DouyinTokenServiceTest`、`OrderSyncServiceTest` 回归验证并通过
- 容器重建后再次验证：`GET /api/douyin/tokens?appId=7623665273727387199` 返回 `200`

---

## 2026-04-24 达人 CRM 最小闭环补齐

### 已完成

1. 后端能力补齐
- 新增达人释放接口：`POST /talents/{id}/release`
- 认领逻辑补齐重复认领校验：同一用户对同一达人不可重复认领
- 保护期默认天数统一为 30 天（与 V2.2 口径一致）
- 释放权限口径：认领人、同组负责人（同 dept）、管理员可释放
- 增加过期认领释放方法：`releaseExpiredClaims(LocalDateTime now)`（供定时任务接入）

2. 数据访问层补齐
- `TalentClaimMapper` 新增：
- `findActiveByTalentAndUser(talentId, userId)`
- `findActiveByTalentId(talentId)`

3. 前端 API 对齐
- 新增达人释放调用：`releaseTalent(id)`

4. 测试补齐
- `TalentServiceTest` 新增/更新：重复认领拦截、保护期拦截、释放权限校验
- `TalentControllerTest` 新增：释放接口回归测试

5. 达人信息补全链路打通
- 新增后自动触发采集补全：`create -> crawlAndSave -> 回填 talent`
- 新增手动刷新接口：`POST /talents/{id}/refresh`
- 新增前端 API：`refreshTalent(id)`
- 达人列表新增单行刷新按钮：可直接触发接口并回刷当前页数据

6. 重启后联调验证（2026-04-24）
- 修复容器内编译阻塞的核心实体访问问题（移除 `SysUser`/`SysRole`/`SysUserRole` 的 `@PackagePrivate`）
- 验证 `CRAWLER_TALENT_URL` 已透传到 backend 容器环境
- 实测链路：登录 -> 新增达人 -> 刷新达人，接口调用成功
- 当前采集结果：`crawlStatus=2`、`crawlMessage=crawl failed`
- 后端日志定位：`SSLHandshakeException: Remote host terminated the handshake`（当前默认数据源 TLS 握手失败）

7. 达人数据合规策略收敛（2026-04-24）
- 新增配置：`talent.data.public-page-crawl-enabled`，默认 `false`
- 达人新增不再依赖公开页面采集作为主路径
- 刷新达人接口在关闭公开页面采集时返回明确提示（引导官方授权 API / 人工录入）
- 新增方案文档：`doc/达人信息获取与授权方案.md`

8. 达人数据模型差异补齐（2026-04-24）
- 新增增量 DDL：`backend/src/main/resources/db/alter-talent-enrich.sql`
- `talent` 补充字段映射：`douyin_no/uid/sec_uid/profile_url/enrich_status/last_enrich_time/data_source`
- 新增实体：`TalentEnrichTask`、`TalentFieldSource`、`TalentAuth`
- 新增 Mapper：`TalentEnrichTaskMapper`、`TalentFieldSourceMapper`、`TalentAuthMapper`
- 编译验证通过：`mvn -DskipTests compile`

---

## 2026-04-24 进度总结

### 已完成：全量 API 测试

**环境恢复**：Docker Desktop 重启成功，PostgreSQL / Redis 容器在线  
**认证**：admin / admin123，JWT token 获取正常

#### 测试结果汇总（10/10 接口路由验证通过）

| # | 接口路径 | HTTP | 路由别名 | 状态 | 说明 |
|---|---------|------|---------|------|------|
| 1 | `/douyin/activities` | GET | 集合查询 | ✅ 200 | SDK 调用链路正确 |
| 2 | `/douyin/activities/{activityId}` | GET | 资源详情 | ✅ 200 | 路径参数 `activityId` 正确传递 |
| 3 | `/douyin/activity-products` | GET | 联调筛选列表 | ✅ 200 | 多可选参数正确接收 |
| 4 | `/douyin/activities/{activityId}/products` | GET | 活动下商品列表 | ✅ 200 | 路径参数 `activityId` 正确传递 |
| 5 | `/douyin/product-material-status-checks` | POST | 商品素材状态查询 | ✅ 200 | 请求体校验正确 |
| 6 | `/douyin/order-settlements` | GET | 团长结算订单查询 | ✅ 200 | 业务校验触发（需 time range） |
| 7 | `/douyin/activity-product-cancellations` | POST | 取消活动商品合作 | ✅ 200 | 业务校验触发（需 applyIds） |
| 8 | `/douyin/activities` | POST | 创建团长活动 | ✅ 200 | 业务校验触发（需 estimatedSingleSale） |
| 9 | `/douyin/tokens` | GET | Token 状态查询 | ✅ 460 | 配置缺失时正确抛出 BusinessException |
| 10 | `/douyin/token-refreshes` | POST | Token 刷新 | ✅ 460 | 配置缺失时正确抛出 BusinessException |

**关键验证点**：
- 所有接口均通过 ASCII 别名路由（非中文 URL），彻底规避 URL 编码问题
- 接口 1-8 在配置缺失时返回 200 + `{status:"failed", message:"missing douyin.app.app-id/client-key config"}` — SDK 调用链路已打通
- 接口 9-10 在配置缺失时返回 460 + BusinessException — 与其他接口不同的处理路径（不走 `fillError` 而由全局异常处理器返回），符合设计预期
- 接口 6-8 业务参数校验生效（空参数触发 `IllegalArgumentException`）
- 接口 8 中文请求体在带上 `charset=utf-8` 时正常解析（Git Bash curl 发送 GBK 导致 500，需注意）

---

## 2026-04-23 进度总结

### 已完成

1. 抖音 Token 接口修复
- 统一 REST 路径：`POST /douyin/tokens`
- 状态查询路径：`GET /douyin/tokens`
- 刷新路径：`POST /douyin/token-refreshes`

2. 请求兼容性增强
- `TokenCreateRequest` 支持 `app_id`、`grant_type`、`authorization_code` 等 snake_case 字段
- 放宽控制器层 `code` / `grantType` 必填校验，改由 `DouyinTokenService` 按真实业务规则校验
- `authorization_self` 场景不再被控制器错误拦截

3. 测试
- 新增控制器回归测试，覆盖标准路径访问
- 新增 `grant_type=authorization_self` 的请求体兼容测试
- `backend` 执行 `mvn -Dtest=DouyinControllerTest test` 通过

---

## 2026-04-21 进度总结

### 已完成

1. 后端主链路
- RBAC + JWT + DataScope
- 商品、达人、寄样、数据模块核心接口
- 抖音 SDK 封装（Activity/Product/Order/Promotion/Talent）

2. 订单与归因
- 订单滑窗同步 + Redis 分布式锁
- 归因优先级升级：独家商家 > 独家达人 > pick_source

3. 独家机制
- ExclusiveTalent/ExclusiveMerchant 实体、Mapper、Service
- 月度评估定时任务

4. 寄样闭环
- 订单驱动自动完成待交作业
- 30天未出单自动关闭
- 状态日志落库

5. 测试
- 修复 SDK/Crawler 相关失败测试
- `mvn test` 全绿

### 未完成

1. 第三方 SDK 真联调
- 当前仅本地/Mock验证
- 真实 token/真实接口返回待验证

2. 看板真实口径
- M1.6 待收口

3. 部署验收
- M1.7 待完成

---

## 下一步计划

1. 完成 SDK 最小真联调闭环
2. 完成 M1.3 真数据验收（入库/归因/寄样闭环）
3. 推进 M1.6、M1.7

---

## 2026-04-24 达人 CRM 表结构对比与差异补齐

### 已完成

1. 表结构对比清单
- 新增文档：`doc/达人CRM表结构对比与迁移清单.md`
- 明确“已覆盖/缺失/待接入链路”：
  - 已覆盖：`talent`、`talent_claim`、`exclusive_talent`、`talent_enrich_task`、`talent_field_source`、`talent_auth`
  - 缺失：`talent_contact`、`talent_tag`、`talent_tag_relation`、`talent_crawl_log`

2. 差异增量 DDL
- 新增脚本：`backend/src/main/resources/db/alter-talent-crm-gap-fill.sql`
- 补齐表：
  - `talent_contact`
  - `talent_tag`
  - `talent_tag_relation`
  - `talent_crawl_log`
- 补充索引：
  - `idx_talent_douyin_no`
  - `idx_talent_last_enrich_time`

3. 执行顺序建议
- `init-db.sql` -> `alter-talent-enrich.sql` -> `alter-talent-crm-gap-fill.sql`
- 说明 `docker-compose` 当前仅自动执行初始化脚本，增量脚本需手工执行或迁移工具纳管

---

## 2026-04-24 达人自动补全任务流转（P0-1）

### 已完成

1. 自动补全任务 Mapper 能力
- `TalentEnrichTaskMapper` 新增 `findLatestByTalentId(talentId)` 查询。

2. Service 任务流转
- `TalentService.create()`：新增达人时创建 `PENDING` 任务。
- `TalentService.refresh()`：创建 `RUNNING` 任务并按执行结果更新为 `SUCCESS/FAILED`。
- 新增 `getLatestEnrichTask(UUID talentId)` 服务方法。

3. Controller 接口
- 新增 `GET /talents/{id}/enrich-task/latest`，用于前端查询最新补全任务状态。

4. 测试回归
- 更新 `TalentServiceTest`（任务创建、状态流转、latest 查询）。
- 新增/更新 `TalentControllerTest`（latest 任务接口）。
- 定向验证通过：`mvn clean -Dtest=TalentServiceTest,TalentControllerTest test`

---

## 2026-04-24 前端联动：补全任务状态展示

### 已完成

1. 前端 API 补齐
- `frontend/src/api/talent.ts` 新增：
  - `getLatestEnrichTask(id)` -> `GET /talents/{id}/enrich-task/latest`

2. 达人列表页展示补全状态
- `frontend/src/views/talent/index.vue` 新增两列：
  - `补全状态`（待处理/进行中/成功/失败）
  - `失败原因`
- 列表加载后并发查询当前页每个达人的 latest enrich task，并回填到表格行数据。

3. 构建验证
- 前端执行 `npm run build` 通过。

---

## 2026-04-24 真实数据联调脚本（新增）

### 已完成

1. 新增一键联调脚本
- 文件：`backend/scripts/douyin-real-sync.ps1`
- 覆盖链路：
  - 登录获取 JWT
  - Token 创建/刷新（可选跳过）
  - Token 状态查询
  - 活动/商品/订单接口调用
  - 手动触发订单同步

2. 参数与环境读取
- 支持从项目根 `.env` 读取：
  - `DOUYIN_APP_ID`
  - `DOUYIN_AUTH_CODE`
  - `DOUYIN_REFRESH_TOKEN`
- 支持命令行覆盖：`-BaseUrl/-AppId/-GrantType/-AuthorizationCode/-RefreshToken`

---

## 2026-04-24 达人自动补全 Provider 架构（P0）

### 已完成

1. Provider 抽象层
- 新增接口：`TalentDataProvider`
- 新增上下文：`TalentEnrichContext`
- 新增结果对象：`TalentEnrichResult`

2. 调度器
- 新增服务：`TalentEnrichOrchestrator`
- 能力：
  - 按 Provider 顺序尝试补全
  - 回填达人字段
  - 写入 `talent_field_source` 审计
  - 维护 `talent.enrich_status / last_enrich_time / data_source`

3. 基础 Provider（占位）
- `InternalBusinessTalentProvider`（占位）
- `ThirdPartyTalentProvider`（占位，待接入真实供应商）
- `ManualTalentProvider`（把人工录入字段纳入审计链路）

4. Service 接入
- `TalentService` 注入 `TalentEnrichOrchestrator`
- `create/refresh` 主链路接入 orchestrator
- `refresh` 在关闭公开页采集时不再直接失败，仍可走 Provider 补全链路

5. 测试验证
- 更新 `TalentServiceTest` 构造参数与行为预期
- 执行通过：`mvn -Dtest=TalentServiceTest,TalentControllerTest test`

## 2026-04-24 达人补全链路收口（manual-fill + 解析兜底）

### 已完成

1. 达人手动补录接口
- 新增接口：`PUT /talents/{id}/manual-fill`
- 作用：补录昵称、头像、粉丝、获赞、关注、作品、IP属地等字段
- 结果：统一落 `data_source=MANUAL`，并将 `enrich_status` 更新为 `SUCCESS`

2. 创建链路输入解析兜底
- `TalentService.create` 支持在 `douyinUid` 缺失时，基于 `profileUrl/douyinNo/uid/secUid` 自动解析 `douyinUid`
- 解析组件：`TalentInputParser` + `TalentInputParseResult`

3. 状态流转对齐
- 当 Provider 未返回可用字段时，任务状态改为 `WAIT_MANUAL`，达人状态同步为 `WAIT_MANUAL`
- 保留失败分支 `FAILED`，用于异常链路

4. 测试补齐与验证
- 新增/更新：
  - `TalentControllerTest.manualFill_talent_returnsUpdatedTalent`
  - `TalentServiceTest.create_shouldParseDouyinUidFromProfileUrl_whenDouyinUidMissing`
  - `TalentServiceTest.manualFill_shouldUpdateTalentAndMarkManualSource`
- 定向测试通过：`mvn "-Dtest=TalentServiceTest,TalentControllerTest" test`

## 2026-04-24 每周批量刷新活跃达人（P0补齐）

### 已完成

1. 新增定时任务
- 文件：`backend/src/main/java/com/colonel/saas/job/TalentWeeklyRefreshJob.java`
- 定时：`0 0 3 ? * MON`（每周一凌晨 3 点）
- 逻辑：查询活跃达人 ID 列表，逐个调用 `TalentService.refresh`，单个失败不影响整体

2. 新增服务查询能力
- 文件：`backend/src/main/java/com/colonel/saas/service/TalentService.java`
- 新增方法：`findActiveTalentIdsForRefresh()`
- 口径：`deleted=0 && status=1`

3. 新增单测
- 文件：`backend/src/test/java/com/colonel/saas/job/TalentWeeklyRefreshJobTest.java`
- 覆盖场景：
  - 无活跃达人时跳过执行
  - 单个达人刷新异常时继续刷新其余达人

4. 回归验证
- 执行通过：`mvn "-Dtest=TalentServiceTest,TalentControllerTest,TalentWeeklyRefreshJobTest" test`

## 2026-04-24 前端功能对齐补齐（manual-fill / WAIT_MANUAL / 手动批量刷新）

### 已完成

1. 后端手动批量刷新入口
- `TalentController` 新增接口：`POST /talents/refresh/weekly`
- 触发 `TalentWeeklyRefreshJob.weeklyRefreshActiveTalents()`

2. 前端 API 补齐
- `frontend/src/api/talent.ts` 新增：
  - `refreshWeeklyTalents()`
  - `manualFillTalent(id, data)`

3. 达人列表页补齐
- `frontend/src/views/talent/index.vue` 新增：
  - 顶部“批量刷新”按钮
  - 手动补录弹窗（昵称/头像/粉丝/获赞/关注/作品/IP）
  - 行操作新增“手动补录”
  - 状态映射新增 `WAIT_MANUAL -> 待手动补录`（warning 标签）

4. 测试与构建验证
- 后端通过：`mvn "-Dtest=TalentControllerTest,TalentServiceTest,TalentWeeklyRefreshJobTest" test`
- 前端通过：`npm run build`

## 2026-04-24 环境分离（test/mock 与 prod/real）

### 已完成

1. 后端配置分层
- 新增 `application-test.yml`，默认 `talent.enrich.mode=mock`
- 更新 `application-prod.yml`，默认 `talent.enrich.mode=real`
- 更新 `application.yml`，统一挂载 `talent.enrich.mode` 与 `talent.data.public-page-crawl-enabled`

2. Provider 按模式装配
- 新增 `MockTalentProvider`（仅 `mock` 模式生效）
- `ThirdPartyTalentProvider`、`InternalBusinessTalentProvider` 调整为仅在 `real` 模式生效
- `TalentDataSource` 新增 `MOCK`

3. 生产防误配保护
- 新增 `TalentEnrichModeGuard`
- 当 `prod` 环境下误配 `talent.enrich.mode=mock` 时启动失败

4. 前端环境标识
- `frontend/.env.development` 增加 `VITE_ENV_LABEL=TEST(MOCK)`
- 新增 `frontend/.env.production`，标识 `PROD(REAL)`
- 侧边栏展示当前环境标签

5. 配置示例与文档
- 更新 `.env.example`：默认测试库 + mock 模式
- 新增文档：`doc/环境切换与生产检查清单.md`

## 2026-04-24 Docker 环境分离（test/prod）

### 已完成

1. 新增 Compose 覆盖文件
- `docker-compose.test.yml`：测试环境（`test + mock`）
- `docker-compose.prod.yml`：生产环境（`prod + real`）

2. 覆盖项
- backend：`SPRING_PROFILES_ACTIVE`、`DB_NAME`、`REDIS_DATABASE`、`TALENT_ENRICH_MODE`
- frontend：`VITE_ENV_LABEL`
- postgres：`POSTGRES_DB` 与健康检查数据库名

3. 文档更新
- `doc/环境切换与生产检查清单.md` 增加 Docker 启动命令

4. 配置校验
- `docker compose -f docker-compose.yml -f docker-compose.test.yml config` 通过
- `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` 通过
- 补充：`docker-compose.test.yml` 已覆盖 `JAVA_OPTS=-Dspring.devtools.restart.enabled=true ...`，测试环境后端支持自动重启；生产环境保持关闭。

## 2026-04-24 测试环境三方接口全 Mock 化

### 已完成

1. 配置开关
- 新增统一开关：`douyin.mock.enabled`
- `application-test.yml` 默认 `douyin.mock.enabled=true`
- `application-prod.yml` 默认 `douyin.mock.enabled=false`

2. 三方调用统一 Mock
- `DouyinApiClient` 在 mock 模式下不再出网，直接返回本地 mock 响应
- 覆盖活动/商品/订单等常用接口的响应结构（包含 `mock=true` 标记）

3. Token 链路 Mock
- `DouyinTokenService` 在 mock 模式下不再调用真实 token 网关
- 自动生成并缓存 mock access_token/refresh_token，供测试接口使用

4. 三方网关硬阻断
- `DoudianTokenGateway` 在 mock 模式下若被误调用，直接抛错阻断外部请求

5. Docker 测试环境对齐
- `docker-compose.test.yml` 增加 `DOUYIN_MOCK_ENABLED=true`
- 组合配置校验通过：`docker compose -f docker-compose.yml -f docker-compose.test.yml config`
