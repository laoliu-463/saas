# Evidence Report 2026-07-10

## 结论

PARTIAL

本轮完成寄样列表查询的 DDD 迁移切片：`SampleQueryApplicationService` 通过 `SamplePageQueryPort` 调用 `LegacySamplePageQueryAdapter`，保持原完整筛选和简化分页入口兼容。Legacy 查询实现尚未替换为寄样域新读模型。真实 real-pre 业务闭环仍因抖音 access token 缺失阻塞。

## 证据

- 时间：2026-07-10 23:02（Asia/Shanghai）
- 环境：real-pre；分支：`codex/ddd-user-role-application`
- 验证 commit：`bfd93696`
- 工作区：dirty；存在用户既有 dirty/untracked 文件，本轮未整体提交或清理
- 迁移文件：`SampleQueryApplicationService.java`、`SamplePageQueryPort.java`、`LegacySamplePageQueryAdapter.java`及对应 4 个测试
- 寄样回归：312 tests / 0 failures / 0 errors / 0 skipped
- 后端 package：PASS（`mvn -f backend/pom.xml -DskipTests package`）
- 前端 build：PASS（`npm --prefix frontend ci`、`npm --prefix frontend run build`）
- Docker：real-pre backend/frontend 重建、重启 PASS；PostgreSQL/Redis 保持运行
- 健康检查：backend `GET /api/system/health` 返回 200 `{"status":"UP"}`；frontend `/healthz` 返回 200
- 业务预检：`runtime/qa/out/real-pre-preflight-20260710-225841/report.md`；admin login、环境守卫、schema readiness PASS；抖音 token readiness `BLOCKED_AUTH`（`hasAccessToken=false`、`hasRefreshToken=true`）
- 远端部署：未执行
- Harness limits：PASS；`harness/reports` 直接文件数回到上限内，原始 314 行 agent-do 报告已压缩归档
- DDD acceptance：PASS；矩阵 DONE 134 / PARTIAL 36 / TODO 0 / BLOCKED 8；redline whitelist 0；宽口径架构/合同 366 tests / 0 failures / 0 errors / 1 skipped

## 风险

- 适配器内部仍委托 `SampleQueryService`，不是最终寄样域读模型实现。
- real-pre 订单、转链、寄样自动完成、业绩和 Dashboard 真实 E2E 未验证，不能用本地测试替代。
- GitHub push 仍受 443 网络不可达影响，待网络恢复后再推送。
