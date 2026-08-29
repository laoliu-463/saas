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
    () => assertSuccessfulApiResponse({ ok: true, status: 200, body: { code: 500, message: 'server error' } }, '/performance'),
    /\/performance failed: HTTP 200, business code 500/
  );
  assert.doesNotThrow(
    () => assertSuccessfulApiResponse({ ok: true, status: 200, body: { code: 200, data: {} } }, '/performance')
  );
  assert.doesNotThrow(
    () => assertSuccessfulApiResponse({ ok: true, status: 200, body: { status: 'UP' } }, '/system/health')
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
