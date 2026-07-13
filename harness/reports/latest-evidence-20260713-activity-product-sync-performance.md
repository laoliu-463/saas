# Evidence — 活动/商品同步性能收口

## 结论

- 本地 `real-pre`：`PASS`。
- 远端部署：`PENDING`，等待本报告随代码提交后执行固定部署脚本。
- 分支：`codex/ddd-user-role-application`。
- 验证前 HEAD：`0b7ef13e8b42277444623de28a2995346876b73f`。
- 工作区：非干净；存在用户其他任务改动，本次只允许选择性提交本任务文件。

## 修改与根因

- 状态分片预检首屏被再次请求：改为复用预检响应，单页 6 状态测试请求数由 12 降为 6。
- 展示规则按商品重复读状态/快照/活动配置：改为批量读取后按商品执行原规则。
- `PARTIAL` 优先同步无条件修复和重算整个活动：改为只处理本轮拉取商品；完整同步仍执行全活动修复和重算。
- 前端活动列表同步接口改为 `/colonel/activities/sync`，并轮询 `/sync-jobs/{jobId}`。
- 活动列表 Mapper 补齐状态同步时间字段，远端部署脚本补齐活动表迁移门禁。

## 构建与测试

- 后端定向回归：99 tests，0 failure，0 error。
  - 商品手动同步、状态分片、展示规则、活动列表同步、Controller。
- 后端打包：`mvn -f backend/pom.xml -DskipTests -Djacoco.skip=true package`，PASS。
- 前端 API 测试：62 tests，PASS。
- 前端类型检查：`npm run typecheck`，PASS。
- 前端生产构建：`npm run build`，PASS。
- Harness 50/50/200：PASS。
- 变更文件 `git diff --check`：PASS。

## Docker 与健康检查

- 固定脚本重建并重启 `backend-real-pre`，PASS。
- backend：`http://127.0.0.1:8081/api/system/health`，200，`UP`。
- frontend：`http://127.0.0.1:3001/healthz`，200。
- PostgreSQL / Redis / backend / frontend 容器均 healthy。

## 业务与性能证据

- 活动列表同步：真实上游 24 条，`SUCCESS`，页面轮询约 0.84 秒（前序同链路证据）。
- 商品同步样本：活动 `3223881`，本地快照约 10902 条。
- 请求：`PRIORITY_1000`，`maxRowsPerActivity=20`，状态 `[0,1]`。
- 点击触发到获得终态三轮：1150ms、1401ms、1150ms，全部小于 5 秒。
- 后端任务三轮：1046.290ms、1386.763ms、1048.911ms。
- 每轮拉取 23 条；终态 `PARTIAL` 是请求行数上限导致，不能写成 `SUCCESS`。
- 多线程日志存在状态分片并发执行；`parallelism=2`。
- 本轮商品库修复仅扫描 23 条；展示规则仅重算 23 个商品，耗时 32-54ms。

## 风险与回滚

- 上游限流、同活动锁占用会使任务保持 `QUEUED`；这是资源冲突口径，不应伪装为 5 秒终态。
- 完整全量同步仍按全活动修复/展示重算，耗时不承诺 5 秒；5 秒验收针对前端优先同步模式。
- 回滚：回退本次提交并按固定部署脚本重建 backend/frontend；无需数据回滚和清库。
