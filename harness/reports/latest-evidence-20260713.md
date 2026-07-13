# 商品管理性能优化证据

## 元数据

- 时间：2026-07-13 21:45（Asia/Shanghai）
- 环境：本地 real-pre
- 分支：codex/ddd-user-role-application
- commit：34878940（证据采集时基线为 2e54714f）
- 远端部署：未执行（用户未要求）
- 结论：PARTIAL

## 修改范围

- 活动商品每页上游结果落库后，立即刷新该页商品库展示状态并失效活动缓存。
- 同一活动的分页状态刷新串行，不同活动仍可并行。
- 活动商品列表/状态计数 Redis 短 TTL 调整为 250ms，写入后按活动版本失效。
- 前端同步任务进行中每 500ms 刷新一次本地商品列表，不再等待整轮同步完成。

## 构建与测试

- 后端：`mvn -f backend/pom.xml -DskipTests package` PASS。
- 前端：`npm --prefix frontend ci`、`npm --prefix frontend run build` PASS。
- 后端回归：`ProductServiceActivityStatusIndependenceTest`、`ActivityProductRedisCacheServiceTest` PASS。
- 前端回归：`activity-sync.test.ts` 6/6 PASS；`npm run typecheck` PASS。
- 一次并发 Maven 清理导致的类文件缺失已重跑排除，不作为代码失败证据。

## 容器与健康检查

- real-pre backend/frontend 已通过 Compose `up -d --build` 重建并重启。
- backend：HTTP 200，`{"status":"UP"}`。
- frontend：`/healthz` HTTP 200。
- PostgreSQL、Redis：healthy。

## 真实业务验证

- real-pre 预检：BLOCKED_AUTH。
- admin login 连续 5 次 HTTP 401；因此没有管理员 token，抖音 token readiness 和需要登录的业务流未执行。
- 该阻塞来自现有 real-pre 认证前置条件，不能用 mock 数据替代。

## 真实同步链路观测

- 定时同步在 real-pre 实际运行，活动 `3929906`：12 页、223 行，21:45:10 完成；活动 `3929905`：28 页、543 行，21:45:33 完成。
- 单页真实观测：上游页响应完成后，商品库状态刷新日志 `totalCostMs` 约 97–342ms；示例：活动 `3929905` 第 1 页 297ms、第 22 页 176ms。
- 前端轮询间隔已降为 500ms，故已落库页无需等待整轮同步即可被页面重新读取。
- 注意：上游接口自身日志出现约 431–1041ms，当前证据能证明“上游页返回后到商品状态刷新”小于 1 秒，不能证明包含上游网络往返的所有请求都稳定小于 1 秒。

## 数据库只读验证

- 活动 `3929905`：543 条快照，最新同步时间 `2026-07-13 13:45:33.236179`。
- 活动 `3929906`：223 条快照，最新同步时间 `2026-07-13 13:45:10.677779`。
- 活动商品列表查询 EXPLAIN ANALYZE：543 行活动数据，执行时间 5.366ms。
- 活动状态计数查询 EXPLAIN ANALYZE：执行时间 0.814ms。
- Redis 认证可用，活动版本键已存在；250ms 列表缓存因短 TTL 在采集时已自然过期，符合配置预期。
- Harness 限制检查：FAIL，`harness/reports` 当前直接文件 82 个，超过 50 个；该历史超限未在本轮删除或归档，避免触碰并发任务资产。

## 剩余风险与下一步

- 管理员账号 401 阻塞了登录后的真实页面/E2E 断言，需要恢复合法 real-pre 账号或由用户提供可用测试账号后复测。
- 上游 API 偶发超过 1 秒，若目标是“从请求发起到页面可见”严格小于 1 秒，还需要单独优化上游调用耗时或增加可观测的端到端时间戳；本轮未凭经验承诺该指标。
- 商品库复杂筛选的全量内存回退路径不在本轮活动商品状态主链路修改范围内，应单独建立压测基线。
