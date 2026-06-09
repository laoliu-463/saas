# DDD-BASE-001 阶段性实施报告（重构安全开关基线）

## 1. 任务定位

DDD-BASE-001 是 DDD 重构在 Phase 0（安全收口）与 Phase 1（领域拆分）之间的**前置基线任务**，目标是为后续五个领域（用户域 / 订单域 / 业绩域 / 商品域 / 寄样域）的渐进式重构提供统一的安全切换入口。本任务**不实施任何业务重构**，仅铺设：

1. 类型安全的开关配置（`ddd.refactor.*`）。
2. 默认全关的运行时行为。
3. 钉死默认值的回归测试。

只要本任务的开关保持全 `false`，线上行为与改动前 100% 一致；只有对应领域的 Phase 1 防护测试通过 + ADR 评审通过后，才允许在指定环境打开对应子开关。

---

## 2. 开关结构（YAML 段）

在 `backend/src/main/resources/` 的三个配置文件中追加（**仅追加，不修改任何已有键**）：

```yaml
ddd:
  refactor:
    enabled: ${DDD_REFACTOR_ENABLED:false}
    user-scope:
      enabled: ${DDD_REFACTOR_USER_SCOPE_ENABLED:false}
    order-attribution:
      enabled: ${DDD_REFACTOR_ORDER_ATTRIBUTION_ENABLED:false}
    performance-calc:
      enabled: ${DDD_REFACTOR_PERFORMANCE_CALC_ENABLED:false}
    product-display:
      enabled: ${DDD_REFACTOR_PRODUCT_DISPLAY_ENABLED:false}
    sample-policy:
      enabled: ${DDD_REFACTOR_SAMPLE_POLICY_ENABLED:false}
```

* **根开关** `ddd.refactor.enabled`：总闸。**任一子开关被打开前必须先打开总闸**，否则 Spring 容器中 `DddRefactorProperties` 仍可注入但调用方需自行校验，避免误读。
* **子开关**：与 `docs/01-V1交付范围与边界.md` 的领域划分一一对应。

| 子开关 | 对应领域 | V1 是否启用 | 启用前置条件（必须全部满足） |
| --- | --- | --- | --- |
| `user-scope` | 用户域 | 否 | 防护测试覆盖 `self/group/all` 三种范围 |
| `order-attribution` | 订单域 | 否 | 防护测试覆盖订单已同步事件、pick_source 落库 |
| `performance-calc` | 业绩域 | 否 | 防护测试覆盖双轨金额计算 + 冲正 |
| `product-display` | 商品域 | 否 | 防护测试覆盖 `pick_source_mapping` 落库 |
| `sample-policy` | 寄样域 | 否 | 防护测试覆盖 `OrderSyncedEvent` 触发交作业 |

---

## 3. 类型安全绑定

新增 `com.colonel.saas.config.DddRefactorProperties`：

* 使用 Lombok `@Data` + Spring `@Component` + `@ConfigurationProperties(prefix = "ddd.refactor")`。
* 5 个静态内部类（`UserScope` / `OrderAttribution` / `PerformanceCalc` / `ProductDisplay` / `SamplePolicy`），每个内部类仅含一个 `private boolean enabled = false;` 字段。
* Javadoc 写明 4 条原则：
  1. 开关未开启时新旧实现路径保持一致。
  2. 仅当该领域任务通过 ADR 评审后才允许打开。
  3. 任意子开关关闭时必须回退到旧实现。
  4. 禁止往里塞运行时数据（该类仅承载静态配置）。

文件位置：`backend/src/main/java/com/colonel/saas/config/DddRefactorProperties.java`（93 行）。

---

## 4. 回归测试

新增 `DddRefactorPropertiesTest`（46 行，2 个测试）：

* `defaultFlagsShouldAllBeFalse()` — 实例化根对象，断言根 `enabled` 与 5 个子对象的 `enabled` 均为 `false`。
* `nestedSubFlagsShouldDefaultToFalseAfterConstruct()` — 逐个 `new` 出嵌套对象，断言每个 `enabled` 为 `false`。

**核心不变量**：本测试**不依赖 Spring 上下文**，纯 POJO 构造。一旦默认值在配置类或 YAML 中被改为 `true`，本测试必须报错。

执行结果（`mvn -Dtest=DddRefactorPropertiesTest test`）：

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全量回归（`mvn test`）：

```text
Tests run: 1781, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

详见 `evidence-20260609-120500-ddd-base-001.md`。

---

## 5. 未变更清单

明确**没有改动**以下任何一项（以满足任务规约的 3 条红线）：

* 任何 Controller 入参/出参、路由、状态码、响应结构。
* 任何 Service / Repository / Mapper / Domain 业务方法签名、调用链、事务边界。
* 任何数据库表结构、字段、约束、索引、outbox 表内容。
* 任何前端 Vue 组件、API 调用、状态管理。
* 任何 Dockerfile、docker-compose、CI 脚本、Nginx 配置。
* 任何已有的 application.yml 中已有键值。

---

## 6. 提交与位置

* **Commit**：`7f72e51c`（`DDD-BASE-001 add refactor safety toggles`）
* **5 files changed, 181 insertions(+)**：
  * `application.yml` (+14)
  * `application-test.yml` (+14)
  * `application-real-pre.yml` (+14)
  * `DddRefactorProperties.java` (+93)
  * `DddRefactorPropertiesTest.java` (+46)
* **配套说明文档**（UTF-8）：`D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\DDD-BASE-001-safety-toggles.md`
  * 文档首行已注明用户原指引路径 `plans-重构计划与路线图/` 与实际目录 `plans/ddd-refactor/` 的差异。

---

## 7. 结论

* **结论：** `PASS`
* **阶段定位：** 阶段性完成（**Phase 0.5：开关基线已就位**）。本任务不是 Phase 1 / Phase 2 的领域重构，不能据此宣称任何领域已完成 DDD 改造。
* **下一步（V1 必做）**：本任务**不能跳过**直接进入领域 Facade。后续各领域必须先在 DDD-PHASE0-ARBITRATION 报告 §2"规则确认 1"的红线下完成防护测试，再回此处打开对应子开关。
