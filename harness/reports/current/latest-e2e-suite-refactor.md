# Playwright E2E 整体重构证据

## 运行摘要

- 时间：2026-07-24
- 环境：本地 TypeScript 检查；`my-second-brain-server` 测试服务器通过 SSH 隧道验收
- 分支：`codex/e2e-suite-refactor`
- 基线：`c125188b`
- 本轮验证提交：`6677a7bf`
- 工作树：代码已提交并推送；本报告待提交
- 远端部署：未执行；未构建、重启或部署测试服务器和生产服务器

## 本轮修复

- 商品链等待外链图片进入完成态后再检查 `naturalWidth`，避免把正常慢加载误报为失败。
- 订单归因和寄样链的数据库探针支持 `E2E_SSH_TARGET`，可直接读取测试服务器容器内 PostgreSQL，不再依赖本机 Docker。
- 订单归因证据区分“同步结果有归因订单”和“当前页面抽样命中归因订单”，避免把两者混为一谈。
- 寄样链使用统一账号配置；按当前后端合同校验创建达人时自动建立首个渠道认领，移除会返回重复认领码 462 的二次调用。

## 已完成的 E2E 收敛

- 认证协议收敛到 `tests/e2e/helpers/auth.ts`。
- `auth.setup.ts`、API 断言和 real-pre 认证注入共用同一登录实现。
- 新增 `LoginPage`、`AppShellPage` 和共享 `fixtures.ts`。
- 普通页面跳转统一使用 `gotoApp`；业务 spec 不再直接写重复认证和固定跳转。
- 35 个 E2E 文件完成迁移；当前 Playwright 可发现 153 个用例、45 个文件。
- real-pre RBAC 用例统一账号密码来源，401 立即停止重试，避免错误配置锁死测试账号。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 变更 E2E TypeScript 检查 | PASS |
| `git diff --check` | PASS |
| Playwright `--list` | PASS：153 tests / 45 files |
| real-pre 31 商品链 | PASS：`1 passed`；真实商品链和图片检查通过 |
| real-pre 32 订单同步 + 归因 | 测试 PASS：`1 passed`；业务结论 `PENDING` |
| real-pre 33 寄样链 | 测试 PASS：`1 passed`；业务结论 `PENDING` |
| real-pre 34 业绩看板 | PASS：`1 passed`；公式和页面无运行时错误 |
| real-pre 35 RBAC | PASS：`1 passed`；六角色登录、权限和范围探针通过 |
| real-pre 36 清理计划 | PASS：`1 passed`；仅生成 PlanOnly 计划，未执行删除 |
| 测试服务器后端健康 | PASS：`/api/system/health` HTTP 200 |
| 测试服务器前端健康 | PASS：`/login` HTTP 200 |
| 测试服务器容器 | PASS：前端、后端、PostgreSQL、Redis 均 healthy |
| 生产服务器 | 未触碰 |

## 真实业务证据

- 订单同步窗口 30 分钟：`totalFetched=34`、`created=4`、`updated=30`、`attributed=1`、`unattributed=33`、`failed=0`。
- 当前订单页抽样未命中归因订单，因此 32 只能记为 `PENDING_ATTRIBUTED_ORDER_SAMPLE`，不能写成归因闭环 PASS。
- 寄样链已验证：达人创建自动认领、重复寄样请求被业务码 462 拦截、状态经过 `PENDING_AUDIT -> PENDING_SHIP -> SHIPPED -> PENDING_TASK`。
- 当前没有真实成交订单触发寄样自动完成，因此 33 只能记为 `PENDING_REAL_ORDER_FOR_HOMEWORK`。
- `QA20260724172035`、`QA20260724172129`、`QA20260724172306`、`QA20260724172353` 已生成 PlanOnly 清理计划；未执行清理 SQL。测试服务器仍保留这些 QA 范围数据，待管理员确认后按计划清理。

## 未执行项与风险

- 完整 P0 编排器未直接运行：其 preflight 依赖本机 Docker，不能指向远端测试服务器。
- `08-real-pre-douyin-integration` 未运行：一键刷新可能触发真实上游写操作，本轮没有扩大副作用范围。
- 真实业务闭环仍缺“真实付款订单 -> 归因命中 -> 寄样自动完成 -> 业绩和 Dashboard 逐字段对账”。
- 本轮只修改和验证 E2E 测试代码，没有应用代码变更，因此不需要重启容器；远端服务保持原样。

## 结论

`PARTIAL`。E2E 代码、远端登录、商品链、RBAC、看板基础检查和远端数据库探针已通过；订单归因和寄样自动完成仍缺真实业务样本，不能宣称完整 P0 闭环通过。生产环境未触碰。

## Retro

本轮把远端数据库读取封装为统一 SSH/本地双模式 helper，避免测试环境必须安装 Docker；对外链图片增加完成态等待，消除已确认的慢加载误报；同时按后端真实合同校正寄样链认领断言。后续若要把 32、33 从 `PENDING` 提升为 `PASS`，必须补真实订单和自动完成功能的可核验样本，不应继续放宽断言。
