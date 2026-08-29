# Performance Access Scope Real-Pre Reconcile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个严格只读的 real-pre 业绩权限探针，用真实 admin/leader/staff 账号把 `/api/performance` 的总数、当前页归属和越权拒绝与 PostgreSQL 最终归属事实对账，并对缺少渠道正向样本如实输出 `PARTIAL`。

**Architecture:** 探针复用现有 real-pre 环境守卫、证据目录和脱敏工具，但独立实现业绩域的 `final_channel_user_id/final_recruiter_user_id` SQL 口径。运行器先从 `/users/current` 获取服务端实际权限上下文，再比较 API 与 SQL，并用只读列表/详情完成负向验证；所有判断通过导出的纯函数接受 Node `node:test` 覆盖。

**Tech Stack:** Node.js 18+ CommonJS、`node:test`、Fetch API、PostgreSQL `psql`（Docker exec）、Spring Boot real-pre API、PowerShell Harness、Docker Compose

---

## 文件映射

- Create `runtime/qa/real-pre-performance-access-reconcile.cjs`：环境守卫、角色归一化、业绩范围 SQL、API/SQL 对账、只读越权验证、脱敏报告和 CLI。
- Create `runtime/qa/real-pre-performance-access-reconcile.test.cjs`：纯函数和安全分级单元测试。
- Modify `package.json`：增加稳定的探针执行命令。
- Modify `docs/ddd-completion-evidence-matrix.md`：更新 Y-17 的真实账号 API/SQL 证据和剩余缺口，状态仍为 `PARTIAL`。
- Modify `harness/rules/state/snapshots/DOMAIN_STATUS.md`：记录本轮 Y-17 最新小切片及下一步。
- Create/Update `harness/reports/current/latest-ddd-performance-access-real-pre-reconcile.md`：由统一 Harness 生成构建、容器、健康、业务验证和 retro 证据。

### Task 1: 用纯函数测试锁定业绩权限口径

**Files:**
- Create: `runtime/qa/real-pre-performance-access-reconcile.test.cjs`
- Test: `runtime/qa/real-pre-performance-access-reconcile.test.cjs`

- [ ] **Step 1: 写入失败测试**

创建测试文件，完整覆盖环境、角色 SQL、fail-closed、逐行归属和结论分级：

```javascript
const test = require('node:test');
const assert = require('node:assert/strict');

const {
  assertSuccessfulApiResponse,
  buildCountSql,
  buildOutOfScopeSql,
  classifyRoleEvidence,
  isOwnershipInScope,
  isPassingRealPreEnv,
  normalizeCurrentUser,
  resolveRoleCases,
  resolvePerformanceScope,
  summarizeStatus
} = require('./real-pre-performance-access-reconcile.cjs');

const USER_ID = '00000000-0000-0000-0000-000000000001';
const OTHER_ID = '00000000-0000-0000-0000-000000000002';
const DEPT_ID = '00000000-0000-0000-0000-000000000003';

test('assertSuccessfulApiResponse rejects failed business envelopes behind HTTP 200', () => {
  assert.throws(
    () => assertSuccessfulApiResponse({ ok: true, status: 200, body: { code: 500 } }, '/performance'),
    /business code 500/
  );
  assert.doesNotThrow(
    () => assertSuccessfulApiResponse({ ok: true, status: 200, body: { code: 200, data: {} } }, '/performance')
  );
});

test('isPassingRealPreEnv only accepts guarded real-pre runtime', () => {
  assert.equal(isPassingRealPreEnv({
    environmentLabel: 'REAL-PRE', activeProfiles: ['real-pre'],
    appTestEnabled: false, douyinTestEnabled: false, database: 'saas_real_pre'
  }), true);
  assert.equal(isPassingRealPreEnv({
    environmentLabel: 'TEST', activeProfiles: ['test'],
    appTestEnabled: true, douyinTestEnabled: false, database: 'saas_test'
  }), false);
});

test('resolvePerformanceScope follows performance role precedence', () => {
  assert.equal(resolvePerformanceScope({ userId: USER_ID, dataScope: 'ALL', roleCodes: ['admin'] }).kind, 'ALL');
  assert.equal(resolvePerformanceScope({ userId: USER_ID, dataScope: 'PERSONAL', roleCodes: ['channel_staff'] }).kind, 'CHANNEL_STAFF');
  assert.equal(resolvePerformanceScope({ userId: USER_ID, dataScope: 'PERSONAL', roleCodes: ['biz_staff'] }).kind, 'BIZ_STAFF');
  assert.equal(resolvePerformanceScope({ userId: USER_ID, deptId: DEPT_ID, dataScope: 'DEPT', roleCodes: ['channel_leader'] }).kind, 'CHANNEL_LEADER');
  assert.equal(resolvePerformanceScope({ userId: USER_ID, deptId: DEPT_ID, dataScope: 'DEPT', roleCodes: ['biz_leader'] }).kind, 'BIZ_LEADER');
});

test('buildCountSql uses final performance owners and never dashboard order owner fields', () => {
  const channelSql = buildCountSql(resolvePerformanceScope({ userId: USER_ID, dataScope: 'PERSONAL', roleCodes: ['channel_staff'] }));
  const bizSql = buildCountSql(resolvePerformanceScope({ userId: USER_ID, dataScope: 'PERSONAL', roleCodes: ['biz_staff'] }));
  assert.match(channelSql, /FROM performance_records pr/);
  assert.match(channelSql, /pr\.is_valid = TRUE/);
  assert.match(channelSql, /pr\.final_channel_user_id = '00000000-0000-0000-0000-000000000001'::uuid/);
  assert.match(bizSql, /pr\.final_recruiter_user_id = '00000000-0000-0000-0000-000000000001'::uuid/);
  assert.doesNotMatch(`${channelSql}\n${bizSql}`, /co\.(user_id|dept_id)/);
});

test('restricted scopes fail closed when user or department context is missing', () => {
  assert.throws(() => resolvePerformanceScope({ dataScope: 'PERSONAL', roleCodes: ['channel_staff'] }), /userId/);
  assert.throws(() => resolvePerformanceScope({ userId: USER_ID, dataScope: 'DEPT', roleCodes: ['biz_leader'] }), /deptId/);
  assert.throws(() => resolvePerformanceScope({ userId: "bad';drop", dataScope: 'PERSONAL', roleCodes: ['biz_staff'] }), /UUID/);
});

test('ownership checks use channel, recruiter and department members independently', () => {
  assert.equal(isOwnershipInScope(
    { final_channel_user_id: USER_ID, final_recruiter_user_id: OTHER_ID },
    { kind: 'CHANNEL_STAFF', userId: USER_ID }, []
  ), true);
  assert.equal(isOwnershipInScope(
    { final_channel_user_id: USER_ID, final_recruiter_user_id: OTHER_ID },
    { kind: 'BIZ_STAFF', userId: USER_ID }, []
  ), false);
  assert.equal(isOwnershipInScope(
    { final_channel_user_id: OTHER_ID, final_recruiter_user_id: null },
    { kind: 'CHANNEL_LEADER', deptId: DEPT_ID }, [OTHER_ID]
  ), true);
});

test('out-of-scope SQL treats null owners as outside restricted scope', () => {
  const sql = buildOutOfScopeSql(resolvePerformanceScope({
    userId: USER_ID, dataScope: 'PERSONAL', roleCodes: ['channel_staff']
  }));
  assert.match(sql, /NOT COALESCE\(\(pr\.final_channel_user_id = .*\), FALSE\)/);
  assert.match(sql, /LIMIT 1/);
});

test('role evidence distinguishes pass, missing positive sample and failures', () => {
  assert.equal(classifyRoleEvidence({
    apiTotal: 2, dbTotal: 2,
    ownershipChecks: [{ pass: true }],
    negativeChecks: [{ required: true, status: 'PASS' }], errors: []
  }), 'PASS');
  assert.equal(classifyRoleEvidence({
    apiTotal: 0, dbTotal: 0,
    ownershipChecks: [],
    negativeChecks: [{ required: true, status: 'PASS' }], errors: []
  }), 'PARTIAL_NO_POSITIVE_SAMPLE');
  assert.equal(classifyRoleEvidence({
    apiTotal: 2, dbTotal: 3,
    ownershipChecks: [], negativeChecks: [], errors: []
  }), 'FAIL');
  assert.equal(classifyRoleEvidence({
    apiTotal: 2, dbTotal: 2,
    ownershipChecks: [{ pass: false }], negativeChecks: [], errors: []
  }), 'FAIL');
});

test('overall status preserves partial and fail evidence', () => {
  assert.equal(summarizeStatus([{ status: 'PASS' }, { status: 'PASS' }]), 'PASS');
  assert.equal(summarizeStatus([{ status: 'PASS' }, { status: 'PARTIAL_NO_POSITIVE_SAMPLE' }]), 'PARTIAL');
  assert.equal(summarizeStatus([{ status: 'PASS' }, { status: 'BLOCKED_AUTH' }]), 'PARTIAL');
  assert.equal(summarizeStatus([{ status: 'PASS' }, { status: 'FAIL' }]), 'FAIL');
});

test('resolveRoleCases overrides only admin from the local real-pre credential source', () => {
  const roles = resolveRoleCases({
    admin: { username: 'admin', password: 'stale-admin' },
    biz_leader: { username: 'biz_leader', password: 'leader-current' },
    biz_staff: { username: 'biz_staff', password: 'staff-current' },
    channel_staff: { username: 'channel_staff', password: 'channel-current' }
  }, { adminCredential: 'admin-current' });
  assert.equal(roles.admin.password, 'admin-current');
  assert.equal(roles.biz_staff.password, 'staff-current');
  assert.equal(roles.channel_staff.password, 'channel-current');
});

test('normalizeCurrentUser accepts wrapped current-user fields without credentials', () => {
  const user = normalizeCurrentUser({
    userId: USER_ID, deptId: DEPT_ID, dataScope: 1,
    roleCodes: ['BIZ_STAFF'], username: 'biz_staff'
  });
  assert.deepEqual(user.roleCodes, ['biz_staff']);
  assert.equal(user.dataScope, 'PERSONAL');
  assert.equal(Object.hasOwn(user, 'token'), false);
});
```

- [ ] **Step 2: 运行测试并确认有效 RED**

```powershell
node --test runtime/qa/real-pre-performance-access-reconcile.test.cjs
```

预期：FAIL，原因是 `Cannot find module './real-pre-performance-access-reconcile.cjs'`。Node 不可用、语法错误或其他现有测试失败都不算有效 RED。

### Task 2: 实现最小只读范围核心与运行器

**Files:**
- Create: `runtime/qa/real-pre-performance-access-reconcile.cjs`
- Test: `runtime/qa/real-pre-performance-access-reconcile.test.cjs`

- [ ] **Step 1: 实现范围解析和 SQL 构造**

实现并导出以下稳定接口；角色判断顺序与 `PerformanceAccessScope.appendRoleScopeCondition` 保持一致：

```javascript
function resolvePerformanceScope(user) {
  const roleCodes = new Set((user.roleCodes || []).map((role) => String(role).trim().toLowerCase()));
  const dataScope = normalizeDataScope(user.dataScope, user.dataScopeName);
  const has = (...roles) => roles.some((role) => roleCodes.has(role));
  if (dataScope === 'ALL' || has('admin', 'ops_staff')) return { kind: 'ALL' };
  if (has('channel_staff') && !has('admin', 'channel_leader', 'ops_staff')) {
    return { kind: 'CHANNEL_STAFF', userId: sqlUuid(user.userId) };
  }
  if (has('biz_staff') && !has('admin', 'biz_leader', 'ops_staff')) {
    return { kind: 'BIZ_STAFF', userId: sqlUuid(user.userId) };
  }
  if (has('channel_leader') && !has('admin')) {
    return { kind: 'CHANNEL_LEADER', deptId: sqlUuid(user.deptId) };
  }
  if (has('biz_leader') && !has('admin')) {
    return { kind: 'BIZ_LEADER', deptId: sqlUuid(user.deptId) };
  }
  if (dataScope === 'DEPT') return { kind: 'DEPT', deptId: sqlUuid(user.deptId) };
  if (dataScope === 'PERSONAL') return { kind: 'PERSONAL', userId: sqlUuid(user.userId) };
  throw new Error(`Unsupported performance scope for ${user.username || 'unknown user'}`);
}

function scopePredicate(scope) {
  const user = scope.userId ? `'${scope.userId}'::uuid` : null;
  const dept = scope.deptId ? `'${scope.deptId}'::uuid` : null;
  switch (scope.kind) {
    case 'ALL': return 'TRUE';
    case 'CHANNEL_STAFF': return `pr.final_channel_user_id = ${user}`;
    case 'BIZ_STAFF': return `pr.final_recruiter_user_id = ${user}`;
    case 'CHANNEL_LEADER': return `pr.final_channel_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0)`;
    case 'BIZ_LEADER': return `pr.final_recruiter_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0)`;
    case 'PERSONAL': return `(pr.final_channel_user_id = ${user} OR pr.final_recruiter_user_id = ${user})`;
    case 'DEPT': return `(pr.final_channel_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0) OR pr.final_recruiter_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0))`;
    default: throw new Error(`Unsupported performance scope kind: ${scope.kind}`);
  }
}

function buildCountSql(scope) {
  return `SELECT COUNT(*) AS total FROM performance_records pr WHERE pr.is_valid = TRUE AND (${scopePredicate(scope)});`;
}

function buildOutOfScopeSql(scope) {
  if (scope.kind === 'ALL') return '';
  return `SELECT pr.order_id FROM performance_records pr WHERE pr.is_valid = TRUE AND NOT COALESCE((${scopePredicate(scope)}), FALSE) ORDER BY pr.calculated_at DESC NULLS LAST LIMIT 1;`;
}
```

`sqlUuid` 必须验证标准 UUID 并在缺少受限上下文时抛错；不得把非法值拼入 SQL。

- [ ] **Step 2: 实现独立逐行归属判断与结论分级**

```javascript
function isOwnershipInScope(row, scope, deptMemberIds = []) {
  const channelId = String(row.final_channel_user_id || '').toLowerCase();
  const recruiterId = String(row.final_recruiter_user_id || '').toLowerCase();
  const members = new Set(deptMemberIds.map((id) => String(id).toLowerCase()));
  if (scope.kind === 'ALL') return true;
  if (scope.kind === 'CHANNEL_STAFF') return channelId === scope.userId;
  if (scope.kind === 'BIZ_STAFF') return recruiterId === scope.userId;
  if (scope.kind === 'CHANNEL_LEADER') return members.has(channelId);
  if (scope.kind === 'BIZ_LEADER') return members.has(recruiterId);
  if (scope.kind === 'PERSONAL') return channelId === scope.userId || recruiterId === scope.userId;
  if (scope.kind === 'DEPT') return members.has(channelId) || members.has(recruiterId);
  return false;
}

function classifyRoleEvidence(evidence) {
  if ((evidence.errors || []).length) return 'FAIL';
  if (Number(evidence.apiTotal) !== Number(evidence.dbTotal)) return 'FAIL';
  if ((evidence.ownershipChecks || []).some((item) => !item.pass)) return 'FAIL';
  if ((evidence.negativeChecks || []).some((item) => item.required && item.status === 'FAIL')) return 'FAIL';
  if ((evidence.negativeChecks || []).some((item) => item.required && item.status === 'NO_SAMPLE')) {
    return 'PARTIAL_NO_NEGATIVE_SAMPLE';
  }
  if (Number(evidence.dbTotal) === 0) return 'PARTIAL_NO_POSITIVE_SAMPLE';
  return 'PASS';
}

function summarizeStatus(roles) {
  if (roles.some((role) => role.status === 'FAIL')) return 'FAIL';
  if (roles.some((role) => String(role.status).startsWith('PARTIAL_'))) return 'PARTIAL';
  return 'PASS';
}
```

- [ ] **Step 3: 实现只读运行链路**

运行器严格按以下顺序执行：

```javascript
async function runRealPrePerformanceAccessReconcile(options = {}) {
  const root = options.root || ROOT;
  const env = options.env || process.env;
  const urls = applyRealPreEnv(env);
  const backendUrl = stripTrailingSlash(options.backendUrl || urls.backendUrl);
  const evidenceDir = options.evidenceDir || createEvidenceDir(root, SCRIPT_NAME);
  const roleCases = options.roles || loadRoleCases(root);
  const health = await requestJson(options.fetchImpl || globalThis.fetch, apiUrl(backendUrl, '/system/health'));
  const adminToken = await loginRole(options.fetchImpl || globalThis.fetch, backendUrl, roleCases.admin);
  const systemEnv = normalizeSystemEnv((await requestJson(
    options.fetchImpl || globalThis.fetch,
    apiUrl(backendUrl, '/system/env'),
    { headers: bearer(adminToken) }
  )).body);
  if (!health.ok || health.body?.status !== 'UP' || !isPassingRealPreEnv(systemEnv)) {
    throw new Error(`Refusing performance access reconcile outside guarded real-pre runtime`);
  }

  const identities = {};
  for (const [roleName, account] of Object.entries(roleCases)) {
    const token = await loginRole(options.fetchImpl || globalThis.fetch, backendUrl, account);
    identities[roleName] = {
      token,
      user: normalizeCurrentUser(await fetchData(options.fetchImpl || globalThis.fetch, backendUrl, token, '/users/current'))
    };
  }

  const roles = [];
  for (const [roleName, identity] of Object.entries(identities)) {
    roles.push(await collectRoleEvidence(roleName, identity, identities, {
      backendUrl,
      fetchImpl: options.fetchImpl || globalThis.fetch,
      dbContainer: options.dbContainer || resolveRealPreDbContainer(env),
      dbUser: options.dbUser || env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER,
      dbName: options.dbName || env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME,
      spawnSyncImpl: options.spawnSyncImpl || spawnSync
    }));
  }

  const summary = redactSecretLikeKeys({
    evidenceType: SCRIPT_NAME,
    generatedAt: new Date().toISOString(),
    backendUrl,
    systemEnv,
    roles,
    status: summarizeStatus(roles),
    policy: 'read-only; no export, seed, backfill or recalculation endpoints'
  });
  summary.ok = summary.status !== 'FAIL';
  writeJson(path.join(evidenceDir, 'summary.json'), summary);
  writeText(path.join(evidenceDir, 'report.md'), buildReport(summary));
  return { ...summary, evidenceDir };
}
```

`collectRoleEvidence` 必须完成以下精确检查：

1. GET `/performance?page=1&pageSize=100&sortBy=calculatedAt&sortOrder=desc`，读取 `total/items`。
2. 执行 `buildCountSql(scope)` 得到 SQL total。
3. 用 API 返回的 `orderId` 执行只读 ownership SQL，读取 `final_channel_user_id/final_recruiter_user_id`，并用 `isOwnershipInScope` 独立判断每行。
4. 对 staff 使用另一个真实账号 ID 请求 `GET /performance?channelId=...` 或 `GET /performance?recruiterId=...`，HTTP/body code 必须为 403。
5. 对 staff 使用 `buildOutOfScopeSql(scope)` 选择一条范围外记录，再 GET `/performance/{orderId}`，必须为 403；无范围外样本记 `NO_SAMPLE`。
6. admin/leader 不执行负向 staff 检查，也不调用 export、seed、backfill 或 recalculate。
7. 单角色异常写入 `errors` 后继续其他角色，最终由 `classifyRoleEvidence` 判定。

- [ ] **Step 4: 导出测试接口并提供 CLI 退出语义**

```javascript
module.exports = {
  assertSuccessfulApiResponse,
  buildCountSql,
  buildOutOfScopeSql,
  classifyRoleEvidence,
  isOwnershipInScope,
  isPassingRealPreEnv,
  normalizeCurrentUser,
  resolveRoleCases,
  resolvePerformanceScope,
  runRealPrePerformanceAccessReconcile,
  summarizeStatus
};

if (require.main === module) {
  const args = parseArgs(process.argv.slice(2));
  runRealPrePerformanceAccessReconcile({
    evidenceDir: args['evidence-dir'] ? path.resolve(args['evidence-dir']) : undefined
  }).then((summary) => {
    console.log(`real-pre performance access reconcile status=${summary.status} output=${summary.evidenceDir}`);
    if (summary.status === 'FAIL') process.exitCode = 1;
  }).catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exitCode = 1;
  });
}
```

`PARTIAL` 返回进程码 0，因为它表示探针完成且当前样本不足；Y-17 状态由报告和矩阵继续保持 `PARTIAL`。只有环境拒绝、API/SQL 漂移、权限泄漏、账号失败等 `FAIL` 返回非零。

- [ ] **Step 5: 运行测试并确认 GREEN**

```powershell
node --test runtime/qa/real-pre-performance-access-reconcile.test.cjs
```

预期：11 tests PASS，零失败、零跳过。

- [ ] **Step 6: 检查只读边界和最小差异**

```powershell
rg -n "POST|PUT|PATCH|DELETE|/export|seed|backfill|recalculate" runtime/qa/real-pre-performance-access-reconcile.cjs
git diff --check -- runtime/qa/real-pre-performance-access-reconcile.cjs runtime/qa/real-pre-performance-access-reconcile.test.cjs
```

预期：`POST` 只用于 `/auth/login` 鉴权；所有业绩业务请求均为 GET。出现的 `deleted = 0` 是查询条件，报告 policy 文本可提及禁止接口。任何业务写方法或 SQL 写语句都必须删除。

- [ ] **Step 7: 提交实现和测试**

```powershell
git add -- runtime/qa/real-pre-performance-access-reconcile.cjs runtime/qa/real-pre-performance-access-reconcile.test.cjs
git diff --cached --check
git commit -m "test(ddd): add real-pre performance access reconcile"
```

### Task 3: 增加稳定入口并执行真实只读对账

**Files:**
- Modify: `package.json:28`
- Verify: `runtime/qa/real-pre-performance-access-reconcile.cjs`
- Verify: `runtime/qa/out/real-pre-performance-access-reconcile-*/summary.json`（忽略的运行态输出，不提交）

- [ ] **Step 1: 增加 package 命令**

在 dashboard reconcile 命令后加入：

```json
"e2e:real-pre:performance-access-reconcile": "node runtime/qa/real-pre-performance-access-reconcile.cjs"
```

保持 JSON 逗号合法，不改其他脚本。

- [ ] **Step 2: 再次运行单元测试**

```powershell
node --test runtime/qa/real-pre-performance-access-reconcile.test.cjs
```

预期：全部 PASS。

- [ ] **Step 3: 在当前本地 real-pre 执行只读探针**

```powershell
npm run e2e:real-pre:performance-access-reconcile -- --evidence-dir runtime/qa/out/ddd-performance-access-real-pre-reconcile-current
```

预期：环境守卫通过；admin 凭证按项目既有规则优先读取本地 `.env.real-pre`，admin、`biz_leader`、`biz_staff` 的 API total 与 SQL total 相等且当前页归属无泄漏；已认证 staff 的越权筛选和详情返回 403。若 `channel_staff` 仍因配置凭证 401 无法登录，记 `BLOCKED_AUTH`；若可登录但没有正向渠道归属样本，记 `PARTIAL_NO_POSITIVE_SAMPLE`。总体 `PARTIAL` 且进程码 0。若实际数据变化，以本次真实输出为准，不硬改预期。

- [ ] **Step 4: 检查 evidence 脱敏和只读结果**

执行：

```powershell
Select-String -Path 'runtime/qa/out/ddd-performance-access-real-pre-reconcile-current/summary.json','runtime/qa/out/ddd-performance-access-real-pre-reconcile-current/report.md' -Pattern 'token|password|authorization|secret' -CaseSensitive:$false
Get-Content -Raw 'runtime/qa/out/ddd-performance-access-real-pre-reconcile-current/report.md'
```

预期：没有凭证值；若出现字段名，只能是 `[redacted]`。报告包含各角色 `apiTotal/dbTotal`、逐行归属、负向检查、样本分级和总体 `PARTIAL/PASS/FAIL`。

- [ ] **Step 5: 提交 package 入口**

```powershell
git add -- package.json
git diff --cached --check
git commit -m "chore(qa): expose performance access reconcile command"
```

### Task 4: 用真实结果更新 Y-17 阶段性证据

**Files:**
- Modify: `docs/ddd-completion-evidence-matrix.md:600`
- Modify: `harness/rules/state/snapshots/DOMAIN_STATUS.md:112-121`

- [ ] **Step 1: 更新 Y-17 矩阵行**

保留 `PARTIAL`，在既有后端证据后追加本轮真实事实：探针命令、各角色实际结论、API/SQL total 一致性、逐行归属和 staff 越权结果。剩余风险必须明确写出：渠道最终归属样本为 0，未完成渠道正向可见性和前端菜单/导出按钮页面 E2E，因此不能标 `DONE`。

- [ ] **Step 2: 更新领域状态快照**

把“仍缺真实账号 API/SQL/E2E”改为精确状态：真实招商账号 API/SQL 已核对；渠道负向已核对但正向样本缺失；页面 E2E 未完成。下一步指定为“获取合法渠道归属业务样本后补渠道正向 API/SQL，并单独完成页面菜单/导出按钮 E2E”，不得建议直接改库造数据。

- [ ] **Step 3: 校验文档没有夸大结论**

```powershell
Select-String -Path 'docs/ddd-completion-evidence-matrix.md','harness/rules/state/snapshots/DOMAIN_STATUS.md' -Pattern 'Y-17' -Context 1,4
git diff --check -- docs/ddd-completion-evidence-matrix.md harness/rules/state/snapshots/DOMAIN_STATUS.md
```

预期：Y-17 仍为 `PARTIAL`；不出现渠道正向 `PASS`、Y-17 `DONE` 或 real-pre 业务数据已补齐等无证据结论。

### Task 5: 执行项目统一 Harness 并推送证据

**Files:**
- Verify: `runtime/qa/real-pre-performance-access-reconcile.cjs`
- Verify: `runtime/qa/real-pre-performance-access-reconcile.test.cjs`
- Verify: `package.json`
- Verify: `docs/ddd-completion-evidence-matrix.md`
- Verify: `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- Create/Update: `harness/reports/current/latest-ddd-performance-access-real-pre-reconcile.md`

- [ ] **Step 1: 组合后端权限测试、Node 单测和真实探针为业务命令**

```powershell
$business = 'mvn -q -f backend/pom.xml -DforkCount=0 "-Dtest=PerformanceControllerTest,PerformanceAccessScopeTest,DddPerformanceAccessScopeClosureContractTest" test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node --test runtime/qa/real-pre-performance-access-reconcile.test.cjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; npm run e2e:real-pre:performance-access-reconcile'
```

- [ ] **Step 2: 通过唯一入口运行 backend Scope Harness**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope backend -ReportKey ddd-performance-access-real-pre-reconcile -OwnedFiles 'runtime/qa/real-pre-performance-access-reconcile.cjs;runtime/qa/real-pre-performance-access-reconcile.test.cjs;package.json;docs/ddd-completion-evidence-matrix.md;harness/rules/state/snapshots/DOMAIN_STATUS.md;docs/superpowers/plans/2026-07-16-performance-access-scope-real-pre-reconcile.md' -BusinessCommand $business -ContentMaintenance off -Message "test(ddd): verify real-pre performance access scope"
```

预期：后端 package 通过、backend 容器完成重启、health 为 `UP`、三个后端权限测试通过、Node 单测通过、真实探针完成且没有 `FAIL`、安全检查通过、稳定 evidence 生成。探针总体 `PARTIAL` 是当前数据样本结论，不应被改写为 Y-17 `PASS`；若发生 API/SQL 漂移或权限泄漏，Harness 必须失败。

- [ ] **Step 3: 独立复核 Harness、容器与分层门禁**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1 -BaselineRef HEAD
docker ps --format "{{.Names}}`t{{.Status}}"
Get-Content -Raw 'harness/reports/current/latest-ddd-performance-access-real-pre-reconcile.md'
git status --short --branch
```

预期：TASK_GATE 没有新增/恶化问题；backend/frontend/postgres/redis 维持健康；报告包含 retro 和剩余风险；仓库级历史健康债务如仍存在，保留为 `PARTIAL`，不得改写。

- [ ] **Step 4: 复核提交与上游同步**

`agent-do.ps1` 会只提交 OwnedFiles 和新 evidence 并推送当前分支。执行：

```powershell
git log -6 --oneline
git status --short --branch
git rev-list --left-right --count HEAD...@{upstream}
```

预期：存在本轮实现、package 入口和 Harness/evidence 提交；工作树干净；当前分支与上游为 `0 0`。不执行远端 real-pre 部署。

## 自检结果

- 设计要求均有任务映射：Task 1/2 固化正确权限口径和安全分级，Task 3 生成真实只读证据，Task 4 如实更新 Y-17，Task 5 覆盖构建、重启、健康、业务验证、evidence、retro 和推送。
- 计划不调用 export、seed、backfill、recalculate 或任何业务写接口；SQL 仅为 `SELECT`。
- 类型和路径与当前源码一致：`PerformancePageResponse.total/items`、`PerformanceListItemDTO.finalChannelId/finalRecruiterId`、`/api/users/current`、`/api/performance`、`performance_records.final_channel_user_id/final_recruiter_user_id`。
- 渠道 0 样本得到 `PARTIAL_NO_POSITIVE_SAMPLE`，账号认证阻塞得到 `BLOCKED_AUTH`；两者都不会推进 Y-17 到 `DONE`。
- 未引入生产代码、schema、业务规则、前端或远端部署变更。
