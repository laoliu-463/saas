const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  runRealPrePreflight,
  summarizeStatus
} = require('./real-pre-preflight.cjs');

test('summarizeStatus classifies fail, blocked, pending, and pass distinctly', () => {
  assert.equal(summarizeStatus([{ status: 'PASS' }]), 'PASS');
  assert.equal(summarizeStatus([{ status: 'PASS' }, { status: 'PENDING_PICK_SOURCE' }]), 'PENDING');
  assert.equal(summarizeStatus([{ status: 'BLOCKED_AUTH' }]), 'BLOCKED');
  assert.equal(summarizeStatus([{ status: 'FAIL' }, { status: 'BLOCKED_AUTH' }]), 'FAIL');
});

test('runRealPrePreflight passes with real-pre env, token, schema, mapping, and cleanup plan', async () => {
  const evidenceDir = fs.mkdtempSync(path.join(os.tmpdir(), 'real-pre-preflight-'));
  const report = await runRealPrePreflight({
    evidenceDir,
    fetchImpl: createFetchStub({
      'GET http://localhost:3001/login': htmlResponse(),
      'GET http://localhost:8081/api/system/health': jsonResponse({ status: 'UP' }),
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
        data: { token: 'jwt-value' }
      }),
      'GET http://localhost:8081/api/douyin/tokens': jsonResponse({
        code: 200,
        data: {
          appId: 'app',
          hasAccessToken: true,
          hasRefreshToken: true,
          reauthorizeRequired: false
        }
      })
    }),
    spawnSyncImpl: createSpawnStub({
      schemaRows: [
        'colonel_partner.create_time=true',
        'colonel_partner.update_time=true',
        'domain_event_consume_log=true',
        'domain_event_outbox=true',
        'product_operation_state.pinned_at=true',
        'product_operation_state.pinned_by=true',
        'product_operation_state.pinned_until=true'
      ],
      mappingCount: '2',
      cleanupJson: JSON.stringify({ mode: 'PlanOnly', protectedTables: ['colonelsettlement_order'] })
    })
  });

  assert.equal(report.status, 'PASS');
  assert.equal(report.canRunBusinessFlows, true);
  assert.equal(fs.existsSync(path.join(evidenceDir, 'summary.json')), true);
});

test('runRealPrePreflight retries admin login while backend settles', async () => {
  const evidenceDir = fs.mkdtempSync(path.join(os.tmpdir(), 'real-pre-preflight-'));
  let loginAttempts = 0;
  const routes = {
    'GET http://localhost:3001/login': htmlResponse(),
    'GET http://localhost:8081/api/system/health': jsonResponse({ status: 'UP' }),
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
    'GET http://localhost:8081/api/douyin/tokens': jsonResponse({
      code: 200,
      data: {
        appId: 'app',
        hasAccessToken: true,
        hasRefreshToken: true,
        reauthorizeRequired: false
      }
    })
  };

  const report = await runRealPrePreflight({
    evidenceDir,
    retryDelayMs: 0,
    retryAttempts: 2,
    fetchImpl: async (url, options = {}) => {
      const method = String(options.method || 'GET').toUpperCase();
      const key = `${method} ${url}`;
      if (key === 'POST http://localhost:8081/api/auth/login') {
        loginAttempts += 1;
        if (loginAttempts === 1) {
          throw new Error('fetch failed');
        }
        return jsonResponse({ code: 200, data: { token: 'jwt-value' } });
      }
      if (!Object.prototype.hasOwnProperty.call(routes, key)) {
        throw new Error(`unexpected request ${key}`);
      }
      return routes[key];
    },
    spawnSyncImpl: createSpawnStub({
      schemaRows: [
        'colonel_partner.create_time=true',
        'colonel_partner.update_time=true',
        'domain_event_consume_log=true',
        'domain_event_outbox=true',
        'product_operation_state.pinned_at=true',
        'product_operation_state.pinned_by=true',
        'product_operation_state.pinned_until=true'
      ],
      mappingCount: '2',
      cleanupJson: JSON.stringify({ mode: 'PlanOnly', protectedTables: [] })
    })
  });

  assert.equal(report.status, 'PASS');
  assert.equal(loginAttempts, 2);
});

test('runRealPrePreflight retries postgres checks while container settles', async () => {
  const evidenceDir = fs.mkdtempSync(path.join(os.tmpdir(), 'real-pre-preflight-'));
  let dockerExecCalls = 0;
  const report = await runRealPrePreflight({
    evidenceDir,
    dbContainer: 'saas-active-postgres-real-pre-1',
    retryDelayMs: 0,
    retryAttempts: 2,
    fetchImpl: createFetchStub({
      'GET http://localhost:3001/login': htmlResponse(),
      'GET http://localhost:8081/api/system/health': jsonResponse({ status: 'UP' }),
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
        data: { token: 'jwt-value' }
      }),
      'GET http://localhost:8081/api/douyin/tokens': jsonResponse({
        code: 200,
        data: {
          appId: 'app',
          hasAccessToken: true,
          hasRefreshToken: true,
          reauthorizeRequired: false
        }
      })
    }),
    spawnSyncImpl: (command) => {
      if (command === 'docker') {
        dockerExecCalls += 1;
        if (dockerExecCalls === 1) {
          return { status: 1, stdout: '', stderr: 'container is not running' };
        }
        if (dockerExecCalls === 2) {
          return {
            status: 0,
            stdout: [
              'colonel_partner.create_time=true',
              'colonel_partner.update_time=true',
              'domain_event_consume_log=true',
              'domain_event_outbox=true',
              'product_operation_state.pinned_at=true',
              'product_operation_state.pinned_by=true',
              'product_operation_state.pinned_until=true'
            ].join('\n'),
            stderr: ''
          };
        }
        return { status: 0, stdout: '2\n', stderr: '' };
      }
      if (command === 'node') {
        return { status: 0, stdout: `${JSON.stringify({ mode: 'PlanOnly', protectedTables: [] })}\n`, stderr: '' };
      }
      throw new Error(`unexpected command ${command}`);
    }
  });

  assert.equal(report.status, 'PASS');
  assert.equal(dockerExecCalls, 3);
});

test('runRealPrePreflight returns BLOCKED when refresh token is missing', async () => {
  const evidenceDir = fs.mkdtempSync(path.join(os.tmpdir(), 'real-pre-preflight-'));
  const report = await runRealPrePreflight({
    evidenceDir,
    fetchImpl: createFetchStub({
      'GET http://localhost:3001/login': htmlResponse(),
      'GET http://localhost:8081/api/system/health': jsonResponse({ status: 'UP' }),
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
        data: { token: 'jwt-value' }
      }),
      'GET http://localhost:8081/api/douyin/tokens': jsonResponse({
        code: 200,
        data: {
          hasAccessToken: true,
          hasRefreshToken: false,
          reauthorizeRequired: true
        }
      })
    }),
    spawnSyncImpl: createSpawnStub({
      schemaRows: [
        'colonel_partner.create_time=true',
        'colonel_partner.update_time=true',
        'domain_event_consume_log=true',
        'domain_event_outbox=true',
        'product_operation_state.pinned_at=true',
        'product_operation_state.pinned_by=true',
        'product_operation_state.pinned_until=true'
      ],
      mappingCount: '1',
      cleanupJson: JSON.stringify({ mode: 'PlanOnly', protectedTables: [] })
    })
  });

  assert.equal(report.status, 'BLOCKED');
  assert.equal(report.canRunBusinessFlows, false);
  assert.equal(report.checks.find((check) => check.name === 'douyin token readiness').status, 'BLOCKED_AUTH');
  assert.match(
    report.checks.find((check) => check.name === 'douyin token readiness').error,
    /hasAccessToken=true, hasRefreshToken=false, reauthorizeRequired=true/
  );
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

function createSpawnStub({ schemaRows, mappingCount, cleanupJson }) {
  let dockerCall = 0;
  return (command) => {
    if (command === 'docker') {
      dockerCall += 1;
      return {
        status: 0,
        stdout: dockerCall === 1 ? `${schemaRows.join('\n')}\n` : `${mappingCount}\n`,
        stderr: ''
      };
    }
    if (command === 'node') {
      return { status: 0, stdout: `${cleanupJson}\n`, stderr: '' };
    }
    throw new Error(`unexpected command ${command}`);
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
