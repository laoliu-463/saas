# Known Risks

## 真实订单归因

- real-pre 历史订单 `pick_source` 为空，不能证明系统转链闭环。
- 没走系统复制讲解链接的订单，不能证明渠道链闭环。
- NATIVE / `colonel_buyin_id` 映射需要单独取证。

## 寄样自动完成

- 寄样自动完成依赖订单归因后的 `channel_id + talent_id + product_id + pay_time`。
- 订单归因失败可能表现为寄样未自动完成，不能直接判定寄样域 bug。

## 商品库

- 推广中商品历史数据可能未入库。
- `selected_to_library`、`audit_status`、`display_status` 漂移会影响商品库入口。
- real-pre repair 写入前必须先保存 dry-run 证据。

## 环境

- real-pre 误用 mock 会导致假闭环。
- 远端代码目录当前事实为 `/opt/saas/app`，与部分模板中的 `/opt/saas` 可能不一致。
- 禁止删除 real-pre volume。
- test 环境 E2E auth setup 可能因后端容器初始化未完成（`health: starting` 状态持续 1-2 分钟）而超时，导致 `agent-do.ps1 -Scope backend` 在业务验证阶段失败。需要在容器启动后等待就绪或增加重试机制。

## Harness 工具

- `git-push-safe.ps1` 在 v0.1.4 前对中文文件名使用了 octal 转义路径，导致 `Test-Path -LiteralPath` 报非法字符错误。已通过 `-c core.quotepath=false` 修复。
- `verify-local.ps1` 在容器刚启动时立即做 HTTP 健康检查，未等待 Spring Boot 初始化完成（通常需要 60-90 秒）。

## 文档

- V2/V2.5 完整方案可能与 V1 交付范围冲突。
- FastAPI / Celery 旧技术建议不适用于当前 Spring Boot 实际代码。
- 老文档高级看板不能升级为 V1 P0。

