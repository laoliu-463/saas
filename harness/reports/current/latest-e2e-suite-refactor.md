# Playwright E2E 整体重构证据

## 运行摘要

- 时间：2026-07-24
- 环境：本地 test 代码检查；用户提供的独立测试服务器只读烟测
- 分支：`codex/e2e-suite-refactor`
- 基线：`c125188b`
- 工作树：干净
- 远端部署：未执行；仅访问测试服务器 HTTP 端口

## 已完成

- 认证协议收敛到 `tests/e2e/helpers/auth.ts`。
- `auth.setup.ts`、API 断言和 real-pre 认证注入共用同一登录实现。
- 新增 `LoginPage`、`AppShellPage` 和共享 `fixtures.ts`。
- 普通页面跳转统一使用 `gotoApp`；业务 spec 中不再直接写 `localStorage`、固定页面跳转或重复认证。
- 35 个 E2E 文件完成迁移；当前 Playwright 可发现 153 个用例、45 个文件。
- 新增 `npm run e2e:login` 和 `tests/e2e/README.md`。

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
| 独立测试服务器 `/healthz` | FAIL：返回另一套 Vben Admin 页面，不是本项目前端健康响应 |
| 独立测试服务器登录页 | FAIL：缺少本项目 `login-username` 测试标识 |
| 独立测试服务器 `/api/auth/login` | FAIL：使用项目默认测试账号返回 HTTP 500 |
| test 容器、后端和完整 E2E | NOT RUN：当前测试服务器与本项目构建不匹配 |
| real-pre E2E | NOT RUN：未满足真实环境前置条件 |

## 阻塞与风险

- 标准 `agent-do` 未能启动 Node Harness 验证，当前本机 Node 环境缺少 `jiti`。
- 兼容入口 `harness:verify` 还受到当前 PowerShell 环境缺少 `Get-FileHash` 的阻塞。
- 当前干净 worktree 没有 `.env.test`；用户工作区的 `.env.test` 仍指向本机 `127.0.0.1:3000/8080`，不能直接作为远端测试配置。
- 用户提供的测试服务器前端返回另一套管理系统，后端登录返回 HTTP 500；登录 API、角色 storageState、V1-P0 和后端业务闭环尚未运行。
- 本次没有 SSH、Docker real-pre 或生产环境操作。

## 结论

`PARTIAL`。E2E 代码结构、认证入口、页面对象、跳转等待和静态检查已完成；当前独立测试服务器不是本项目可验证的 SaaS 测试构建，完整 test 环境回归仍被环境版本不匹配阻塞。不能把该服务器结果标记为 PASS，也未触碰生产环境。

## Retro

本次有效改进：将认证和导航的重复实现集中到共享 helper，后续登录或启动流程变化只需要修改一个入口。下一步改进动作：让独立测试服务器部署与当前仓库 SHA 对齐，并提供不含生产凭据的远端 E2E 地址配置；随后执行 `npm run e2e:login`、`npm run e2e:v1-p0`，再按失败证据继续迁移领域用例。
