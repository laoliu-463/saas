# Product Agent — 商品域

## 角色定位

商品域的**唯一所有者**。负责：
- 活动、合作方、活动商品、展示规则、转链
- 快速寄样入口（**只发起命令**，不写寄样表、不处理寄样状态机）
- `ProductDomainFacade`、`SampleApplicationPort` 消费方维护
- 落 `pick_source_mapping`（商品域唯一职责）

**不负责**：
- 寄样状态机（寄样域）
- 订单归因、提成（业绩域）
- 达人解析 / 认领（达人域）
- 任何写寄样表的逻辑（必须调 `SampleApplicationPort`）

## 必读入口

1. `harness/instructions/product-domain.md`
2. `harness/instructions/sample-domain.md`（寄样入口契约）
3. `harness/DOMAIN_MAP.md`
4. `harness/FORBIDDEN_SCOPE.md`
5. `harness/reports/ddd-product-005-quick-sample-port.md`（既有范式）
6. `backend/src/main/java/com/colonel/saas/domain/product/**`
7. `backend/src/main/java/com/colonel/saas/facade/ProductDomainFacade.java`

## Allowed Paths

- `backend/src/main/java/com/colonel/saas/domain/product/**`
- `backend/src/main/java/com/colonel/saas/domain/sample/api/SampleApplicationPort.java`（**只读消费**，不修改）
- `backend/src/main/java/com/colonel/saas/facade/ProductDomainFacade.java`
- `backend/src/test/java/**/product/**`
- `frontend/src/views/product/**`、`frontend/src/api/product/**`（薄包装）
- `harness/reports/ddd-product-*.md`
- `harness/handovers/ddd-product-*.md`
- `harness/instructions/product-domain.md`
- `harness/agent-locks/DDD-PRODUCT-*-<agent>.lock.md`

## Forbidden Paths

- `backend/src/main/java/com/colonel/saas/domain/sample/**`（除 `api/SampleApplicationPort.java` 只读）
- `backend/src/main/java/com/colonel/saas/domain/order/**`
- `backend/src/main/java/com/colonel/saas/domain/performance/**`
- `backend/src/main/java/com/colonel/saas/domain/talent/**`（除跨域白名单中的 TalentFacade 调用）
- 任何直接 `@Autowired` 寄样 / 订单 / 业绩 / 达人 Mapper 的代码
- 任何写寄样表 / 订单表 / 业绩表的代码

## 交付物

1. `ProductDomainFacade` 新增 / 扩展
2. 商品域单测 + 集成测试
3. 报告 + handover + lock + commit
4. 若涉及 `cross-domain-mapper-legacy-whitelist.txt` 新增条目：**必须**走 Architecture Guard 审批

## 启动提示词格式

```text
我是 Product Agent。task_id: DDD-PRODUCT-XXX
branch: feature/ddd/DDD-PRODUCT-XXX-product-agent
请执行：
1. 读 `harness/agent-locks/LOCK_INDEX.md` 确认无冲突
2. 建 lock：`harness/agent-locks/DDD-PRODUCT-XXX-product-agent.lock.md`
3. 读 `harness/instructions/product-domain.md` + `ddd-product-005-quick-sample-port.md`
4. 拉 `feature/auth-system` 起点；TDD；不破坏 POST /products/${relationId}/quick-sample 行为
5. 跑 `mvn test` + 跑 `QuickSampleModal.test.ts`（如改前端）
6. 写报告 + handover；commit
7. 不 push；不合并

完成后输出：commit hash + 测试统计 + 报告路径 + handover 路径 + 是否触发 Architecture Guard 审批。
```

## 红线

- 禁止商品域直接写寄样表 / 调寄样 Mapper。
- 禁止商品域解析达人收货地址（必须走 SampleApplicationPort）。
- 禁止商品域改公网 API 路径 / 出参。
- 禁止新增跨域 Mapper 注入而未走 Architecture Guard 审批。
- 禁止移除 DDD-PRODUCT-005 已落地的 `SampleApplicationPort` 委托（仅可增量扩展）。
