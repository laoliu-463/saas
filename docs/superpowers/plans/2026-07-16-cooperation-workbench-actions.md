# Cooperation Workbench Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有合作单表格右侧固定竖排提供“通过、拒绝、修改订单、查看进度、复制链接、复制订单、投诉达人、备注”八项操作，并完成后端权限、状态、事务、隐私、投诉提醒和附件保护闭环。

**Architecture:** 合作单继续复用寄样域。寄样域负责状态动作、受限编辑、复制订单与私有备注；商品域负责真实转链和推广文本；达人域负责认领地址、投诉、风险、附件和专项提醒；用户域只提供提醒接收人。前端固定渲染八项操作，后端返回可用性并在写接口再次校验。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、PostgreSQL、Vue 3、TypeScript、Naive UI、Vitest、Playwright、Docker Compose、项目 Harness。

---

## 1. 必读任务分册

按顺序完整执行以下分册；每个分册独立低于仓库 200 行文本硬门禁：

1. [后端数据与边界](./cooperation-workbench-actions/01-backend-foundation.md)
2. [合作单编辑与复制](./cooperation-workbench-actions/02-sample-product-actions.md)
3. [投诉、附件与提醒](./cooperation-workbench-actions/03-complaints-storage.md)
4. [前端操作栏与风险提醒](./cooperation-workbench-actions/04-frontend-actions.md)
5. [迁移、测试与 Harness 验收](./cooperation-workbench-actions/05-verification-harness.md)

## 2. 不变量

- 后端状态使用 `SampleStatus`；前端继续兼容 `PENDING_AUDIT/PENDING_SHIP/SHIPPED/PENDING_TASK/FINISHED/REJECTED/CLOSED`。
- 管理员拥有全部人工操作权限，但 `PENDING_TASK -> FINISHED/CLOSED` 仍只能由系统推进。
- 八项操作在每行固定存在；不可执行项置灰并显示后端原因。
- 修改订单只改申请备注和地址；达人、商品、数量、规格和门槛只读。
- 修改地址在同一事务内更新 `sample_request` 和合作单申请人的有效 `talent_claim`。
- 私有备注按 `(sample_request_id,user_id)` 隔离，管理员也不能读取他人内容，日志不记录正文。
- 投诉绑定当前合作单达人和商品，不改变合作单状态。
- 普通用户只看投诉风险摘要；管理员、招商组长、渠道组长可看详情并收到固化未读提醒。
- 投诉文件只允许 JPG/PNG/WEBP，最多 9 张、单张最大 10 MB，受保护存储且不映射静态目录。
- “近30天橱窗销量”只读取真实的 `windowSales30d/window_sales_30d/showcaseSales30d`；缺失显示 `---`，不能用金额字段 `sales_30d` 冒充。
- 真实转链失败时返回明确失败原因和可手动复制文本，不伪造链接成功。
- 不执行远端部署，除非用户后续明确授权。

## 3. 固定操作模型

后端 `SampleVO.actionAvailability` 固定返回八个键，每个值为 `{enabled,disabledReason}`：

| 键 | 文案 | 核心规则 |
| --- | --- | --- |
| `APPROVE` | 通过 | 待审核；招商专员或管理员 |
| `REJECT` | 拒绝 | 待审核；招商专员或管理员；原因必填 |
| `EDIT` | 修改订单 | 申请人或管理员；待审核/待发货/快递中/已拒绝 |
| `PROGRESS` | 查看进度 | 所有可见合作单；复用 `SampleDetail` |
| `COPY_LINK` | 复制链接 | 商品域生成真实文案与链接 |
| `COPY_ORDER` | 复制订单 | 寄样域按事实生成固定文本 |
| `COMPLAIN` | 投诉达人 | 所有可查看当前合作单的用户 |
| `NOTE` | 备注 | 当前用户私有；不显示“我的备注” |

待交作业、已完成、已关闭时仍显示“修改订单”，但置灰。

## 4. 固定接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/samples/{id}/edit-context` | 只读达人/商品/门槛和申请人真实地址 |
| `PUT` | `/samples/{id}/cooperation-details` | 乐观锁更新备注和地址 |
| `POST` | `/samples/{id}/promotion-copy` | 商品域生成推广复制文本 |
| `GET` | `/samples/{id}/order-copy` | 生成订单文本 |
| `GET/PUT` | `/samples/{id}/private-note` | 当前用户私有备注 |
| `POST` | `/samples/{id}/complaints` | multipart 提交投诉 |
| `POST` | `/talent-complaints/risks` | 批量风险摘要 |
| `GET` | `/talent-complaints/reminders/unread-count` | 当前用户未读数 |
| `GET` | `/talent-complaints/reminders` | 当前用户提醒列表 |
| `PUT` | `/talent-complaints/reminders/{id}/read` | 当前用户标记已读 |
| `GET` | `/talent-complaints/{id}` | 领导角色投诉详情 |
| `GET` | `/talent-complaints/{id}/attachments/{attachmentId}` | 鉴权下载附件 |

## 5. 执行纪律

- [ ] 每个任务先写失败测试，确认 RED 后实现最小代码，再确认 GREEN。
- [ ] 不向前端硬编码状态机或权限事实；前端只消费后端能力对象。
- [ ] 跨域只使用 Facade/Application Service，不新增跨域 Mapper 注入。
- [ ] 每个分册完成后精确暂存本分册文件并提交，禁止 `git add .` 或 `git add -A`。
- [ ] 保留工作区现有无关修改，不修改 `.env`、凭据、旧报告或用户文件。
- [ ] 实施结束使用 code-review-graph 做影响半径和审查上下文检查。
- [ ] 运行本地 real-pre 完整 Harness：构建、重启、健康、业务验证、evidence、提交和推送。

## 6. 完成标准

- 八项操作顺序和竖排样式正确，禁用原因可见。
- 通过/拒绝复用状态机，系统专属流转未被绕过。
- 地址按申请人读取并原子双写，乐观锁冲突可见。
- 两类复制文本逐行符合用户确认格式。
- 私有备注隔离、投诉权限、附件保护、风险摘要和提醒已读均有自动化证据。
- 构建、容器重启、健康检查、定向业务验证和 evidence 完成。
- 所有任务提交已推送，远端环境未部署。
