# Playwright E2E 整体重构证据

## 运行摘要

- 时间：2026-07-24
- 环境：本地 test 代码检查；`my-second-brain-server` 只读 Docker / SSH 隧道烟测
- 分支：`codex/e2e-suite-refactor`
- 基线：`c125188b`
- 本轮验证提交：`d08915d1`
- 工作树：干净
- 远端部署：未执行；仅通过 SSH 隧道访问已运行容器

## 已完成

- 认证协议收敛到 `tests/e2e/helpers/auth.ts`。
- `auth.setup.ts`、API 断言和 real-pre 认证注入共用同一登录实现。
- 新增 `LoginPage`、`AppShellPage` 和共享 `fixtures.ts`。
- 普通页面跳转统一使用 `gotoApp`；业务 spec 中不再直接写 `localStorage`、固定页面跳转或重复认证。
- 35 个 E2E 文件完成迁移；当前 Playwright 可发现 153 个用例、45 个文件。
- 新增 `npm run e2e:login` 和 `tests/e2e/README.md`。
- 统一 real-pre RBAC 用例的账号密码来源，移除角色级硬编码密码。
- 统一 real-pre 导出权限合同：渠道组长和渠道专员按当前后端授权可访问导出接口。
- 认证 helper 对 HTTP 401 立即停止重试，避免错误配置反复撞击 real-pre 登录锁。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 前端生产构建 | PASS |
| 变更 E2E TypeScript 检查 | PASS |
| Playwright `--list` | PASS：153 tests / 45 files |
| 本地前端登录页浏览器烟测 | PASS：1 passed |
| Harness typecheck | PASS |
| Harness tests | PASS：293 passed / 1 skipped |
| Harness 文件门禁 | TASK_GATE=PASS；REPOSITORY_HEALTH=PARTIAL（历史目录债务） |
| `my-second-brain-server` Compose | PASS：4 个服务均 healthy，未执行启动或重启 |
| 远端镜像摘要 | PASS：backend/frontend 均存在 RepoDigest |
| 远端 `/healthz` | PASS：通过 `localhost:3001` SSH 隧道 |
| 远端管理员登录 API | PASS：使用未提交的受控 `ADMIN_PASSWORD`，HTTP 200 且返回 Token |
| 远端登录页浏览器烟测 | PASS：`00-health`、`01-login` 共 2 passed |
| 远端业务账号密码修复 | PASS：管理员重置 5 个业务账号均返回业务码 200，5 个账号随后均登录 200 |
| 远端六角色 storageState setup | PASS：管理员、招商组长、招商专员、渠道组长、渠道专员、运营共 6 个状态生成成功 |
| 远端管理员 Dashboard | PASS：`02-dashboard` 与 setup 共 2 passed |
| 远端 real-pre RBAC 35 | PASS：六角色登录、页面权限、关键接口权限与范围探针，`1 passed` |
| test 容器、后端和完整 E2E | NOT RUN：当前远端是 real-pre 形态栈，不是本地 mock test 栈 |
| real-pre 完整业务 P0 | NOT RUN：本轮只执行无副作用 RBAC 35，商品、订单、寄样和真实上游链路未执行 |

## 阻塞与风险

- 标准 `agent-do` 未能启动 Node Harness 验证，当前本机 Node 环境缺少 `jiti`。
- 兼容入口 `harness:verify` 还受到当前 PowerShell 环境缺少 `Get-FileHash` 的阻塞。
- 当前干净 worktree 没有 `.env.test`；远端验证按文档使用 `my-second-brain-server` 隧道和 `localhost:3001/18081`，没有复制或提交任何环境文件。
- 六个角色账号均存在且启用；已按管理员操作重置 5 个业务账号密码。一次错误配置触发两个账号临时登录锁，已等待锁自然过期；未删除 Redis 键、未绕过认证。
- RBAC 35 首次暴露的是测试合同与后端权限不一致，已修正为当前真实授权后通过；验证过程只读取页面和接口。
- 本次只读检查未执行远端 Docker 启动、重启、构建、部署或生产服务器操作。

## 结论

`PARTIAL`。E2E 代码结构、认证入口、页面对象、跳转等待、远端健康、管理员登录、六角色认证 setup、管理员 Dashboard 和 RBAC 35 已通过；商品、订单、寄样、真实上游和完整 P0 业务套件尚未运行。未触碰生产环境。

## Retro

本次有效改进：将认证和导航的重复实现集中到共享 helper；账号密码统一从环境配置读取；401 不再重试，避免测试把账号锁死；RBAC 合同与当前后端权限保持一致。远端验证继续遵守只读优先，下一步再决定是否进入有明确样本和回滚方案的业务 P0。
