const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const SCRIPT_NAME = 'real-pre-pending-evidence-watch';
const ROOT = path.join(__dirname, '..', '..');
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://localhost:8080');
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);
const ADMIN_USERNAME = process.env.QA_ADMIN_USER || 'admin';
const ADMIN_PASSWORD = process.env.QA_ADMIN_PASSWORD || 'admin123';
const POSTGRES_CONTAINER = process.env.QA_POSTGRES_CONTAINER || 'saas-postgres';
const DB_USER = process.env.QA_DB_USER || 'saas';
const DB_NAME = process.env.QA_DB_NAME || 'saas_real_pre';
const SYNC_WINDOW_MINUTES = Number(process.env.QA_SYNC_WINDOW_MINUTES || 30);
const RAW_PROBE_WINDOW_DAYS = Number(process.env.QA_RAW_PROBE_WINDOW_DAYS || 3);
const RAW_PROBE_COUNT = Number(process.env.QA_RAW_PROBE_COUNT || 20);
const LOCAL_LOOKBACK_DAYS = Number(process.env.QA_LOCAL_LOOKBACK_DAYS || 30);
const CANDIDATE_LIMIT = Number(process.env.QA_CANDIDATE_LIMIT || 20);

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

function formatDateTime(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function toInt(value) {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
}

function parseDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function sqlLiteral(value) {
  if (value == null) return 'NULL';
  return `'${String(value).replace(/'/g, "''")}'`;
}

function sqlLines(sql) {
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
    .filter(Boolean);
}

function parsePipeRow(line, columns) {
  const parts = String(line || '').split('|');
  const out = {};
  for (let i = 0; i < columns.length; i += 1) {
    out[columns[i]] = parts[i] == null ? '' : parts[i];
  }
  return out;
}

function sqlRows(sql, columns) {
  return sqlLines(sql).map((line) => parsePipeRow(line, columns));
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
  const result = await fetchJson(apiUrl(apiPath), {
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

function extractRecords(body) {
  const data = unwrapApiBody(body);
  if (Array.isArray(data)) return data;
  if (!data || typeof data !== 'object') return [];
  for (const key of ['records', 'list', 'rows', 'items', 'content', 'orders']) {
    if (Array.isArray(data[key])) return data[key];
  }
  return [];
}

function analyzeMapping(order, mappings) {
  const orderCreateTime = parseDate(order.orderCreateTime);
  const exactMappings = mappings.filter((item) => item.activityId === order.activityId && item.productId === order.productId);
  const safeMappings = exactMappings.filter((item) => {
    const mappingTime = parseDate(item.mappingCreatedAt);
    return orderCreateTime && mappingTime && mappingTime.getTime() <= orderCreateTime.getTime();
  });
  const safeMappingsWithUser = safeMappings.filter((item) => item.userId);
  const expectedStatus = safeMappingsWithUser.length > 0 ? 'ATTRIBUTED' : 'UNATTRIBUTED';
  const actualStatus = String(order.attributionStatus || '');
  let reason = 'CHANNEL_NOT_FOUND';
  if (expectedStatus === 'ATTRIBUTED') {
    reason = 'SAFE_MAPPING_FOUND';
  } else if (mappings.length === 0) {
    reason = 'MAPPING_NOT_FOUND';
  } else if (exactMappings.length === 0) {
    reason = 'MAPPING_PRODUCT_ACTIVITY_MISMATCH';
  } else if (safeMappings.length === 0) {
    reason = 'MAPPING_CREATED_AFTER_ORDER';
  }
  return {
    expectedAttributionStatus: expectedStatus,
    actualAttributionStatus: actualStatus,
    statusMatchesExpectation: expectedStatus === actualStatus,
    reason,
    exactMappingCount: exactMappings.length,
    safeExactMappingCount: safeMappings.length,
    safeExactMappingWithUserCount: safeMappingsWithUser.length,
    winningMapping: safeMappingsWithUser.slice(0, 1)
  };
}

function getLocalCorpus(startTime) {
  return sqlRows(
    `
SELECT
  COUNT(*)::text AS total_orders,
  COUNT(*) FILTER (WHERE COALESCE(pick_source, '') <> '')::text AS orders_with_pick_source
FROM colonelsettlement_order
WHERE deleted = 0
  AND create_time >= ${sqlLiteral(formatDateTime(startTime))};
`.trim(),
    ['totalOrders', 'ordersWithPickSource']
  )[0] || { totalOrders: '0', ordersWithPickSource: '0' };
}

function getLocalOrdersWithPickSource(startTime, limit) {
  return sqlRows(
    `
SELECT
  order_id,
  COALESCE(product_id, '') AS product_id,
  COALESCE(colonel_activity_id, '') AS activity_id,
  COALESCE(pick_source, '') AS pick_source,
  COALESCE(create_time::text, '') AS order_create_time,
  COALESCE(settle_time::text, '') AS settle_time,
  COALESCE(order_amount, 0)::text AS order_amount,
  COALESCE(settle_colonel_commission, 0)::text AS service_fee,
  COALESCE(attribution_status, '') AS attribution_status,
  COALESCE(attribution_remark, '') AS unattributed_reason,
  COALESCE(channel_user_id::text, '') AS channel_id,
  COALESCE(colonel_buyin_id::text, '') AS colonel_buyin_id
FROM colonelsettlement_order
WHERE deleted = 0
  AND create_time >= ${sqlLiteral(formatDateTime(startTime))}
  AND COALESCE(pick_source, '') <> ''
ORDER BY create_time DESC
LIMIT ${Math.max(limit, 1)};
`.trim(),
    [
      'orderId',
      'productId',
      'activityId',
      'pickSource',
      'orderCreateTime',
      'settleTime',
      'orderAmount',
      'serviceFee',
      'attributionStatus',
      'unattributedReason',
      'channelId',
      'colonelBuyinId'
    ]
  );
}

function getMappingsForPickSource(pickSource) {
  if (!pickSource) return [];
  return sqlRows(
    `
SELECT
  COALESCE(activity_id, '') AS activity_id,
  COALESCE(product_id, '') AS product_id,
  COALESCE(user_id::text, '') AS user_id,
  COALESCE(create_time::text, '') AS mapping_created_at,
  COALESCE(source_type, '') AS mapping_source_type,
  COALESCE(colonel_buyin_id::text, '') AS colonel_buyin_id
FROM pick_source_mapping
WHERE deleted = 0
  AND pick_source = ${sqlLiteral(pickSource)}
ORDER BY create_time DESC;
`.trim(),
    ['activityId', 'productId', 'userId', 'mappingCreatedAt', 'mappingSourceType', 'colonelBuyinId']
  );
}

function getDuplicatePromotionCandidates() {
  return sqlRows(
    `
SELECT
  COALESCE(activity_id, '') AS activity_id,
  product_id,
  COALESCE(channel_user_id::text, '') AS channel_user_id,
  COALESCE(pick_extra, '') AS pick_extra,
  COUNT(*)::text AS promotion_link_count,
  COUNT(DISTINCT COALESCE(pick_source, ''))::text AS distinct_pick_source_count,
  COALESCE(MIN(created_at)::text, '') AS first_created_at,
  COALESCE(MAX(created_at)::text, '') AS last_created_at
FROM promotion_link
WHERE deleted = 0
GROUP BY activity_id, product_id, channel_user_id, pick_extra
HAVING COUNT(*) > 1
ORDER BY COUNT(*) DESC, MAX(created_at) DESC
LIMIT 10;
`.trim(),
    [
      'activityId',
      'productId',
      'channelUserId',
      'pickExtra',
      'promotionLinkCount',
      'distinctPickSourceCount',
      'firstCreatedAt',
      'lastCreatedAt'
    ]
  );
}

function getPromotionHistoryRows(candidate) {
  return sqlRows(
    `
SELECT
  id::text AS promotion_link_id,
  COALESCE(pick_source, '') AS pick_source,
  COALESCE(pick_extra, '') AS pick_extra,
  COALESCE(link_status, '') AS link_status,
  COALESCE(created_at::text, '') AS created_at
FROM promotion_link
WHERE deleted = 0
  AND COALESCE(activity_id, '') = ${sqlLiteral(candidate.activityId)}
  AND product_id = ${sqlLiteral(candidate.productId)}
  AND COALESCE(channel_user_id::text, '') = ${sqlLiteral(candidate.channelUserId)}
  AND COALESCE(pick_extra, '') = ${sqlLiteral(candidate.pickExtra)}
ORDER BY created_at DESC
LIMIT 10;
`.trim(),
    ['promotionLinkId', 'pickSource', 'pickExtra', 'linkStatus', 'createdAt']
  );
}

function getPromotionMappingSummary(candidate) {
  return sqlRows(
    `
SELECT
  COUNT(*)::text AS mapping_count,
  COUNT(DISTINCT COALESCE(pick_source, ''))::text AS distinct_mapping_pick_source_count,
  COALESCE(MAX(update_time)::text, '') AS mapping_last_update_time
FROM pick_source_mapping
WHERE deleted = 0
  AND COALESCE(activity_id, '') = ${sqlLiteral(candidate.activityId)}
  AND product_id = ${sqlLiteral(candidate.productId)}
  AND COALESCE(user_id::text, '') = ${sqlLiteral(candidate.channelUserId)}
  AND COALESCE(pick_extra, '') = ${sqlLiteral(candidate.pickExtra)};
`.trim(),
    ['mappingCount', 'distinctMappingPickSourceCount', 'mappingLastUpdateTime']
  )[0] || { mappingCount: '0', distinctMappingPickSourceCount: '0', mappingLastUpdateTime: '' };
}

function getPromotionOperationRows(candidate) {
  return sqlRows(
    `
SELECT
  operation_type,
  COALESCE(success::text, '') AS success,
  COALESCE(operation_remark, '') AS operation_remark,
  COALESCE(create_time::text, '') AS create_time
FROM product_operation_log
WHERE deleted = 0
  AND activity_id = ${sqlLiteral(candidate.activityId)}
  AND product_id = ${sqlLiteral(candidate.productId)}
  AND operation_type = 'PROMOTION_LINK'
ORDER BY create_time DESC
LIMIT 10;
`.trim(),
    ['operationType', 'success', 'operationRemark', 'createTime']
  );
}

function getSkuHistoricalRows() {
  return {
    productOperationRows: sqlRows(
      `
SELECT
  id::text AS id,
  activity_id,
  product_id,
  operation_type,
  COALESCE(success::text, '') AS success,
  COALESCE(error_message, '') AS error_message,
  COALESCE(operation_remark, '') AS operation_remark,
  COALESCE(create_time::text, '') AS create_time
FROM product_operation_log
WHERE deleted = 0
  AND (
    operation_type ILIKE '%SKU%'
    OR COALESCE(error_message, '') ILIKE '%SKU%'
    OR COALESCE(operation_remark, '') ILIKE '%SKU%'
  )
ORDER BY create_time DESC
LIMIT 20;
`.trim(),
      ['id', 'activityId', 'productId', 'operationType', 'success', 'errorMessage', 'operationRemark', 'createTime']
    ),
    operationLogRows: sqlRows(
      `
SELECT
  id::text AS id,
  COALESCE(module, '') AS module,
  COALESCE(action, '') AS action,
  COALESCE(target_id, '') AS target_id,
  COALESCE(error_message, '') AS error_message,
  COALESCE(create_time::text, '') AS create_time
FROM operation_log
WHERE deleted = 0
  AND (
    action ILIKE '%SKU%'
    OR COALESCE(request_url, '') ILIKE '%sku%'
    OR COALESCE(content, '') ILIKE '%SKU%'
    OR COALESCE(error_message, '') ILIKE '%SKU%'
    OR COALESCE(request_body::text, '') ILIKE '%productSkus%'
    OR COALESCE(response_body::text, '') ILIKE '%productSkus%'
    OR COALESCE(response_body::text, '') ILIKE '%skus%'
  )
ORDER BY create_time DESC
LIMIT 20;
`.trim(),
      ['id', 'module', 'action', 'targetId', 'errorMessage', 'createTime']
    )
  };
}

function buildReport(summary) {
  const lines = [];
  lines.push('# P3 pending evidence watch');
  lines.push('');
  lines.push(`- status: ${summary.status}`);
  lines.push(`- generatedAt: ${summary.generatedAt}`);
  lines.push(`- environment: ${summary.environment.environmentLabel} / ${summary.environment.activeProfiles.join(',')} / appTestEnabled=${summary.environment.appTestEnabled} / douyinTestEnabled=${summary.environment.douyinTestEnabled}`);
  lines.push('');
  lines.push('## Snapshot');
  lines.push('');
  lines.push(`- P3-6 pick_source watch: ${summary.pendingEvidence.p3_6.status}`);
  lines.push(`- EXC-004 live unsafe sample: ${summary.pendingEvidence.exc004.status}`);
  lines.push(`- EXC-005 duplicate promotion read-only candidate: ${summary.pendingEvidence.exc005.status}`);
  lines.push(`- EXC-006 SKU abnormal history sample: ${summary.pendingEvidence.exc006.status}`);
  lines.push('');
  lines.push('## Details');
  lines.push('');
  lines.push(`- P3-6 sync: totalFetched=${summary.pendingEvidence.p3_6.sync.totalFetched} / created=${summary.pendingEvidence.p3_6.sync.created} / updated=${summary.pendingEvidence.p3_6.sync.updated} / failed=${summary.pendingEvidence.p3_6.sync.failed}`);
  lines.push(`- P3-6 raw probe: orderCount=${summary.pendingEvidence.p3_6.rawProbe.orderCount} / ordersWithPickSource=${summary.pendingEvidence.p3_6.rawProbe.ordersWithPickSource}`);
  lines.push(`- P3-6 local corpus: totalOrders=${summary.pendingEvidence.p3_6.localCorpus.totalOrders} / ordersWithPickSource=${summary.pendingEvidence.p3_6.localCorpus.ordersWithPickSource}`);
  lines.push(`- EXC-004 dashboardUnsafeCount=${summary.pendingEvidence.exc004.dashboardUnsafeCount} / statsTotalOrders=${summary.pendingEvidence.exc004.statsTotalOrders}`);
  lines.push(`- EXC-005 duplicate candidate groups=${summary.pendingEvidence.exc005.duplicateGroupCount}`);
  lines.push(`- EXC-006 sku historical rows=${summary.pendingEvidence.exc006.historicalRowCount}`);
  lines.push('');
  lines.push('## Conclusion');
  lines.push('');
  for (const line of summary.conclusion) {
    lines.push(`- ${line}`);
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
  const dashboardSummaryResult = await apiRequest('GET', '/dashboard/summary', { token });
  const dashboardSummary = unwrapApiBody(dashboardSummaryResult.body) || {};
  writeJson(path.join(outDir, 'dashboard-summary.json'), dashboardSummaryResult.body);

  const syncEnd = new Date();
  const syncStart = new Date(syncEnd.getTime() - SYNC_WINDOW_MINUTES * 60 * 1000);
  const rawProbeStart = new Date(syncEnd.getTime() - RAW_PROBE_WINDOW_DAYS * 24 * 60 * 60 * 1000);
  const localLookbackStart = new Date(syncEnd.getTime() - LOCAL_LOOKBACK_DAYS * 24 * 60 * 60 * 1000);

  const syncResult = await apiRequest('POST', '/orders/sync', {
    token,
    body: {
      startTime: formatDateTime(syncStart),
      endTime: formatDateTime(syncEnd)
    }
  });
  writeJson(path.join(outDir, 'order-sync.json'), syncResult.body);
  const syncData = unwrapApiBody(syncResult.body) || {};

  const rawProbeResult = await apiRequest('POST', '/douyin/order-sync-probes/raw', {
    token,
    body: {
      start_time: formatDateTime(rawProbeStart),
      end_time: formatDateTime(syncEnd),
      count: RAW_PROBE_COUNT,
      cursor: '0'
    }
  });
  writeJson(path.join(outDir, 'raw-probe.json'), rawProbeResult.body);
  const rawOrders = (((rawProbeResult.body || {}).data || {}).remoteResponse || {}).data?.orders || [];
  const rawOrdersWithPickSource = rawOrders.filter((item) => item && item.pick_source);

  const localCorpus = getLocalCorpus(localLookbackStart);
  const localOrdersWithPickSource = getLocalOrdersWithPickSource(localLookbackStart, CANDIDATE_LIMIT);
  const mappingChecks = localOrdersWithPickSource.map((order) => {
    const mappings = getMappingsForPickSource(order.pickSource);
    return {
      orderId: order.orderId,
      productId: order.productId,
      activityId: order.activityId,
      pickSource: order.pickSource,
      orderCreateTime: order.orderCreateTime,
      attributionStatus: order.attributionStatus,
      unattributedReason: order.unattributedReason,
      analysis: analyzeMapping(order, mappings),
      mappings
    };
  });
  const verifiedOrders = mappingChecks.filter((item) => item.analysis.expectedAttributionStatus === 'ATTRIBUTED' && item.analysis.statusMatchesExpectation);
  writeJson(path.join(outDir, 'pick-source-watch.json'), {
    syncWindowMinutes: SYNC_WINDOW_MINUTES,
    rawProbeWindowDays: RAW_PROBE_WINDOW_DAYS,
    localLookbackDays: LOCAL_LOOKBACK_DAYS,
    rawOrdersWithPickSource: rawOrdersWithPickSource.slice(0, 10),
    localCorpus,
    localOrdersWithPickSource,
    mappingChecks
  });

  let p36Status = 'SYNC_OK_NO_SAMPLE';
  if (!syncResult.ok || syncResult.businessCode !== 200 || toInt(syncData.failed) !== 0) {
    p36Status = 'SYNC_FAILED';
  } else if (verifiedOrders.length > 0) {
    p36Status = 'PICK_SOURCE_SAMPLE_VERIFIED';
  } else if (rawOrdersWithPickSource.length > 0 || localOrdersWithPickSource.length > 0) {
    p36Status = 'PICK_SOURCE_SAMPLE_FOUND_UNVERIFIED';
  }

  const unsafeStatsResult = await apiRequest('GET', '/orders/stats?dashboardDiagnosis=UNSAFE_BECAUSE_CREATED_AFTER_ORDER&timeField=settleTime', { token });
  const unsafeStats = unwrapApiBody(unsafeStatsResult.body) || {};
  const unsafeListResult = await apiRequest('GET', '/orders/unattributed?dashboardDiagnosis=UNSAFE_BECAUSE_CREATED_AFTER_ORDER&timeField=settleTime&page=1&size=5', { token });
  const unsafeRecords = extractRecords(unsafeListResult.body);
  writeJson(path.join(outDir, 'unsafe-orders.json'), {
    stats: unsafeStatsResult.body,
    list: unsafeListResult.body
  });
  const exc004Status = toInt(dashboardSummary.unsafeBecauseCreatedAfterOrderCount) > 0 || toInt(unsafeStats.totalOrders) > 0
    ? 'LIVE_SAMPLE_FOUND'
    : 'NO_LIVE_SAMPLE';

  const duplicateCandidates = getDuplicatePromotionCandidates().map((candidate) => {
    const mappingSummary = getPromotionMappingSummary(candidate);
    const promotionHistory = getPromotionHistoryRows(candidate);
    const operationRows = getPromotionOperationRows(candidate);
    return {
      ...candidate,
      mappingSummary: {
        mappingCount: toInt(mappingSummary.mappingCount),
        distinctMappingPickSourceCount: toInt(mappingSummary.distinctMappingPickSourceCount),
        mappingLastUpdateTime: mappingSummary.mappingLastUpdateTime
      },
      promotionHistory,
      operationRows
    };
  });
  writeJson(path.join(outDir, 'duplicate-promotion-candidates.json'), duplicateCandidates);
  const exc005Status = duplicateCandidates.length > 0 ? 'READ_ONLY_CANDIDATE_FOUND' : 'NO_HISTORICAL_CANDIDATE';

  const skuHistory = getSkuHistoricalRows();
  writeJson(path.join(outDir, 'sku-history-probes.json'), skuHistory);
  const exc006HistoricalRowCount = skuHistory.productOperationRows.length + skuHistory.operationLogRows.length;
  const exc006Status = exc006HistoricalRowCount > 0 ? 'HISTORICAL_SAMPLE_FOUND' : 'NO_HISTORICAL_SAMPLE';

  const summary = {
    evidenceType: SCRIPT_NAME,
    generatedAt: new Date().toISOString(),
    evidenceDir: outDir,
    status: 'WATCH_COMPLETED',
    environment: {
      activeProfiles: env.activeProfiles,
      environmentLabel: env.environmentLabel,
      appTestEnabled: env.appTestEnabled,
      douyinTestEnabled: env.douyinTestEnabled,
      database: env.database,
      healthStatus: healthResult.body?.status || null
    },
    dashboardSummary,
    pendingEvidence: {
      p3_6: {
        status: p36Status,
        sync: {
          startTime: formatDateTime(syncStart),
          endTime: formatDateTime(syncEnd),
          totalFetched: toInt(syncData.totalFetched),
          created: toInt(syncData.created),
          updated: toInt(syncData.updated),
          attributed: toInt(syncData.attributed),
          unattributed: toInt(syncData.unattributed),
          failed: toInt(syncData.failed)
        },
        rawProbe: {
          windowDays: RAW_PROBE_WINDOW_DAYS,
          orderCount: rawOrders.length,
          ordersWithPickSource: rawOrdersWithPickSource.length
        },
        localCorpus: {
          lookbackDays: LOCAL_LOOKBACK_DAYS,
          totalOrders: toInt(localCorpus.totalOrders),
          ordersWithPickSource: toInt(localCorpus.ordersWithPickSource)
        },
        verifiedOrderCount: verifiedOrders.length,
        verifiedOrderIds: verifiedOrders.slice(0, 5).map((item) => item.orderId),
        sampleOrderIds: localOrdersWithPickSource.slice(0, 5).map((item) => item.orderId)
      },
      exc004: {
        status: exc004Status,
        dashboardUnsafeCount: toInt(dashboardSummary.unsafeBecauseCreatedAfterOrderCount),
        statsTotalOrders: toInt(unsafeStats.totalOrders),
        sampleOrderIds: unsafeRecords.slice(0, 5).map((item) => item.orderId).filter(Boolean)
      },
      exc005: {
        status: exc005Status,
        duplicateGroupCount: duplicateCandidates.length,
        candidates: duplicateCandidates.slice(0, 3).map((item) => ({
          activityId: item.activityId,
          productId: item.productId,
          channelUserId: item.channelUserId,
          pickExtra: item.pickExtra,
          promotionLinkCount: toInt(item.promotionLinkCount),
          distinctPickSourceCount: toInt(item.distinctPickSourceCount),
          mappingCount: item.mappingSummary.mappingCount,
          distinctMappingPickSourceCount: item.mappingSummary.distinctMappingPickSourceCount,
          firstCreatedAt: item.firstCreatedAt,
          lastCreatedAt: item.lastCreatedAt,
          latestOperationRemark: item.operationRows[0]?.operationRemark || ''
        }))
      },
      exc006: {
        status: exc006Status,
        historicalRowCount: exc006HistoricalRowCount,
        productOperationRowCount: skuHistory.productOperationRows.length,
        operationLogRowCount: skuHistory.operationLogRows.length,
        samples: {
          productOperationRows: skuHistory.productOperationRows.slice(0, 5),
          operationLogRows: skuHistory.operationLogRows.slice(0, 5)
        },
        existingCoverage: 'runtime/qa/p1-warning-risk-regression.cjs PROMO-003 + runtime/qa/out/real-pre-e2e-20260517-090457-069/report.md'
      }
    },
    conclusion: [
      p36Status === 'SYNC_OK_NO_SAMPLE'
        ? 'P3-6 is still waiting for a real pick_source order sample.'
        : `P3-6 now reports ${p36Status}.`,
      exc004Status === 'NO_LIVE_SAMPLE'
        ? 'EXC-004 still has no live unsafe sample in real-pre.'
        : 'EXC-004 now has a live unsafe sample to promote.',
      exc005Status === 'READ_ONLY_CANDIDATE_FOUND'
        ? 'EXC-005 has at least one historical duplicate promotion-link candidate for safe read-only design.'
        : 'EXC-005 still has no historical duplicate promotion-link candidate in real-pre.',
      exc006Status === 'HISTORICAL_SAMPLE_FOUND'
        ? 'EXC-006 has historical SKU-related rows to inspect before designing a safer proof.'
        : 'EXC-006 still has no historical SKU abnormal sample in real-pre, so it should remain observation-only.'
    ]
  };

  writeJson(path.join(outDir, 'summary.json'), summary);
  writeText(path.join(outDir, 'report.md'), buildReport(summary));
  console.log(`real-pre pending evidence watch output: ${outDir}`);
  if (p36Status === 'SYNC_FAILED') {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error?.stack || error?.message || String(error));
  process.exitCode = 1;
});
