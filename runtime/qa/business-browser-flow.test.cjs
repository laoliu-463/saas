const test = require('node:test');
const assert = require('node:assert/strict');
const {
  normalizeEnv,
  normalizePathname,
  isPathUnder,
  blockingApiErrors
} = require('./business-browser-flow.cjs');

test('normalizeEnv accepts TEST by label', () => {
  const env = normalizeEnv({ data: { environmentLabel: 'TEST', appTestEnabled: false, douyinTestEnabled: false } });
  assert.equal(env.isTest, true);
});

test('normalizeEnv accepts TEST by profile', () => {
  const env = normalizeEnv({ data: { activeProfiles: ['test'] } });
  assert.equal(env.isTest, true);
});

test('normalizeEnv rejects real-pre without mock switches', () => {
  const env = normalizeEnv({ data: { environmentLabel: 'REAL-PRE', activeProfiles: ['real-pre'], appTestEnabled: false } });
  assert.equal(env.isTest, false);
});

test('path helpers normalize and match prefixes', () => {
  assert.equal(normalizePathname('/system/users?x=1'), '/system/users');
  assert.equal(isPathUnder('/system/users', '/system'), true);
  assert.equal(isPathUnder('/data', '/system'), false);
});

test('blockingApiErrors treats 500 and 401 as blockers', () => {
  const ctx = {
    apiErrors: [
      { status: 403 },
      { status: 401 },
      { status: 500 }
    ]
  };
  assert.deepEqual(blockingApiErrors(ctx).map((item) => item.status), [401, 500]);
});
