# Runbook: third-party integration

## 适用场景

抖音 / 抖店授权、Token、活动商品、转链、订单、物流、达人信息等真实上游联调。

## 前置检查

1. 读取 `docs/08-第三方对接总览.md`、当前对接项对应 `docs/对接/*.md`。
2. 读取 `docs/验收/real-pre联调手册.md` 和 `harness/rules/skills/ddd/real-pre-debug.skill.md`。
3. 执行 safety-check，确认 real-pre 未 mock 化。

## 操作步骤

1. 检查环境开关：`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
2. 检查 Token / 授权主体 / 权限包状态，只输出存在性和脱敏结果。
3. 收集后端日志、第三方响应摘要、DB/API 事实。
4. 先跑预检：

```powershell
npm run e2e:real-pre:p0:preflight
```

5. 样本充足后再跑 P0 或专项链路。

## 验证标准

- 真实接口响应、后端日志和 DB/API 结果能串成证据链。
- 缺 Token、权限包、限流或真实样本时，结论为 `BLOCKED` 或 `PENDING`。
- 前端仍只调用内部 API，第三方封装留在 Gateway/SDK。

## 常见失败原因

- OAuth code 过期。
- 权限包不足。
- 上游当前窗口无真实订单。
- 历史订单缺 `pick_source`，不能证明系统转链归因。

## 禁止事项

- 禁止输出真实 Token、密钥、密码。
- 禁止用 mock Token 或 mock 订单证明 real-pre。
- 禁止关闭真实 API 开关后声明真实闭环通过。

## 产出物位置

- `runtime/qa/out/real-pre-*`
- 后端日志摘要。
- `docs/验收/验收证据索引.md`；Harness 侧只在 `harness/rules/state/snapshots/04-real-pre证据索引.md` 保留执行摘要。
- `harness/reports/current/latest-<report-key>.md`。
