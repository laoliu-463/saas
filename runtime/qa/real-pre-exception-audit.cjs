const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const {
  DEFAULT_REAL_PRE_DB_CONTAINER,
  DEFAULT_REAL_PRE_DB_NAME,
  DEFAULT_REAL_PRE_DB_USER
} = require('./real-pre-env.cjs');

const SCRIPT_NAME = 'real-pre-exception-audit';
const ROOT = path.join(__dirname, '..', '..');
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://localhost:8081');
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);
const ADMIN_USERNAME = process.env.QA_ADMIN_USER || 'admin';
const ADMIN_PASSWORD = process.env.QA_ADMIN_PASSWORD || 'admin123';
const POSTGRES_CONTAINER = process.env.QA_POSTGRES_CONTAINER || process.env.E2E_DB_CONTAINER || DEFAULT_REAL_PRE_DB_CONTAINER;
const DB_USER = process.env.QA_DB_USER || process.env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER;
const DB_NAME = process.env.QA_DB_NAME || process.env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME;

function timestamp(date = new Date()) {
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`);
}

function writeText(filePath, value) {
  fs.writeFileSync(filePath, value);
}

function unwrapApiBody(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data;
  }
  return body;
}

async function fetchJson(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const started = Date.now();
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = { rawText: text.slice(0, 4000) };
    }
    return { url, status: response.status, ok: response.ok, durationMs: Date.now() - started, body };
  } finally {
    clearTimeout(timeout);
  }
}

function apiUrl(apiPath) {
  return `${API_BASE_URL}/api${apiPath.startsWith('/') ? apiPath : `/${apiPath}`}`;
}

async function apiRequest(method, apiPath, { token, body } = {}) {
  const url = apiUrl(apiPath);
  const result = await fetchJson(url, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body == null ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  });
  return { ...result, method, path: apiPath, businessCode: result.body?.code };
}

async function login() {
  const result = await apiRequest('POST', '/auth/login', {
    body: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD }
  });
  const token = unwrapApiBody(result.body)?.token;
  if (!result.ok || !token) {
    throw new Error(`login failed: HTTP ${result.status}, code=${result.businessCode ?? '-'}, body=${JSON.stringify(result.body)}`);
  }
  return token;
}

function normalizeEnv(raw) {
  const data = raw?.data && typeof raw.data === 'object' ? raw.data : raw || {};
  const activeProfiles = []
    .concat(data.activeProfiles || data.activeProfile || [])
    .filter(Boolean)
    .map((item) => String(item).toLowerCase());
  return {
    activeProfiles,
    environmentLabel: String(data.environmentLabel || ''),
    appTestEnabled: data.appTestEnabled === true || String(data.appTestEnabled).toLowerCase() === 'true',
    douyinTestEnabled: data.douyinTestEnabled === true || String(data.douyinTestEnabled).toLowerCase() === 'true',
    database: data.database || null
  };
}

function formatDateTime(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function sqlScalarRow(sql) {
  const child = spawnSync(
    'docker',
    [
      'exec',
      '-i',
      POSTGRES_CONTAINER,
      'psql',
      '-X',
      '-q',
      '-v',
      'ON_ERROR_STOP=1',
      '-U',
      DB_USER,
      '-d',
      DB_NAME,
      '-t',
      '-A',
      '-F',
      '|',
      '-c',
      sql
    ],
    { cwd: ROOT, encoding: 'utf8' }
  );
  if (child.status !== 0) {
    throw new Error(`sql failed: ${child.stderr || child.stdout}`);
  }
  return String(child.stdout || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)[0] || '';
}

function parsePipeRow(line, columns) {
  const parts = String(line || '').split('|');
  const out = {};
  for (let i = 0; i < columns.length; i += 1) {
    out[columns[i]] = parts[i] == null ? '' : parts[i];
  }
  return out;
}

function toInt(value) {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
}

function extractRecords(body) {
  const data = unwrapApiBody(body);
  if (Array.isArray(data)) return data;
  if (!data || typeof data !== 'object') return [];
  for (const key of ['records', 'list', 'rows', 'items', 'content', 'orders']) {
    if (Array.isArray(data[key])) return data[key];
  }
  return [];
}

function pushCase(results, item) {
  results.push(item);
}

function summarizeStatus(caseResults) {
  const counts = {};
  for (const item of caseResults) {
    counts[item.status] = (counts[item.status] || 0) + 1;
  }
  if (counts.FAIL) return { status: 'FAIL', statusCounts: counts };
  if (Object.keys(counts).some((key) => key !== 'PASS')) return { status: 'PARTIAL_PASS', statusCounts: counts };
  return { status: 'PASS', statusCounts: counts };
}

function buildReport(summary) {
  const lines = [];
  lines.push('# P3-8 real-pre exception audit');
  lines.push('');
  lines.push(`- generatedAt: ${summary.generatedAt}`);
  lines.push(`- status: ${summary.status}`);
  lines.push(`- environment: ${summary.environment.environmentLabel} / ${summary.environment.activeProfiles.join(',')} / appTestEnabled=${summary.environment.appTestEnabled} / douyinTestEnabled=${summary.environment.douyinTestEnabled}`);
  lines.push(`- corpus: totalOrders=${summary.corpus.totalOrders} / ordersWithPickSource=${summary.corpus.ordersWithPickSource} / unattributedOrders=${summary.corpus.unattributedOrders} / mappingNotFoundOrders=${summary.corpus.mappingNotFoundOrders}`);
  lines.push('');
  lines.push('## Cases');
  lines.push('');
  for (const item of summary.caseResults) {
    lines.push(`- [${item.status}] ${item.id} ${item.name}`);
    lines.push(`  - details: ${JSON.stringify(item.details)}`);
  }
  lines.push('');
  return `${lines.join('\n')}\n`;
}

async function main() {
  const outDir = path.join(OUT_ROOT, `${SCRIPT_NAME}-${timestamp()}`);
  ensureDir(outDir);

  const envResult = await apiRequest('GET', '/system/env');
  const env = normalizeEnv(envResult.body);
  if (!env.activeProfiles.includes('real-pre') || env.appTestEnabled || env.douyinTestEnabled) {
    throw new Error(`refusing to run outside real-pre guardrails: ${JSON.stringify(env)}`);
  }
  const healthResult = await apiRequest('GET', '/actuator/health');
  if (!healthResult.ok || healthResult.body?.status !== 'UP') {
    throw new Error(`/actuator/health is not UP: ${JSON.stringify(healthResult.body)}`);
  }
  const token = await login();
  const caseResults = [];

  const futureStart = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const futureEnd = new Date(futureStart.getTime() + 10 * 60 * 1000);
  const futureSync = await apiRequest('POST', '/orders/sync', {
    token,
    body: {
      startTime: formatDateTime(futureStart),
      endTime: formatDateTime(futureEnd)
    }
  });
  const futureSyncData = unwrapApiBody(futureSync.body) || {};
  pushCase(caseResults, {
    id: 'EXC-001',
    name: 'No-order sync succeeds',
    status: futureSync.ok && futureSync.businessCode === 200 && toInt(futureSyncData.failed) === 0 && toInt(futureSyncData.totalFetched) === 0 ? 'PASS' : 'FAIL',
    details: {
      startTime: formatDateTime(futureStart),
      endTime: formatDateTime(futureEnd),
      totalFetched: toInt(futureSyncData.totalFetched),
      created: toInt(futureSyncData.created),
      updated: toInt(futureSyncData.updated),
      failed: toInt(futureSyncData.failed)
    }
  });

  const dashboardSummaryResult = await apiRequest('GET', '/dashboard/summary', { token });
  const dashboardSummary = unwrapApiBody(dashboardSummaryResult.body) || {};
  const corpusRow = parsePipeRow(
    sqlScalarRow(`
SELECT
  COUNT(*)::text AS total_orders,
  COUNT(*) FILTER (WHERE COALESCE(pick_source, '') <> '')::text AS orders_with_pick_source,
  COUNT(*) FILTER (WHERE COALESCE(attribution_status, 'UNATTRIBUTED') = 'UNATTRIBUTED')::text AS unattributed_orders,
  COUNT(*) FILTER (WHERE COALESCE(attribution_remark, '') = 'COLONEL_MAPPING_NOT_FOUND')::text AS mapping_not_found_orders
FROM colonelsettlement_order
WHERE deleted = 0;
`.trim()),
    ['totalOrders', 'ordersWithPickSource', 'unattributedOrders', 'mappingNotFoundOrders']
  );
  pushCase(caseResults, {
    id: 'EXC-002',
    name: 'Orders without pick_source remain visible as unattributed',
    status:
      toInt(corpusRow.totalOrders) > 0 &&
      toInt(corpusRow.ordersWithPickSource) === 0 &&
      toInt(dashboardSummary.orderCount) === toInt(corpusRow.totalOrders) &&
      toInt(dashboardSummary.unattributedOrderCount) === toInt(corpusRow.unattributedOrders)
        ? 'PASS'
        : 'FAIL',
    details: {
      totalOrders: toInt(corpusRow.totalOrders),
      ordersWithPickSource: toInt(corpusRow.ordersWithPickSource),
      dashboardOrderCount: toInt(dashboardSummary.orderCount),
      dashboardUnattributedOrderCount: toInt(dashboardSummary.unattributedOrderCount)
    }
  });

  const mappingMissingStats = unwrapApiBody((await apiRequest('GET', '/orders/stats?unattributedReason=COLONEL_MAPPING_NOT_FOUND&timeField=settleTime', { token })).body) || {};
  const mappingMissingList = await apiRequest('GET', '/orders/unattributed?unattributedReason=COLONEL_MAPPING_NOT_FOUND&timeField=settleTime&page=1&size=5', { token });
  const mappingMissingRecords = extractRecords(mappingMissingList.body);
  const mappingDryRun = unwrapApiBody((await apiRequest('POST', '/orders/replay-attribution', {
    token,
    body: { reason: 'COLONEL_MAPPING_NOT_FOUND', limit: 20, dryRun: true }
  })).body) || {};
  pushCase(caseResults, {
    id: 'EXC-003',
    name: 'Missing-mapping orders are preserved and dry-run stays read-only',
    status:
      toInt(corpusRow.mappingNotFoundOrders) > 0 &&
      mappingMissingRecords.length > 0 &&
      mappingDryRun.dryRun === true &&
      toInt(mappingDryRun.scanned) > 0
        ? 'PASS'
        : 'FAIL',
    details: {
      dbMappingNotFoundOrders: toInt(corpusRow.mappingNotFoundOrders),
      statsTotalOrders: toInt(mappingMissingStats.totalOrders),
      sampleOrderIds: mappingMissingRecords.slice(0, 3).map((item) => item.orderId),
      dryRun: mappingDryRun.dryRun === true,
      dryRunScanned: toInt(mappingDryRun.scanned),
      dryRunAttributed: toInt(mappingDryRun.attributed),
      dryRunUnattributed: toInt(mappingDryRun.unattributed)
    }
  });

  const unsafeStats = unwrapApiBody((await apiRequest('GET', '/orders/stats?dashboardDiagnosis=UNSAFE_BECAUSE_CREATED_AFTER_ORDER&timeField=settleTime', { token })).body) || {};
  pushCase(caseResults, {
    id: 'EXC-004',
    name: 'Mapping created after order time',
    status: toInt(dashboardSummary.unsafeBecauseCreatedAfterOrderCount) > 0 ? 'PASS' : 'NO_LIVE_SAMPLE',
    details: {
      dashboardUnsafeCount: toInt(dashboardSummary.unsafeBecauseCreatedAfterOrderCount),
      statsTotalOrders: toInt(unsafeStats.totalOrders),
      note:
        toInt(dashboardSummary.unsafeBecauseCreatedAfterOrderCount) > 0
          ? 'Live unsafe orders exist and are visible through dashboard diagnosis.'
          : 'Current real-pre pool has no live unsafe sample; this branch remains zero-count only.'
    }
  });

  pushCase(caseResults, {
    id: 'EXC-005',
    name: 'Repeated promotion-link idempotency',
    status: 'DEFERRED_NO_SAFE_READ_ONLY_PATH',
    details: {
      note: 'A real-pre proof needs a controlled repeated promotion-link creation action, so the read-only audit defers it.',
      existingCoverage: 'runtime/qa/p1-warning-risk-regression.cjs PROMO-004'
    }
  });

  pushCase(caseResults, {
    id: 'EXC-006',
    name: 'SKU invalid/empty should not block the main flow',
    status: 'DEFERRED_NO_SAFE_READ_ONLY_PATH',
    details: {
      note: 'A live negative SKU probe would require a crafted upstream-facing error case, so the read-only audit defers it.',
      existingCoverage: 'runtime/qa/p1-warning-risk-regression.cjs PROMO-003 + runtime/qa/out/real-pre-e2e-20260517-090457-069/report.md'
    }
  });

  const summaryInfo = summarizeStatus(caseResults);
  const summary = {
    evidenceType: SCRIPT_NAME,
    generatedAt: new Date().toISOString(),
    evidenceDir: outDir,
    status: summaryInfo.status,
    environment: {
      activeProfiles: env.activeProfiles,
      environmentLabel: env.environmentLabel,
      appTestEnabled: env.appTestEnabled,
      douyinTestEnabled: env.douyinTestEnabled,
      database: env.database,
      healthStatus: healthResult.body?.status || null
    },
    dashboardSummary,
    corpus: {
      totalOrders: toInt(corpusRow.totalOrders),
      ordersWithPickSource: toInt(corpusRow.ordersWithPickSource),
      unattributedOrders: toInt(corpusRow.unattributedOrders),
      mappingNotFoundOrders: toInt(corpusRow.mappingNotFoundOrders)
    },
    statusCounts: summaryInfo.statusCounts,
    caseResults
  };

  writeJson(path.join(outDir, 'summary.json'), summary);
  writeText(path.join(outDir, 'report.md'), buildReport(summary));
  console.log(`real-pre exception audit output: ${outDir}`);
  if (summary.status === 'FAIL') {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error?.stack || error?.message || String(error));
  process.exitCode = 1;
});
