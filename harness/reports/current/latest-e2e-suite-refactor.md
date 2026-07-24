# Playwright E2E 整体重构证据

## 运行摘要

- 时间：2026-07-24
- 环境：本地 test 代码检查；real-pre 未执行
- 分支：`codex/e2e-suite-refactor`
- 基线：`c125188b`
- 工作树：有本次未提交变更
- 远端部署：未执行

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
| test 容器、后端和完整 E2E | NOT RUN：缺少 `.env.test` |
| real-pre E2E | NOT RUN：未满足真实环境前置条件 |

## 阻塞与风险

- 标准 `agent-do` 未能启动 Node Harness 验证，当前本机 Node 环境缺少 `jiti`。
- 兼容入口 `harness:verify` 还受到当前 PowerShell 环境缺少 `Get-FileHash` 的阻塞。
- 没有 `.env.test`，因此登录 API、角色 storageState、V1-P0 和后端业务闭环尚未运行。
- 本次没有 SSH、Docker real-pre 或生产环境操作。

## 结论

`PARTIAL`。E2E 代码结构、认证入口、页面对象、跳转等待和静态检查已完成；完整 test 环境回归和 real-pre 真实闭环仍待配置环境后执行，不能提前标记为 PASS。

## Retro

本次有效改进：将认证和导航的重复实现集中到共享 helper，后续登录或启动流程变化只需要修改一个入口。下一步改进动作：补齐 `.env.test` 与本机 Harness 运行时后，执行 `npm run e2e:login`、`npm run e2e:v1-p0`，再按失败证据继续迁移领域用例。
