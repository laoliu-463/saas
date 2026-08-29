# Phase 2 Task 9：本地 migration 人工门

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 9: Apply the dormant schema to local real-pre only after explicit approval

**Files:**

- Existing migration: `backend/src/main/resources/db/alter-authorization-foundation-20260713.sql`
- Existing schema test: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSchemaMigrationIntegrationTest.java`
- Evidence output: `harness/reports/current/latest-rbac-phase2-shadow-runtime.md`

- [ ] **Step 1: Stop and obtain explicit local migration authorization**

The required approval sentence is unambiguous: “批准在本地 real-pre 执行 `alter-authorization-foundation-20260713.sql`”。“继续”“可以”“按计划做”不能代替本门的数据库写授权。

If approval is absent, stop here with `Database migration=PENDING`, do not restart the backend with the Phase 2 entity mapping, and do not mark Phase 2 PASS.

- [ ] **Step 2: Capture pre-migration read-only facts**

Run against the existing local real-pre PostgreSQL container without printing environment values:

```powershell
$container = docker ps --filter "name=^/saas-active-postgres-real-pre-1$" --format "{{.ID}}"
if ([string]::IsNullOrWhiteSpace($container)) {
    throw "local real-pre postgres container is not running"
}
docker exec $container sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At -c "SELECT (SELECT count(*) FROM information_schema.tables WHERE table_schema=''public'' AND table_name IN (''sys_permission'',''sys_role_permission'',''sys_role_domain_scope'',''sys_authz_change_log'')), (SELECT count(*) FROM information_schema.columns WHERE table_schema=''public'' AND table_name=''sys_user'' AND column_name=''authz_version'');"'
```

Expected for the first Phase 2 activation, based on Phase 1 evidence: `0|0`. If the result differs, treat it as new evidence, inspect schema definitions, and do not overwrite or drop anything.

- [ ] **Step 3: Apply only the approved idempotent migration twice**

```powershell
$migration = Resolve-Path 'backend/src/main/resources/db/alter-authorization-foundation-20260713.sql'
docker cp $migration.Path "${container}:/tmp/alter-authorization-foundation-20260713.sql"
docker exec $container sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/alter-authorization-foundation-20260713.sql'
docker exec $container sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f /tmp/alter-authorization-foundation-20260713.sql'
```

Expected: both invocations exit 0. Do not execute `migrate-all.sql`; the database runbook identifies unconditional historical DML there as a repeatability risk.

- [ ] **Step 4: Verify post-migration schema and absence of seed writes**

```powershell
docker exec $container sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -At -c "SELECT (SELECT count(*) FROM information_schema.tables WHERE table_schema=''public'' AND table_name IN (''sys_permission'',''sys_role_permission'',''sys_role_domain_scope'',''sys_authz_change_log'')), (SELECT count(*) FROM information_schema.columns WHERE table_schema=''public'' AND table_name=''sys_user'' AND column_name=''authz_version''), (SELECT count(*) FROM sys_user WHERE authz_version IS NULL OR authz_version < 1), (SELECT count(*) FROM sys_permission), (SELECT count(*) FROM sys_role_permission), (SELECT count(*) FROM sys_role_domain_scope), (SELECT count(*) FROM sys_authz_change_log);"'
```

Expected for first activation: `4|1|0|0|0|0|0`. If authorization tables already contain reviewed data from another approved workflow, record actual counts and provenance; never delete them to force this expected value.

- [ ] **Step 5: Record rollback boundary**

Rollback is configuration/code based:

- Set runtime back to `LEGACY` and restart through the fixed Harness script.
- Revert the Phase 2 code commit if required.
- Keep `authz_version` and the four additive tables; do not drop tables, columns, indexes, constraints, PostgreSQL volume, or Redis volume.
- Generate a new rollback evidence report; do not reuse pre-rollback PASS status.

