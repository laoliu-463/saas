const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildScopeClause,
  buildSummarySql,
  compareMetric,
  isPassingRealPreEnv,
  normalizeCurrentUser
} = require('./real-pre-dashboard-reconcile.cjs');

test('isPassingRealPreEnv only accepts real-pre with mock switches disabled', () => {
  assert.equal(isPassingRealPreEnv({
    environmentLabel: 'REAL-PRE',
    activeProfiles: ['real-pre'],
    appTestEnabled: false,
    douyinTestEnabled: false,
    database: 'saas_real_pre'
  }), true);

  assert.equal(isPassingRealPreEnv({
    environmentLabel: 'TEST',
    activeProfiles: ['test'],
    appTestEnabled: true,
    douyinTestEnabled: false,
    database: 'colonel_saas_test'
  }), false);
});

test('normalizeCurrentUser maps numeric data scope to domain labels', () => {
  const user = normalizeCurrentUser({
    userId: '00000000-0000-0000-0000-000000000001',
    deptId: '00000000-0000-0000-0000-000000000002',
    dataScope: 2
  });

  assert.equal(user.scope, 'group');
  assert.equal(user.userId, '00000000-0000-0000-0000-000000000001');
  assert.equal(user.deptId, '00000000-0000-0000-0000-000000000002');
});

test('buildScopeClause creates safe scoped SQL filters', () => {
  assert.deepEqual(
    buildScopeClause({
      scope: 'self',
      userId: '00000000-0000-0000-0000-000000000001'
    }),
    { sql: " AND co.user_id = '00000000-0000-0000-0000-000000000001'::uuid", status: 'SCOPED_SELF' }
  );

  assert.deepEqual(
    buildScopeClause({
      scope: 'group',
      deptId: '00000000-0000-0000-0000-000000000002'
    }),
    { sql: " AND co.dept_id = '00000000-0000-0000-0000-000000000002'::uuid", status: 'SCOPED_GROUP' }
  );

  assert.deepEqual(buildScopeClause({ scope: 'all' }), { sql: '', status: 'SCOPED_ALL' });
  assert.throws(() => buildScopeClause({ scope: 'self', userId: "bad';drop" }), /Invalid UUID/);
});

test('buildSummarySql mirrors dashboard performance-record summary source', () => {
  const sql = buildSummarySql({
    scope: 'group',
    deptId: '00000000-0000-0000-0000-000000000002'
  });

  assert.match(sql, /FROM performance_records pr/);
  assert.match(sql, /JOIN colonelsettlement_order co/);
  assert.match(sql, /pr\.is_valid = TRUE/);
  assert.match(sql, /co\.dept_id = '00000000-0000-0000-0000-000000000002'::uuid/);
});

test('compareMetric fails when API and SQL values drift beyond tolerance', () => {
  assert.equal(compareMetric('orders', 10, 10).pass, true);
  assert.equal(compareMetric('money', 10.001, 10.0, 0.01).pass, true);
  assert.equal(compareMetric('drift', 10.02, 10.0, 0.01).pass, false);
});
