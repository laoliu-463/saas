# 合作单操作栏：验证、门禁与交付

## Task 10：补齐跨层验收测试

**Files:**

- Create: `tests/e2e/39-cooperation-workbench-actions.spec.ts`
- Modify: `docs/接口/寄样接口.md`
- Modify: `docs/接口/达人接口.md`
- Modify: `docs/领域/寄样域.md`
- Modify: `docs/领域/达人域.md`

- [ ] 先写失败的端到端用例，覆盖管理员进入合作单列表、八项操作固定竖排、禁用原因、审核、编辑、三类复制、投诉和私有备注。
- [ ] 用普通用户会话验证越权按钮禁用、私有备注隔离、投诉风险标识可见。
- [ ] 验证待交作业、已完成、已关闭状态下“修改订单”禁用，且系统状态流转不因管理员身份放开。
- [ ] 更新接口和领域文档，明确状态、权限、事务、隐私与存储边界。

Run backend regression:

```powershell
mvn -f backend/pom.xml -Dtest=CooperationWorkbenchActionsSchemaContractTest,SysUserRoleRecipientLookupAdapterTest,LegacyTalentDomainFacadeTest,SampleCooperationActionPolicyTest,SampleCooperationApplicationServiceTest,CopyTextPolicyDouyinShareTest,CopyPromotionApplicationServiceTest,SampleOrderCopyPolicyTest,ComplaintImagePolicyTest,TalentComplaintApplicationServiceTest,TalentComplaintControllerTest,SampleControllerTest test
```

Expected: exit code `0`; all listed tests pass.

Run frontend regression:

```powershell
npm --prefix frontend run test -- src/views/sample/cooperation-actions.test.ts src/api/sample.test.ts src/views/sample/components/CooperationActionColumn.test.ts src/views/sample/components/SampleEditModal.test.ts src/views/sample/components/TalentComplaintModal.test.ts src/views/sample/components/PrivateNoteModal.test.ts src/components/talent/TalentComplaintReminderPopover.test.ts src/views/talent/index.test.ts src/views/talent/components/TalentDetailModal.security.test.ts
```

Expected: exit code `0`; all listed test files pass.

Run browser acceptance after the `real-pre` stack is healthy:

```powershell
npx playwright test tests/e2e/39-cooperation-workbench-actions.spec.ts --project=chromium
```

Expected: exit code `0`; screenshots and traces contain no secret values.

## Task 11：安全应用本地 real-pre 迁移

- [ ] 先确认 `postgres-real-pre` 容器正在运行，禁止清库、删卷或切换 mock。
- [ ] 把幂等迁移文件复制进容器并用 `ON_ERROR_STOP=1` 执行。
- [ ] 查询新增表、索引和约束；不得输出数据库口令。

```powershell
$pgContainer = docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml ps -q postgres-real-pre
if ([string]::IsNullOrWhiteSpace($pgContainer)) { throw 'postgres-real-pre 容器未运行' }
docker cp backend/src/main/resources/db/migrate/V20260716_001__cooperation_workbench_actions.sql "${pgContainer}:/tmp/V20260716_001__cooperation_workbench_actions.sql"
docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/V20260716_001__cooperation_workbench_actions.sql'
docker compose --env-file .env.real-pre -f docker-compose.real-pre.yml exec -T postgres-real-pre sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from information_schema.tables where table_schema=''public'' and table_name in (''sample_private_note'',''talent_complaint'',''talent_complaint_attachment'',''talent_complaint_reminder'');"'
```

Expected: migration exits `0`; final query returns `4`.

## Task 12：图谱复核与统一 Harness

- [ ] 使用 code-review-graph `detect_changes` 检查实际变更。
- [ ] 对样品编辑、真实推广复制、投诉与提醒入口分别运行 `get_impact_radius`。
- [ ] 使用 `get_review_context` 检查跨域调用和测试缺口；图谱无结果时才回退到 `rg`。
- [ ] 从任务基线提交动态生成 `OwnedFiles`，不得夹带用户已有修改。
- [ ] 通过项目唯一入口构建、重启容器、健康检查并执行业务验证。

```powershell
$ownedFiles = @(git diff --name-only 26fce056..HEAD | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($ownedFiles.Count -eq 0) { throw '未发现本任务文件' }
$businessCommand = 'mvn -f backend/pom.xml -Dtest=CooperationWorkbenchActionsSchemaContractTest,SysUserRoleRecipientLookupAdapterTest,LegacyTalentDomainFacadeTest,SampleCooperationActionPolicyTest,SampleCooperationApplicationServiceTest,CopyTextPolicyDouyinShareTest,CopyPromotionApplicationServiceTest,SampleOrderCopyPolicyTest,ComplaintImagePolicyTest,TalentComplaintApplicationServiceTest,TalentComplaintControllerTest,SampleControllerTest test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; npm --prefix frontend run test -- src/views/sample/cooperation-actions.test.ts src/api/sample.test.ts src/views/sample/components/CooperationActionColumn.test.ts src/views/sample/components/SampleEditModal.test.ts src/views/sample/components/TalentComplaintModal.test.ts src/views/sample/components/PrivateNoteModal.test.ts src/components/talent/TalentComplaintReminderPopover.test.ts src/views/talent/index.test.ts src/views/talent/components/TalentDetailModal.security.test.ts; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; npm run e2e:real-pre:p0:preflight'
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey cooperation-workbench-actions -ContentMaintenance off -OwnedFiles ($ownedFiles -join ';') -BusinessCommand $businessCommand -Message 'feat(sample): add cooperation workbench actions'
```

Expected: build, Docker restart, health check and business validation are recorded truthfully in `harness/reports/current/latest-cooperation-workbench-actions.md`.

- [ ] 运行 Harness 分层限制检查。

```powershell
powershell -ExecutionPolicy Bypass -File harness/scripts/check-harness-limits.ps1 -BaselineRef HEAD
```

Expected: current task introduces no new limit violation; historical debt remains separately reported.

## Task 13：交付结论与提交

- [ ] 检查 evidence 含时间、环境、分支、commit、工作区、构建、容器、健康、业务验证、远端部署、结论、风险和 retro。
- [ ] 若没有可安全使用的真实达人，不写入真实投诉数据；报告必须写“真实投诉写入未执行，已由服务、控制器和浏览器契约验证”，不得记为真实闭环 `PASS`。
- [ ] 本任务不远端部署，除非用户另行明确授权。
- [ ] 只暂存 `OwnedFiles` 和最新 evidence，复核 staged diff 后提交并推送当前分支。

```powershell
git diff --check
git status --short
git diff --cached --stat
git commit -m "feat(sample): add cooperation workbench actions"
git push
```

Expected: push succeeds; unrelated dirty files remain unstaged；最终仅对证据支持的结果声明完成。
