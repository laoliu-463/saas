const assert = require('node:assert/strict');
const {
  normalizeEnv,
  normalizePathname,
  isPathUnder,
  makeQaRunId,
  containsQaRunId,
  isProtectedAccount,
  blockingApiErrors,
  isIgnorableConsole,
  prepareTestSeed,
  prepareTestOrders
} = require('./business-crud-flow.cjs');

function testNormalizeEnvTest() {
  const env = normalizeEnv({
    data: {
      environmentLabel: 'TEST',
      activeProfiles: ['test'],
      appTestEnabled: true,
      douyinTestEnabled: true,
      database: 'saas_test'
    }
  });
  assert.equal(env.isTest, true);
  assert.equal(env.isRealPre, false);
}

function testNormalizeEnvRejectsRealPre() {
  const env = normalizeEnv({
    data: {
      environmentLabel: 'REAL-PRE',
      activeProfiles: ['real-pre'],
      appTestEnabled: false
    }
  });
  assert.equal(env.isRealPre, true);
  assert.equal(env.isTest, false);
}

function testPathHelpers() {
  assert.equal(normalizePathname('/product/library/'), '/product/library');
  assert.equal(isPathUnder('/product/manage/list', '/product/manage'), true);
}

function testQaRunId() {
  const id = makeQaRunId();
  assert.match(id, /^QA\d{8}_\d{6}_[a-z0-9]{4}$/);
  assert.equal(containsQaRunId(`QA达人_${id}`, id), true);
}

function testProtectedAccounts() {
  assert.equal(isProtectedAccount('admin'), true);
  assert.equal(isProtectedAccount('qa_user_QA20260101_120000_abcd'), false);
}

function testBlockingApiErrors() {
  const ctx = {
    allowExpected401: false,
    apiErrors: [{ status: 500, url: '/api/x' }, { status: 403, url: '/api/y' }]
  };
  assert.equal(blockingApiErrors(ctx).length, 1);
}

function testIgnorableConsole() {
  assert.equal(isIgnorableConsole('ResizeObserver loop limit exceeded'), true);
  assert.equal(isIgnorableConsole('TypeError: boom'), false);
}

async function testPrepareTestOrders() {
  const calls = [];
  const responses = {
    '/test/orders/generate-attributed': { orderId: 'MOCK_GEN_ATTR_1' },
    '/test/orders/generate-no-pick-source': { orderId: 'MOCK_GEN_NOPICK_1' },
    '/test/orders/generate-missing-mapping': { orderId: 'MOCK_GEN_NOMAP_1' }
  };
  const ctx = {};
  const result = await prepareTestOrders(ctx, 'admin-token', async (_ctx, method, route, options) => {
    calls.push({ method, route, token: options.token, allowStatuses: options.allowStatuses });
    return { ok: true, status: 200, body: { code: 200, data: responses[route] } };
  });

  assert.deepEqual(calls.map((call) => `${call.method} ${call.route}`), [
    'POST /test/orders/generate-attributed',
    'POST /test/orders/generate-no-pick-source',
    'POST /test/orders/generate-missing-mapping'
  ]);
  assert.equal(calls.every((call) => call.token === 'admin-token'), true);
  assert.equal(result.attributed.orderId, 'MOCK_GEN_ATTR_1');
  assert.equal(result.noPickSource.orderId, 'MOCK_GEN_NOPICK_1');
  assert.equal(result.missingMapping.orderId, 'MOCK_GEN_NOMAP_1');
  assert.equal(ctx.testOrders.attributed.orderId, 'MOCK_GEN_ATTR_1');
}

async function testPrepareTestSeed() {
  const ctx = {};
  const result = await prepareTestSeed(ctx, 'admin-token', async (_ctx, method, route, options) => {
    assert.equal(method, 'POST');
    assert.equal(route, '/test/seed');
    assert.equal(options.token, 'admin-token');
    return {
      ok: true,
      status: 200,
      body: {
        code: 200,
        data: {
          sampleRequestNo: 'TEST-SAMPLE-001',
          shippingSampleId: '11111111-1111-1111-1111-111111111111'
        }
      }
    };
  });
  assert.equal(result.sampleRequestNo, 'TEST-SAMPLE-001');
  assert.equal(result.shippingSampleId, '11111111-1111-1111-1111-111111111111');
  assert.equal(ctx.testSeed.shippingSampleId, '11111111-1111-1111-1111-111111111111');
}

const tests = [
  testNormalizeEnvTest,
  testNormalizeEnvRejectsRealPre,
  testPathHelpers,
  testQaRunId,
  testProtectedAccounts,
  testBlockingApiErrors,
  testIgnorableConsole,
  testPrepareTestSeed,
  testPrepareTestOrders
];

let failed = 0;
(async () => {
  for (const fn of tests) {
    try {
      const result = fn();
      if (result && typeof result.then === 'function') {
        await result;
      }
      console.log(`PASS ${fn.name}`);
    } catch (error) {
      failed += 1;
      console.error(`FAIL ${fn.name}:`, error.message);
    }
  }

  if (failed > 0) {
    process.exitCode = 1;
  }
})();
