# RBAC Phase 2 主分支集成与远端部署证据

## 基本信息

- 时间：2026-07-15（Asia/Shanghai）
- 目标环境：远端 `real-pre`
- Completion Gate：Gate 4（管理链与上线前验证）
- Git Gate：G3 / G4
- 集成工作树：`D:\Projects\SAAS\.worktrees\integrate-rbac-to-auth-system-20260714`
- 集成分支：`codex/integrate-rbac-to-auth-system-20260714`
- 集成提交：`539fbb68e27cf781b1536516f54a09a07e2c07e0`
- 目标主分支：`feature/auth-system`
- 工作区：干净；本地 `.env.real-pre` 仅以被忽略硬链接供安全门禁读取，未提交、未输出内容

## 合并与推送证据

- Gitee 主线 `e3b7abfd` 与 RBAC 分支 `88659fb0` 从共同基点 `d7569b03` 分叉。
- 合并前 Gitee 独有 21 个提交、RBAC 侧独有 26 个提交；两侧变更文件交集为 0。
- Git `ort` 合并成功，无冲突，生成提交 `539fbb68`。
- `gitee/feature/auth-system` 与 `codex/rbac-shadow-runtime-plan` 均为集成提交祖先。
- GitHub `origin/feature/auth-system` 已由 `676de811` fast-forward 到 `539fbb68`。
- Gitee `feature/auth-system` 已由 `e3b7abfd` fast-forward 到 `539fbb68`。
- 两个远端 `ls-remote` 均返回完整提交 `539fbb68e27cf781b1536516f54a09a07e2c07e0`。

## 本地构建与测试

| 检查项 | 结果 | 证据摘要 |
| --- | --- | --- |
| 后端全量测试 | PASS | Surefire 606 个 XML；3426 tests，0 failures，0 errors，3 skipped；Maven 退出码 0 |
| 后端打包 | PASS | `mvn -f backend/pom.xml -DskipTests package`；生成 `backend/target/colonel-saas.jar` |
| 前端依赖安装 | PASS_WITH_RISK | `npm ci` 成功；审计报告 6 个既有漏洞（1 low、1 moderate、2 high、2 critical），本任务未擅自升级依赖 |
| 前端全量测试 | PASS | 94 files、710 tests 全部通过 |
| 前端类型检查 | PASS | `npm run typecheck` 退出码 0 |
| 前端生产构建 | PASS | `npm run build` 退出码 0；Vite 3699 modules transformed |
| real-pre safety-check | PASS | 仅检查敏感配置存在性，未输出值；未开启 mock/test |
| Harness 任务门禁 | PASS | `TASK_GATE=PASS`，无任务违规 |
| Harness 仓库健康 | PARTIAL | 既有债务：`harness/reports` 根目录 23 个文件，高于目标 20；本任务未新增恶化 |

## 远端只读预检

- SSH 目标：`saas`；目录 `/opt/saas/app`。
- 服务器分支：`feature/auth-system`。
- 服务器预检提交：`3e1c02e611a08138d448da924bea6509fd7cf2e5`。
- 服务器工作区：干净（porcelain 计数 0）。
- PostgreSQL `postgres-real-pre` 容器：存在并运行。
- 授权目录：`AUTHZ_CATALOG=0|0`，即 4 张授权基础表均不存在，`sys_user.authz_version` 列不存在。
- 预检全部为只读；未执行 SQL migration，未重启容器，未拉取代码，未改变远端配置或业务数据。

## 部署门禁与回滚边界

- 固定部署脚本 `harness/scripts/commands/deploy-remote.ps1` 不包含 `alter-authorization-foundation-20260713.sql`。
- 新后端映射并读写 `sys_user.authz_version`；在远端 catalog 为 `0|0` 时直接部署不满足数据库前置条件。
- 本轮已有授权只覆盖本地 real-pre migration，不能自动扩大为远端数据库写入授权。
- 所需 migration 为幂等、纯增量 DDL：新增 4 张授权基础表及 `sys_user.authz_version BIGINT NOT NULL DEFAULT 1`，不写 permission/role seed，不删除历史数据。
- 数据库回滚边界：代码与运行模式可回到旧提交/LEGACY；增量表、列和索引保留，不执行破坏性 DROP。
- 部署回滚基点：服务器当前提交 `3e1c02e6`；部署前仍需记录当前 backend/frontend 镜像或容器制品。

## 业务验证状态

- 远端部署：PENDING（等待远端 migration 明确授权）。
- 远端健康检查：PENDING（尚未部署）。
- 远端 LEGACY 运行模式：PENDING（尚未启动新代码）。
- 登录及角色权限链：BLOCKED_AUTH；既有本地管理员凭据连续登录返回 HTTP 401，不能记为 PASS。
- SHADOW/ENFORCE：未激活；本次部署目标仍是 `LEGACY(default)`，不得宣称新 RBAC 已接管业务权限。

## Retro 结论

本次先识别出 GitHub 与 Gitee 主线不一致，并以实际服务器跟踪的 Gitee 主线作为部署合并基线，避免覆盖服务器已部署的 21 个提交。集成代码通过后端 3426 测试和前端 710 测试。远端只读预检发现授权 schema 为 `0|0`，从而在部署前阻止了缺 schema 启动风险。后续固定部署脚本应在单独评审后增加“只检查 RBAC schema、缺失即失败”的 guard；是否自动执行 migration 仍应保持人工批准边界。

## 结论

`PARTIAL`：主分支合并与 GitHub/Gitee 推送已完成；远端数据库 migration、服务器拉取、容器重建、健康检查和业务验收均未执行。继续条件是用户明确批准在远端 real-pre 执行 `alter-authorization-foundation-20260713.sql`。
