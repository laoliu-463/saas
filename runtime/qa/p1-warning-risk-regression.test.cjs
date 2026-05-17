const test = require('node:test');
const assert = require('node:assert/strict');

const {
  normalizeEnv,
  summarizeCases
} = require('./p1-warning-risk-regression.cjs');

test('normalizeEnv treats explicit test flags as TEST/mock even without label', () => {
  const env = normalizeEnv({
    data: {
      activeProfiles: ['test'],
      appTestEnabled: true,
      douyinTestEnabled: true,
      database: 'saas_test'
    }
  });

  assert.equal(env.isTest, true);
  assert.equal(env.isRealPre, false);
  assert.deepEqual(env.activeProfiles, ['test']);
  assert.equal(env.database, 'saas_test');
});

test('normalizeEnv distinguishes real-pre profile from test flags', () => {
  const env = normalizeEnv({
    data: {
      activeProfiles: ['real-pre'],
      environmentLabel: 'REAL-PRE',
      appTestEnabled: false,
      douyinTestEnabled: false
    }
  });

  assert.equal(env.isRealPre, true);
  assert.equal(env.isTest, false);
});

test('summarizeCases returns failures with ids and overall pass flag', () => {
  const summary = summarizeCases([
    { id: 'PROMO-001', name: 'ok', pass: true },
    { id: 'ATTR-001', name: 'bad', pass: false, failureReason: 'expected UNATTRIBUTED' }
  ]);

  assert.equal(summary.total, 2);
  assert.equal(summary.pass, 1);
  assert.equal(summary.fail, 1);
  assert.equal(summary.overallPass, false);
  assert.deepEqual(summary.failures, [
    { id: 'ATTR-001', name: 'bad', failureReason: 'expected UNATTRIBUTED' }
  ]);
});
