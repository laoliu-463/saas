# ADR-006 real-pre 作为上线前联调环境

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。

## 状态

- [V1 必做] 已采纳。

## 背景

- [V1 必做] test/mock 能证明本地业务闭环，但不能证明第三方真实接口可用。
- [V1 必做] real-pre 用于上线前真实 Token、真实接口、真实数据路径联调。

## 决策

- [V1 必做] real-pre 是上线前联调环境。
- [V1 必做] real-pre 必须关闭 mock：`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`。
- [V1 必做] real-pre 的 PASS 必须有真实接口响应、系统入库和日志证据。
- [V1 必做] 缺 Token、权限包、真实样本或限流导致失败时，标记 BLOCKED 并保留证据。

## 影响

- [V1 必做] real-pre 不能用 test/mock 数据冒充通过。
- [V1 必做] 验收报告必须区分 PASS、PASS_NEEDS_CLEANUP、BLOCKED、PENDING、FAIL。
- [V1 简化] profile 名称存在旧口径差异时，以实际 `.env.real-pre`、compose 和启动日志为准。

## 验证方式

- [V1 必做] 运行 `npm run e2e:real-pre:p0:preflight`。
- [V1 必做] 运行 `npm run e2e:real-pre:p0` 或记录阻塞原因。
- [V1 必做] 证据登记到 [../验收/验收证据索引.md](../验收/验收证据索引.md)。

