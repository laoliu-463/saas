# Forbidden Scope

## V1 明确不做

- 不做独家达人。
- 不做独家商家。
- 不做毛利口径扩展。
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
- 禁止把毛利作为 P0 验收指标。
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
- 禁止用“应该没问题”替代验证结果。
