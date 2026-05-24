const path = require('node:path');
const { spawnSync } = require('node:child_process');
const {
  DEFAULT_REAL_PRE_DB_CONTAINER,
  DEFAULT_REAL_PRE_DB_NAME,
  DEFAULT_REAL_PRE_DB_USER,
  applyRealPreEnv,
  createEvidenceDir,
  isRealPreRuntime,
  normalizeSystemEnv,
  redactSecretLikeKeys,
  unwrapApiBody,
  writeJson,
  writeText
} = require('./real-pre-env.cjs');

const ROOT = path.join(__dirname, '..', '..');
const REQUIRED_SCHEMA = [
  ['domain_event_outbox', null],
  ['domain_event_consume_log', null],
  ['product_operation_state', 'pinned_at'],
  ['product_operation_state', 'pinned_until'],
  ['product_operation_state', 'pinned_by'],
  ['colonel_partner', 'create_time'],
  ['colonel_partner', 'update_time']
];

async function runRealPrePreflight(options = {}) {
  const root = options.root || ROOT;
  const urls = applyRealPreEnv(options.env || process.env);
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  const spawnSyncImpl = options.spawnSyncImpl || spawnSync;
  const evidenceDir = options.evidenceDir || createEvidenceDir(root, 'real-pre-preflight');
  const timeoutMs = Number(options.timeoutMs || process.env.E2E_REAL_PRE_PREFLIGHT_TIMEOUT_MS || 90_000);
  const adminUsername = options.adminUsername || process.env.QA_ADMIN_USER || 'admin';
  const adminPassword = options.adminPassword || process.env.QA_ADMIN_PASSWORD || 'admin123';
  const dbContainer = options.dbContainer || process.env.QA_POSTGRES_CONTAINER || process.env.E2E_DB_CONTAINER || DEFAULT_REAL_PRE_DB_CONTAINER;
  const dbUser = options.dbUser || process.env.QA_DB_USER || process.env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER;
  const dbName = options.dbName || process.env.QA_DB_NAME || process.env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME;

  if (typeof fetchImpl !== 'function') {
    throw new Error('global fetch is unavailable; use Node.js 18+ to run real-pre preflight');
  }

  const checks = [];
  checks.push(await runCheck('frontend real-pre 3001', 'FAIL', () => checkFrontend(urls.frontendUrl, fetchImpl, timeoutMs)));
  checks.push(await runCheck('backend health 8081', 'FAIL', () => checkBackendHealth(urls.backendUrl, fetchImpl, timeoutMs)));
  checks.push(await runCheck('real-pre env guard', 'FAIL', () => checkRealPreEnv(urls.backendUrl, fetchImpl, timeoutMs)));
  const loginCheck = await runCheck('admin login', 'FAIL', () => login(urls.backendUrl, fetchImpl, adminUsername, adminPassword, timeoutMs));
  checks.push(loginCheck);

  const token = loginCheck.details?.token || '';
  checks.push(await runCheck('douyin token readiness', 'BLOCKED_AUTH', () => checkDouyinToken(urls.backendUrl, fetchImpl, token, timeoutMs)));
  checks.push(await runCheck('database schema readiness', 'FAIL', () => checkDatabaseSchema(spawnSyncImpl, dbContainer, dbUser, dbName)));
  checks.push(await runCheck('reusable promotion mapping', 'PENDING_PICK_SOURCE', () => checkReusablePromotionMapping(spawnSyncImpl, dbContainer, dbUser, dbName)));
  checks.push(await runCheck('qa run cleanup plan available', 'FAIL', () => checkCleanupPlan(root, spawnSyncImpl)));

  const status = summarizeStatus(checks);
  const summary = {
    evidenceType: 'real-pre-preflight',
    generatedAt: new Date().toISOString(),
    evidenceDir,
    status,
    ok: status === 'PASS',
    canRunBusinessFlows: status === 'PASS',
    urls,
    database: { container: dbContainer, user: dbUser, name: dbName },
    checks: checks.map((check) => ({
      ...check,
      details: redactSecretLikeKeys(check.details)
    }))
  };

  writeJson(path.join(evidenceDir, 'summary.json'), summary);
  writeText(path.join(evidenceDir, 'report.md'), buildReport(summary));
  return summary;
}

async function assertRealPrePreflight(options = {}) {
  const summary = await runRealPrePreflight(options);
  for (const check of summary.checks) {
    const message = `[real-pre preflight] ${check.status} ${check.name}`;
    if (check.status === 'PASS') {
      console.log(message);
    } else {
      console.error(`${message}: ${check.error || JSON.stringify(check.details || {})}`);
    }
  }
  console.log(`[real-pre preflight] evidence: ${summary.evidenceDir}`);
  if (!summary.canRunBusinessFlows) {
    const error = new Error(`real-pre preflight ${summary.status}; see ${summary.evidenceDir}`);
    error.summary = summary;
    throw error;
  }
  return summary;
}

async function runCheck(name, nonPassStatus, fn) {
  try {
    const details = await fn();
    return { name, status: 'PASS', details: details || {} };
  } catch (error) {
    return {
      name,
      status: nonPassStatus,
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
  const response = await requestJson(fetchImpl, `${backendUrl}/api/system/health`, { timeoutMs });
  if (!response.ok || response.body?.status !== 'UP') {
    throw new Error(`/api/system/health is not UP: HTTP ${response.status}`);
  }
  return { url: `${backendUrl}/api/system/health`, status: response.body.status };
}

async function checkRealPreEnv(backendUrl, fetchImpl, timeoutMs) {
  const response = await requestJson(fetchImpl, `${backendUrl}/api/system/env`, { timeoutMs });
  const env = normalizeSystemEnv(response.body);
  if (!response.ok || !isRealPreRuntime(env)) {
    throw new Error(`expected REAL-PRE + saas_real_pre + test switches off, got ${JSON.stringify(env)}`);
  }
  return env;
}

async function login(backendUrl, fetchImpl, username, password, timeoutMs) {
  const response = await requestJson(fetchImpl, `${backendUrl}/api/auth/login`, {
    method: 'POST',
    timeoutMs,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const data = unwrapApiBody(response.body) || {};
  const token = data.token || data.accessToken;
  if (!response.ok || !token) {
    throw new Error(`admin login failed: HTTP ${response.status}`);
  }
  return { username, token };
}

async function checkDouyinToken(backendUrl, fetchImpl, token, timeoutMs) {
  if (!token) {
    throw new Error('admin token is unavailable');
  }
  const response = await requestJson(fetchImpl, `${backendUrl}/api/douyin/tokens`, {
    timeoutMs,
    headers: { Authorization: `Bearer ${token}` }
  });
  const data = unwrapApiBody(response.body) || {};
  const hasAccessToken = data.hasAccessToken === true;
  const hasRefreshToken = data.hasRefreshToken === true;
  const reauthorizeRequired = data.reauthorizeRequired === true;
  if (!response.ok || !hasAccessToken || !hasRefreshToken || reauthorizeRequired) {
    throw new Error('BLOCKED_AUTH: missing access_token/refresh_token or reauthorization required');
  }
  return {
    appId: data.appId,
    hasAccessToken,
    hasRefreshToken,
    tokenExpiringSoon: data.tokenExpiringSoon === true,
    reauthorizeRequired
  };
}

function checkDatabaseSchema(spawnSyncImpl, container, user, dbName) {
  const valueRows = REQUIRED_SCHEMA.map(([table, column]) =>
    `('${escapeSql(table)}','${column ? escapeSql(column) : ''}')`
  ).join(',');
  const sql = `
WITH required(table_name, column_name) AS (VALUES ${valueRows}),
checks AS (
  SELECT r.table_name,
         r.column_name,
         CASE
           WHEN r.column_name = '' THEN EXISTS (
             SELECT 1 FROM information_schema.tables
             WHERE table_schema = 'public' AND table_name = r.table_name
           )
           ELSE EXISTS (
             SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = r.table_name AND column_name = r.column_name
           )
         END AS ok
  FROM required r
)
SELECT table_name || CASE WHEN column_name = '' THEN '' ELSE '.' || column_name END || '=' || ok::text
FROM checks
ORDER BY table_name, column_name;
`.trim();
  const rows = runPsql(spawnSyncImpl, container, user, dbName, sql);
  const missing = rows.filter((row) => row.endsWith('=false')).map((row) => row.replace(/=false$/, ''));
  if (missing.length > 0) {
    throw new Error(`missing real-pre schema objects: ${missing.join(', ')}`);
  }
  return { checked: rows.map((row) => row.replace(/=true$/, '')) };
}

function checkReusablePromotionMapping(spawnSyncImpl, container, user, dbName) {
  const sql = `
SELECT COUNT(*)::text
FROM pick_source_mapping psm
LEFT JOIN promotion_link pl ON pl.id = psm.promotion_link_id AND pl.deleted = 0
WHERE psm.deleted = 0
  AND psm.status = 1
  AND COALESCE(psm.pick_source, '') <> ''
  AND COALESCE(pl.promotion_url, psm.converted_url, '') <> '';
`.trim();
  const rows = runPsql(spawnSyncImpl, container, user, dbName, sql);
  const count = Number(rows[0] || 0);
  if (!Number.isFinite(count) || count < 1) {
    throw new Error('PENDING_PICK_SOURCE: no reusable real-pre promotion mapping was found');
  }
  return { reusableMappingCount: count };
}

function checkCleanupPlan(root, spawnSyncImpl) {
  const result = spawnSyncImpl('node', ['runtime/qa/real-pre-cleanup-plan.cjs', '--run-id', 'QA_PRECHECK'], {
    cwd: root,
    encoding: 'utf8'
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(result.stderr || result.stdout || `cleanup plan exited ${result.status}`);
  }
  const parsed = JSON.parse(result.stdout);
  if (parsed.mode !== 'PlanOnly') {
    throw new Error(`cleanup plan must be PlanOnly, got ${parsed.mode}`);
  }
  return {
    mode: parsed.mode,
    protectedTables: parsed.protectedTables
  };
}

function runPsql(spawnSyncImpl, container, user, dbName, sql) {
  const result = spawnSyncImpl(
    'docker',
    ['exec', '-i', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', dbName, '-t', '-A', '-F', '|', '-c', sql],
    { encoding: 'utf8' }
  );
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(result.stderr || result.stdout || `docker psql exited ${result.status}`);
  }
  return String(result.stdout || '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

async function requestJson(fetchImpl, url, options = {}) {
  const response = await request(fetchImpl, url, options);
  const body = await response.json().catch(async () => ({
    rawText: await response.text().catch(() => '')
  }));
  return { ok: response.ok, status: response.status, body };
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

function summarizeStatus(checks) {
  if (checks.some((check) => check.status === 'FAIL')) return 'FAIL';
  if (checks.some((check) => String(check.status).startsWith('BLOCKED'))) return 'BLOCKED';
  if (checks.some((check) => String(check.status).startsWith('PENDING'))) return 'PENDING';
  return 'PASS';
}

function buildReport(summary) {
  const lines = [];
  lines.push('# real-pre preflight');
  lines.push('');
  lines.push(`- generatedAt: ${summary.generatedAt}`);
  lines.push(`- status: ${summary.status}`);
  lines.push(`- canRunBusinessFlows: ${summary.canRunBusinessFlows}`);
  lines.push(`- frontend: ${summary.urls.frontendUrl}`);
  lines.push(`- backend: ${summary.urls.backendUrl}`);
  lines.push(`- database: ${summary.database.name}`);
  lines.push('');
  lines.push('## Checks');
  lines.push('');
  for (const check of summary.checks) {
    lines.push(`- [${check.status}] ${check.name}`);
    if (check.error) lines.push(`  - error: ${check.error}`);
    if (check.details) lines.push(`  - details: ${JSON.stringify(check.details)}`);
  }
  lines.push('');
  lines.push('## Result Policy');
  lines.push('');
  lines.push('- PASS: real-pre business scripts may run.');
  lines.push('- BLOCKED/PENDING: external prerequisites are missing; do not count the full business flow as passed.');
  lines.push('- FAIL: environment/schema guard failed; fix before any real-pre run.');
  return `${lines.join('\n')}\n`;
}

function escapeSql(value) {
  return String(value).replace(/'/g, "''");
}

function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (!arg.startsWith('--')) continue;
    const key = arg.slice(2);
    const next = argv[index + 1];
    if (next && !next.startsWith('--')) {
      args[key] = next;
      index += 1;
    } else {
      args[key] = true;
    }
  }
  return args;
}

if (require.main === module) {
  const args = parseArgs(process.argv.slice(2));
  runRealPrePreflight({
    evidenceDir: args['evidence-dir'] ? path.resolve(args['evidence-dir']) : undefined
  }).then((summary) => {
    console.log(`real-pre preflight output: ${summary.evidenceDir}`);
    if (summary.status === 'PASS') process.exit(0);
    if (summary.status === 'PENDING' || summary.status === 'BLOCKED') process.exit(2);
    process.exit(1);
  }).catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exit(1);
  });
}

module.exports = {
  REQUIRED_SCHEMA,
  runRealPrePreflight,
  assertRealPrePreflight,
  summarizeStatus
};
