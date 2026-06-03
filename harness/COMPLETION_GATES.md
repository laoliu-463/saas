# Completion Gates

## 作用

本文件定义 Agent 执行任务时的完成门禁。任何任务完成前必须选择一个 Gate，并按 Gate 输出证据。Agent 不得仅因完成代码修改、编译通过、单个接口通过或单个页面可打开而声明 DONE。

## Gate 选择规则

1. Agent 必须在任务开始时声明本次选择的 Gate。
2. 如果执行中发现影响范围扩大，必须升级 Gate，不能降级。
3. 如果任务同时命中多个 Gate，取最高级别 Gate。
4. Gate 4 > Gate 3 > Gate 2 > Gate 1 > Gate 0。

## 统一最终输出模板

每个任务结束时，Agent 必须按以下模板输出：

```text
## Final Status
DONE / PARTIAL / BLOCKED_BY_SAMPLE / BLOCKED_BY_EXTERNAL / FAILED

## Selected Gate
Gate X - xxx

## Scope
- 修改领域：
- 修改文件：
- 影响接口：
- 影响页面：
- 影响表：
- 影响容器：

## Changes
- ...

## Verification
| 检查项 | 结果 | 证据 |
|---|---|---|
| Build | PASS/FAIL | ... |
| Container Reload | PASS/FAIL/SKIP | ... |
| Health | PASS/FAIL/SKIP | ... |
| API Smoke | PASS/FAIL/SKIP | ... |
| UI Smoke | PASS/FAIL/SKIP | ... |
| SQL Reconcile | PASS/FAIL/SKIP | ... |
| Business Flow | PASS/FAIL/PARTIAL/BLOCKED | ... |

## Evidence Paths
- harness/reports/xxx.md
- runtime/qa/out/xxx/report.md

## Not Done / Blockers
- 没有则写：None
- 有则必须写清楚，且 Final Status 不能是 DONE

## State Updates
- CURRENT_STATE.md: updated / not needed
- DOMAIN_STATUS.md: updated / not needed
- DECISIONS.md: updated / not needed
- HARNESS_CHANGELOG.md: updated

## Git
- branch:
- status:
- commit:
```

---

## Gate 0：Docs Only

适用：

- 只修改 harness / docs / README / 计划 / 报告
- 不修改 Java / Vue / SQL / Docker / 配置

必须验证：

- `git diff` 仅包含文档或 harness 文件
- safety-check docs dry-run 通过
- 不得重启容器
- 输出报告路径

推荐命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
git status --short
```

完成状态：

- 通过以上检查才可 DONE
- 如果发现代码文件变更，升级到 Gate 1/2/3

---

## Gate 1：Backend Change

适用：

- 修改 Java / Spring Boot 后端代码
- 修改 Mapper / Service / Controller / Security / Scheduler
- 修改 migration 或 SQL

必须验证：

1. 后端编译通过
2. 相关单测或最小集成测试通过
3. 容器重新构建或重启后加载新 jar
4. 后端 health = UP
5. 后端日志无启动异常
6. 至少一个相关 API smoke 通过
7. 如涉及数据库，必须有只读 SQL 对账

推荐命令：

```powershell
mvn -f backend/pom.xml -DskipTests package
docker compose -f docker-compose.real-pre.yml ps
docker compose -f docker-compose.real-pre.yml logs backend --tail=200
curl http://localhost:8081/api/system/health
```

完成状态：

- 编译通过但容器未重启：PARTIAL
- 容器健康但业务 API 未验证：PARTIAL
- 业务验证失败：BLOCKED 或 FAILED，不得 DONE

---

## Gate 2：Frontend Change

适用：

- 修改 Vue / Vite / Pinia / Naive UI / 路由 / API 调用

必须验证：

1. 前端构建通过
2. 页面可访问
3. 修改页面的关键交互可执行
4. 如依赖后端接口，必须验证真实接口返回
5. 前端容器或开发服务已加载新代码
6. 浏览器控制台无关键报错

推荐命令：

```powershell
cd frontend && npm run build
docker compose -f docker-compose.real-pre.yml ps
curl http://localhost:3001/healthz
```

完成状态：

- build 通过但页面没打开：PARTIAL
- 页面打开但接口失败：PARTIAL / BLOCKED
- 控制台存在动态模块加载失败：FAILED

---

## Gate 3：Domain Change

适用：

- 修改任一领域业务规则
- 修改用户域 / 商品域 / 达人域 / 寄样域 / 订单域 / 业绩域 / 配置域 / 分析模块

必须验证：

1. 本领域主流程通过
2. 本领域权限范围通过
3. 上游输入验证通过
4. 下游消费者验证通过
5. 相关事件或状态流转验证通过
6. 数据库状态与接口返回一致

### 各领域闭环要求

#### 用户域

- 登录
- 获取菜单
- 多角色 / 数据范围
- 管理员 all、组长 group、普通成员 self
- 至少验证一个业务域读取 data_scope 后生效

#### 商品域

- 活动同步或样本准备
- 商品入库 / 上架 / 展示
- 商品库查询
- 复制讲解 / 转链映射
- 商品负责人或活动默认招商归因可被订单 / 业绩读取

#### 达人域

- 达人创建 / 补全
- 认领
- 保护期
- 标签 / 地址
- 寄样域可读取达人信息

#### 寄样域

- 申请
- 7 天限制
- 招商审核
- 运营发货
- 签收 / 待交作业
- 订单触发已完成

#### 订单域

- 同步任务
- 原始订单落库
- pick_source / native 归因
- 默认渠道 / 默认招商
- 订单事件发出
- 寄样 / 业绩下游消费成功

#### 业绩域

- 订单同步后生成 performance
- final_channel / final_recruiter 正确
- 预估 / 结算双轨金额正确
- 退款 / 失效冲正
- summary 与明细对账

#### 配置域

- 配置读取
- 配置更新
- 缓存失效
- 至少一个消费域读取新值生效

#### 分析模块

- 业绩事件消费
- 汇总表更新
- Dashboard API 返回
- 汇总值与明细 SQL 对账

完成状态：

- 只验证本域，不验证下游：PARTIAL
- 下游失败：BLOCKED / FAILED
- 数据不一致：FAILED

---

## Gate 4：E2E Business Flow

适用：

- 用户要求"跑通流程"
- 改动影响两个以上领域
- P0 / P1 修复
- 上线前 / 交付前验证
- 订单、寄样、业绩、看板相关任务

### 必须验证三条主线

#### 渠道链

认领达人 -> 选品 -> 复制讲解 / 转链 -> 寄样申请 -> 审核发货 -> 订单同步 -> 寄样完成 -> 业绩生成 -> 看板可见

#### 招商链

同步活动 -> 商品上架 -> 审核寄样 -> 订单归因默认招商 -> 业绩生成 -> 招商视角可见

#### 管理链

创建 / 配置用户 -> 分配角色 / 部门 / 数据范围 -> 配置业务规则 -> 各业务域读取规则 -> 权限和数据范围生效

### 必须输出

- 测试环境
- 测试账号
- 样本 ID
- API 路径
- SQL 对账
- 容器状态
- 日志摘要
- 报告路径

完成状态：

- 三条主线全部通过：DONE
- 有真实订单样本缺失：BLOCKED_BY_SAMPLE，不得 DONE
- 有代码 bug：FAILED
- 有外部接口不可用：BLOCKED_BY_EXTERNAL

---

## 强制规则

以下规则适用于所有 Gate：

1. 没有 evidence path，不得 DONE。
2. 修改后端但未重启 / 确认容器加载，不得 DONE。
3. 修改前端但未 build / 页面验证，不得 DONE。
4. 涉及订单 / 寄样 / 业绩 / 看板但未跑下游闭环，不得 DONE。
5. 真实订单样本缺失时必须写 BLOCKED_BY_SAMPLE，不能写 DONE。
6. 外部 API / token / 样本限制必须明确标为 BLOCKED_BY_EXTERNAL 或 BLOCKED_BY_SAMPLE。
7. 只允许 docs-only 任务跳过容器重启，但必须说明未修改代码。
8. 未更新 DOMAIN_STATUS / CURRENT_STATE 就结束任务，不得 DONE。
9. 未生成 evidence report 就结束任务，不得 DONE。

## Git Gate（G0-G4 内部子门禁）

任何 Gate 都必须按 `harness/skills/git-change-control.md` 执行下列 Git 子门禁。

### Gate G0：Docs-only clean

适用：纯文档 / Harness 规则 / 状态文件 / 报告变更。

必须验证：

- `git status --short` 输出已分类。
- `git diff --name-only` 仅含 `harness/` / `docs/` / `AGENTS.md` / `CLAUDE.md` / 报告。
- `git diff --check` 无输出。
- `git diff --cached --check` 无输出（如有 staged）。
- 状态文件变更已记录到 `CURRENT_STATE.md` / `DOMAIN_STATUS.md` / `HARNESS_CHANGELOG.md`。
- 未使用 `git add .` / `git add -A` / `git add <dir>/`。
- commit message 含类型和 scope（如 `docs(harness): GIT-HARNESS-001 add git worktree governance gates`）。

### Gate G1：Frontend clean

适用：纯前端 UI / 路由 / 状态 / API 调用变更。

必须验证：

- `git status --short` 输出已分类。
- `git diff --name-only` 仅含 `frontend/src/` / `frontend/package.json` / `frontend/vite.config.ts` / E2E。
- `git diff --check` 无输出。
- frontend `npm run build` 通过。
- 相关 vitest 通过。
- frontend `safety-check` 通过。
- commit message 含类型和 scope（如 `feat(product-ui)` / `fix(frontend)`）。
- 不含 backend / SQL / Docker / env / harness docs。

### Gate G2：Backend clean

适用：纯后端 Java / MyBatis-Plus / Service / Controller / Mapper / 测试变更。

必须验证：

- `git status --short` 输出已分类。
- `git diff --name-only` 仅含 `backend/src/main/` / `backend/src/test/` / `backend/pom.xml`。
- `git diff --check` 无输出。
- `mvn -f backend/pom.xml test` 通过。
- `mvn -f backend/pom.xml -DskipTests package` 通过。
- backend `safety-check` 通过。
- commit message 含类型和 scope（如 `fix(user-domain)` / `feat(order)`）。
- 不含 frontend / SQL / Docker / env / harness docs。

### Gate G3：Deploy clean

适用：Docker / Compose / env / 部署脚本 / 部署配置变更。

必须验证：

- `git status --short` 输出已分类。
- `git diff --name-only` 仅含 `docker-compose*.yml` / `Dockerfile*` / `deploy-*.ps1` / `runbook` / `harness/environment/`。
- `git diff --check` 无输出。
- 远端 `git fetch` + `git checkout` + `git pull --ff-only` 成功。
- 远端 `git rev-parse HEAD` 等于本地 HEAD。
- 远端 `git status --short` 为空。
- 部署后 `docker compose ps` 全部 healthy。
- health check 通过。
- jar / dist 时间与 commit 时间一致。
- commit message 含类型和 scope（如 `chore(deploy)` / `feat(deploy)`）。
- `.env` 文件未 commit。

### Gate G4：Session clean

适用：所有任务结束 / Session Exit Gate。

必须验证：

- `git status --short` 输出已分类。
- 所有 dirty 已归入十种分类之一（`current_task / previous_partial / docs_state / report_only / frontend / backend / sql_migration / docker_deploy / cleanup_retire / unknown`）。
- 不存在 unknown dirty；如存在，写 `BLOCKED_DIRTY_UNKNOWN`，禁止 DONE。
- 当前任务已 commit + push 到目标 remote。
- 状态文件已更新。
- 临时 debug 文件已清理。
- 报告已生成并归档。
- Dirty 归属登记表已写入。
- 下一任务队列已更新（如有 PARTIAL 残留）。
- 终态为 `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一。

### Git 子门禁强制规则

- Gate G0-G4 任一未通过，最终状态不得 DONE。
- 同一 commit 不得跨 Gate（如 docs commit 不得含 backend Java）。
- 业务代码 commit 不得含状态文件（除非范围清晰且有说明）。
- 多任务 dirty 必须分批提交，禁止单 commit 包含多任务变更。
