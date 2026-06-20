# Forbidden Scope

## V1 明确不做

- 不做独家达人。
- 不做独家商家。
- ~~不做毛利口径扩展。~~ **已撤销**（2026-06-05 用户决策：毛利纳入 V1）
- 不做个别品负责人覆盖。
- 不做商品负责人变更历史重算。
- 不做差异化提成。
- 不做 Cookie 池 / 代理池。
- 不做物流 API 自动跟踪作为 V1 必需能力。
- 不做外部抖店快速寄样作为 V1 必需能力。
- 不做数据平台整体导出。
- 不用 mock 数据证明真实业务闭环。

## DDD / V1 禁止实现规则

### V1 禁止启用

- 禁止启用独家达人覆盖渠道归因。
- 禁止启用独家商家覆盖招商归因。
- 禁止用个别品负责人覆盖招商归因。
- ~~禁止把毛利作为 P0 验收指标。~~ **已撤销**（2026-06-05 用户决策：毛利要做）
- 禁止启用寄样 30 天自动关闭。
- 禁止用物流 API 阻塞寄样主流程。
- 禁止启用差异化提成。
- 禁止从 Spring Boot 重构为 FastAPI。
- 禁止把 V1 模块化单体拆成微服务。
- 禁止引入 Kafka / RabbitMQ 作为 V1 必需项。

### 领域边界禁止

- 禁止订单域计算提成。
- 禁止订单域写 `performance_records`。
- 禁止订单域直接更新寄样完成状态。
- 禁止订单域应用独家覆盖。
- 禁止业绩域同步抖音订单。
- 禁止业绩域修改订单原始事实。
- 禁止分析模块重新计算业绩归因。
- 禁止商品域直接写寄样表。
- 禁止寄样域直接同步订单。
- 禁止 Controller 编排复杂业务规则或状态机。
- 禁止跨领域直接访问对方 Repository；优先通过应用服务、Facade、查询 API 或领域事件。

### real-pre 数据破坏禁止

- 禁止 drop / truncate / delete 全表。
- 禁止绕过 repair / backfill 入口裸 SQL 批量直改业务事实。
- 禁止 `docker compose down -v` 或删除 PostgreSQL / Redis volume。

## real-pre 禁止事项

- 禁止清库。
- 禁止执行 `docker compose down -v`。
- 禁止删除 PostgreSQL / Redis volume。
- 禁止把 real-pre 改成 test / mock。
- 禁止打开 `APP_TEST_ENABLED=true`。
- 禁止打开 `DOUYIN_TEST_ENABLED=true`。
- 禁止把 `DOUYIN_REAL_UPSTREAM_MODE` 改成非 `live`。
- 禁止关闭真实抖音 API 开关后仍声明真实闭环通过。
- 禁止用 test 环境结果证明 real-pre 真实闭环。
- 禁止没有真实订单样本时声明渠道归因闭环已通过。

## Git 与密钥禁止事项

- 禁止提交 `.env`、`.env.real-pre`、`.env.test`。
- 禁止提交 `*.pem`、`*.key`、私钥、证书、凭证文件。
- 禁止输出或提交 Token、密码、密钥、OAuth code、数据库密码。
- 禁止把 `.env.real-pre` 从本机直接复制到远端作为交付结论。

## Git 工作区治理禁止事项

按 `harness/rules/skills/git/git-change-control.md` 执行：

- 禁止 `git add .`。
- 禁止 `git add -A`。
- 禁止 `git add <dir>/`（如 `git add backend/`、`git add frontend/`、`git add harness/`）。
- 禁止提交来源不明文件（unknown dirty）。
- 禁止混合多任务 commit。
- 禁止 dirty 工作区部署。
- 禁止从本地 dirty 拷贝到远端。
- 禁止未提交代码部署。
- 禁止远端 dirty 源码部署。
- 禁止把未部署写成已部署。
- 禁止把未提交写成已推送。
- 禁止把 PARTIAL 状态写成 DONE。
- 禁止状态文件混合错误状态（PARTIAL / BLOCKED / FAILED 与 DONE 混记）。
- 禁止任务未收口继续新任务，除非明确 PARTIAL 并登记。
- 禁止 `git commit --amend`（除非用户明确要求）。
- 禁止 `--no-verify` 跳过 commit hook（除非用户明确要求）。
- 禁止 `git push --force` 到 `main` / `master`。
- 禁止使用未在 `git status` 中归类为 `current_task` / `previous_partial` / `docs_state` / `report_only` / `frontend` / `backend` / `sql_migration` / `docker_deploy` / `cleanup_retire` 的文件进行 commit。
- 禁止 Git Intake Gate 不通过就开始任务。
- 禁止 Git Exit Gate 不输出合法终态就结束任务。

## 代码边界禁止事项

- 前端不得直接调用抖音 / 抖店开放接口。
- 前端不得硬编码核心业务规则、权限规则或状态机。
- 订单域不得计算提成或最终归属。
- 配置域不得执行具体业务规则。
- 分析模块不得重算业绩归属。
- SDK / Gateway 不得泄漏业务语义到第三方接口适配层。
- 数据库不得承载不可追踪的隐式业务流程判断。

## 结论禁止事项

- 禁止未构建就声明代码可用。
- 禁止未重启容器就声明容器内已生效。
- 禁止未执行健康检查就声明服务正常。
- 禁止未生成 evidence report 就声明完成。
- 禁止把 `BLOCKED`、`PENDING`、`PARTIAL` 写成 `PASS`。
- 禁止用"应该没问题"替代验证结果。

## 禁止提前完成 / 虚假完成

以下行为禁止：

1. 未重启容器却声称新代码已生效。
2. 只编译通过就声称业务完成。
3. 只测 Controller / API，不测数据库落库和下游领域。
4. 只测后端，不测前端实际页面或接口联动。
5. 只测 mock，不说明 real-pre 是否验证。
6. 将 BLOCKED、SKIP、WARN 包装成 PASS 或 DONE。
7. 缺少真实订单、真实 pick_source、真实 token 时声称订单闭环完成。
8. 未生成 evidence / report 就声称已验收。
9. 未更新 DOMAIN_STATUS / CURRENT_STATE 就结束任务。
10. 把"应该可以""理论上可以"写成"已完成"。
11. 未选择 Completion Gate 就声明任务完成。
12. 只验证本域，不验证下游消费者就声称领域闭环。
13. 修改影响两个以上领域但未跑 E2E 就声称完成。

如果任务存在无法验证项，必须使用以下状态之一：

- `DONE`：按 Gate 要求全部验证通过。
- `PARTIAL`：部分验证通过，仍有明确未验证项。
- `BLOCKED_BY_SAMPLE`：缺少真实订单 / pick_source / 样本。
- `BLOCKED_BY_EXTERNAL`：外部 API / token / 权限包不可用。
- `FAILED`：已复现失败，需要继续修复或回滚。
- `RISK_ACCEPTED_BY_USER`：用户明确接受剩余风险。

禁止使用模糊状态：

- 基本完成
- 应该没问题
- 已大致完成
- 待后续观察但算完成
- 理论上可以

## 禁止留下脏状态

Agent 禁止在以下状态退出：

1. build / test 失败但未说明。
2. 容器异常但未说明。
3. 临时文件未清理。
4. debug 日志、console.log、debugger 残留。
5. TODO / FIXME 无归属、无计划、无报告。
6. 修改了启动路径但未更新文档。
7. 修改了领域状态但未更新 DOMAIN_STATUS。
8. 修改了 harness 规则但未更新 `harness/rules/changelog.md`。
9. 没有最终 Session Exit Report。
10. 让下一个 Agent 需要重新猜测当前状态。
