---
name: real-pre-acceptance
description: 执行或审计 real-pre 受控验收，区分环境就绪、业务通过和真实样本缺失，并输出可追溯证据。
---

# real-pre 验收

## 触发场景

- 用户要求 real-pre 预检、P0、角色权限、真实订单闭环或部署后验收。
- 用户要求判断“能否发布”“是否通过”或“是否可以放量”。

## 输入

- 目标环境：默认本地 `real-pre`；远端只接受 `release/real-pre` 经 Jenkins 队列发布。
- 验收范围：环境就绪、P0、角色权限，或商品→转链→订单→归因→寄样→业绩→Dashboard 闭环。
- 证据目录：`runtime/qa/out/<run-id>/summary.json` 和 `report.md`。

## 安全边界

- 必须先读 `harness/policy/safety.md`、`harness/runbooks/environments/real-pre.md`、`docs/08-第三方对接总览.md`、`docs/10-部署运行总览.md`、`docs/验收/real-pre联调手册.md`。
- real-pre 必须保持真实上游：`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`；禁止用 test/mock 数据证明真实闭环。
- 禁止清库、删除 volume、修改服务器 `.env` 或直接 SSH 部署。远端只看 Jenkins 构建、发布清单和服务器证据。
- 缺 Token、权限、样本或上游响应时，保留证据并标记 `BLOCKED` / `PENDING`，不能绕过或改写状态。

## 步骤

1. 本地验收前先检查 Harness 环境：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect -TargetEnv real-pre
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify -TargetEnv real-pre
```

2. 需要单独诊断环境时运行：

```bash
npm run e2e:real-pre:p0:preflight
```

3. P0 统一入口运行预检和商品、订单归因、寄样、业绩看板、RBAC、清理计划：

```bash
npm run e2e:real-pre:p0
```

4. 按部署验收要求补跑角色业务交接：

```bash
npm run e2e:real-pre:roles
```

5. 读取本次输出目录的 `summary.json`、`report.md` 和各步骤证据。不能只看终端退出码或单个细分脚本。

## 状态判定

- `PASS`：环境、Token、真实样本、所有必需步骤和业务闭环均有证据。
- `PASS_NEEDS_CLEANUP`：主链路通过但仍有清理项；不能写成完全通过。
- `PENDING`：系统没有失败，但缺真实订单、映射或其他业务样本；不能宣称业务闭环通过。
- `BLOCKED`：Token、权限、限流、环境或外部服务阻塞；记录请求时间、响应摘要和下一步。
- `FAIL`：系统内可复现失败；记录最小复现、日志和影响范围。

## 输出

输出一张短表，至少包含：环境守卫、部署版本 / 镜像摘要（如适用）、preflight、P0、角色权限、真实业务闭环、状态和证据路径。

结论必须明确属于：环境可用、受控验收通过、业务闭环 `PENDING`、外部条件 `BLOCKED` 或系统 `FAIL`。远端发布还必须补充 Jenkins 构建号、`current.json`、运行 digest、健康检查和回滚结果。

## 验证

- `summary.json` 与 `report.md` 存在且状态一致。
- `PENDING`、`BLOCKED`、`PARTIAL`、`PASS_NEEDS_CLEANUP` 不得被写成 `PASS`。
- 真实订单归因、寄样自动完成、业绩和 Dashboard 未逐字段对账时，业务闭环保持 `PENDING`。
- 不输出 Token、密码、OAuth code 或服务器环境文件内容。
