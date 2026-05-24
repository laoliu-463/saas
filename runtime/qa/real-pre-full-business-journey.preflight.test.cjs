const test = require('node:test');
const assert = require('node:assert/strict');

const {
  runJourneyPreflight,
  summarizePreflightFailure
} = require('./real-pre-full-business-journey.preflight.cjs');

test('runJourneyPreflight passes when frontend, backend, real-pre env, accounts, and gitignore are ready', async () => {
  const fetchImpl = createFetchStub({
    'GET http://localhost:3001/login': htmlResponse(),
    'GET http://localhost:8081/api/actuator/health': jsonResponse({ status: 'UP' }),
    'GET http://localhost:8081/api/system/env': jsonResponse({
      code: 200,
      data: {
        environmentLabel: 'REAL-PRE',
        activeProfiles: ['real-pre'],
        appTestEnabled: false,
        douyinTestEnabled: false,
        database: 'saas_real_pre'
      }
    }),
    'POST http://localhost:8081/api/auth/login': jsonResponse({
      code: 200,
      data: { token: 'token-value' }
    })
  });

  const report = await runJourneyPreflight({
    frontendUrl: 'http://localhost:3001',
    backendUrl: 'http://localhost:8081',
    root: 'D:/Projects/SAAS',
    fetchImpl,
    spawnSyncImpl: () => ({ status: 0 }),
    accounts: ['admin', 'biz_leader']
  });

  assert.equal(report.ok, true);
  assert.deepEqual(report.checks.map((check) => check.status), ['PASS', 'PASS', 'PASS', 'PASS', 'PASS']);
});

test('runJourneyPreflight fails before Playwright when frontend login is unreachable', async () => {
  const fetchImpl = createFetchStub({
    'GET http://localhost:3001/login': Promise.reject(new Error('connect ECONNREFUSED')),
    'GET http://localhost:8081/api/actuator/health': jsonResponse({ status: 'UP' }),
    'GET http://localhost:8081/api/system/env': jsonResponse({
      code: 200,
      data: {
        environmentLabel: 'REAL-PRE',
        activeProfiles: ['real-pre'],
        appTestEnabled: false,
        douyinTestEnabled: false
      }
    }),
    'POST http://localhost:8081/api/auth/login': jsonResponse({
      code: 200,
      data: { token: 'token-value' }
    })
  });

  const report = await runJourneyPreflight({
    frontendUrl: 'http://localhost:3001',
    backendUrl: 'http://localhost:8081',
    root: 'D:/Projects/SAAS',
    fetchImpl,
    spawnSyncImpl: () => ({ status: 0 }),
    accounts: ['admin']
  });

  assert.equal(report.ok, false);
  assert.equal(report.checks[0].name, 'frontend 3001');
  assert.equal(report.checks[0].status, 'FAIL');
  assert.match(summarizePreflightFailure(report), /frontend 3001/);
});

test('runJourneyPreflight rejects non real-pre backend env and unignored real-pre env file', async () => {
  const fetchImpl = createFetchStub({
    'GET http://localhost:3001/login': htmlResponse(),
    'GET http://localhost:8081/api/actuator/health': jsonResponse({ status: 'UP' }),
    'GET http://localhost:8081/api/system/env': jsonResponse({
      code: 200,
      data: {
        environmentLabel: 'TEST',
        activeProfiles: ['test'],
        appTestEnabled: true,
        douyinTestEnabled: true
      }
    }),
    'POST http://localhost:8081/api/auth/login': jsonResponse({
      code: 200,
      data: { token: 'token-value' }
    })
  });

  const report = await runJourneyPreflight({
    frontendUrl: 'http://localhost:3001',
    backendUrl: 'http://localhost:8081',
    root: 'D:/Projects/SAAS',
    fetchImpl,
    spawnSyncImpl: () => ({ status: 1 }),
    accounts: ['admin']
  });

  assert.equal(report.ok, false);
  assert.equal(report.checks.find((check) => check.name === 'real-pre env guard').status, 'FAIL');
  assert.equal(report.checks.find((check) => check.name === '.env.real-pre gitignore').status, 'FAIL');
});

function createFetchStub(routes) {
  return async (url, options = {}) => {
    const method = String(options.method || 'GET').toUpperCase();
    const key = `${method} ${url}`;
    if (!Object.prototype.hasOwnProperty.call(routes, key)) {
      throw new Error(`unexpected request ${key}`);
    }
    return routes[key];
  };
}

function htmlResponse() {
  return {
    ok: true,
    status: 200,
    text: async () => '<div id="app"></div>',
    json: async () => ({})
  };
}

function jsonResponse(body) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify(body),
    json: async () => body
  };
}
