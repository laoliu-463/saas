# Performance OrderSyncedEvent Single Consumer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除业绩聚合服务中的重复空事件监听，使订单同步后的业绩记录生成只有一个明确入口，并用架构测试阻止边界回退。

**Architecture:** 保留外层 `PerformanceRecordSyncListener` 作为 `OrderSyncedEvent` 到业绩计算应用服务的适配器；`domain/performance` 只保留领域与应用逻辑，不直接订阅订单域集成事件。先增加源码架构守卫观察失败，再删除无副作用监听及其伪测试，最后通过定向测试和统一 Harness 验证。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、AssertJ、Mockito、Maven、PowerShell Harness、Docker Compose

---

## 文件映射

- 修改 `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java`：增加业绩领域包不得直接依赖 `OrderSyncedEvent` 的架构守卫。
- 修改 `backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java`：删除重复空监听、事件/异步/日志依赖及无用 `@Slf4j`。
- 修改 `backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java`：删除只验证空事件不抛异常的伪测试，其余聚合查询行为测试保持不变。
- 只验证 `backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java` 及其测试：现有有效监听逻辑不变。
- 生成 `runtime/qa/out/latest-ddd-performance-order-event-single-consumer.md`：记录门禁证据与 retro。

### Task 1: 以架构守卫证明重复领域监听存在

**Files:**
- Modify: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java:51`
- Test: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java`

- [ ] **Step 1: 写入失败的架构测试**

在 `eventAndManualEntrypointsShouldDelegateIntoTheSameGenerationFunnel` 之前加入：

```java
    @Test
    void performanceDomainShouldNotSubscribeToOrderSyncedEvent() throws IOException {
        assertThat(mainJavaFilesContaining(Pattern.compile("\\bOrderSyncedEvent\\b")))
                .filteredOn(path -> path.startsWith("com/colonel/saas/domain/performance/"))
                .isEmpty();
    }
```

源码扫描会先去除注释，因此匹配的是真实 import 或代码引用。

- [ ] **Step 2: 运行测试并确认按预期失败**

从 `backend` 目录执行：

```powershell
mvn -Dtest=DddPerformanceRecordGenerationEntrypointTest test
```

预期：命令失败；断言列出 `com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java`。编译错误、测试发现数为零或其他文件导致的失败都不算有效 RED。

### Task 2: 删除重复空监听并恢复绿灯

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java:6-16,52-53,466-496`
- Modify: `backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java:338-346`
- Test: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java`
- Test: `backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java`
- Test: `backend/src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java`

- [ ] **Step 1: 删除聚合服务的事件监听依赖**

删除以下 import（`Slf4j` 当前重复两次，两行都删）和类注解：

```java
import com.colonel.saas.event.OrderSyncedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import lombok.extern.slf4j.Slf4j;

@Slf4j
```

- [ ] **Step 2: 删除无副作用的重复监听方法**

完整删除该方法及其 Javadoc，不新增替代实现：

```java
    @Async
    @EventListener
    public void handleOrderSynced(OrderSyncedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        try {
            log.info("OrderSyncedEvent received for performance recalculation, orderId={}", event.orderId());
            // Phase 1: 失效缓存, 后续查询时重算
            // Phase 4 将改为持久化 performance_records
        } catch (Exception ex) {
            log.warn("PerformanceAggregateApplicationService.handleOrderSynced failed, orderId={}",
                    event.orderId(), ex);
        }
    }
```

- [ ] **Step 3: 删除只验证空事件的伪测试**

从 `PerformanceAggregateApplicationServiceTest` 删除：

```java
    @Test
    void handleOrderSynced_nullEvent_doesNotThrow() {
        applicationService.handleOrderSynced(null);
    }
```

同时删除该测试紧邻的 Phase 1 Javadoc。聚合服务原有 SQL、数据范围、趋势和看板测试保持不变。

- [ ] **Step 4: 运行三个定向测试类并确认绿灯**

从 `backend` 目录执行：

```powershell
mvn -Dtest=DddPerformanceRecordGenerationEntrypointTest,PerformanceAggregateApplicationServiceTest,PerformanceRecordSyncListenerTest test
```

预期：Maven `BUILD SUCCESS`，三个测试类零失败、零错误；架构测试不再发现 `domain/performance` 对 `OrderSyncedEvent` 的引用，监听器行为测试保持通过。

- [ ] **Step 5: 检查最小差异**

从仓库根目录执行：

```powershell
git diff --check -- backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
git diff -- backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
```

预期：只有新增架构守卫、删除空监听及删除伪测试，无空白错误，也没有修改 `PerformanceRecordSyncListener`。

### Task 3: 执行项目门禁并交付本轮小步

**Files:**
- Verify: `backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java`
- Verify: `backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java`
- Verify: `backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java`
- Create/Update: `runtime/qa/out/latest-ddd-performance-order-event-single-consumer.md`

- [ ] **Step 1: 通过唯一入口运行完整 Harness**

从仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey ddd-performance-order-event-single-consumer -OwnedFiles 'backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java;backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java;backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java;docs/superpowers/plans/2026-07-16-performance-order-synced-single-consumer.md' -Message "refactor(ddd): keep a single performance order event consumer"
```

预期：脚本执行后端构建、对应 Docker 容器重启、健康检查、相关验证、安全检查并生成稳定 evidence。任何阻塞或失败保留为 `PARTIAL` / `FAIL`，不得改写成 `PASS`。

- [ ] **Step 2: 独立复核 Harness 结果与运行态**

执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1 -BaselineRef HEAD
docker compose ps
git diff --check -- backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java runtime/qa/out/latest-ddd-performance-order-event-single-consumer.md
```

预期：分层门禁没有新增/恶化违规，对应容器为健康运行态，OwnedFiles 与 evidence 没有空白错误。失败时保留证据并继续定位。

- [ ] **Step 3: 只暂存本轮拥有文件并复核暂存区**

执行：

```powershell
git add -- backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java docs/superpowers/plans/2026-07-16-performance-order-synced-single-consumer.md runtime/qa/out/latest-ddd-performance-order-event-single-consumer.md
git diff --cached --name-only
git diff --cached --check
```

预期：暂存区只包含上述五个文件，不包含 `.codex/config.toml`、前端商品库文件或其他现存工作区改动。

- [ ] **Step 4: 提交并推送当前分支**

门禁结果可接受且 evidence 如实记录后执行：

```powershell
git commit -m "refactor(ddd): keep single performance order event consumer"
git push
```

预期：提交成功并推送 `codex/ddd-user-role-application` 的当前上游；不执行远端 `real-pre` 部署。

## 自检结果

- 设计范围全部映射到任务：Task 1 锁定单入口边界，Task 2 删除重复监听与伪测试，Task 3 覆盖构建、重启、健康、业务、evidence 和推送。
- 没有新增缓存、接口、数据库字段、归因规则或远端部署动作。
- 类型和名称与当前源码一致：`OrderSyncedEvent`、`PerformanceAggregateApplicationService.handleOrderSynced`、`PerformanceRecordSyncListener`、`upsertFromOrder`。
- 没有占位实现；执行时只暂存本轮 OwnedFiles，保护工作区既有改动。
