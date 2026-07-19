# 达人地址默认识别与商品快速寄样诊断

## 报告信息

| 项目 | 结果 |
| --- | --- |
| 时间 | 2026-07-15（Asia/Shanghai） |
| 环境 | 本地 `real-pre`，真实上游模式 |
| 分支 | `codex/ddd-user-role-application` |
| 基线 commit | `3016ae7c` |
| 任务类型 | 只读排障；未修改业务代码或数据 |
| Selected Gate | Gate 3 - 达人域 / 寄样域 / 商品域跨域诊断 |
| 结论 | `PARTIAL`：根因已定位，修复未获本轮“排查原因”范围授权 |

## 问题复述

1. 渠道第一次填写达人收货地址后，第二次选择同一达人未自动带出地址。
2. 商品库快速寄样提示“商品不存在或已不在商品库，请刷新商品后重试”。

## 运行环境证据

- `safety-check.ps1 -Env real-pre`：PASS；只检查密钥是否存在，未输出密钥值。
- `backend-real-pre`、`frontend-real-pre`、`postgres-real-pre`、`redis-real-pre`：4/4 healthy。
- 后端 `/api/system/health`：`{"status":"UP"}`。
- 前端 `/healthz`：HTTP 200。
- `application-real-pre.yml` 中 `ddd.refactor.enabled` 与 `product-facade.enabled` 默认均为 `true`；容器未提供覆盖变量。

## 复现与客观证据

### 商品快速寄样

- 后端访问日志在 `2026-07-15 10:02:22Z` 与 `10:04:21Z` 捕获两次请求：
  `POST /api/products/a369f9f9-3a86-3c29-88c2-0ad88e6691d1/quick-sample`。
- 统一响应 HTTP 状态为 200；该状态只证明 `ApiResult` 已返回，不证明寄样业务成功。
- `sample_request` 最新记录仍停留在 2026-07-12，以上两次请求没有新增寄样单。
- 只读 SQL 对请求中的关系 ID 验证结果：
  - `product.id = a369...`：0 行；
  - `product_snapshot.id = a369...`：1 行，`status=1`；
  - 对应 `product_operation_state`：`selected_to_library=true`、`display_status=DISPLAYING`、`deleted=0`。

### 达人地址

- 访问日志多次捕获：
  `GET /api/talents/24afbd1d-bc4b-45bb-a4bb-9bdbd35a9e75/shipping-address`，均为 HTTP 200。
- 该达人当前只有一条有效 `talent_claim`；`recipient_name`、`recipient_phone`、`recipient_address` 均为空。
- 今天的失败请求没有生成 `sample_request`，因此也没有进入地址回写步骤。

## 现象 → 证据 → 推论 → 结论

### 问题 2：商品不存在提示

**现象**：商品明明展示在商品库中，快速寄样却提示不存在。

**证据**：

1. 商品库查询通过 `ProductService.toLegacyProduct` 将 `product_snapshot.id` 写入前端卡片的 `id`。
2. `ProductController.quickSample` 的接口注释和参数契约也明确 `relationId` 是 `product_snapshot.id`。
3. `ProductQuickSampleApplicationService` 在 DDD 路由开启时先调用
   `ProductDomainFacade.existsById(relationId)`。
4. `LegacyProductDomainFacade.existsById` 实际执行 `productMapper.selectById`，查询的是 `product.id`。
5. 本次真实样本在快照表和运营状态表存在、可展示、已入库，但在 `product` 表按相同主键不存在。
6. 下游 `ProductQuickSampleService.getById` 原本按快照主键读取，并具备从快照物化 `product` 的逻辑；当前在委派前已被错误的前置校验拦截。

**最终根因**：`product_snapshot.id` 的接口契约被错误地拿去按 `product.id` 校验。DDD 应用层新增的前置检查比原快速寄样路径更窄，造成合法商品快照被误判为不存在。

### 问题 1：第二次未默认识别地址

**现象**：第一次填写地址后，第二次选择同一达人仍为空。

**证据**：

1. 前端 `QuickSampleModal.vue` 已实现选择达人后调用 `GET shipping-address` 并填充地址。
2. 后端 `SampleApplicationPortImpl` 已实现：寄样单插入成功后，再把地址回写到当前渠道的 `talent_claim`。
3. 本次地址 GET 已真实发出并返回 200，但认领记录的地址字段客观为空。
4. 两次快速寄样均在商品前置校验处失败，没有寄样单落库，也没有执行地址回写。

**阶段性结论**：当前用户场景中，地址问题是商品误判的下游结果，不是地址 GET 或前端自动填充逻辑失效。现有语义仅保证“寄样创建成功后保存默认地址”；失败的寄样提交不会保存地址。

## 排除项

- 排除“商品真的不在商品库”：快照与运营状态均存在且可展示、已入库。
- 排除“页面只是旧缓存”：请求 ID 与当前快照表记录一致。
- 排除“前端没有请求地址”：运行日志已捕获多次地址 GET。
- 排除“没有达人认领关系”：存在一条有效认领记录；只是地址字段为空。
- 暂未发现达人 UUID 映射错误：运行日志中的地址 GET 使用了真实达人主键，且返回 200。

## 修复方案（未在本轮实施）

### 根因修复

- 修改位置：`ProductQuickSampleApplicationService` 与商品域 Facade 契约。
- 修改思路：快速寄样前置校验必须按 `product_snapshot.id` 验证快照存在，不能复用“按 `product.id` 校验”的 `existsById`。
- 推荐做法：增加语义明确的快照存在性能力，或直接使用 `findSnapshotById(relationId)`；随后仍由现有服务校验 `DISPLAYING`、`selected_to_library` 并按需物化商品。
- 回归测试：覆盖“快照存在、运营状态已入库且展示中、`product` 主表尚无同主键记录”的真实模式；断言请求继续委派并完成物化，而不是返回商品不存在。
- 风险：不能简单删除全部商品校验；必须保留快照不存在、未入库、非展示状态的负例。

### 地址语义待用户确认

- 若业务定义是“寄样成功后保存地址”，修复商品主键错配后，现有地址回写与第二次自动带入链路即可恢复。
- 若业务定义是“即使寄样失败，只要用户填完地址也要保存”，需要把地址保存拆成独立达人域动作；这会产生“寄样失败但地址已更新”的非原子行为，并需明确多选达人时地址归属。该规则不能由 AI 擅自拍板。

### 临时止血

- 不建议通过刷新页面、吞掉异常或关闭 DDD 路由作为正式方案。
- 人工在达人详情维护默认收货地址可临时绕过重复填写，但不能修复快速寄样的商品误判。

## 建议验证清单

- 后端回归：快照存在 / 不存在、已入库 / 未入库、展示 / 隐藏、物化并发负例。
- 前端回归：选择同一达人第二次自动带入；快速切换达人不串地址。
- API：`POST /api/products/{snapshotId}/quick-sample` 成功返回且 `sample_request` 落库。
- SQL：`sample_request.recipient_*` 保存快照；对应 `talent_claim.recipient_*` 更新。
- 权限：地址按 `channel_user_id + talent_id` 隔离，其他渠道不可读取。
- 业务回归：七天重复限制、管理员代渠道、多达人批量寄样。
- 完整交付时必须执行构建、容器重启、健康检查、相关业务验证与 Gate 3/4 evidence。

## 未执行项与剩余风险

- 未修改业务代码，因此未执行构建、重启或修复后回归；不能声明已修复。
- 未读取 HTTP 业务响应体；根因由源码契约、真实请求 ID、表事实和无落库结果交叉确认。
- 当前工作区已有其他任务的未提交变更，本任务未触碰或暂存这些文件。
- 未部署远端；用户未要求远端部署。

## Retro

本次缺陷可由“接口参数语义测试”预防：Controller 明确接收快照主键，但应用层测试仅 Mock `existsById=true/false`，没有验证 Facade 查询的表语义。后续回归应使用“快照有记录、商品主表无同主键”的夹具，避免同名 `existsById` 掩盖跨模型主键错配。
