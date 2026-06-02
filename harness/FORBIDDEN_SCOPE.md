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

