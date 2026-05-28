const test = require('node:test');
const assert = require('node:assert/strict');

const {
  ROLE_BUSINESS_CASES,
  normalizeRoleAlias,
  evaluateOperation,
  isIgnorableConsoleError,
  summarizeRoleResults
} = require('./page-role-business-smoke.cjs');

test('role business matrix covers all required TEST/mock roles', () => {
  const roles = Object.keys(ROLE_BUSINESS_CASES).sort();

  assert.deepEqual(roles, [
    'admin',
    'biz_leader',
    'biz_staff',
    'channel_leader',
    'channel_staff',
    'operator'
  ]);
  for (const role of roles) {
    assert.ok(ROLE_BUSINESS_CASES[role].allowedOperation, `${role} missing allowed operation`);
    assert.ok(ROLE_BUSINESS_CASES[role].forbiddenOperation, `${role} missing forbidden operation`);
  }
});

test('normalizeRoleAlias maps operator to the existing ops_staff account', () => {
  assert.equal(normalizeRoleAlias('operator'), 'ops_staff');
  assert.equal(normalizeRoleAlias('ops_staff'), 'ops_staff');
  assert.equal(normalizeRoleAlias('biz_staff'), 'biz_staff');
});

test('evaluateOperation distinguishes allowed and forbidden expectations', () => {
  const allowed = evaluateOperation(
    { expect: { ok: true } },
    { status: 200, ok: true, body: { code: 200, data: { id: 1 } } }
  );
  const forbidden = evaluateOperation(
    { expect: { forbidden: true } },
    { status: 403, ok: false, body: { code: 403, msg: '无权限访问该接口' } }
  );
  const failed = evaluateOperation(
    { expect: { forbidden: true } },
    { status: 200, ok: true, body: { code: 200, data: {} } }
  );

  assert.equal(allowed.pass, true);
  assert.equal(forbidden.pass, true);
  assert.equal(failed.pass, false);
});

test('isIgnorableConsoleError filters local transient resource noise only', () => {
  assert.equal(isIgnorableConsoleError('Failed to load resource: net::ERR_CONNECTION_CLOSED'), true);
  assert.equal(isIgnorableConsoleError('ResizeObserver loop completed with undelivered notifications.'), true);
  assert.equal(isIgnorableConsoleError('TypeError: Cannot read properties of undefined'), false);
});

test('summarizeRoleResults requires menu, allowed operation, and forbidden operation to pass per role', () => {
  const summary = summarizeRoleResults([
    {
      role: 'admin',
      menu: { pass: true },
      allowedOperation: { pass: true },
      forbiddenOperation: { pass: true }
    },
    {
      role: 'biz_staff',
      menu: { pass: true },
      allowedOperation: { pass: false, reason: 'HTTP 500' },
      forbiddenOperation: { pass: true }
    }
  ]);

  assert.equal(summary.overallPass, false);
  assert.equal(summary.roles.admin.pass, true);
  assert.equal(summary.roles.biz_staff.pass, false);
  assert.equal(summary.failures[0].role, 'biz_staff');
});
