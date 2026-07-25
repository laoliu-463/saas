---
name: douyin-api-audit
description: 审计抖音/抖店授权、Token、活动商品、转链、订单、物流和达人接口的真实可用性与业务证据。
---

# 抖音接口审计

## 触发场景

- 用户要求排查 OAuth、Token 刷新、接口权限、错误码、限流或真实上游响应。
- 用户要求验证活动商品、转链、订单、物流或达人数据是否真正进入业务链路。

## 输入

- 环境和目标接口；默认 real-pre，必须说明是否为 live 上游。
- 授权主体、Token readiness、接口请求时间窗口和脱敏响应证据。
- 对应业务目标：读、同步、刷新、回调或真实写入。

## 必读依据

- `docs/08-第三方对接总览.md`。
- `docs/对接/` 下对应的授权、Token、活动商品、转链、订单、物流和达人文档。
- `docs/验收/real-pre联调手册.md`、`harness/policy/real-pre.md`。

## 安全边界

- real-pre 必须保持 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
- 前端只能调用内部 API；抖音/抖店请求必须经过后端 Gateway/SDK。
- 真实写入、推广转链和回调属于高风险动作；没有明确授权和证据时只做只读预检。
- 不输出 Token、OAuth code、签名、密码或完整第三方请求头；远端不直接 SSH。

## 步骤

1. 先运行 `npm run e2e:real-pre:p0:preflight`，确认环境、Token、Schema 和可复用映射。
2. 按接口类别检查：请求参数/签名、HTTP 与业务码、响应字段转换、重试/限流、持久化结果和关联事件。
3. 区分 raw probe 可达、单接口成功、数据已落库和业务闭环完成；后一层不能由前一层推导。
4. 对阻塞项保留请求时间、脱敏响应摘要、授权主体、影响范围和恢复动作；不要切到 mock 绕过阻塞。

## 输出与状态

输出接口表：接口、目的、请求时间、响应状态、落库/事件、业务影响、证据路径和状态。

- `PASS`：真实接口响应和业务落库/事件证据完整。
- `BLOCKED`：权限、Token、限流、上游冻结或环境阻塞。
- `PENDING`：接口可用但缺真实商品、订单或后续业务样本。
- `FAIL`：系统可复现的请求、转换、落库或业务错误。
- `HISTORICAL`：只存在于旧方案或归档文档，不作为当前事实。

## 验证

- 证据包含脱敏请求、响应摘要、时间、环境和下一步。
- raw probe 或单个 HTTP 200 不得单独写成业务闭环 `PASS`。
- `BLOCKED`、`PENDING`、`PARTIAL` 不得升级为 `PASS`。
