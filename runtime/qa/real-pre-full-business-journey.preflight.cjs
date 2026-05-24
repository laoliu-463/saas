const { spawnSync } = require('node:child_process');

const DEFAULT_JOURNEY_ACCOUNTS = [
  'admin',
  'biz_leader',
  'biz_staff',
  'channel_staff',
  'ops_staff',
  'channel_leader'
];

async function runJourneyPreflight(options = {}) {
  const frontendUrl = stripTrailingSlash(options.frontendUrl || 'http://localhost:3001');
  const backendUrl = stripTrailingSlash(options.backendUrl || 'http://localhost:8081');
  const root = options.root || process.cwd();
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  const spawnSyncImpl = options.spawnSyncImpl || spawnSync;
  const accounts = options.accounts || DEFAULT_JOURNEY_ACCOUNTS;
  const password = options.password || 'admin123';
  const timeoutMs = options.timeoutMs || Number(process.env.E2E_REAL_PRE_PREFLIGHT_TIMEOUT_MS || 90_000);

  if (typeof fetchImpl !== 'function') {
    throw new Error('global fetch is unavailable; use Node.js 18+ to run journey preflight');
  }

  const checks = [];
  checks.push(await runCheck(`frontend ${endpointPort(frontendUrl)}`, () => checkFrontend(frontendUrl, fetchImpl, timeoutMs)));
  checks.push(await runCheck(`backend ${endpointPort(backendUrl)}`, () => checkBackendHealth(backendUrl, fetchImpl, timeoutMs)));
  checks.push(await runCheck('real-pre env guard', () => checkRealPreEnv(backendUrl, fetchImpl, timeoutMs)));
  checks.push(await runCheck('required accounts', () => checkAccounts(backendUrl, fetchImpl, accounts, password, timeoutMs)));
  checks.push(await runCheck('.env.real-pre gitignore', () => checkRealPreEnvIgnored(root, spawnSyncImpl)));

  return {
    ok: checks.every((check) => check.status === 'PASS'),
    frontendUrl,
    backendUrl,
    checks
  };
}

async function assertJourneyPreflight(options = {}) {
  const report = await runJourneyPreflight(options);
  for (const check of report.checks) {
    if (check.status === 'PASS') {
      console.log(`[preflight] PASS ${check.name}`);
    } else {
      console.error(`[preflight] FAIL ${check.name}: ${check.error}`);
    }
  }
  if (!report.ok) {
    throw new Error(`real-pre journey preflight failed:\n${summarizePreflightFailure(report)}`);
  }
  return report;
}

async function runCheck(name, fn) {
  try {
    const details = await fn();
    return { name, status: 'PASS', details: details || {} };
  } catch (error) {
    return {
      name,
      status: 'FAIL',
      error: error instanceof Error ? error.message : String(error)
    };
  }
}

async function checkFrontend(frontendUrl, fetchImpl, timeoutMs) {
  const response = await request(fetchImpl, `${frontendUrl}/login`, { timeoutMs });
  if (!response.ok) {
    throw new Error(`GET /login returned HTTP ${response.status}`);
  }
  return { url: `${frontendUrl}/login`, status: response.status };
}

async function checkBackendHealth(backendUrl, fetchImpl, timeoutMs) {
  const response = await requestJson(fetchImpl, `${backendUrl}/api/actuator/health`, { timeoutMs });
  if (!response.ok || response.body?.status !== 'UP') {
    throw new Error(`actuator health is not UP: HTTP ${response.status}`);
  }
  return { url: `${backendUrl}/api/actuator/health`, status: response.body.status };
}

async function checkRealPreEnv(backendUrl, fetchImpl, timeoutMs) {
  const response = await requestJson(fetchImpl, `${backendUrl}/api/system/env`, { timeoutMs });
  const env = unwrap(response.body);
  const activeProfiles = asStringArray(env?.activeProfiles);
  const label = String(env?.environmentLabel || '').toUpperCase();
  if (!response.ok || label !== 'REAL-PRE' || !(activeProfiles.includes('real-pre') || activeProfiles.includes('real'))) {
    throw new Error(`expected REAL-PRE env, got ${JSON.stringify(env)}`);
  }
  if (env?.appTestEnabled !== false) {
    throw new Error('APP_TEST_ENABLED must be false');
  }
  if (env?.douyinTestEnabled !== false) {
    throw new Error('DOUYIN_TEST_ENABLED must be false');
  }
  if (env?.database !== 'saas_real_pre') {
    throw new Error(`database must be saas_real_pre, got ${env?.database || 'unknown'}`);
  }
  return {
    environmentLabel: env.environmentLabel,
    activeProfiles,
    appTestEnabled: env.appTestEnabled,
    douyinTestEnabled: env.douyinTestEnabled,
    database: env.database
  };
}

async function checkAccounts(backendUrl, fetchImpl, accounts, password, timeoutMs) {
  const checked = [];
  for (const username of accounts) {
    const response = await requestJson(fetchImpl, `${backendUrl}/api/auth/login`, {
      method: 'POST',
      timeoutMs,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = unwrap(response.body);
    const token = data?.token || data?.accessToken;
    if (!response.ok || !token) {
      throw new Error(`${username} login preflight failed: HTTP ${response.status}`);
    }
    checked.push(username);
  }
  return { checked };
}

function checkRealPreEnvIgnored(root, spawnSyncImpl) {
  const result = spawnSyncImpl('git', ['check-ignore', '-q', '.env.real-pre'], {
    cwd: root,
    encoding: 'utf8'
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error('.env.real-pre is not ignored by git');
  }
  return { path: '.env.real-pre' };
}

async function requestJson(fetchImpl, url, options = {}) {
  const response = await request(fetchImpl, url, options);
  const body = await response.json().catch(async () => ({
    rawText: await response.text().catch(() => '')
  }));
  return {
    ok: response.ok,
    status: response.status,
    body
  };
}

async function request(fetchImpl, url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs || 30_000);
  try {
    return await fetchImpl(url, {
      method: options.method || 'GET',
      headers: options.headers,
      body: options.body,
      signal: controller.signal
    });
  } catch (error) {
    throw new Error(`${url} unreachable: ${error instanceof Error ? error.message : String(error)}`);
  } finally {
    clearTimeout(timeout);
  }
}

function summarizePreflightFailure(report) {
  return report.checks
    .filter((check) => check.status !== 'PASS')
    .map((check) => `- ${check.name}: ${check.error}`)
    .join('\n');
}

function unwrap(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data;
  }
  return body;
}

function asStringArray(value) {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item));
}

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/$/, '');
}

function endpointPort(value) {
  try {
    const url = new URL(value);
    return url.port || (url.protocol === 'https:' ? '443' : '80');
  } catch {
    return String(value || 'unknown');
  }
}

module.exports = {
  DEFAULT_JOURNEY_ACCOUNTS,
  runJourneyPreflight,
  assertJourneyPreflight,
  summarizePreflightFailure
};
