# RBAC Phase 2 主分支集成与远端部署证据

## 基本信息

- 时间：2026-07-15（Asia/Shanghai）
- 环境：远端 `real-pre`
- 目标分支：`feature/auth-system`
- 部署代码提交：`38689b0a00d17b0ee8f11d3cf38862c0973cc168`
- 集成提交：`539fbb68e27cf781b1536516f54a09a07e2c07e0`
- Completion Gate：Gate 4（管理链与上线前验证）
- 总结论：`PARTIAL`

## Git 集成与本地验证

- RBAC 分支与服务器实际跟踪的 Gitee 主线以 Git `ort` 无冲突合并；GitHub、Gitee 的 `feature/auth-system` 均已 fast-forward 到部署代码提交。
- 后端全量测试：3426 tests，0 failures，0 errors，3 skipped；package 通过。
- 前端：94 files、710 tests 通过；typecheck、production build 通过。
- real-pre safety-check：`PASS`；未开启 mock/test，未提交或输出 `.env.real-pre`。
- Harness：`TASK_GATE=PASS`；`REPOSITORY_HEALTH=PARTIAL`，原因为既有 `harness/reports` 根目录 23 个文件高于目标 20，本任务未新增恶化。
- npm audit：6 个既有漏洞（1 low、1 moderate、2 high、2 critical）；本任务未擅自升级依赖。

## 数据库备份与迁移

- 迁移前数据库大小：18,247,327,079 bytes；备份目录可用空间：67,298,760 KB。
- 备份：`/opt/saas/backups/rbac-authz-foundation-pre-20260715-091606.dump`。
- 备份大小：2,340,976,610 bytes；SHA-256：`57644624ba97562eb2b01536e512f94eaabe83908be5bae32482abb88a913fb3`。
- 迁移文件：`backend/src/main/resources/db/alter-authorization-foundation-20260713.sql`；SHA-256：`5e724b1b1e0999dc140bf42837c467640134f2b8bed741848f270912324afe6c`。
- 迁移前 catalog：`0|0`；无持续超过 5 分钟的事务。
- 在单事务中执行增量 migration 成功；迁移后 catalog：4 张授权表、1 个 `sys_user.authz_version` 列。
- `sys_user.authz_version` 为 `bigint NOT NULL DEFAULT 1`；非法版本数为 0；命名索引数为 6。
- 四张授权表记录数均为 0，未写 permission/role seed，未改变业务权限矩阵。
- 第一次尝试仅因只读预检命令引号错误退出，DDL 尚未执行；修正传输方式后完成迁移。

## 固定部署与运行态

- 使用固定脚本 `harness/scripts/commands/deploy-remote.ps1` 完成拉取、构建、镜像重建、容器重启和健康检查。
- 远端 Maven package：`BUILD SUCCESS`；JAR 81,064,678 bytes，制品 guard 通过。
- 新 backend 镜像：`sha256:b414818f9d146e8137e1b03ee36c577e97b6a79f69f9cc7a3672a817b00c917e`。
- 新 frontend 镜像：`sha256:9c72850133d70c7795d8c52676a327445b5ead6fa8a237f7e2ccf3e7bd012134`。
- backend、frontend、PostgreSQL、Redis 均为 `healthy`；backend `/api/system/health` 返回 `UP`，frontend 返回 `ok`。
- 容器环境保持 `SPRING_PROFILES_ACTIVE=real-pre`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
- 未设置 Authorization 模式环境变量，配置解析为 `LEGACY(default)`；新 RBAC 未进入 `SHADOW` 或 `ENFORCE`。
- 重启后授权 catalog 保持 4 表 1 列，授权事实表仍为空，非法用户版本为 0。
- Redis 中 `authz:snapshot:*` 和全部 `authz:*` 键数量均为 0；未认证 `/api/users?page=1&size=1` 返回统一 HTTP 401 JSON。
- backend 启动致命错误计数为 0。

## 业务验证

### real-pre preflight

- `PASS`：frontend、backend health、admin 登录、real-pre 环境、抖音 token、数据库 schema、可复用推广映射、cleanup plan。
- 证据：`/opt/saas/app/runtime/qa/out/real-pre-preflight-20260715-092638`。

### real-pre P0

- 总结论：`FAIL`；证据：`/opt/saas/app/runtime/qa/out/real-pre-p0-20260715-092657`。
- `PASS`：preflight、商品链、业绩看板、RBAC scope、cleanup plan（需后续清理）。
- `FAIL`：抖音集成页面未出现“商品 SKU 已验证”，页面展示“权限不足，无法执行当前操作”。
- `PENDING`：真实订单全部未归因，不能证明订单归因链；无真实成交订单，不能证明寄样自动完成链。
- SKU 页面失败定向重跑仍可复现；证据：`/opt/saas/runtime/qa/out/rbac-deploy-sku-diagnose-20260715-093337`。
- 同一管理员 JWT 的角色为 `admin`、`authzVersion=1`；直接调用 backend SKU probe 连续返回 HTTP 200 且有 1 条 SKU，frontend proxy 独立探针也返回 HTTP 200。
- 失败窗口中 UI 请求未到达 backend API timing/gateway 日志；没有 JWT、角色或授权版本拒绝日志。阶段性结论：已排除稳定 RBAC 角色/版本回归和稳定 Nginx 路由失败，但客户端请求时序、前端状态或瞬时上游/网关因素仍未定位，不能给最终根因。

### 六角色业务流

- 总结论：`FAIL`，5/13 steps 通过；证据：`/opt/saas/app/runtime/qa/out/real-pre-role-business-e2e-20260715-093845`。
- `PASS`：环境守卫、六角色登录及用户上下文、token preflight、活动分配、六账号登出后旧 token 拒绝。
- 首个失败与 P0 相同：管理员 SKU 页面断言超时。
- 独立失败：招商组长同步返回异步状态 `QUEUED`，测试预期非队列中状态。
- 后续 6 个步骤因前置 `productId` / `sampleId` / `sampleRequestNo` 未生成而级联失败，不能单独解释为权限拒绝。
- SSH 会话中途断开，但测试进程继续运行并写出完整 FAIL 报告；无 OOM，主机资源和容器状态正常，因此不重复执行。

## 回滚边界

- 旧 backend 镜像：`sha256:8e475eeded60b5cd8366bf1d6a740f7062ace6b28013bab9adc6e19cffb9ac9f`。
- 旧 frontend 镜像：`sha256:e95f23c345e124cf3b84fb5243d5c9451c2b14179cf7eac9088e902ea9bc4c8d`。
- 代码和镜像可回退；数据库新增表、列、索引保留，不执行破坏性 DROP。需要数据级恢复时使用部署前 dump，并由人工确认恢复窗口。

## Retro

- 有效做法：部署前以服务器实际跟踪主线合并，先备份再迁移；迁移后分别验证 catalog、约束、空 seed、Redis 和 LEGACY 模式，避免把基础设施部署误写成 RBAC 接管。
- 可执行改进：为固定部署脚本增加只读 Authorization schema guard；缺少 4 表 1 列时失败并提示人工 migration。负责人：后续 RBAC 运维任务；验证：缺 schema 负例与已存在 schema 正例。
- 可执行改进：将 SKU 页面动作增加前端请求级证据（请求是否发出、响应码和 request-id），并让异步商品同步 E2E 轮询终态而非立即断言。负责人：后续专项诊断；验证：定向 E2E 可区分未发请求、后端拒绝和上游失败。

## 结论

`PARTIAL`：主分支双远端推送、数据库备份、授权基础 migration、固定远端部署、容器健康、LEGACY 守卫和基础权限探针均已完成；但 real-pre P0 与六角色完整业务流均为 `FAIL`，订单归因和寄样自动完成仍为 `PENDING`。本轮不得声明全量业务验收通过，不得切换 `SHADOW/ENFORCE`，也不得把新 RBAC 表述为已接管权限。
