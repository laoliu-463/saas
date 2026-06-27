# DDD100-TALENT-E2E #72 Evidence

- 时间：2026-06-27 16:47 Asia/Shanghai
- 环境：local real-pre
- 分支：feature/ddd/DDD-VERIFY-001
- Issue：#72 `[DDD100-TALENT-E2E] 达人数据范围、越权负例和 E2E`
- 结论：`PASS`

## 目标

重新执行达人域数据范围、越权负例和 E2E 验证，并生成当前分支可追溯 evidence。此前 issue 评论不能作为本轮 DoD 证据。

## 图谱与代码边界

`code-review-graph find_large_functions(file_path_pattern="talent")` 定位热点：

- `TalentQueryService.page`：达人列表视图与数据范围过滤。
- `TalentService.page/blacklist/unblacklist`：claim 维度 PERSONAL/DEPT 过滤与操作权限。
- `TalentController`：把 `userId/deptId/dataScope/roleCodes` 传入 Application/Query 层。
- `tests/e2e/29-talent-claim-protection.spec.ts`：前端保护期冲突提示。
- `tests/e2e/35-real-pre-rbac-scope.spec.ts`：real-pre 多角色页面/API 权限。

本轮未修改业务代码、DB schema 或 real-pre 配置。

## 后端 Targeted Tests

命令：

```powershell
mvn -q -f backend/pom.xml "-Dtest=TalentControllerTest,TalentQueryServiceTest,TalentServiceTest,DddTalent003TalentRoutingTest,DddTalentProfileApplicationRoutingTest,TalentClaimPolicyTest" test
```

结果：`PASS`。

覆盖要点：

- `TalentControllerTest`：列表查询注入 `dataScope/userId/deptId`，详情、标签、地址、手动补全等操作先调用 `assertCanOperate`。
- `TalentQueryServiceTest`：`TEAM_PUBLIC/MY_TALENTS/TEAM_PRIVATE` 视图过滤、PERSONAL/DEPT 详情越权拒绝、DataScopePolicy 灰度路径保持 claim 语义。
- `TalentServiceTest`：PERSONAL/DEPT claim 范围过滤、拉黑/取消拉黑越权负例、无 claim scoped query 返回空集。

## 前端 Targeted Tests

命令：

```powershell
npm --prefix frontend run test -- --run src/views/talent/constants.test.ts src/views/talent/composables/useTalentFilters.test.ts src/views/talent/components/TalentDetailModal.security.test.ts src/router/menuTree.test.ts
```

结果：`PASS`，4 files / 36 tests。

覆盖要点：达人视图枚举、筛选参数、详情隐藏字段与菜单 active key。

## 浏览器 E2E

命令：

```powershell
$env:E2E_BASE_URL='http://127.0.0.1:3001'
$env:E2E_BACKEND_URL='http://127.0.0.1:8081'
$env:E2E_ENV_FILE='.env.real-pre'
npx playwright test --project=chromium tests/e2e/29-talent-claim-protection.spec.ts
```

结果：`PASS`，2 tests passed。

覆盖要点：渠道组长进入 `/talent?view=TEAM_PUBLIC`，认领保护期达人时展示“该达人在保护期内”冲突提示。

## real-pre RBAC / 数据范围 E2E

命令：

```powershell
$env:E2E_REAL_PRE='true'
$env:E2E_REAL_PRE_P0='true'
$env:E2E_REAL_PRE_P0_DIR='runtime/qa/out/real-pre-p0-20260627-164147'
npx playwright test --project=real-pre-p0 tests/e2e/35-real-pre-rbac-scope.spec.ts
```

结果：`PASS`，summary：

```text
stepKey=35-real-pre-rbac-scope
runId=QA20260627-164147
status=PASS
conclusion=PASS
failures=[]
pendingReasons=[]
blockedReasons=[]
summary=runtime/qa/out/real-pre-p0-20260627-164147/steps/35-real-pre-rbac-scope/step-summary.json
```

关键事实：

- 六类账号均登录成功。
- `biz_leader/biz_staff/channel_leader/channel_staff/ops_staff` 的禁止页面均未停留在原禁止路由，未出现运行时错误。
- 禁止 API 返回 403：`/api/users`、`/api/configs`、`/api/samples/exports`、`/api/products` 等按角色被拦截。
- `admin/biz/channel` 范围探针均有样本；`ops_staff` 待发货样本为 0，但该脚本未标 PENDING，整体结论为 PASS。

## real-pre 达人 API 只读探针

命令：登录角色后调用 `/api/talents`，仅记录状态码、业务码、total 和 records，不输出 token。

结果：

```json
[
  {"role":"channel_leader","path":"/talents?view=TEAM_PUBLIC&page=1&size=5","http":200,"code":200,"total":37,"records":5},
  {"role":"channel_leader","path":"/talents?view=MY_TALENTS&page=1&size=5","http":200,"code":200,"total":0,"records":0},
  {"role":"channel_staff","path":"/talents?view=TEAM_PUBLIC&page=1&size=5","http":200,"code":200,"total":1,"records":1},
  {"role":"channel_staff","path":"/talents?view=MY_TALENTS&page=1&size=5","http":200,"code":200,"total":36,"records":5},
  {"role":"biz_staff","path":"/talents?view=TEAM_PUBLIC&page=1&size=5","http":403},
  {"role":"ops_staff","path":"/talents?view=TEAM_PUBLIC&page=1&size=5","http":403}
]
```

解释：渠道角色可访问达人列表；非达人域角色访问达人接口被拒绝。

## real-pre DB 与运行状态

只读 SQL：

```sql
SELECT count(*) FROM talent WHERE COALESCE(deleted,0)=0;
SELECT count(*) FROM talent_claim WHERE status=1 AND COALESCE(deleted,0)=0;
```

结果：

```text
talents=37
claims=36
active_claims=36
active_protected_claims=25
claims_with_address=2
```

角色数据范围：

```text
channel_leader data_scope=2 active_claims=0
channel_staff  data_scope=1 active_claims=36
biz_staff      data_scope=1 active_claims=0
ops_staff      data_scope=1 active_claims=0
```

运行状态：

```text
backend container health=healthy
frontend container health=healthy
GET http://127.0.0.1:3001/ HTTP 200
```

## 现象、证据、推论、结论

- 现象：#72 要求达人数据范围、越权负例和 E2E 入库 evidence。
- 证据：后端 targeted tests PASS，前端 targeted tests PASS，浏览器保护期 E2E PASS，real-pre RBAC/data-scope E2E PASS，API 探针显示非渠道角色访问 `/api/talents` 为 403。
- 推论：当前分支可证明达人列表视图、操作权限、详情数据范围和前端保护期提示均有自动化覆盖；real-pre 渠道/非渠道访问边界符合预期。
- 结论：#72 满足关闭条件。

## 剩余风险

- `gender` 筛选仍是达人域已知缺口，当前 API 会拒绝该筛选而不是静默假筛选。
- 本轮未新增真实 follow/tag 正向样本；#72 范围聚焦数据范围、越权负例和 E2E。
- 未执行远端 real-pre 部署。
