const test = require('node:test');
const assert = require('node:assert/strict');

const {
  loadRoleCases,
  evaluateAccessRule,
  evaluateRejectRule,
  evaluateTextRules
} = require('./page-role-smoke.cjs');

test('loadRoleCases returns configured admin and biz_leader cases', () => {
  const roleCases = loadRoleCases();

  assert.ok(roleCases.admin, 'expected admin role case');
  assert.ok(roleCases.biz_leader, 'expected biz_leader role case');
  assert.equal(roleCases.admin.username, 'admin');
  assert.equal(roleCases.biz_leader.username, 'biz_leader');
  assert.ok(Array.isArray(roleCases.admin.mustAccess));
  assert.ok(Array.isArray(roleCases.biz_leader.mustReject));
});

test('evaluateAccessRule accepts allowed redirect target', () => {
  const result = evaluateAccessRule(
    { path: '/system', allowedFinalPaths: ['/system/users'] },
    { route: '/system', finalPath: '/system/users' }
  );

  assert.equal(result.pass, true);
});

test('evaluateRejectRule rejects sensitive page and requires fallback path', () => {
  const passResult = evaluateRejectRule(
    { path: '/system', allowedFinalPaths: ['/dashboard'] },
    { route: '/system', finalPath: '/dashboard' }
  );
  const failResult = evaluateRejectRule(
    { path: '/system', allowedFinalPaths: ['/dashboard'] },
    { route: '/system', finalPath: '/system/users' }
  );

  assert.equal(passResult.pass, true);
  assert.equal(failResult.pass, false);
});

test('evaluateTextRules checks sidebar and route body text together', () => {
  const seeRules = [
    { scope: '@sidebar', texts: ['商品管理'] },
    { path: '/system', texts: ['系统管理', '用户管理'] }
  ];
  const notSeeRules = [
    { scope: '@sidebar', texts: ['达人 CRM'] },
    { path: '/system', texts: ['角色越权入口'] }
  ];
  const routeResults = [
    { route: '/system', bodyText: '系统管理 用户管理 操作日志' }
  ];

  const passResult = evaluateTextRules({
    seeRules,
    notSeeRules,
    sidebarText: '数据看板 商品库 商品管理',
    routeResults
  });

  const failResult = evaluateTextRules({
    seeRules: [{ path: '/dashboard', texts: ['不存在的文案'] }],
    notSeeRules: [],
    sidebarText: '数据看板 商品库 商品管理',
    routeResults
  });

  assert.equal(passResult.pass, true);
  assert.equal(failResult.pass, false);
});
