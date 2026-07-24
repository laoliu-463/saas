# Playwright E2E 整体重构证据

## 运行摘要

- 时间：2026-07-24
- 环境：本地 test 代码检查；`my-second-brain-server` 只读 Docker / SSH 隧道烟测
- 分支：`codex/e2e-suite-refactor`
- 基线：`c125188b`
- 工作树：干净
- 远端部署：未执行；仅通过 SSH 隧道访问已运行容器

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
| `my-second-brain-server` Compose | PASS：4 个服务均 healthy，未执行启动或重启 |
| 远端镜像摘要 | PASS：backend/frontend 均存在 RepoDigest |
| 远端 `/healthz` | PASS：通过 `localhost:3001` SSH 隧道 |
| 远端管理员登录 API | PASS：使用未提交的受控 `ADMIN_PASSWORD`，HTTP 200 且返回 Token |
| 远端登录页浏览器烟测 | PASS：`00-health`、`01-login` 共 2 passed |
| 远端业务账号密码修复 | PASS：管理员重置 5 个业务账号均返回业务码 200，5 个账号随后均登录 200 |
| 远端六角色 storageState setup | PASS：管理员、招商组长、招商专员、渠道组长、渠道专员、运营共 6 个状态生成成功 |
| 远端管理员 Dashboard | PASS：`02-dashboard` 与 setup 共 2 passed |
| test 容器、后端和完整 E2E | NOT RUN：当前远端是 real-pre 形态栈，不是本地 mock test 栈 |
| real-pre E2E | NOT RUN：未满足真实环境前置条件 |

## 阻塞与风险

- 标准 `agent-do` 未能启动 Node Harness 验证，当前本机 Node 环境缺少 `jiti`。
- 兼容入口 `harness:verify` 还受到当前 PowerShell 环境缺少 `Get-FileHash` 的阻塞。
- 当前干净 worktree 没有 `.env.test`；远端验证按文档使用 `my-second-brain-server` 隧道和 `localhost:3001/18081`，没有复制或提交任何环境文件。
- 六个角色账号均存在且启用；已按管理员操作重置 5 个业务账号密码。首次验证时 `biz_leader` 命中过期登录锁，等待锁过期后已重新登录成功。
- 本次只读检查未执行远端 Docker 启动、重启、构建、部署或生产服务器操作。

## 结论

`PARTIAL`。E2E 代码结构、认证入口、页面对象、跳转等待、远端健康、管理员登录、六角色认证 setup 和管理员 Dashboard 已通过；商品、订单、寄样、真实上游和完整 P0 业务套件尚未运行。未触碰生产环境。

## Retro

本次有效改进：将认证和导航的重复实现集中到共享 helper，且已按文档 SSH 隧道验证真实运行栈。账号密码漂移统一由管理员重置；登录锁按安全策略等待过期，不重复撞击接口。下一步按无副作用优先级继续执行角色导航，再进入业务 P0。
