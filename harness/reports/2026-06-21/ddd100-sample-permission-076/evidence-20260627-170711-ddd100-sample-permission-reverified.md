# Evidence: DDD100-SAMPLE-PERMISSION (#76) re-verify

## 基本信息

- Time: 2026-06-27 17:07:11 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #76 `[DDD100-SAMPLE-PERMISSION] 寄样动作权限和数据范围边界`
- Scope: docs/evidence only; no business code change
- Conclusion: PASS

## 现象

#76 曾因缺少可追溯 evidence / agent-do / 健康检查链路被重新打开。
本次只复核现有实现与测试证据，不修改权限业务规则。

## 证据

- `SampleActionPermissionPolicy` 定义寄样域动作权限语义。
- `CurrentUserPermissionPolicy` 只解释角色编码集合。
- `SampleApplicationService` 已接入 `SampleActionPermissionPolicy`、`DataScopePolicy` 和 `UserDomainFacade`。
- `SampleApplicationPortImpl` 仍有用户域角色解释依赖，边界由 `DddUserPermissionPolicySamplePortBoundaryTest` 守护。

## 验证命令

```powershell
mvn -q -f backend/pom.xml "-Dtest=SampleActionPermissionPolicyTest,SampleActionRequestTest,SampleBatchActionRequestTest,DddUserPermissionPolicySamplePortBoundaryTest,DddUserDataScopePolicySampleApplicationBoundaryTest,DddUserDataScopePolicySampleFilterOptionsBoundaryTest,DddUserFacadeSampleApplicationBoundaryTest,DddUserFacadeSampleFilterBoundaryTest,SampleControllerTest" test
```

## 测试结果

- `SampleActionPermissionPolicyTest`: 5 run, 0 failures, 0 errors
- `SampleActionRequestTest` nested suites: 9 run, 0 failures, 0 errors
- `SampleBatchActionRequestTest` nested suites: 8 run, 0 failures, 0 errors
- `DddUserPermissionPolicySamplePortBoundaryTest`: 3 run, 0 failures, 0 errors
- `DddUserDataScopePolicySampleApplicationBoundaryTest`: 2 run, 0 failures, 0 errors
- `DddUserDataScopePolicySampleFilterOptionsBoundaryTest`: 1 run, 0 failures, 0 errors
- `DddUserFacadeSampleApplicationBoundaryTest`: 1 run, 0 failures, 0 errors
- `DddUserFacadeSampleFilterBoundaryTest`: 2 run, 0 failures, 0 errors
- `SampleControllerTest`: 83 run, 0 failures, 0 errors

## real-pre 健康检查

- `docker compose ps`: backend / frontend / postgres / redis 均为 `healthy`
- backend container health endpoint: `GET http://127.0.0.1:8080/api/system/health` -> `{"status":"UP"}`
- frontend container root: 返回 HTML boot shell

## 边界结论

- 动作权限归属寄样域：申请、删除、审核、物流、导出、覆盖导入、7 天限制豁免、全局访问和私海认领校验均由 `SampleActionPermissionPolicy` 表达。
- 用户域只解释角色编码，不决定寄样业务动作。
- 数据范围灰度旁路仍默认关闭；开启后只委托用户域解释 `PERSONAL/DEPT/ALL`，寄样域保留业务语义。

## 未执行项

- 未重启 Docker：本次没有业务代码变更，仅补充 evidence 和状态文档。
- 未执行远端部署：用户未要求远端 real-pre 部署。

## 风险

- `SampleApplicationPortImpl` 仍消费 `CurrentUserPermissionPolicy`，当前由架构测试守护；后续如继续抽 port，可单独拆分。
