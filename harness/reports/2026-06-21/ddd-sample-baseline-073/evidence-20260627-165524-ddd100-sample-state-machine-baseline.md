# DDD100-SAMPLE-BASELINE #73 Evidence

- 时间：2026-06-27 16:55 Asia/Shanghai
- 环境：local real-pre + backend Testcontainers
- 分支：feature/ddd/DDD-VERIFY-001
- Issue：#73 `[DDD100-SAMPLE-BASELINE] SampleApplicationService 状态机基线`
- 结论：`PASS`

## 目标

重新执行寄样状态机基线验证，冻结 `SampleApplicationService` 当前状态流转、状态枚举、操作日志和生命周期行为。此前并发 issue 评论不能作为当前分支 DoD 证据。

## 图谱与代码边界

`code-review-graph find_large_functions(file_path_pattern="Sample")` 定位关键热点：

- `SampleApplicationService.actionSample`：审核、驳回、发货、签收、待交作业、完成、关闭流转入口。
- `SampleApplicationService.buildStatusTransitions`：对前端/API 暴露的状态机元数据。
- `SampleStateMachine`：动作别名归一化、前置状态校验、可删除状态判断。
- `SampleLifecycleService`：订单已同步后自动完成、超时关闭和状态批量更新。

本轮未修改业务代码、DB schema 或 real-pre 配置。

## 状态机事实

内部状态码：

```text
1 PENDING_AUDIT
2 PENDING_SHIP
3 SHIPPING
4 DELIVERED
5 PENDING_HOMEWORK
6 COMPLETED
7 REJECTED
8 CLOSED
```

主要流转：

```text
PENDING_AUDIT -> PENDING_SHIP
PENDING_AUDIT -> REJECTED
PENDING_SHIP -> SHIPPING
SHIPPING -> DELIVERED
SHIPPING/DELIVERED -> PENDING_HOMEWORK
PENDING_HOMEWORK -> COMPLETED
PENDING_HOMEWORK -> CLOSED
```

动作别名：

```text
APPROVED -> PENDING_SHIP
SHIPPED -> SHIPPING
SIGNED/PENDING_TASK -> PENDING_HOMEWORK
FINISHED -> COMPLETED
```

## 指定基线测试

命令：

```powershell
mvn -q -f backend/pom.xml "-Dtest=CharacterizationBaselineTest,SampleStateMachineTest" test
```

结果：`PASS`。

Surefire：

```text
CharacterizationBaselineTest: Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
SampleStateMachineTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

覆盖要点：

- `test04_SampleLifecycleAndAttributionCompleteBaseline`：发货、签收、订单归因后自动完成。
- `test07_QuickSampleApplyCharacterizationBaseline`：快样申请落入寄样申请链路。
- `test13_SampleStatesRefinedCharacterizationBaseline`：细化状态码和批量动作基线。
- `SampleStateMachineTest`：别名归一化、非法前置状态拒绝、`SHIPPING/DELIVERED` 可进入待交作业、仅待审核/已驳回可删除。

## 补充后端验证

命令：

```powershell
mvn -q -f backend/pom.xml "-Dtest=SampleControllerTest,SampleLifecycleServiceTest,SampleStatusLogServiceTest" test
```

结果：`PASS`。

Surefire：

```text
SampleControllerTest: Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
SampleLifecycleServiceTest: Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
SampleStatusLogServiceTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

说明：运行日志中的 `SR-MISSING` 是批量发货/审核/驳回缺失请求号负例测试的预期警告。

## real-pre 只读样本基线

命令：

```sql
SELECT status, count(*)
FROM sample_request
WHERE COALESCE(deleted,0)=0
GROUP BY status
ORDER BY status;
```

结果：

```text
status=1 count=14
status=5 count=27
sample_count=41
with_tracking=27
with_ship_time=27
with_complete_time=0
with_close_time=0
```

运行状态：

```text
backend container health=healthy
frontend container health=healthy
```

解释：real-pre 当前存在待审核和待交作业样本；没有完成/关闭样本。真实订单命中自动完成与幂等样本留给 #75/#78，不在本轮写成 PASS。

## 现象、证据、推论、结论

- 现象：#73 要求 `SampleApplicationService` 状态机基线重新验证并入库 evidence。
- 证据：状态枚举、状态机 policy、`actionSample` 流转入口、状态转移元数据均可追踪；指定基线测试 18/18 PASS；补充 controller/lifecycle/log 测试 99/99 PASS；real-pre DB 有 41 条寄样样本。
- 推论：当前分支能证明寄样状态机基础流转、别名兼容、非法流转拒绝、状态日志和生命周期服务基线未回归。
- 结论：#73 满足关闭条件。

## 剩余风险

- real-pre 当前没有 `COMPLETED/CLOSED` 样本，真实订单触发的交作业完成仍需 #75/#78 继续验证。
- 本轮未执行远端部署。
- 本轮未修改代码，因此 `agent-do` 将使用 `Scope=docs`，构建/重启会按规则跳过，不能把 docs 门禁写成代码构建 PASS。
