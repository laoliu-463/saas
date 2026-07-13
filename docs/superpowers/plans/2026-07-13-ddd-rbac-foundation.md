# DDD RBAC Dormant Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independently testable, dormant DDD authorization foundation inside the user bounded context without changing current request authorization or applying a live database migration.

**Architecture:** Introduce canonical permission and decision types, a pure fail-closed decision policy, a narrow `AuthorizationFacade`, and a port/adapter that reads active user-role-permission facts. Add idempotent PostgreSQL schema assets and Testcontainers coverage, but do not wire the new facade into JWT, controllers, aspects, role mutation, or current-user responses in this phase.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring Security, MyBatis-Plus 3.5.6, PostgreSQL 16, JUnit 5, Mockito, AssertJ, Testcontainers, Maven, PowerShell Harness.

---

## Scope and file map

Create:

- `backend/src/main/resources/db/alter-authorization-foundation-20260713.sql`
- `backend/src/main/java/com/colonel/saas/domain/user/api/PermissionCode.java`
- `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationScope.java`
- `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationReason.java`
- `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationDecision.java`
- `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationSubject.java`
- `backend/src/main/java/com/colonel/saas/domain/user/domain/GrantedRolePermission.java`
- `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationSnapshot.java`
- `backend/src/main/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicy.java`
- `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java`
- `backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationFacade.java`
- `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationApplicationService.java`
- `backend/src/main/java/com/colonel/saas/mapper/projection/AuthorizationSnapshotRow.java`
- `backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java`
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java`
- `backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationSchemaContractTest.java`
- `backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationDormancyContractTest.java`
- `backend/src/test/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicyTest.java`
- `backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationApplicationServiceTest.java`
- `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapterTest.java`
- `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSnapshotStoreIntegrationTest.java`
- `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSchemaMigrationIntegrationTest.java`

Modify:

- `backend/src/main/resources/db/init-db.sql`
- `backend/src/main/resources/db/migrate-all.sql`
- `backend/src/test/resources/db/mapper-integration-schema.sql`
- `backend/src/test/java/com/colonel/saas/testsupport/BaseIntegrationTest.java`
- `backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java`
- `docs/07-权限与数据范围.md`
- `docs/领域/用户域.md`
- `harness/reports/latest-evidence-20260713.md` through the Harness only
- one `harness/reports/retro-*.md` through the Harness only

Explicitly do not modify in this phase:

- `JwtAuthenticationFilter`, `JwtTokenProvider`, `AuthService`, `SecurityConfig`
- `RoleGuardAspect`, `DataScopeAspect`, `@RequireRoles`
- `SysUserRoleAssignmentApplicationService`, `SysRoleApplication`
- controllers, frontend permission store, menus, or existing external API responses
- live local/remote `real-pre` schema or data

## Command execution convention

All command blocks assume the shell starts at the isolated worktree root. Every Maven command must run with `backend` as the current directory because architecture and source-contract tests resolve `src/...` paths relative to the process working directory. Use this shape for every Maven invocation:

```powershell
Push-Location backend
mvn <arguments>
Pop-Location
```

Do not run Maven from the worktree root through `-f` indirection. In a block that also stages or commits files, `Pop-Location` must appear before `git add`, `git commit`, or any other root-relative Git command so Git always runs from the worktree root.

### Task 0: Establish a reproducible baseline

**Files:** no changes

- [ ] **Step 1: Work only in an isolated worktree**

Run:

```powershell
git status --short --branch
git rev-parse HEAD
```

Expected: the branch is an isolated `codex/` branch, the worktree has no unrelated source changes, and the base contains approved ADR-012.

- [ ] **Step 2: Record the inherited Harness limit state**

Run:

```powershell
$baseReports = git ls-tree -r --name-only ab201aa5 -- harness/reports |
    Where-Object { $_ -match '^harness/reports/[^/]+$' }
$baseReports.Count

$currentReports = Get-ChildItem .\harness\reports -File
$currentReports.Count

Get-Content -Raw .\harness\reports\latest-harness-limits-check.md
```

Expected on planning commit `ef76f09a`: `FAIL`. The fixed design base `ab201aa5` contains 85 direct `harness/reports` files, and the planning Harness added 4 tracked reports, so the reproducible current count is 89. The 71-file value in the older `latest-harness-limits-check.md` is stale and is read only as evidence of that discrepancy. `harness/scripts/check-harness-limits.ps1` currently hardcodes `D:\Projects\SAAS\harness` and writes the main worktree report; until a separate Harness-governance change fixes that defect, it is forbidden to invoke the script from an isolated worktree. This inherited exception permits development to continue but forbids a final `PASS` until a separate housekeeping batch fixes it. Do not archive or modify unrelated reports in the RBAC commit.

- [ ] **Step 3: Run the current user/security characterization set**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=SysUserRoleAssignmentApplicationServiceTest,CurrentUserPermissionPolicyTest,DataScopePolicyTest,DddArchitectureRedlineGuardTest"
Pop-Location
```

Expected: Maven exits `0`. If a test fails, stop and report the baseline failure before editing code.

### Task 1: Define the authorization schema contract before writing SQL

**Files:**

- Create: `backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationSchemaContractTest.java`
- Create: `backend/src/main/resources/db/alter-authorization-foundation-20260713.sql`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Modify: `backend/src/main/resources/db/migrate-all.sql`
- Modify: `backend/src/test/resources/db/mapper-integration-schema.sql`

- [ ] **Step 1: Add the failing schema contract test**

Use a file-content contract so fresh-volume schema, incremental migration, and Testcontainers schema cannot silently diverge:

```java
package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DddAuthorizationSchemaContractTest {

    private static final List<String> REQUIRED_FACTS = List.of(
            "authz_version bigint not null default 1",
            "create table if not exists sys_permission",
            "create table if not exists sys_role_permission",
            "create table if not exists sys_role_domain_scope",
            "create table if not exists sys_authz_change_log",
            "check (scope_code in ('self', 'group', 'all'))");

    @Test
    void migrationAndBootstrapSchemas_shouldContainAuthorizationFacts() throws IOException {
        for (Path path : List.of(
                Path.of("src/main/resources/db/alter-authorization-foundation-20260713.sql"),
                Path.of("src/main/resources/db/init-db.sql"),
                Path.of("src/test/resources/db/mapper-integration-schema.sql"))) {
            String sql = normalize(Files.readString(path));
            for (String fact : REQUIRED_FACTS) {
                assertThat(sql).as(path + " should contain " + fact).contains(fact);
            }
        }
    }

    @Test
    void migrateAll_shouldInvokeAuthorizationMigration() throws IOException {
        String sql = normalize(Files.readString(Path.of("src/main/resources/db/migrate-all.sql")));
        assertThat(sql).contains("\\i alter-authorization-foundation-20260713.sql");
    }

    private static String normalize(String sql) {
        return sql.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
```

- [ ] **Step 2: Run the contract and confirm RED**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=DddAuthorizationSchemaContractTest"
Pop-Location
```

Expected: failure because the migration file and the new authorization tables do not exist.

- [ ] **Step 3: Add the idempotent incremental SQL**

Put this exact structure in `alter-authorization-foundation-20260713.sql`:

```sql
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS authz_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS sys_permission (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_code     VARCHAR(128) NOT NULL,
    domain_code         VARCHAR(64) NOT NULL,
    resource_code       VARCHAR(64) NOT NULL,
    action_code         VARCHAR(64) NOT NULL,
    data_scope_required BOOLEAN NOT NULL DEFAULT FALSE,
    status              SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    deleted             SMALLINT NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           UUID,
    update_by           UUID,
    remark              VARCHAR(500),
    CONSTRAINT ck_sys_permission_code_parts CHECK (
        permission_code = resource_code || ':' || action_code
        AND permission_code = LOWER(permission_code)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_permission_code
    ON sys_permission(permission_code);
CREATE INDEX IF NOT EXISTS idx_sys_permission_domain_status
    ON sys_permission(domain_code, status, deleted);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       UUID NOT NULL REFERENCES sys_role(id),
    permission_id UUID NOT NULL REFERENCES sys_permission(id),
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_permission_permission
    ON sys_role_permission(permission_id);

CREATE TABLE IF NOT EXISTS sys_role_domain_scope (
    role_id     UUID NOT NULL REFERENCES sys_role(id),
    domain_code VARCHAR(64) NOT NULL,
    scope_code  VARCHAR(16) NOT NULL CHECK (scope_code IN ('SELF', 'GROUP', 'ALL')),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   UUID,
    update_by   UUID,
    PRIMARY KEY (role_id, domain_code)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_domain_scope_domain
    ON sys_role_domain_scope(domain_code, scope_code);

CREATE TABLE IF NOT EXISTS sys_authz_change_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_action   VARCHAR(64) NOT NULL,
    target_type     VARCHAR(64) NOT NULL,
    target_id       UUID NOT NULL,
    actor_user_id   UUID,
    before_snapshot JSONB,
    after_snapshot  JSONB,
    request_id      VARCHAR(128),
    trace_id        VARCHAR(128),
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_authz_change_target_time
    ON sys_authz_change_log(target_type, target_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_authz_change_actor_time
    ON sys_authz_change_log(actor_user_id, changed_at DESC);
```

The test normalizes case, so the uppercase `SELF/GROUP/ALL` constraint must be reflected in `REQUIRED_FACTS` as `check (scope_code in ('self', 'group', 'all'))`.

- [ ] **Step 4: Keep bootstrap and integration schemas aligned**

Add the same DDL block after the existing `sys_user_role` definition in both `init-db.sql` and `mapper-integration-schema.sql`. Add this final include to `migrate-all.sql`:

```sql
\i alter-authorization-foundation-20260713.sql
```

Do not seed role-permission mappings. Those mappings are business decisions reserved for the approved matrix in phase 3.

- [ ] **Step 5: Run GREEN and commit the schema contract**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=DddAuthorizationSchemaContractTest"
Pop-Location
git add backend/src/main/resources/db/alter-authorization-foundation-20260713.sql backend/src/main/resources/db/init-db.sql backend/src/main/resources/db/migrate-all.sql backend/src/test/resources/db/mapper-integration-schema.sql backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationSchemaContractTest.java
git commit -m "feat(auth): define authorization foundation schema"
```

Expected: test exits `0`; commit contains SQL assets and the contract only. No database command is run.

### Task 2: Build the fail-closed domain decision kernel with TDD

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/PermissionCode.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationScope.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationReason.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationDecision.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationSubject.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/domain/GrantedRolePermission.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationSnapshot.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicy.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicyTest.java`

- [ ] **Step 1: Write failing policy tests for the approved invariants**

Cover these cases in one test class:

```java
@Test
void decide_shouldDenyWhenPermissionIsNotGranted() {
    AuthorizationDecision decision = policy.decide(
            new PermissionCode("sample:read"), List.of());

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(AuthorizationReason.PERMISSION_NOT_GRANTED);
    assertThat(decision.scope()).isEqualTo(AuthorizationScope.DENY);
}

@Test
void decide_shouldDenyScopedPermissionWhenEveryGrantLacksDomainScope() {
    AuthorizationDecision decision = policy.decide(
            new PermissionCode("sample:read"),
            List.of(grant("sample:read", "sample", true, AuthorizationScope.DENY)));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(AuthorizationReason.DOMAIN_SCOPE_MISSING);
}

@Test
void decide_shouldMergeOnlyRolesThatGrantTheRequestedPermission() {
    AuthorizationDecision decision = policy.decide(
            new PermissionCode("sample:read"),
            List.of(
                    grant("sample:read", "sample", true, AuthorizationScope.GROUP),
                    grant("product:read", "product", true, AuthorizationScope.ALL)));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.scope()).isEqualTo(AuthorizationScope.GROUP);
}

@Test
void decide_shouldAllowUnscopedPermissionWithoutRoleDomainScope() {
    AuthorizationDecision decision = policy.decide(
            new PermissionCode("system:login"),
            List.of(grant("system:login", "system", false, AuthorizationScope.DENY)));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.scope()).isEqualTo(AuthorizationScope.ALL);
}
```

Also test that `PermissionCode` accepts `resource:action` lowercase syntax and rejects blank, uppercase, missing-colon, and extra-colon values.

- [ ] **Step 2: Run the tests and confirm RED**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationDecisionPolicyTest"
Pop-Location
```

Expected: test compilation fails because the new types do not exist.

- [ ] **Step 3: Implement canonical API types**

`PermissionCode` must reject noncanonical values rather than silently normalize them:

```java
public record PermissionCode(String value) {
    private static final Pattern CANONICAL =
            Pattern.compile("[a-z][a-z0-9-]{0,62}:[a-z][a-z0-9-]{0,62}");

    public PermissionCode {
        if (value == null || !CANONICAL.matcher(value).matches()) {
            throw new IllegalArgumentException("permissionCode must use canonical resource:action syntax");
        }
    }
}
```

Use these stable API enums and decision shape:

```java
public enum AuthorizationScope { DENY, SELF, GROUP, ALL }
```

```java
public enum AuthorizationReason {
    GRANTED,
    SUBJECT_NOT_ACTIVE,
    PERMISSION_NOT_GRANTED,
    DOMAIN_SCOPE_MISSING
}
```

```java
public record AuthorizationDecision(
        boolean allowed,
        String permissionCode,
        String domainCode,
        AuthorizationScope scope,
        AuthorizationReason reason) {

    public static AuthorizationDecision allow(
            PermissionCode permission, String domain, AuthorizationScope scope) {
        return new AuthorizationDecision(true, permission.value(), domain, scope, AuthorizationReason.GRANTED);
    }

    public static AuthorizationDecision deny(
            PermissionCode permission, String domain, AuthorizationReason reason) {
        return new AuthorizationDecision(false, permission.value(), domain, AuthorizationScope.DENY, reason);
    }
}
```

- [ ] **Step 4: Implement internal subject and grant records**

```java
public record AuthorizationSubject(UUID userId, UUID deptId, long authzVersion) {
    public AuthorizationSubject {
        Objects.requireNonNull(userId, "userId");
        if (authzVersion < 1) {
            throw new IllegalArgumentException("authzVersion must be positive");
        }
    }
}
```

```java
public record GrantedRolePermission(
        UUID roleId,
        PermissionCode permission,
        String domainCode,
        boolean dataScopeRequired,
        AuthorizationScope scope) {
}
```

```java
public record AuthorizationSnapshot(
        AuthorizationSubject subject,
        List<GrantedRolePermission> grants) {
    public AuthorizationSnapshot {
        Objects.requireNonNull(subject, "subject");
        grants = grants == null ? List.of() : List.copyOf(grants);
    }
}
```

- [ ] **Step 5: Implement the pure policy**

The policy must filter by the requested permission before calculating the widest scope. Use rank `DENY < SELF < GROUP < ALL`; for an unscoped permission return `ALLOW/ALL`; for a scoped permission with no non-DENY scope return `DOMAIN_SCOPE_MISSING`.

- [ ] **Step 6: Run GREEN and commit the decision kernel**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationDecisionPolicyTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/domain/user/api backend/src/main/java/com/colonel/saas/domain/user/domain backend/src/main/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicy.java backend/src/test/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicyTest.java
git commit -m "feat(auth): add fail-closed decision policy"
```

Expected: policy tests pass; no Spring or persistence dependency appears in the new policy/API types.

### Task 3: Add the authorization snapshot port and active-fact adapter

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/projection/AuthorizationSnapshotRow.java`
- Create: `backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapterTest.java`

- [ ] **Step 1: Write failing adapter unit tests**

Test row-to-domain mapping without a database:

```java
@Test
void loadActiveSnapshot_shouldMapMissingScopeToDeny() {
    UUID userId = UUID.randomUUID();
    AuthorizationSnapshotRow row = new AuthorizationSnapshotRow();
    row.setUserId(userId);
    row.setAuthzVersion(1L);
    row.setRoleId(UUID.randomUUID());
    row.setPermissionCode("sample:read");
    row.setDomainCode("sample");
    row.setDataScopeRequired(true);
    row.setScopeCode(null);
    when(mapper.findActiveSnapshotRows(userId)).thenReturn(List.of(row));

    AuthorizationSnapshot snapshot = adapter.loadActiveSnapshot(userId).orElseThrow();

    assertThat(snapshot.grants()).singleElement().satisfies(grant -> {
        assertThat(grant.permission().value()).isEqualTo("sample:read");
        assertThat(grant.scope()).isEqualTo(AuthorizationScope.DENY);
    });
}
```

Also cover active subject mapping, a subject row with no grant, and empty/null mapper results.

- [ ] **Step 2: Confirm RED**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=SysAuthorizationSnapshotStoreAdapterTest"
Pop-Location
```

Expected: test compilation fails because the port, rows, mapper, and adapter do not exist.

- [ ] **Step 3: Add the domain port**

```java
public interface AuthorizationSnapshotStore {
    Optional<AuthorizationSnapshot> loadActiveSnapshot(UUID userId);
}
```

- [ ] **Step 4: Add mapper projection beans**

Use ordinary no-argument Java/Lombok beans, not domain records, for MyBatis result mapping:

```java
@Data
public class AuthorizationSnapshotRow {
    private UUID userId;
    private UUID deptId;
    private Long authzVersion;
    private UUID roleId;
    private String permissionCode;
    private String domainCode;
    private Boolean dataScopeRequired;
    private String scopeCode;
}
```

- [ ] **Step 5: Add the active-fact mapper**

```java
@Mapper
public interface AuthorizationSnapshotMapper {

    @Select("""
            SELECT u.id AS user_id,
                   u.dept_id,
                   u.authz_version,
                   r.id AS role_id,
                   p.permission_code,
                   p.domain_code,
                   p.data_scope_required,
                   rds.scope_code
            FROM sys_user u
            LEFT JOIN sys_user_role ur
              ON ur.user_id = u.id
             AND ur.deleted = 0
            LEFT JOIN sys_role r
              ON r.id = ur.role_id
             AND r.status = 1
             AND r.deleted = 0
            LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
            LEFT JOIN sys_permission p
              ON p.id = rp.permission_id
             AND p.status = 1
             AND p.deleted = 0
            LEFT JOIN sys_role_domain_scope rds
              ON rds.role_id = r.id
             AND rds.domain_code = p.domain_code
            WHERE u.id = #{userId}
              AND u.status = 1
              AND u.deleted = 0
            ORDER BY p.permission_code, r.id
            """)
    List<AuthorizationSnapshotRow> findActiveSnapshotRows(@Param("userId") UUID userId);
}
```

The single statement is the consistency boundary: it distinguishes an active user with zero grants from a missing/inactive user without a two-query race. Disabled/deleted roles and permissions never reach the policy, and the scope comes only from a role that grants the requested permission.

- [ ] **Step 6: Implement the infrastructure adapter**

Return `Optional.empty()` when the mapper returns no rows. Build the subject from the first row, ignore rows whose `permissionCode` is null, and map a null or unknown `scopeCode` to `AuthorizationScope.DENY`. Do not catch database exceptions here; phase 2 will translate authorization-store unavailability to HTTP 503. Do not fall back to legacy roles from this adapter.

- [ ] **Step 7: Run GREEN and the architecture guard, then commit**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=SysAuthorizationSnapshotStoreAdapterTest,DddArchitectureRedlineGuardTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java backend/src/main/java/com/colonel/saas/mapper/projection backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapterTest.java
git commit -m "feat(auth): add authorization snapshot adapter"
```

Expected: tests pass, and only the infrastructure adapter imports the mapper.

### Task 4: Expose a narrow, dormant AuthorizationFacade

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationFacade.java`
- Create: `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationApplicationService.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationApplicationServiceTest.java`
- Modify: `backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java`

- [ ] **Step 1: Write failing application-service tests**

```java
@Test
void authorize_shouldDenyWhenSubjectIsNotActive() {
    UUID userId = UUID.randomUUID();
    when(store.loadActiveSnapshot(userId)).thenReturn(Optional.empty());

    AuthorizationDecision decision = service.authorize(userId, "sample:read");

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo(AuthorizationReason.SUBJECT_NOT_ACTIVE);
}

@Test
void authorize_shouldDelegateActiveFactsToPolicy() {
    UUID userId = UUID.randomUUID();
    AuthorizationSubject subject = new AuthorizationSubject(userId, UUID.randomUUID(), 7);
    when(store.loadActiveSnapshot(userId)).thenReturn(Optional.of(
            new AuthorizationSnapshot(subject, List.of(
                    new GrantedRolePermission(
                            UUID.randomUUID(), new PermissionCode("sample:read"),
                            "sample", true, AuthorizationScope.SELF)))));

    AuthorizationDecision decision = service.authorize(userId, "sample:read");

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.scope()).isEqualTo(AuthorizationScope.SELF);
}
```

Also verify that null user IDs deny and malformed permission codes throw `IllegalArgumentException` as a programming/configuration error.

- [ ] **Step 2: Confirm RED**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationApplicationServiceTest"
Pop-Location
```

Expected: test compilation fails because the facade and service do not exist.

- [ ] **Step 3: Add the narrow facade**

```java
public interface AuthorizationFacade {
    AuthorizationDecision authorize(UUID userId, String permissionCode);
}
```

Do not add user directory, role normalization, menu, or ownership methods. Existing `UserDomainFacade` remains the migration adapter and receives no new responsibility.

- [ ] **Step 4: Add the transactional read application service**

```java
@Service
public class AuthorizationApplicationService implements AuthorizationFacade {
    private final AuthorizationSnapshotStore store;
    private final AuthorizationDecisionPolicy policy;

    public AuthorizationApplicationService(
            AuthorizationSnapshotStore store,
            AuthorizationDecisionPolicy policy) {
        this.store = store;
        this.policy = policy;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationDecision authorize(UUID userId, String rawPermissionCode) {
        PermissionCode permission = new PermissionCode(rawPermissionCode);
        if (userId == null) {
            return AuthorizationDecision.deny(
                    permission, null, AuthorizationReason.SUBJECT_NOT_ACTIVE);
        }
        return store.loadActiveSnapshot(userId)
                .map(snapshot -> policy.decide(permission, snapshot.grants()))
                .orElseGet(() -> AuthorizationDecision.deny(
                        permission, null, AuthorizationReason.SUBJECT_NOT_ACTIVE));
    }
}
```

- [ ] **Step 5: Wire the pure policy at the composition root**

Add to `DomainPolicyConfig`:

```java
@Bean
public AuthorizationDecisionPolicy authorizationDecisionPolicy() {
    return new AuthorizationDecisionPolicy();
}
```

- [ ] **Step 6: Run GREEN and commit**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationApplicationServiceTest,AuthorizationDecisionPolicyTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationFacade.java backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationApplicationService.java backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationApplicationServiceTest.java
git commit -m "feat(auth): expose dormant authorization facade"
```

Expected: tests pass; no controller, JWT, filter, aspect, or legacy facade calls the new interface.

### Task 5: Prove PostgreSQL behavior and migration idempotency

**Files:**

- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSchemaMigrationIntegrationTest.java`
- Create: `backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSnapshotStoreIntegrationTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/testsupport/BaseIntegrationTest.java`

- [ ] **Step 1: Extend the Testcontainers cleanup list**

Place authorization tables before their parents in `TRUNCATE TABLE`:

```sql
sys_authz_change_log,
sys_role_domain_scope,
sys_role_permission,
sys_permission,
sys_user_role,
sys_role,
sys_user,
```

Keep `RESTART IDENTITY CASCADE`. Do not add runtime schema patching to `BaseIntegrationTest`; the canonical test schema now owns these tables.

- [ ] **Step 2: Add a real PostgreSQL adapter test**

Create `AuthorizationSnapshotStoreIntegrationTest extends BaseIntegrationTest` and autowire `AuthorizationSnapshotStore` plus `JdbcTemplate`. Seed:

- one active user with `authz_version=7`;
- one active role granting `sample:read` with `GROUP` scope;
- one disabled role granting the same permission with `ALL` scope;
- one disabled permission granted by the active role.

Assert:

```java
AuthorizationSnapshot snapshot = store.loadActiveSnapshot(userId).orElseThrow();
assertThat(snapshot.subject().authzVersion()).isEqualTo(7L);

assertThat(snapshot.grants())
        .singleElement()
        .satisfies(grant -> {
             assertThat(grant.permission().value()).isEqualTo("sample:read");
             assertThat(grant.scope()).isEqualTo(AuthorizationScope.GROUP);
         });
```

This test proves disabled roles/permissions cannot expand a decision.

- [ ] **Step 3: Add the migration idempotency test**

```java
class AuthorizationSchemaMigrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authorizationMigration_shouldBeIdempotent() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/alter-authorization-foundation-20260713.sql"));

        populator.execute(dataSource);
        populator.execute(dataSource);

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'sys_user'
                  AND column_name = 'authz_version'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 4: Run Testcontainers GREEN and commit**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationSchemaMigrationIntegrationTest,AuthorizationSnapshotStoreIntegrationTest"
Pop-Location
git add backend/src/test/java/com/colonel/saas/testsupport/BaseIntegrationTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSchemaMigrationIntegrationTest.java backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSnapshotStoreIntegrationTest.java
git commit -m "test(auth): verify authorization persistence facts"
```

Expected: PostgreSQL 16 container starts; migration executes twice; active-fact filtering passes.

### Task 6: Prove the new foundation is dormant and architecture-safe

**Files:**

- Create: `backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationDormancyContractTest.java`

- [ ] **Step 1: Add a no-consumer contract**

Scan production Java outside `domain/user` and assert that no file references `AuthorizationFacade` or `AuthorizationApplicationService`:

```java
@Test
void authorizationFoundation_shouldRemainDormantOutsideUserDomain() throws IOException {
    Path root = Path.of("src/main/java");
    try (Stream<Path> paths = Files.walk(root)) {
        List<String> consumers = paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().replace('\\', '/').contains("/domain/user/"))
                .filter(path -> {
                    try {
                        String source = Files.readString(path);
                        return source.contains("AuthorizationFacade")
                                || source.contains("AuthorizationApplicationService");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .map(Path::toString)
                .toList();
        assertThat(consumers).isEmpty();
    }
}
```

This is intentionally temporary. Phase 2 must replace it with explicit allowed security-layer consumers when shadow execution is introduced.

- [ ] **Step 2: Run all phase-1 tests**

Run:

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=DddAuthorizationSchemaContractTest,DddAuthorizationDormancyContractTest,AuthorizationDecisionPolicyTest,AuthorizationApplicationServiceTest,SysAuthorizationSnapshotStoreAdapterTest,AuthorizationSnapshotStoreIntegrationTest,AuthorizationSchemaMigrationIntegrationTest,DddArchitectureRedlineGuardTest"
Pop-Location
```

Expected: Maven exits `0`; no existing authorization class is required in the new test set.

- [ ] **Step 3: Compile the complete backend**

Run:

```powershell
Push-Location backend
mvn -q -DskipTests compile
Pop-Location
```

Expected: Maven exits `0`.

- [ ] **Step 4: Review the phase diff for forbidden activation**

Run:

```powershell
git diff ab201aa5 -- backend/src/main/java backend/src/main/resources/db backend/src/test
rg -n "AuthorizationFacade|AuthorizationApplicationService" backend/src/main/java --glob "*.java"
```

Expected: references are confined to the user-domain foundation and composition root. There are no edits to JWT, controllers, aspects, role assignments, or frontend files.

- [ ] **Step 5: Commit the dormancy guard**

Run:

```powershell
git add backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationDormancyContractTest.java
git commit -m "test(auth): keep authorization foundation dormant"
```

### Task 7: Update facts, run the mandated Harness, and publish evidence

**Files:**

- Modify: `docs/07-权限与数据范围.md`
- Modify: `docs/领域/用户域.md`
- Generated/modified by Harness: `harness/reports/latest-evidence-20260713.md`
- Generated/modified by Harness: one `harness/reports/retro-*.md`

- [ ] **Step 1: Update docs with verified status only**

Record:

- schema assets exist but have not been applied to local or remote `real-pre`;
- the new facade is dormant and has no request-path consumer;
- legacy role/JWT/data-scope behavior remains current runtime fact;
- phase 2 requires explicit database migration approval;
- no role-permission seed matrix exists yet.

Do not write that RBAC migration is complete or active.

- [ ] **Step 2: Run the unique project execution entry**

Run from the isolated worktree:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -Message "auth: add dormant DDD RBAC foundation"
```

Expected in scope:

- backend build/package;
- corresponding local Docker container restart;
- local health check;
- relevant automated business verification;
- evidence report and retro summary;
- safe commit/push through the project workflow.

Do not add `-DeployRemote true`. Do not execute the authorization migration against local or remote `real-pre` in this phase.

- [ ] **Step 3: Verify evidence truthfulness**

The evidence report must say:

- database migration: `PENDING — not authorized/not applied`;
- runtime activation: `PENDING — dormant foundation only`;
- remote deployment: `false`;
- focused tests, compile, package, restart, and health: actual observed results;
- Harness limit check: inherited `FAIL` until the separate report-cleanup batch lands;
- overall conclusion: `PARTIAL` if that inherited failure remains, never `PASS`.

- [ ] **Step 4: Verify branch and upstream state**

Run:

```powershell
git status --short --branch
git log -5 --oneline
git branch -vv
```

Expected: only intentional Harness-generated evidence remains, or the worktree is clean; all implementation commits are on the isolated branch and its upstream reflects the pushed HEAD.

## Phase-1 acceptance checklist

- [ ] Permission syntax is canonical and tested.
- [ ] Missing permission or required domain scope returns `DENY`.
- [ ] Scope is merged only from roles that grant the requested permission.
- [ ] Disabled/deleted users, roles, and permissions are excluded by the active-fact query.
- [ ] Migration is idempotent in PostgreSQL 16 tests.
- [ ] Fresh, incremental, and Testcontainers schemas contain the same authorization facts.
- [ ] No permission mappings are seeded without business approval.
- [ ] No current request, JWT, controller, aspect, role mutation, menu, or frontend path consumes the new facade.
- [ ] Existing user/security characterization tests still pass.
- [ ] Backend compiles; local real-pre backend is rebuilt/restarted and healthy.
- [ ] Evidence distinguishes completed work from pending migration and activation.
- [ ] No remote deployment or live schema write occurred.

## Next planning gate

After phase 1 has evidence and the database migration window is explicitly approved, write and review `2026-07-13-ddd-rbac-shadow-runtime.md`. That plan will own schema application, `authzVersion` transaction semantics, JWT version checks, versioned cache, `AuthorizationPrincipal`, `LEGACY/SHADOW/ENFORCE`, 401/403/503 translation, and shadow-difference observability. None of those changes belong in this phase-1 commit series.
