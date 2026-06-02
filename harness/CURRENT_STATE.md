# Current State

## 当前日期

- 记录日期：2026-06-02
- Harness 版本：v0.1.4

## 当前技术栈

- 后端：Spring Boot 3.2 / Java 17
- 前端：Vue 3 / Vite / Pinia / Naive UI / TypeScript
- 数据库：PostgreSQL
- 缓存：Redis
- 部署：Docker Compose
- 验收：Playwright、Maven、Vitest、PowerShell QA 脚本
- 环境：`test`、`real-pre`、远端 `real-pre`

旧文档中出现的 FastAPI、Celery、Python 爬虫式方案只作为历史背景，不作为当前运行事实。

## 当前环境事实

| 环境 | 前端 | 后端 | Compose | Env 文件 | 用途 |
| --- | --- | --- | --- | --- | --- |
| `test` | `3000` | `8080` | `docker-compose.test.yml` | `.env.test` | mock 回归、P0 基线 |
| `real-pre` | `3001` | `8081` | `docker-compose.real-pre.yml` | `.env.real-pre` | 真实上游、上线前联调 |

real-pre 必须保持：

- `APP_TEST_ENABLED=false`
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`
- 不清库、不删除 volume、不使用 mock 数据证明真实闭环

## 当前已完成领域

- 用户域：登录、角色、菜单、组织和数据范围主链路已具备。
- 配置域：配置读取、变更和审计主链路已具备。
- 商品域：商品库、活动商品同步、转链和映射主链路已具备，历史推广中入库仍需持续验证。
- 达人域：达人资料、标签、地址和跟进主链路已具备。
- 寄样域：申请、审批、发货和订单事件自动完成链路已具备，real-pre 仍依赖真实归因订单样本。
- 订单域：订单事实、退款事实、同步日志和归因输入已具备。
- 业绩域：最终归属、提成、冲正和汇总主链路已具备。
- 分析模块：dashboard、报表和只读汇总主链路已具备。

## 当前未闭环点

- 渠道链真实闭环仍依赖真实通过系统转链产生的订单样本。
- real-pre 历史订单 `pick_source` 大量为空，不能证明渠道归因闭环。
- 寄样自动完成需要订单归因后的 `channel_id + talent_id + product_id + pay_time`。
- 推广中商品历史数据可能需要 repair / backfill。

## 当前 P0 / P1

- P0：真实渠道订单归因样本不足，阻塞 real-pre 渠道链真实闭环 PASS。
- P1：寄样自动完成依赖真实归因订单样本。
- P1：推广中商品历史数据可能存在入库漂移。
- P1：权限注解和数据范围覆盖仍需持续审计。

## V1 核心闭环

### 渠道链

认领达人 -> 商品库选品 -> 复制讲解 / 转链 -> 寄样申请 -> 订单同步 -> 渠道业绩 -> 寄样自动完成。

### 招商链

同步活动 -> 活动商品入库 -> 商品上架 -> 审核寄样 -> 订单同步 -> 招商业绩。

### 管理链

用户角色 -> 数据范围 -> 规则配置 -> 各领域读取配置 -> 权限生效。

## 当前关键业务事实

- 管理链基本可闭环。
- 招商链大部分可闭环。
- 渠道链真实闭环仍依赖真实通过系统转链产生的订单样本。
- 当前 real-pre 历史订单 `pick_source` 大量为空，不能证明渠道归因闭环。
- 寄样自动完成依赖订单归因后的 `channel_id + talent_id + product_id`。
- 寄样自动完成的真实判定还需要 `pay_time` 命中。
- 推广中商品历史数据需要入库 / 重算，避免商品库选品入口不完整。
- 订单进入数据库不等于业务闭环；必须验证 `default_channel_id`、`default_recruiter_id`、`sample_requests` 状态流转和 `performance_records`。
- 真正闭环必须验证 `orders.pick_source`、`orders.default_channel_id`、`orders.default_recruiter_id`、`sample_requests`、`performance_records` 和 dashboard / 业绩归属。
- 商品库漂移优先通过活动商品手动同步或商品域 repair 入口处理，不允许裸 SQL 批量直改。

## 当前代码与文档关系

- `CLAUDE.md` 是仓库地图。
- `docs/README.md` 是文档地图。
- `docs/01-V1交付范围与边界.md` 是 V1 范围主源。
- `docs/02-业务闭环总览.md` 是业务闭环主源。
- `docs/03-领域架构总览.md` 与 `docs/领域/*.md` 是领域边界主源。
- `docs/05-API契约总表.md` 是内部 API 入口。
- `docs/06-数据模型总表.md` 是数据模型入口。
- `docs/07-权限与数据范围.md` 是 RBAC / 数据范围入口。
- `docs/08-第三方对接总览.md` 与 `docs/对接/*.md` 是 SDK / 上游接口入口。
- `docs/09-测试验收总览.md` 与 `docs/验收/*.md` 是验收入口。
- `docs/10-部署运行总览.md` 与 `docs/deploy/README.md` 是部署入口。

## 旧文档冲突处理

| 冲突 | 当前处理 |
| --- | --- |
| 旧 V2.2 完整方案 vs 当前 V1 范围 | 以 `docs/01-V1交付范围与边界.md` 和本文件为准 |
| FastAPI / Celery 旧技术建议 | 标记为历史归档，当前以 Spring Boot 源码为准 |
| 独家达人 / 独家商家 | V1 不启用 |
| 毛利字段设计 | V1 不做毛利口径扩展；不得扩大为财务结算 |
| 寄样 30 天自动关闭 | V1 不自动关闭，按当前寄样状态机和订单事件验证 |
| 个别品负责人覆盖 | V1 不做 |
| 物流 API 自动跟踪 | V1 以手动物流和可证据物流接口为准 |

冲突不得靠 AI 自行裁决；必须补充证据并写入 `docs/决策/ADR-002-V1范围优先级.md`。

## 待确认

- 真实渠道订单样本是否已经通过系统转链产生。
- 远端每次部署是否要求同步执行完整 `e2e:real-pre:p0`、`roles` 与 preflight。
- 若远端 SSH alias、目录或仓库 remote 变化，需要更新 `harness/runbooks/remote-deploy.md` 和 `harness/commands/deploy-remote.ps1` 参数默认值。

## 状态子系统

细分状态见：

- `harness/state/current-business-state.md`
- `harness/state/p0-p1-register.md`
- `harness/state/real-pre-evidence-index.md`
- `harness/state/known-risks.md`
