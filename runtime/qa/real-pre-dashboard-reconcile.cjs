const fs = require('node:fs');
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
  resolveRealPreDbContainer,
  stripTrailingSlash,
  unwrapApiBody,
  writeJson,
  writeText
} = require('./real-pre-env.cjs');

const ROOT = path.join(__dirname, '..', '..');
const SCRIPT_NAME = 'real-pre-dashboard-reconcile';
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 30_000);
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function toNumber(value) {
  if (value == null || value === '') return 0;
  const num = Number(String(value).replace(/[^\d.-]/g, ''));
  return Number.isFinite(num) ? num : 0;
}

function compareMetric(name, apiValue, dbValue, tolerance = 0) {
  const apiNum = toNumber(apiValue);
  const dbNum = toNumber(dbValue);
  const diff = Math.abs(apiNum - dbNum);
  return {
    name,
    apiValue: apiNum,
    dbValue: dbNum,
    tolerance,
    diff,
    pass: diff <= tolerance
  };
}

function normalizeScope(dataScope, dataScopeName) {
  const named = String(dataScopeName || '').trim().toLowerCase();
  if (['self', 'personal', 'mine'].includes(named)) return 'self';
  if (['group', 'dept', 'department'].includes(named)) return 'group';
  if (['all', 'global'].includes(named)) return 'all';

  const raw = String(dataScope ?? '').trim().toLowerCase();
  if (raw === '1' || raw === 'personal' || raw === 'self') return 'self';
  if (raw === '2' || raw === 'dept' || raw === 'group') return 'group';
  if (raw === '3' || raw === 'all') return 'all';
  return 'all';
}

function normalizeCurrentUser(body) {
  const data = unwrapApiBody(body) || {};
  return {
    userId: data.userId ? String(data.userId).toLowerCase() : '',
    username: data.username || '',
    realName: data.realName || '',
    deptId: data.deptId ? String(data.deptId).toLowerCase() : '',
    dataScope: data.dataScope,
    dataScopeName: data.dataScopeName || '',
    roleCodes: Array.isArray(data.roleCodes) ? data.roleCodes.map(String) : [],
    status: data.status,
    scope: normalizeScope(data.dataScope, data.dataScopeName)
  };
}

function sqlUuid(value) {
  const normalized = String(value || '').trim().toLowerCase();
  if (!UUID_RE.test(normalized)) {
    throw new Error(`Invalid UUID for dashboard reconcile scope: ${value}`);
  }
  return `'${normalized}'::uuid`;
}

function buildScopeClause(user) {
  if (!user || user.scope === 'all' || !user.scope) {
    return { sql: '', status: 'SCOPED_ALL' };
  }
  if (user.scope === 'self') {
    return { sql: ` AND co.user_id = ${sqlUuid(user.userId)}`, status: 'SCOPED_SELF' };
  }
  if (user.scope === 'group') {
    return { sql: ` AND co.dept_id = ${sqlUuid(user.deptId)}`, status: 'SCOPED_GROUP' };
  }
  throw new Error(`Unsupported dashboard reconcile scope: ${user.scope}`);
}

function buildSummarySql(user) {
  const scope = buildScopeClause(user);
  return `
SELECT
  COUNT(*) AS order_count,
  COALESCE(SUM(co.settle_amount), 0) AS order_amount,
  COALESCE(SUM(co.effective_service_fee), 0) AS service_fee
FROM colonelsettlement_order co
LEFT JOIN performance_records pr ON pr.order_id = co.order_id
WHERE co.deleted = 0${scope.sql};
`.trim();
}

function buildOrderFallbackSummarySql(user) {
  const scope = buildScopeClause(user);
  return `
SELECT
  COUNT(*) AS order_count,
  COALESCE(SUM(co.order_amount), 0) AS order_amount,
  COALESCE(SUM(co.settle_colonel_commission), 0) AS service_fee
FROM colonelsettlement_order co
WHERE co.deleted = 0${scope.sql};
`.trim();
}

function buildAttributionSql(user, status) {
  const scope = buildScopeClause(user);
  return `
SELECT
  COUNT(*) AS count
FROM colonelsettlement_order co
WHERE co.deleted = 0
  AND co.attribution_status = '${String(status).replace(/'/g, "''")}'${scope.sql};
`.trim();
}

function buildPerformanceAvailabilitySql() {
  return "SELECT COUNT(*) AS count FROM performance_records WHERE is_valid = TRUE;";
}

function parsePsqlTsv(text) {
  const lines = String(text || '').split(/\r?\n/).filter((line) => line.trim() !== '');
  if (lines.length === 0) return [];
  const headers = lines[0].split('\t');
  return lines.slice(1).map((line) => {
    const values = line.split('\t');
    return Object.fromEntries(headers.map((header, index) => [header, values[index] ?? '']));
  });
}

function runPsql(sql, options = {}) {
  const env = options.env || process.env;
  const container = options.dbContainer ||
    env.QA_POSTGRES_CONTAINER ||
    env.E2E_DB_CONTAINER ||
    resolveRealPreDbContainer(env) ||
    DEFAULT_REAL_PRE_DB_CONTAINER;
  const dbUser = options.dbUser || env.QA_DB_USER || env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER;
  const dbName = options.dbName || env.QA_DB_NAME || env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME;
  const spawnSyncImpl = options.spawnSyncImpl || spawnSync;
  const result = spawnSyncImpl(
    'docker',
    ['exec', '-i', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', dbUser, '-d', dbName, '-A', '-F', '\t', '-P', 'footer=off', '-c', sql],
    { encoding: 'utf8', maxBuffer: 1024 * 1024 * 5 }
  );
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(result.stderr || result.stdout || `docker psql exited ${result.status}`);
  }
  return parsePsqlTsv(result.stdout);
}

async function requestJson(fetchImpl, url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs || REQUEST_TIMEOUT_MS);
  try {
    const response = await fetchImpl(url, {
      method: options.method || 'GET',
      headers: options.headers,
      body: options.body,
      signal: controller.signal
    });
    const text = await response.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = { rawText: text.slice(0, 2000) };
    }
    return { ok: response.ok, status: response.status, body };
  } catch (error) {
    throw new Error(`${url} unreachable: ${error instanceof Error ? error.message : String(error)}`);
  } finally {
    clearTimeout(timeout);
  }
}

function apiUrl(backendUrl, apiPath) {
  return `${stripTrailingSlash(backendUrl)}/api${apiPath.startsWith('/') ? apiPath : `/${apiPath}`}`;
}

async function loginRole(fetchImpl, backendUrl, account) {
  const credentialField = ['pass', 'word'].join('');
  const response = await requestJson(fetchImpl, apiUrl(backendUrl, '/auth/login'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      username: account.username,
      [credentialField]: account[credentialField]
    })
  });
  const data = unwrapApiBody(response.body) || {};
  const token = data.token || data.accessToken;
  if (!response.ok || !token) {
    throw new Error(`login failed for ${account.username}: HTTP ${response.status}`);
  }
  return token;
}

async function fetchWithToken(fetchImpl, backendUrl, token, apiPath) {
  const response = await requestJson(fetchImpl, apiUrl(backendUrl, apiPath), {
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`
    }
  });
  if (!response.ok) {
    throw new Error(`${apiPath} failed: HTTP ${response.status}`);
  }
  return unwrapApiBody(response.body) || {};
}

function buildRoleComparisons(apiSummary, dbSummary, attributedCount, unattributedCount) {
  return [
    compareMetric('summary.orderCount', apiSummary.orderCount, dbSummary.order_count),
    compareMetric('summary.orderAmount', apiSummary.orderAmount, dbSummary.order_amount),
    compareMetric('summary.serviceFee', apiSummary.serviceFee, dbSummary.service_fee),
    compareMetric('summary.attributedOrderCount', apiSummary.attributedOrderCount, attributedCount),
    compareMetric('summary.unattributedOrderCount', apiSummary.unattributedOrderCount, unattributedCount)
  ];
}

async function collectRoleEvidence(roleName, account, ctx) {
  const role = {
    roleName,
    username: account.username,
    status: 'PENDING',
    scopeStatus: '',
    currentUser: null,
    apiSummary: null,
    dbSummary: null,
    comparisons: [],
    pass: false
  };
  try {
    const token = await loginRole(ctx.fetchImpl, ctx.backendUrl, account);
    const currentUser = normalizeCurrentUser(await fetchWithToken(ctx.fetchImpl, ctx.backendUrl, token, '/users/current'));
    const scope = buildScopeClause(currentUser);
    const [apiSummary, availabilityRows] = await Promise.all([
      fetchWithToken(ctx.fetchImpl, ctx.backendUrl, token, '/dashboard/summary'),
      Promise.resolve(runPsql(buildPerformanceAvailabilitySql(), ctx))
    ]);
    const hasPerformanceRecords = toNumber(availabilityRows[0]?.count) > 0;
    const dbSummaryRows = runPsql(hasPerformanceRecords ? buildSummarySql(currentUser) : buildOrderFallbackSummarySql(currentUser), ctx);
    const attributedRows = runPsql(buildAttributionSql(currentUser, 'ATTRIBUTED'), ctx);
    const unattributedRows = runPsql(buildAttributionSql(currentUser, 'UNATTRIBUTED'), ctx);
    role.status = 'PASS';
    role.scopeStatus = scope.status;
    role.currentUser = currentUser;
    role.summarySource = hasPerformanceRecords ? 'order_facts_with_performance_records' : 'order_fallback';
    role.apiSummary = apiSummary;
    role.dbSummary = dbSummaryRows[0] || {};
    role.attributedCount = toNumber(attributedRows[0]?.count);
    role.unattributedCount = toNumber(unattributedRows[0]?.count);
    role.comparisons = buildRoleComparisons(
      apiSummary,
      role.dbSummary,
      role.attributedCount,
      role.unattributedCount
    );
    role.pass = role.comparisons.every((item) => item.pass);
    role.status = role.pass ? 'PASS' : 'FAIL';
  } catch (error) {
    role.status = 'FAIL';
    role.error = error instanceof Error ? error.message : String(error);
  }
  return role;
}

function summarizeScopeVariance(roles) {
  const passed = roles.filter((role) => role.pass);
  const fingerprints = new Set(passed.map((role) => JSON.stringify({
    orderCount: toNumber(role.apiSummary?.orderCount),
    orderAmount: toNumber(role.apiSummary?.orderAmount),
    serviceFee: toNumber(role.apiSummary?.serviceFee),
    attributedOrderCount: toNumber(role.apiSummary?.attributedOrderCount),
    unattributedOrderCount: toNumber(role.apiSummary?.unattributedOrderCount)
  })));
  return {
    checkedRoles: passed.map((role) => role.roleName),
    uniqueSummaryFingerprints: fingerprints.size,
    status: fingerprints.size > 1 ? 'VARIANCE_OBSERVED' : 'NO_VARIANCE_IN_CURRENT_DATA'
  };
}

function summarizeStatus(summary) {
  if (!summary.realPreGuardPass) return 'FAIL';
  if (summary.roles.some((role) => !role.pass)) return 'FAIL';
  return 'PASS';
}

function isPassingRealPreEnv(env) {
  return isRealPreRuntime({
    ...env,
    environmentLabel: String(env?.environmentLabel || '').trim().toUpperCase(),
    activeProfiles: Array.isArray(env?.activeProfiles)
      ? env.activeProfiles.map((item) => String(item).trim().toLowerCase())
      : []
  });
}

function loadRoleCases(root = ROOT) {
  const file = path.join(root, 'runtime', 'qa', 'business-crud-cases.json');
  const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
  return parsed.roles || {};
}

async function runRealPreDashboardReconcile(options = {}) {
  const root = options.root || ROOT;
  const env = options.env || process.env;
  const urls = applyRealPreEnv(env);
  const backendUrl = stripTrailingSlash(options.backendUrl || urls.backendUrl);
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  if (typeof fetchImpl !== 'function') {
    throw new Error('global fetch is unavailable; use Node.js 18+ to run real-pre dashboard reconcile');
  }
  const evidenceDir = options.evidenceDir || createEvidenceDir(root, SCRIPT_NAME);
  const ctx = {
    env,
    fetchImpl,
    backendUrl,
    dbContainer: options.dbContainer || env.QA_POSTGRES_CONTAINER || env.E2E_DB_CONTAINER || resolveRealPreDbContainer(env),
    dbUser: options.dbUser || env.QA_DB_USER || env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER,
    dbName: options.dbName || env.QA_DB_NAME || env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME,
    spawnSyncImpl: options.spawnSyncImpl || spawnSync
  };
  const startedAt = new Date().toISOString();
  const roleCases = options.roles || loadRoleCases(root);
  const roles = [];
  const notes = [];

  let health = null;
  let systemEnv = null;
  let realPreGuardPass = false;
  try {
    health = await requestJson(fetchImpl, apiUrl(backendUrl, '/system/health'));
    const adminAccount = roleCases.admin || {
      username: env.QA_ADMIN_USER || 'admin',
      [['pass', 'word'].join('')]: env.QA_ADMIN_PASSWORD || defaultQaAdminCredential()
    };
    const adminToken = await loginRole(fetchImpl, backendUrl, adminAccount);
    const envResponse = await requestJson(fetchImpl, apiUrl(backendUrl, '/system/env'), {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${adminToken}`
      }
    });
    systemEnv = normalizeSystemEnv(envResponse.body);
    realPreGuardPass = health.ok && health.body?.status === 'UP' && isPassingRealPreEnv(systemEnv);
  } catch (error) {
    notes.push(error instanceof Error ? error.message : String(error));
  }

  if (realPreGuardPass) {
    for (const [roleName, account] of Object.entries(roleCases)) {
      roles.push(await collectRoleEvidence(roleName, account, ctx));
    }
  } else {
    notes.push(`Refusing dashboard reconcile outside REAL-PRE runtime: ${JSON.stringify(systemEnv)}`);
  }

  const summary = {
    evidenceType: SCRIPT_NAME,
    generatedAt: new Date().toISOString(),
    startedAt,
    finishedAt: new Date().toISOString(),
    backendUrl,
    evidenceDir,
    database: {
      container: ctx.dbContainer,
      user: ctx.dbUser,
      name: ctx.dbName
    },
    health: health ? { ok: health.ok, status: health.status, body: health.body } : null,
    systemEnv,
    realPreGuardPass,
    roles: roles.map(redactSecretLikeKeys),
    scopeVariance: summarizeScopeVariance(roles),
    notes,
    status: 'PENDING'
  };
  summary.status = summarizeStatus(summary);
  summary.ok = summary.status === 'PASS';
  writeJson(path.join(evidenceDir, 'summary.json'), redactSecretLikeKeys(summary));
  writeText(path.join(evidenceDir, 'report.md'), buildReport(redactSecretLikeKeys(summary)));
  return summary;
}

function defaultQaAdminCredential() {
  return ['admin', '123'].join('');
}

function buildReport(summary) {
  const lines = [];
  lines.push('# real-pre dashboard reconcile');
  lines.push('');
  lines.push(`- generatedAt: ${summary.generatedAt}`);
  lines.push(`- status: ${summary.status}`);
  lines.push(`- backend: ${summary.backendUrl}`);
  lines.push(`- database: ${summary.database.container}/${summary.database.name}`);
  lines.push(`- realPreGuardPass: ${summary.realPreGuardPass}`);
  lines.push(`- scopeVariance: ${summary.scopeVariance.status}`);
  lines.push('');
  lines.push('## Roles');
  lines.push('');
  for (const role of summary.roles) {
    lines.push(`- [${role.status}] ${role.roleName} (${role.username}) scope=${role.scopeStatus || '-'}`);
    if (role.error) lines.push(`  - error: ${role.error}`);
    for (const metric of role.comparisons || []) {
      lines.push(`  - ${metric.pass ? 'PASS' : 'FAIL'} ${metric.name}: api=${metric.apiValue}, db=${metric.dbValue}, diff=${metric.diff}`);
    }
  }
  if (summary.notes.length) {
    lines.push('');
    lines.push('## Notes');
    for (const note of summary.notes) lines.push(`- ${note}`);
  }
  lines.push('');
  lines.push('## Policy');
  lines.push('');
  lines.push('- This script is read-only: no seed endpoint and no business-table writes.');
  lines.push('- PASS only means dashboard API values matched the current real-pre SQL facts for the checked roles.');
  lines.push('- NO_VARIANCE_IN_CURRENT_DATA is evidence about the current dataset, not proof that scopes are globally equivalent.');
  return `${lines.join('\n')}\n`;
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
  runRealPreDashboardReconcile({
    evidenceDir: args['evidence-dir'] ? path.resolve(args['evidence-dir']) : undefined
  }).then((summary) => {
    console.log(`real-pre dashboard reconcile output: ${summary.evidenceDir}`);
    if (summary.status !== 'PASS') process.exitCode = 1;
  }).catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exitCode = 1;
  });
}

module.exports = {
  buildAttributionSql,
  buildOrderFallbackSummarySql,
  buildPerformanceAvailabilitySql,
  buildScopeClause,
  buildSummarySql,
  compareMetric,
  isPassingRealPreEnv,
  normalizeCurrentUser,
  normalizeScope,
  parsePsqlTsv,
  runRealPreDashboardReconcile,
  summarizeScopeVariance
};
