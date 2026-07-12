# Evidence Report 2026-07-10

## 结论

PARTIAL

本轮完成寄样列表查询的 DDD 迁移切片：`SampleQueryApplicationService` 通过 `SamplePageQueryPort` 调用 `LegacySamplePageQueryAdapter`，保持原完整筛选和简化分页入口兼容。Legacy 查询实现尚未替换为寄样域新读模型。随后从原 real-pre 服务器同步有效 token 到本地 real-pre Redis，授权前置已解除；真实 P0 仍因商品页面图片 FAIL、订单归因和寄样自动完成 PENDING 保持 PARTIAL。

## 证据

- 时间：2026-07-10 23:12（Asia/Shanghai）
- 环境：real-pre；分支：`codex/ddd-user-role-application`
- 验证 commit：`2372dfff`
- 工作区：dirty；存在用户既有 dirty/untracked 文件，本轮未整体提交或清理
- 迁移文件：`SampleQueryApplicationService.java`、`SamplePageQueryPort.java`、`LegacySamplePageQueryAdapter.java`及对应 4 个测试
- 寄样回归：312 tests / 0 failures / 0 errors / 0 skipped
- 后端 package：PASS（`mvn -f backend/pom.xml -DskipTests package`）
- 前端 build：PASS（`npm --prefix frontend ci`、`npm --prefix frontend run build`）
- Docker：real-pre backend/frontend 重建、重启 PASS；PostgreSQL/Redis 保持运行
- 健康检查：backend `GET /api/system/health` 返回 200 `{"status":"UP"}`；frontend `/healthz` 返回 200
- 业务预检：`runtime/qa/out/real-pre-preflight-20260710-230525/report.md`；admin login、环境守卫、schema readiness、抖音 token readiness 全部 PASS；`hasAccessToken=true`、`hasRefreshToken=true`、`reauthorizeRequired=false`
- real-pre P0：`runtime/qa/out/real-pre-p0-20260710-230558/report.md`；preflight、抖店接入、业绩看板、RBAC PASS；商品链 FAIL（商品列表有数据但外链图片渲染数为 0）；订单归因 PENDING（35 条订单全部 `PICK_SOURCE_EMPTY`）；寄样链 PENDING（无真实成交订单触发自动完成）
- 远端部署：未执行
- Harness limits：PASS；`harness/reports` 直接文件数回到上限内，原始 314 行 agent-do 报告已压缩归档
- DDD acceptance：PASS；矩阵 DONE 134 / PARTIAL 36 / TODO 0 / BLOCKED 8；redline whitelist 0；宽口径架构/合同 366 tests / 0 failures / 0 errors / 1 skipped

## 风险

- 适配器内部仍委托 `SampleQueryService`，不是最终寄样域读模型实现。
- real-pre 订单归因与寄样自动完成仍缺真实命中样本；商品管理页图片渲染问题待修复后复跑 P0，不能用本地测试替代。
- GitHub push 仍受 443 网络不可达影响，待网络恢复后再推送。
