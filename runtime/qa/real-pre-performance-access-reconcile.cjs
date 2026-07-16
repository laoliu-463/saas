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
const SCRIPT_NAME = 'real-pre-performance-access-reconcile';
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 30_000);
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ROLE_NAMES = ['admin', 'biz_leader', 'biz_staff', 'channel_staff'];

function normalizeDataScope(dataScope, dataScopeName) {
  const named = String(dataScopeName || '').trim().toUpperCase();
  if (['PERSONAL', 'SELF', 'MINE'].includes(named)) return 'PERSONAL';
  if (['DEPT', 'DEPARTMENT', 'GROUP'].includes(named)) return 'DEPT';
  if (['ALL', 'GLOBAL'].includes(named)) return 'ALL';

  const raw = String(dataScope ?? '').trim().toUpperCase();
  if (['1', 'PERSONAL', 'SELF', 'MINE'].includes(raw)) return 'PERSONAL';
  if (['2', 'DEPT', 'DEPARTMENT', 'GROUP'].includes(raw)) return 'DEPT';
  if (['3', 'ALL', 'GLOBAL'].includes(raw)) return 'ALL';
  return '';
}

function normalizeCurrentUser(body) {
  const data = unwrapApiBody(body) || {};
  return {
    userId: String(data.userId || data.id || '').trim().toLowerCase(),
    username: String(data.username || '').trim(),
    realName: String(data.realName || '').trim(),
    deptId: String(data.deptId || '').trim().toLowerCase(),
    dataScope: normalizeDataScope(data.dataScope, data.dataScopeName),
    dataScopeName: String(data.dataScopeName || '').trim(),
    roleCodes: Array.isArray(data.roleCodes)
      ? data.roleCodes.map((role) => String(role).trim().toLowerCase()).filter(Boolean)
      : []
  };
}

function sqlUuid(value, label = 'UUID') {
  const normalized = String(value || '').trim().toLowerCase();
  if (!normalized) throw new Error(`${label} is required for restricted performance scope`);
  if (!UUID_RE.test(normalized)) throw new Error(`Invalid UUID for ${label}: ${value}`);
  return normalized;
}

function resolvePerformanceScope(user = {}) {
  const roleCodes = new Set((user.roleCodes || []).map((role) => String(role).trim().toLowerCase()));
  const dataScope = normalizeDataScope(user.dataScope, user.dataScopeName);
  const has = (...roles) => roles.some((role) => roleCodes.has(role));

  if (dataScope === 'ALL' || has('admin', 'ops_staff')) return { kind: 'ALL' };
  if (has('channel_staff') && !has('admin', 'channel_leader', 'ops_staff')) {
    return { kind: 'CHANNEL_STAFF', userId: sqlUuid(user.userId, 'userId') };
  }
  if (has('biz_staff') && !has('admin', 'biz_leader', 'ops_staff')) {
    return { kind: 'BIZ_STAFF', userId: sqlUuid(user.userId, 'userId') };
  }
  if (has('channel_leader') && !has('admin')) {
    return { kind: 'CHANNEL_LEADER', deptId: sqlUuid(user.deptId, 'deptId') };
  }
  if (has('biz_leader') && !has('admin')) {
    return { kind: 'BIZ_LEADER', deptId: sqlUuid(user.deptId, 'deptId') };
  }
  if (dataScope === 'DEPT') return { kind: 'DEPT', deptId: sqlUuid(user.deptId, 'deptId') };
  if (dataScope === 'PERSONAL') return { kind: 'PERSONAL', userId: sqlUuid(user.userId, 'userId') };
  throw new Error(`Unsupported performance scope for ${user.username || 'unknown user'}`);
}

function scopePredicate(scope) {
  const user = scope.userId ? `'${scope.userId}'::uuid` : null;
  const dept = scope.deptId ? `'${scope.deptId}'::uuid` : null;
  switch (scope.kind) {
    case 'ALL':
      return 'TRUE';
    case 'CHANNEL_STAFF':
      return `pr.final_channel_user_id = ${user}`;
    case 'BIZ_STAFF':
      return `pr.final_recruiter_user_id = ${user}`;
    case 'CHANNEL_LEADER':
      return `pr.final_channel_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0)`;
    case 'BIZ_LEADER':
      return `pr.final_recruiter_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0)`;
    case 'PERSONAL':
      return `(pr.final_channel_user_id = ${user} OR pr.final_recruiter_user_id = ${user})`;
    case 'DEPT':
      return `(pr.final_channel_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0) OR pr.final_recruiter_user_id IN (SELECT id FROM sys_user WHERE dept_id = ${dept} AND deleted = 0))`;
    default:
      throw new Error(`Unsupported performance scope kind: ${scope.kind}`);
  }
}

function buildCountSql(scope) {
  return `SELECT COUNT(*) AS total FROM performance_records pr WHERE pr.is_valid = TRUE AND (${scopePredicate(scope)});`;
}

function buildOutOfScopeSql(scope) {
  if (scope.kind === 'ALL') return '';
  return `SELECT pr.order_id FROM performance_records pr WHERE pr.is_valid = TRUE AND NOT COALESCE((${scopePredicate(scope)}), FALSE) ORDER BY pr.calculated_at DESC NULLS LAST LIMIT 1;`;
}

function sqlTextLiteral(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function buildOwnershipSql(orderIds) {
  const normalized = [...new Set((orderIds || []).map((id) => String(id).trim()).filter(Boolean))];
  if (normalized.length === 0) return '';
  return `
SELECT
  pr.order_id,
  pr.final_channel_user_id::text AS final_channel_user_id,
  pr.final_recruiter_user_id::text AS final_recruiter_user_id
FROM performance_records pr
WHERE pr.is_valid = TRUE
  AND pr.order_id IN (${normalized.map(sqlTextLiteral).join(', ')});
`.trim();
}

function buildDeptMembersSql(scope) {
  if (!['CHANNEL_LEADER', 'BIZ_LEADER', 'DEPT'].includes(scope.kind)) return '';
  return `SELECT id::text AS user_id FROM sys_user WHERE dept_id = '${scope.deptId}'::uuid AND deleted = 0;`;
}

function isOwnershipInScope(row, scope, deptMemberIds = []) {
  const channelId = String(row.final_channel_user_id || '').toLowerCase();
  const recruiterId = String(row.final_recruiter_user_id || '').toLowerCase();
  const members = new Set(deptMemberIds.map((id) => String(id).toLowerCase()));
  if (scope.kind === 'ALL') return true;
  if (scope.kind === 'CHANNEL_STAFF') return channelId === scope.userId;
  if (scope.kind === 'BIZ_STAFF') return recruiterId === scope.userId;
  if (scope.kind === 'CHANNEL_LEADER') return members.has(channelId);
  if (scope.kind === 'BIZ_LEADER') return members.has(recruiterId);
  if (scope.kind === 'PERSONAL') return channelId === scope.userId || recruiterId === scope.userId;
  if (scope.kind === 'DEPT') return members.has(channelId) || members.has(recruiterId);
  return false;
}

function classifyRoleEvidence(evidence) {
  if ((evidence.errors || []).length) return 'FAIL';
  if (Number(evidence.apiTotal) !== Number(evidence.dbTotal)) return 'FAIL';
  if ((evidence.ownershipChecks || []).some((item) => !item.pass)) return 'FAIL';
  if ((evidence.negativeChecks || []).some((item) => item.required && item.status === 'FAIL')) return 'FAIL';
  if ((evidence.negativeChecks || []).some((item) => item.required && item.status === 'NO_SAMPLE')) {
    return 'PARTIAL_NO_NEGATIVE_SAMPLE';
  }
  if (Number(evidence.dbTotal) === 0) return 'PARTIAL_NO_POSITIVE_SAMPLE';
  return 'PASS';
}

function summarizeStatus(roles) {
  if ((roles || []).some((role) => role.status === 'FAIL')) return 'FAIL';
  if ((roles || []).some((role) => String(role.status).startsWith('PARTIAL_'))) return 'PARTIAL';
  return 'PASS';
}

function isPassingRealPreEnv(env) {
  return isRealPreRuntime({
    ...env,
    environmentLabel: String(env?.environmentLabel || '').trim().toUpperCase(),
    activeProfiles: Array.isArray(env?.activeProfiles)
      ? env.activeProfiles.map((profile) => String(profile).trim().toLowerCase())
      : []
  });
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
  const container = options.dbContainer || resolveRealPreDbContainer(env) || DEFAULT_REAL_PRE_DB_CONTAINER;
  const dbUser = options.dbUser || env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER;
  const dbName = options.dbName || env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME;
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
  } finally {
    clearTimeout(timeout);
  }
}

function apiUrl(backendUrl, apiPath) {
  return `${stripTrailingSlash(backendUrl)}/api${apiPath.startsWith('/') ? apiPath : `/${apiPath}`}`;
}

function bearer(token) {
  return { Accept: 'application/json', Authorization: `Bearer ${token}` };
}

async function loginRole(fetchImpl, backendUrl, account) {
  const response = await requestJson(fetchImpl, apiUrl(backendUrl, '/auth/login'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ username: account.username, password: account.password })
  });
  const data = unwrapApiBody(response.body) || {};
  const token = data.token || data.accessToken;
  if (!response.ok || !token) {
    throw new Error(`login failed for ${account.username}: HTTP ${response.status}`);
  }
  return token;
}

async function fetchData(fetchImpl, backendUrl, token, apiPath) {
  const response = await requestJson(fetchImpl, apiUrl(backendUrl, apiPath), {
    headers: bearer(token)
  });
  if (!response.ok) throw new Error(`${apiPath} failed: HTTP ${response.status}`);
  return unwrapApiBody(response.body) || {};
}

function isForbidden(response) {
  const code = Number(response?.body?.code);
  return [401, 403].includes(Number(response?.status)) || [401, 403].includes(code);
}

function loadRoleCases(root = ROOT) {
  const file = path.join(root, 'runtime', 'qa', 'role-page-cases.json');
  const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
  const roles = parsed.roles || {};
  return Object.fromEntries(ROLE_NAMES.map((roleName) => {
    const account = roles[roleName];
    if (!account?.username || !account?.password) {
      throw new Error(`Missing QA role credentials for ${roleName}`);
    }
    return [roleName, { username: account.username, password: account.password }];
  }));
}

function normalizePage(data) {
  return {
    total: Number(data?.total || 0),
    items: Array.isArray(data?.items) ? data.items : []
  };
}

function isStaffScope(scope) {
  return scope.kind === 'CHANNEL_STAFF' || scope.kind === 'BIZ_STAFF';
}

function otherUserId(identity, identities) {
  return Object.values(identities)
    .map((candidate) => candidate?.user?.userId)
    .find((candidate) => candidate && candidate !== identity.user.userId) || '';
}

async function collectNegativeChecks(identity, identities, scope, ctx) {
  if (!isStaffScope(scope)) return [];
  const checks = [];
  const otherId = otherUserId(identity, identities);
  if (!otherId) {
    checks.push({ probe: 'cross_filter', required: true, status: 'NO_SAMPLE' });
  } else {
    const parameter = scope.kind === 'CHANNEL_STAFF' ? 'channelId' : 'recruiterId';
    const response = await requestJson(
      ctx.fetchImpl,
      apiUrl(ctx.backendUrl, `/performance?page=1&pageSize=1&${parameter}=${encodeURIComponent(otherId)}`),
      { headers: bearer(identity.token) }
    );
    checks.push({
      probe: 'cross_filter',
      required: true,
      status: isForbidden(response) ? 'PASS' : 'FAIL',
      httpStatus: response.status,
      apiCode: Number(response?.body?.code || 0)
    });
  }

  const outOfScopeRows = runPsql(buildOutOfScopeSql(scope), ctx);
  const orderId = outOfScopeRows[0]?.order_id;
  if (!orderId) {
    checks.push({ probe: 'out_of_scope_detail', required: true, status: 'NO_SAMPLE' });
  } else {
    const response = await requestJson(
      ctx.fetchImpl,
      apiUrl(ctx.backendUrl, `/performance/${encodeURIComponent(orderId)}`),
      { headers: bearer(identity.token) }
    );
    checks.push({
      probe: 'out_of_scope_detail',
      required: true,
      status: isForbidden(response) ? 'PASS' : 'FAIL',
      httpStatus: response.status,
      apiCode: Number(response?.body?.code || 0)
    });
  }
  return checks;
}

async function collectRoleEvidence(roleName, identity, identities, ctx) {
  const evidence = {
    roleName,
    username: identity.user.username,
    currentUser: identity.user,
    scopeKind: '',
    apiTotal: null,
    dbTotal: null,
    ownershipChecks: [],
    negativeChecks: [],
    errors: [],
    status: 'PENDING'
  };
  try {
    const scope = resolvePerformanceScope(identity.user);
    evidence.scopeKind = scope.kind;
    const page = normalizePage(await fetchData(
      ctx.fetchImpl,
      ctx.backendUrl,
      identity.token,
      '/performance?page=1&pageSize=100&sortBy=calculatedAt&sortOrder=desc'
    ));
    evidence.apiTotal = page.total;

    const countRows = runPsql(buildCountSql(scope), ctx);
    evidence.dbTotal = Number(countRows[0]?.total || 0);

    const orderIds = page.items.map((item) => item?.orderId).filter(Boolean);
    const ownershipRows = orderIds.length ? runPsql(buildOwnershipSql(orderIds), ctx) : [];
    const ownershipByOrderId = new Map(ownershipRows.map((row) => [String(row.order_id), row]));
    const memberRows = buildDeptMembersSql(scope) ? runPsql(buildDeptMembersSql(scope), ctx) : [];
    const memberIds = memberRows.map((row) => row.user_id);
    const missingOrderIds = [];
    const outOfScopeOrderIds = [];
    for (const orderId of orderIds) {
      const row = ownershipByOrderId.get(String(orderId));
      if (!row) {
        missingOrderIds.push(String(orderId));
      } else if (!isOwnershipInScope(row, scope, memberIds)) {
        outOfScopeOrderIds.push(String(orderId));
      }
    }
    evidence.ownershipChecks.push({
      checked: orderIds.length,
      missingCount: missingOrderIds.length,
      outOfScopeCount: outOfScopeOrderIds.length,
      sampleMissingOrderIds: missingOrderIds.slice(0, 5),
      sampleOutOfScopeOrderIds: outOfScopeOrderIds.slice(0, 5),
      pass: missingOrderIds.length === 0 && outOfScopeOrderIds.length === 0
    });

    evidence.negativeChecks = await collectNegativeChecks(identity, identities, scope, ctx);
    evidence.status = classifyRoleEvidence(evidence);
  } catch (error) {
    evidence.errors.push(error instanceof Error ? error.message : String(error));
    evidence.status = 'FAIL';
  }
  return evidence;
}

function buildReport(summary) {
  const lines = [
    '# real-pre performance access reconcile',
    '',
    `- generatedAt: ${summary.generatedAt}`,
    `- status: ${summary.status}`,
    `- backend: ${summary.backendUrl}`,
    `- realPreGuardPass: ${summary.realPreGuardPass}`,
    `- policy: ${summary.policy}`,
    '',
    '## Roles',
    ''
  ];
  for (const role of summary.roles || []) {
    lines.push(`- [${role.status}] ${role.roleName} (${role.username || '-'}) scope=${role.scopeKind || '-'}`);
    lines.push(`  - apiTotal=${role.apiTotal ?? '-'}, dbTotal=${role.dbTotal ?? '-'}`);
    for (const ownership of role.ownershipChecks || []) {
      lines.push(`  - ownership: checked=${ownership.checked}, missing=${ownership.missingCount}, outOfScope=${ownership.outOfScopeCount}, pass=${ownership.pass}`);
    }
    for (const check of role.negativeChecks || []) {
      lines.push(`  - negative ${check.probe}: ${check.status} (HTTP ${check.httpStatus ?? '-'})`);
    }
    for (const error of role.errors || []) lines.push(`  - error: ${error}`);
  }
  lines.push('', '## Policy', '');
  lines.push('- Authentication uses the existing login endpoint; performance business probes are GET-only.');
  lines.push('- No export, seed, backfill or recalculation endpoint is called.');
  lines.push('- PARTIAL_NO_POSITIVE_SAMPLE means API and SQL agree on zero rows but positive visibility is unproven.');
  lines.push('- PASS means current real-pre API facts matched independent SQL for the checked role and sample only.');
  return `${lines.join('\n')}\n`;
}

function ensureEvidenceDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

async function runRealPrePerformanceAccessReconcile(options = {}) {
  const root = options.root || ROOT;
  const env = options.env || process.env;
  const urls = applyRealPreEnv(env);
  const backendUrl = stripTrailingSlash(options.backendUrl || urls.backendUrl);
  const fetchImpl = options.fetchImpl || globalThis.fetch;
  if (typeof fetchImpl !== 'function') {
    throw new Error('global fetch is unavailable; use Node.js 18+');
  }
  const evidenceDir = options.evidenceDir
    ? ensureEvidenceDir(path.resolve(options.evidenceDir))
    : createEvidenceDir(root, SCRIPT_NAME);
  const roleCases = options.roles || loadRoleCases(root);
  const ctx = {
    env,
    backendUrl,
    fetchImpl,
    dbContainer: options.dbContainer || resolveRealPreDbContainer(env),
    dbUser: options.dbUser || env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER,
    dbName: options.dbName || env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME,
    spawnSyncImpl: options.spawnSyncImpl || spawnSync
  };
  const startedAt = new Date().toISOString();

  const health = await requestJson(fetchImpl, apiUrl(backendUrl, '/system/health'));
  const adminToken = await loginRole(fetchImpl, backendUrl, roleCases.admin);
  const envResponse = await requestJson(fetchImpl, apiUrl(backendUrl, '/system/env'), {
    headers: bearer(adminToken)
  });
  if (!envResponse.ok) throw new Error(`/system/env failed: HTTP ${envResponse.status}`);
  const systemEnv = normalizeSystemEnv(envResponse.body);
  const healthBody = unwrapApiBody(health.body) || health.body || {};
  const realPreGuardPass = health.ok && healthBody.status === 'UP' && isPassingRealPreEnv(systemEnv);
  if (!realPreGuardPass) {
    throw new Error('Refusing performance access reconcile outside guarded real-pre runtime');
  }

  const identities = {};
  const identityErrors = {};
  for (const [roleName, account] of Object.entries(roleCases)) {
    try {
      const token = roleName === 'admin' ? adminToken : await loginRole(fetchImpl, backendUrl, account);
      identities[roleName] = {
        token,
        user: normalizeCurrentUser(await fetchData(fetchImpl, backendUrl, token, '/users/current'))
      };
    } catch (error) {
      identityErrors[roleName] = error instanceof Error ? error.message : String(error);
    }
  }

  const roles = [];
  for (const [roleName, account] of Object.entries(roleCases)) {
    const identity = identities[roleName];
    if (!identity) {
      roles.push({
        roleName,
        username: account.username,
        currentUser: null,
        scopeKind: '',
        apiTotal: null,
        dbTotal: null,
        ownershipChecks: [],
        negativeChecks: [],
        errors: [identityErrors[roleName] || 'identity unavailable'],
        status: 'FAIL'
      });
      continue;
    }
    roles.push(await collectRoleEvidence(roleName, identity, identities, ctx));
  }

  const summary = redactSecretLikeKeys({
    evidenceType: SCRIPT_NAME,
    generatedAt: new Date().toISOString(),
    startedAt,
    finishedAt: new Date().toISOString(),
    backendUrl,
    database: { container: ctx.dbContainer, user: ctx.dbUser, name: ctx.dbName },
    health: { ok: health.ok, status: health.status, body: healthBody },
    systemEnv,
    realPreGuardPass,
    roles,
    status: summarizeStatus(roles),
    policy: 'read-only performance business probes; no export, seed, backfill or recalculation endpoints'
  });
  summary.ok = summary.status !== 'FAIL';
  writeJson(path.join(evidenceDir, 'summary.json'), summary);
  writeText(path.join(evidenceDir, 'report.md'), buildReport(summary));
  return { ...summary, evidenceDir };
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
  runRealPrePerformanceAccessReconcile({
    evidenceDir: args['evidence-dir'] ? path.resolve(args['evidence-dir']) : undefined
  }).then((summary) => {
    console.log(`real-pre performance access reconcile status=${summary.status} output=${summary.evidenceDir}`);
    if (summary.status === 'FAIL') process.exitCode = 1;
  }).catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exitCode = 1;
  });
}

module.exports = {
  buildCountSql,
  buildOutOfScopeSql,
  classifyRoleEvidence,
  isOwnershipInScope,
  isPassingRealPreEnv,
  normalizeCurrentUser,
  resolvePerformanceScope,
  runRealPrePerformanceAccessReconcile,
  summarizeStatus
};
