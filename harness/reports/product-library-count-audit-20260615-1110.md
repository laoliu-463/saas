# 商品库数量缺失专项排查报告

## 1. 审计结论
- 时间：2026-06-15 11:10 Asia/Shanghai；DB 最终观察 UTC：2026-06-15 03:10:00
- 环境：本地 real-pre Docker Compose，未远端部署
- 分支：feature/ddd/DDD-VERIFY-001
- commit：0c8bb9ba7aa0952c089c1a77e18c563dccd3094d
- 工作区：生成报告前干净；本报告生成后仅新增本文件
- 结论：FAIL

阶段性根因结论：商品库几千不是前端误读，也不是角色权限过滤导致。真实上游 dry-run 在最近 30 天活动范围内已读取到 48,617 行 / 36,489 个 distinct product_id，而本地 DB 最终仅有 7,979 条活动商品关联 / 7,020 个 distinct product_id，展示口径仅 2,974。高可信根因是同步覆盖范围不足：定时同步只处理 active/未过期且最近 30 分钟未同步活动，历史大活动缺口不会被常规定时任务补齐；部分历史大活动 dry-run 到 300 页仍 hasNext，当前回补能力不足以证明已全量拉完。

## 2. 执行边界
- 本轮为第一阶段只读审计；未清库，未执行 mock，未手动修改业务数据。
- 未执行构建/重启：本轮 Scope=docs/审计报告，无源码改动。
- Docker 启动后 real-pre 后端定时同步自动运行，审计期间 DB 数量从 7,962 漂移到 7,979；这是后台任务写入，不是人工写库。

## 3. 五类证据
### 3.1 DB 数量证据
最终观察：
| 指标 | 数量 |
|---|---:|
| product_snapshot deleted=0 | 7,979 |
| product_snapshot distinct product_id | 7,020 |
| product_operation_state deleted=0 | 7,979 |
| display_status=DISPLAYING | 2,974 |
| DISPLAYING distinct product_id | 2,974 |
| snapshot max(sync_time) | 2026-06-15 03:07:21.347407 |

初始观察（2026-06-15 02:29:38 UTC）：product_snapshot=7,962，distinct activity_id=24，distinct product_id=7,004，上游 status=1 推广中=3,151，DISPLAYING=2,956，旧 product 主表=12。

结构证据：当前不存在 activity_product_relation 表；活动商品关联事实在 product_snapshot。product_snapshot 唯一键为 (activity_id, product_id)，product_operation_state 唯一键为 (activity_id, product_id)，另有单 product DISPLAYING 约束。未发现“只按 product_id 写 relation 导致跨活动覆盖”的结构证据。

### 3.2 真实上游 API 数量证据
使用现有只读 dry-run probe，不写库。
| 范围 | activitiesScanned | apiFetchedRows | apiDistinctProductIds | dbRowsForScannedActivities | estimatedGapRows | apiErrors |
|---|---:|---:|---:|---:|---:|---:|
| ACTIVE_ONLY | 5 | 2,377 | 2,014 | 2,370 | 7 | 0 |
| RECENT_30D | 24 | 48,617 | 36,489 | 7,972 | 40,647 | 0 |

RECENT_30D 另有 activitiesReachedMaxPages=4、activitiesStillHasNextAfterMaxPages=4。
| activityId | dry-run rows | distinct | DB rows | gap | stoppedReason |
|---|---:|---:|---:|---:|---|
| 3223881 | 6,000 | 5,992 | 20 | 5,980 | MAX_PAGES_REACHED, stillHasNext=true |
| 3272470 | 6,000 | 5,997 | 20 | 5,980 | MAX_PAGES_REACHED, stillHasNext=true |
| 3419461 | 6,000 | 6,000 | 500 | 5,500 | MAX_PAGES_REACHED, stillHasNext=true |
| 3864871 | 6,000 | 5,086 | 2,015 | 3,985 | MAX_PAGES_REACHED, stillHasNext=true |
| 3558291 | 4,656 | 4,651 | 20 | 4,636 | DONE_NO_MORE |

限制说明：现有 dry-run 返回未暴露抖音原始 response.total 字段，只暴露分页实际获取 rows/distinct。raw total 仍需后续新增或扩展 probe 采集，不能伪造。

### 3.3 同步日志证据
backend-real-pre 日志显示 2026-06-15 02:30-02:32 UTC 定时同步只处理 5 个 active 活动。
| activityId | pagesFetched | fetchedRows | distinctProductIds | created | updated | stoppedReason |
|---|---:|---:|---:|---:|---:|---|
| 3929905 | 20 | 386 | 386 | 4 | 382 | DONE_NO_MORE |
| 3916506 | 57 | 1,136 | 1,129 | 4 | 1,132 | DONE_NO_MORE |
| 3891192 | 13 | 252 | 252 | 0 | 252 | DONE_NO_MORE |
| 3920684 | 21 | 410 | 410 | 0 | 410 | DONE_NO_MORE |
| 3929906 | 10 | 193 | 193 | 2 | 191 | DONE_NO_MORE |

后续 02:34、02:39、02:44、02:49、02:54、02:59 UTC 定时任务 finished ok=0, fail=0，说明 active 活动同步完后，历史活动不会被该 job 继续覆盖。

### 3.4 后端接口 total 证据
`GET /api/products?page=1&size=1` 最终观察：
| 账号 | 角色 | total | records |
|---|---|---:|---:|
| admin | 管理员 | 2,974 | 1 |
| biz_leader | 招商组长 | 2,974 | 1 |
| biz_staff | 招商专员 | 2,974 | 1 |
| channel_leader | 渠道组长 | 2,974 | 1 |
| channel_staff | 渠道专员 | 2,974 | 1 |
| ops_staff | 运营 | 403 | - |

`GET /api/colonel/products/library/health`：snapshotTotal=7,979，promotingTotal=3,170，promotingNotSelected=0，promotingNotDisplaying=196，upstreamNotPromoting=4,809，localRejected=2，localPaused=0。接口 total 与 DB DISPLAYING=2,974 对齐，说明后端商品库接口返回的是展示口径，不是 DB 全量关联。

### 3.5 页面 total 证据
使用本机 Chrome + Playwright 打开 `http://127.0.0.1:3001/product/library`，管理员登录后页面文本：
```text
已加载 100 / 2974 件
```
页面同时触发：
```text
GET /api/products?page=1&size=100&sortBy=default
response.data.total = 2974
records = 100
```
前端源码证据：`ProductLibrary.vue` 使用 `data.total` 赋值 `totalCount`，模板显示 `已加载 products.length / totalCount 件`。未发现把当前页长度当总数的证据。

## 4. 代码链路证据
- `ProductActivitySyncJob.resolveActivityIds()` 无白名单时调用 `selectActiveActivityIds(limit, now-30min)`。
- `ColonelsettlementActivityMapper.xml.selectActiveActivityIds` 条件限制 active/未过期，且最近 30 分钟未同步。
- `ProductService.refreshActivitySnapshots` 分页拉取后逐页 upsert product_snapshot，并初始化/更新 product_operation_state。
- `ProductSnapshotMapper.xml` 使用 `ON CONFLICT (activity_id, product_id) DO UPDATE`。
- `ProductDisplayRuleService.applyForActivityId` 只处理 selected_to_library=true 的商品，并按展示规则落 DISPLAYING/HIDDEN/PENDING。
- `ProductController /products` 调 `ProductService.getSelectedLibraryPage`，查询 DISPLAYING 口径并返回 total。

## 5. 缓存证据
- Redis 只读 scan：`*product*` 仅发现 `product:activity:sync:job:lock`；`*library*` 未发现 key。
- 源码检索未发现 `/products` 商品库列表使用 `ShortTtlCacheService` 或 Spring `@Cacheable`。
- 前端请求头设置 no-cache/no-store；商品库页未发现 Pinia 缓存商品列表。
- 阶段性判断：缓存不是本次“几万变几千”的高可信根因。

## 6. 现象到结论链路
现象：业务预期抖音真实商品数是几万，系统商品库页面仅显示 2,974。

证据：上游 dry-run 最近 30 天活动可读到 48,617 行 / 36,489 distinct product_id；DB 只有 7,979 条活动商品关联 / 7,020 distinct product_id；页面和后端接口 total 都是 2,974，与 DB DISPLAYING 一致；定时同步日志只覆盖 5 个 active 活动；历史大活动 dry-run 到 300 页仍 hasNext，DB 缺口数千。

推论：不是前端 total 误读；不是普通角色权限过滤；不是上游只有几千；不是 relation 唯一键只按 product_id 覆盖。主要缺口在同步/回补覆盖范围与大活动分页完成度。

结论：高可信根因是常规定时同步只覆盖 active 活动，历史大活动未被持续回补；对大结果集活动，现有 probe/同步参数在 300 页仍无法证明拉完，导致 DB 活动商品关联远低于真实上游。次要原因是商品库列表采用 DISPLAYING 展示口径，会把 DB 关联 7,979 进一步收敛到 2,974；这是展示规则口径差异，不是几万到几千的主缺口。

## 7. 修复建议
临时止血：
- 暂停对外宣称商品库已全量同步。
- 管理后台或报告中同时展示上游 dry-run、DB 关联、DB distinct、DISPLAYING 四个口径。
- 对历史大活动先按 CUSTOM activityIds 做只读 dry-run，确认缺口活动清单，不直接写库。

根因修复：
- 增加可恢复的 product-library backfill job，支持 RECENT_30D、RECENT_90D、ALL、CUSTOM 活动范围。
- 每个 activity 建立独立 checkpoint：cursor、pages、rows、last status、stoppedReason、hasNext。
- `MAX_PAGES_REACHED && stillHasNext=true` 不允许标记成功，也不能推进“已完成”状态。
- dry-run probe 增加上游 request_params、response.total、nextCursor、inserted/updated/skipped 预估或实际统计。
- pageSize 上限与抖音真实接口能力保持一致；若上游 count 实际最大 20，则文档和调用方不要写 100。

长期治理：
- 建立商品库数量巡检：上游 rows/distinct、DB rows/distinct、DISPLAYING、HIDDEN、PENDING、过期/非推广。
- 同步日志结构化记录 fetched、created、updated、skipped、failed、stoppedReason、hasNext。
- 对历史大活动提供可审计回补任务，不依赖 active 定时同步兜底。

## 8. 验证清单
- 修复后 dry-run RECENT_30D 的 estimatedGapRows 应明显收敛。
- DB product_snapshot rows 应接近 dry-run apiFetchedRows，distinct product_id 应接近 apiDistinctProductIds。
- `/api/colonel/products/library/health` 中 snapshotTotal 与 DB 一致。
- `/api/products` total 与 DB DISPLAYING 口径一致。
- 页面显示 `已加载 当前页 / total` 与接口 total 一致。
- 后端日志不得出现 `MAX_PAGES_REACHED && stillHasNext=true` 仍标记 complete/success。
- real-pre 不允许 mock，不允许清库，不允许关闭真实上游开关后声明通过。

## 9. 未完成与风险
- raw 上游 response.total 未采集：现有 probe 未暴露该字段，后续需扩展。
- ops_staff 访问 `/api/products` 返回 403，需确认该角色是否应具备商品库访问权限。
- 本轮未执行源码修复、构建、容器重启；只读审计不满足“修复完成”定义。
- 本次无需 Harness 升级，未单独生成 retro summary 文件。
