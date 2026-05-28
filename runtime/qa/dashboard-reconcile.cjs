const fs = require('node:fs');
const path = require('node:path');
const { execFile } = require('node:child_process');
const { execFileSync } = require('node:child_process');
const { promisify } = require('node:util');

const execFileAsync = promisify(execFile);

const SCRIPT_NAME = 'dashboard-reconcile';
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://127.0.0.1:8080');
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);
const DB_CONTAINER = selectDbContainer();
const DB_USER = process.env.DB_USER || 'saas';
let DB_NAME = process.env.QA_DB_NAME || process.env.DB_NAME || 'colonel_saas_test';

function timestamp(date = new Date()) {
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}-${pad(date.getMilliseconds(), 3)}`;
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

function detectComposeServiceContainer(project, service) {
  try {
    const stdout = execFileSync(
      'docker',
      [
        'ps',
        '--filter',
        `label=com.docker.compose.project=${project}`,
        '--filter',
        `label=com.docker.compose.service=${service}`,
        '--format',
        '{{.Names}}'
      ],
      { encoding: 'utf8' }
    );
    return stdout.split(/\r?\n/).find((line) => line.trim()) || '';
  } catch {
    return '';
  }
}

function selectDbContainer(env = process.env, detectedContainer = detectComposeServiceContainer(env.QA_COMPOSE_PROJECT || 'saas-test', 'postgres')) {
  return env.QA_DB_CONTAINER || detectedContainer || 'saas-test-postgres-1';
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function unwrapApiBody(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data;
  }
  return body;
}

function extractRecords(body) {
  const data = unwrapApiBody(body);
  if (Array.isArray(data)) return data;
  if (!data || typeof data !== 'object') return [];
  for (const key of ['records', 'list', 'rows', 'items', 'content', 'orderList', 'orders']) {
    if (Array.isArray(data[key])) return data[key];
  }
  return [];
}

function toNumber(value) {
  if (value == null || value === '') return 0;
  const num = Number(String(value).replace(/[^\d.-]/g, ''));
  return Number.isFinite(num) ? num : 0;
}

function centToYuan(value) {
  return Math.round((toNumber(value) / 100) * 100) / 100;
}

function money(value) {
  return Math.round(toNumber(value) * 100) / 100;
}

function incBucket(map, key, order) {
  const name = key || '(未归属)';
  if (!map[name]) map[name] = { orderCount: 0, gmv: 0, serviceFee: 0 };
  map[name].orderCount += 1;
  map[name].gmv = money(map[name].gmv + centToYuan(order.orderAmount ?? order.orderAmountCent ?? order.gmvCent));
  map[name].serviceFee = money(map[name].serviceFee + centToYuan(order.settleColonelCommission ?? order.serviceFeeCent ?? order.serviceFeeIncomeCent));
}

function aggregateOrders(orders) {
  const aggregate = {
    orderCount: 0,
    gmv: 0,
    serviceFee: 0,
    attributed: 0,
    unattributed: 0,
    productUncovered: 0,
    refundClosed: 0,
    byBiz: {},
    byChannel: {}
  };
  for (const order of orders || []) {
    aggregate.orderCount += 1;
    aggregate.gmv = money(aggregate.gmv + centToYuan(order.orderAmount ?? order.orderAmountCent ?? order.totalAmount));
    aggregate.serviceFee = money(aggregate.serviceFee + centToYuan(order.settleColonelCommission ?? order.serviceFeeIncome ?? order.serviceFee));
    const status = String(order.attributionStatus || order.attribution_status || '').toUpperCase();
    const reason = String(order.attributionRemark || order.unattributedReason || order.reason || '').toUpperCase();
    if (status === 'ATTRIBUTED') aggregate.attributed += 1;
    if (status !== 'ATTRIBUTED') aggregate.unattributed += 1;
    if (reason.includes('PRODUCT_NOT_FOUND') || reason.includes('UPSTREAM_PRODUCT_UNCOVERED') || reason.includes('PRODUCT_UNCOVERED')) {
      aggregate.productUncovered += 1;
    }
    const orderStatus = String(order.orderStatus ?? order.order_status ?? '').toUpperCase();
    if (orderStatus === '4' || orderStatus.includes('CANCEL') || orderStatus.includes('CLOSED') || reason.includes('REFUND')) {
      aggregate.refundClosed += 1;
    }
    if (order.colonelUserName || order.colonel_user_name) incBucket(aggregate.byBiz, order.colonelUserName || order.colonel_user_name, order);
    if (order.channelUserName || order.channel_user_name) incBucket(aggregate.byChannel, order.channelUserName || order.channel_user_name, order);
  }
  return aggregate;
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

function parsePsqlTsv(text) {
  const lines = String(text || '').split(/\r?\n/).filter((line) => line.trim() !== '');
  if (!lines.length) return [];
  const headers = lines[0].split('\t');
  return lines.slice(1).map((line) => {
    const values = line.split('\t');
    return Object.fromEntries(headers.map((header, index) => [header, values[index] ?? '']));
  });
}

function buildReconcileSummary(metricResults) {
  const failedMetrics = metricResults.filter((item) => !item.pass);
  return {
    totalMetrics: metricResults.length,
    passedMetrics: metricResults.length - failedMetrics.length,
    failedMetrics,
    overallPass: failedMetrics.length === 0
  };
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
      body = { rawText: text.slice(0, 2000) };
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
  try {
    const result = await fetchJson(url, {
      method,
      body: body == null ? undefined : JSON.stringify(body),
      headers: {
        Accept: 'application/json',
        ...(body == null ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
    });
    return { ...result, method, path: apiPath, businessCode: result.body?.code };
  } catch (error) {
    return { method, path: apiPath, url, status: 0, ok: false, body: null, durationMs: 0, error: error?.message || String(error) };
  }
}

function normalizeEnv(raw) {
  const data = raw?.data && typeof raw.data === 'object' ? raw.data : raw || {};
  const activeProfiles = []
    .concat(data.activeProfiles || data.activeProfile || data.profile || data.profiles || [])
    .filter(Boolean)
    .map((item) => String(item).toLowerCase());
  const environmentLabel = String(data.environmentLabel || data.environment || data.env || '').trim();
  const profile = String(data.profile || data.activeProfile || activeProfiles[0] || '').toLowerCase();
  const appTestEnabled = data.appTestEnabled === true || String(data.appTestEnabled).toLowerCase() === 'true';
  const douyinTestEnabled = data.douyinTestEnabled === true || String(data.douyinTestEnabled).toLowerCase() === 'true';
  const isRealPre = environmentLabel.toLowerCase() === 'real-pre' || profile === 'real-pre' || activeProfiles.includes('real-pre');
  const isTest = !isRealPre && (
    environmentLabel.toLowerCase() === 'test' ||
    profile === 'test' ||
    activeProfiles.includes('test') ||
    appTestEnabled ||
    douyinTestEnabled
  );
  return { environmentLabel, profile, activeProfiles, appTestEnabled, douyinTestEnabled, database: data.database || null, raw, isRealPre, isTest };
}

async function ensureTestEnvironment(ctx) {
  const envResult = await apiRequest('GET', '/system/env');
  if (!envResult.ok) throw new Error(`/api/system/env failed with HTTP ${envResult.status}`);
  ctx.environment = normalizeEnv(envResult.body);
  if (!ctx.environment.isTest) throw new Error(`Refusing to run outside TEST/mock environment: ${JSON.stringify(ctx.environment)}`);
  const health = await apiRequest('GET', '/system/health');
  ctx.health = health.body;
  if (!health.ok || health.body?.status !== 'UP') throw new Error('/api/system/health is not UP');
}

async function loginAdmin() {
  const result = await apiRequest('POST', '/auth/login', {
    body: { username: process.env.QA_ADMIN_USER || 'admin', password: process.env.QA_ADMIN_PASSWORD || 'admin123' }
  });
  const data = unwrapApiBody(result.body);
  const token = data?.token || data?.accessToken;
  if (!result.ok || !token) throw new Error(`admin login failed: HTTP ${result.status}`);
  return token;
}

async function seed(token) {
  const result = await apiRequest('POST', '/test/seed', { token });
  if (!result.ok || Number(result.body?.code) >= 400) throw new Error(`/api/test/seed failed: HTTP ${result.status}`);
}

async function fetchAllOrders(token) {
  const all = [];
  let page = 1;
  const size = 200;
  while (page <= 20) {
    const result = await apiRequest('GET', `/orders?page=${page}&size=${size}&timeField=createTime`, { token });
    if (!result.ok || Number(result.body?.code) >= 400) {
      throw new Error(`/api/orders failed: HTTP ${result.status}, code=${result.body?.code ?? '-'}`);
    }
    const records = extractRecords(result.body);
    all.push(...records);
    const total = Number(unwrapApiBody(result.body)?.total || 0);
    if (!records.length || all.length >= total) break;
    page += 1;
  }
  return all;
}

function selectMetricTrack(metrics, track) {
  if (metrics && typeof metrics === 'object' && metrics[track] && typeof metrics[track] === 'object') {
    return metrics[track];
  }
  return metrics || {};
}

function reasonCount(summary, reasons) {
  const wanted = new Set([].concat(reasons).map((reason) => String(reason).toUpperCase()));
  return (summary.unattributedReasons || []).reduce((total, item) => {
    const reason = String(item.reason || '').toUpperCase();
    return wanted.has(reason) ? total + toNumber(item.count) : total;
  }, 0);
}

async function runPsql(sql) {
  const args = [
    'exec',
    DB_CONTAINER,
    'psql',
    '-U',
    DB_USER,
    '-d',
    DB_NAME,
    '-X',
    '-A',
    '-F',
    '\t',
    '-P',
    'footer=off',
    '-c',
    sql
  ];
  const { stdout } = await execFileAsync('docker', args, { maxBuffer: 1024 * 1024 * 5 });
  return parsePsqlTsv(stdout);
}

async function collectDbAggregates() {
  const orderFactRows = await runPsql(`
    SELECT
      COUNT(*) AS order_count,
      COALESCE(SUM(order_amount), 0) AS gmv_cent,
      COALESCE(SUM(settle_colonel_commission), 0) AS service_fee_cent,
      COALESCE(SUM(CASE WHEN attribution_status = 'ATTRIBUTED' THEN 1 ELSE 0 END), 0) AS attributed,
      COALESCE(SUM(CASE WHEN COALESCE(attribution_status, 'UNATTRIBUTED') <> 'ATTRIBUTED' THEN 1 ELSE 0 END), 0) AS unattributed,
      COALESCE(SUM(CASE WHEN attribution_remark IN ('PRODUCT_NOT_FOUND', 'UPSTREAM_PRODUCT_UNCOVERED', 'PRODUCT_UNCOVERED') THEN 1 ELSE 0 END), 0) AS product_uncovered,
      COALESCE(SUM(CASE WHEN order_status = 4 OR attribution_remark = 'REFUNDED_OR_CLOSED' THEN 1 ELSE 0 END), 0) AS refund_closed
    FROM colonelsettlement_order
    WHERE deleted = 0;
  `);
  const summaryRows = await runPsql(`
    SELECT
      COUNT(*) AS order_count,
      COALESCE(SUM(pr.settle_amount), 0) AS gmv_cent,
      COALESCE(SUM(pr.effective_service_fee), 0) AS service_fee_cent
    FROM performance_records pr
    JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
    WHERE pr.is_valid = TRUE;
  `);
  const todayEstimateRows = await runPsql(`
    SELECT
      COUNT(*) AS order_count,
      COALESCE(SUM(pr.pay_amount), 0) AS gmv_cent,
      COALESCE(SUM(pr.estimate_service_fee), 0) AS service_fee_cent
    FROM performance_records pr
    JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
    WHERE pr.is_valid = TRUE
      AND co.create_time >= CURRENT_DATE
      AND co.create_time < CURRENT_DATE + INTERVAL '1 day';
  `);
  const todaySettleRows = await runPsql(`
    SELECT
      COUNT(*) AS order_count,
      COALESCE(SUM(pr.settle_amount), 0) AS gmv_cent,
      COALESCE(SUM(pr.effective_service_fee), 0) AS service_fee_cent
    FROM performance_records pr
    JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
    WHERE pr.is_valid = TRUE
      AND co.settle_time IS NOT NULL
      AND co.settle_time >= CURRENT_DATE
      AND co.settle_time < CURRENT_DATE + INTERVAL '1 day';
  `);
  const bizRows = await runPsql(`
    SELECT
      COALESCE(MAX(co.colonel_user_name), '(未归属)') AS name,
      COUNT(*) AS order_count,
      COALESCE(SUM(pr.settle_amount), 0) AS gmv_cent,
      COALESCE(SUM(pr.effective_service_fee), 0) AS service_fee_cent
    FROM performance_records pr
    JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
    WHERE pr.is_valid = TRUE
      AND co.attribution_status = 'ATTRIBUTED'
      AND pr.final_recruiter_user_id IS NOT NULL
    GROUP BY pr.final_recruiter_user_id
    ORDER BY COUNT(*) DESC, COALESCE(SUM(pr.settle_amount), 0) DESC
    LIMIT 10;
  `);
  const channelRows = await runPsql(`
    SELECT
      COALESCE(MAX(co.channel_user_name), '(未归属)') AS name,
      COUNT(*) AS order_count,
      COALESCE(SUM(pr.settle_amount), 0) AS gmv_cent,
      COALESCE(SUM(pr.effective_service_fee), 0) AS service_fee_cent
    FROM performance_records pr
    JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
    WHERE pr.is_valid = TRUE
      AND co.attribution_status = 'ATTRIBUTED'
      AND pr.final_channel_user_id IS NOT NULL
    GROUP BY pr.final_channel_user_id
    ORDER BY COUNT(*) DESC, COALESCE(SUM(pr.settle_amount), 0) DESC
    LIMIT 10;
  `);
  return {
    orderFacts: orderFactRows[0] || {},
    summary: summaryRows[0] || {},
    todayEstimate: todayEstimateRows[0] || {},
    todaySettle: todaySettleRows[0] || {},
    byBiz: Object.fromEntries(bizRows.map((row) => [row.name, row])),
    byChannel: Object.fromEntries(channelRows.map((row) => [row.name, row]))
  };
}

function rowsToYuanAggregate(row) {
  return {
    orderCount: toNumber(row.order_count),
    gmv: centToYuan(row.gmv_cent),
    serviceFee: centToYuan(row.service_fee_cent),
    attributed: toNumber(row.attributed),
    unattributed: toNumber(row.unattributed),
    productUncovered: toNumber(row.product_uncovered),
    refundClosed: toNumber(row.refund_closed)
  };
}

function comparePerformance(prefix, apiItems, dbMap, nameKey) {
  const results = [];
  for (const item of apiItems || []) {
    const name = item[nameKey] || '(未归属)';
    const db = dbMap[name];
    if (!db) {
      results.push({ name: `${prefix}:${name}:exists`, apiValue: 1, dbValue: 0, tolerance: 0, diff: 1, pass: false });
      continue;
    }
    results.push(compareMetric(`${prefix}:${name}:orderCount`, item.orderCount, db.order_count));
    results.push(compareMetric(`${prefix}:${name}:gmv`, centToYuan(item.orderAmount), centToYuan(db.gmv_cent), 0.01));
    results.push(compareMetric(`${prefix}:${name}:serviceFee`, centToYuan(item.serviceFee), centToYuan(db.service_fee_cent), 0.01));
  }
  return results;
}

function buildReport(summary) {
  const lines = [];
  lines.push('# Dashboard 对账报告');
  lines.push('');
  lines.push(`- 脚本：${SCRIPT_NAME}`);
  lines.push(`- 后端：${summary.apiBaseUrl}`);
  lines.push(`- 数据库：${summary.db.container}/${summary.db.name}`);
  lines.push(`- 环境：${summary.environment?.environmentLabel || '(unknown)'}`);
  lines.push(`- 总体结论：**${summary.overallPass ? 'PASS' : 'FAIL'}**`);
  lines.push('');
  lines.push('## 指标对账');
  for (const metric of summary.metricResults) {
    lines.push(`- ${metric.pass ? 'PASS' : 'FAIL'} ${metric.name}: api=${metric.apiValue}, db=${metric.dbValue}, diff=${metric.diff}`);
  }
  lines.push('');
  lines.push('## 订单明细聚合');
  lines.push('```json');
  lines.push(JSON.stringify(summary.detailAggregate, null, 2));
  lines.push('```');
  lines.push('');
  lines.push('## DB 聚合');
  lines.push('```json');
  lines.push(JSON.stringify(summary.dbAggregate, null, 2));
  lines.push('```');
  if (summary.failedMetrics.length) {
    lines.push('');
    lines.push('## Failures');
    for (const item of summary.failedMetrics) lines.push(`- ${item.name}: api=${item.apiValue}, db=${item.dbValue}`);
  }
  if (summary.notes.length) {
    lines.push('');
    lines.push('## Notes');
    for (const note of summary.notes) lines.push(`- ${note}`);
  }
  return `${lines.join('\n')}\n`;
}

function writeArtifacts(ctx, summary) {
  const summaryJson = JSON.stringify(summary, null, 2);
  const report = buildReport(summary);
  fs.writeFileSync(path.join(ctx.outDir, 'summary.json'), summaryJson);
  fs.writeFileSync(path.join(ctx.outDir, 'report.md'), report);
  fs.writeFileSync(path.join(ctx.outDir, 'reconcile-summary.json'), summaryJson);
  fs.writeFileSync(path.join(ctx.outDir, 'reconcile-report.md'), report);
}

function createContext(outDir) {
  return {
    qaRunId: process.env.QA_RUN_ID || `QA_DASH_${timestamp()}`,
    outDir,
    environment: null,
    health: null,
    notes: []
  };
}

async function main() {
  const outDir = process.argv[2] || path.join(OUT_ROOT, `${SCRIPT_NAME}-${timestamp()}`);
  ensureDir(outDir);
  const ctx = createContext(outDir);
  const startedAt = new Date().toISOString();
  let metricResults = [];
  let api = {};
  let detailAggregate = {};
  let dbAggregate = {};
  try {
    await ensureTestEnvironment(ctx);
    if (!process.env.QA_DB_NAME && !process.env.DB_NAME && ctx.environment?.database) {
      DB_NAME = ctx.environment.database;
    }
    const token = await loginAdmin();
    await seed(token);
    const [metricsResult, summaryResult, activityProductsResult, orders] = await Promise.all([
      apiRequest('GET', '/dashboard/metrics?timeField=createTime', { token }),
      apiRequest('GET', '/dashboard/summary', { token }),
      apiRequest('GET', '/dashboard/activity-products?page=1&size=20', { token }),
      fetchAllOrders(token)
    ]);
    api = {
      metrics: unwrapApiBody(metricsResult.body) || {},
      summary: unwrapApiBody(summaryResult.body) || {},
      activityProducts: extractRecords(activityProductsResult.body)
    };
    detailAggregate = aggregateOrders(orders);
    const db = await collectDbAggregates();
    dbAggregate = {
      orderFacts: rowsToYuanAggregate(db.orderFacts),
      summary: rowsToYuanAggregate(db.summary),
      todayEstimate: rowsToYuanAggregate(db.todayEstimate),
      todaySettle: rowsToYuanAggregate(db.todaySettle),
      byBiz: db.byBiz,
      byChannel: db.byChannel
    };
    const estimateMetrics = selectMetricTrack(api.metrics, 'estimate');
    const settleMetrics = selectMetricTrack(api.metrics, 'settle');

    metricResults = [
      compareMetric('estimateTodayOrderCount', estimateMetrics.todayOrderCount, dbAggregate.todayEstimate.orderCount),
      compareMetric('estimateTodayGmv', estimateMetrics.todayGmv, dbAggregate.todayEstimate.gmv, 0.01),
      compareMetric('estimateTodayServiceFee', estimateMetrics.serviceFeeIncome, dbAggregate.todayEstimate.serviceFee, 0.01),
      compareMetric('settleTodayOrderCount', settleMetrics.todayOrderCount, dbAggregate.todaySettle.orderCount),
      compareMetric('settleTodayGmv', settleMetrics.todayGmv, dbAggregate.todaySettle.gmv, 0.01),
      compareMetric('settleTodayServiceFee', settleMetrics.serviceFeeIncome, dbAggregate.todaySettle.serviceFee, 0.01),
      compareMetric('summaryOrderCount', api.summary.orderCount, dbAggregate.summary.orderCount),
      compareMetric('summaryGmv', centToYuan(api.summary.orderAmount), dbAggregate.summary.gmv, 0.01),
      compareMetric('summaryServiceFee', centToYuan(api.summary.serviceFee), dbAggregate.summary.serviceFee, 0.01),
      compareMetric('attributed', api.summary.attributedOrderCount, dbAggregate.orderFacts.attributed),
      compareMetric('unattributed', api.summary.unattributedOrderCount, dbAggregate.orderFacts.unattributed),
      compareMetric('productUncovered', reasonCount(api.summary, ['PRODUCT_NOT_FOUND', 'UPSTREAM_PRODUCT_UNCOVERED', 'PRODUCT_UNCOVERED']), dbAggregate.orderFacts.productUncovered),
      compareMetric('refundClosed', reasonCount(api.summary, 'REFUNDED_OR_CLOSED'), dbAggregate.orderFacts.refundClosed),
      compareMetric('detailOrderCount', detailAggregate.orderCount, dbAggregate.orderFacts.orderCount),
      compareMetric('detailGmv', detailAggregate.gmv, dbAggregate.orderFacts.gmv, 0.01),
      compareMetric('detailServiceFee', detailAggregate.serviceFee, dbAggregate.orderFacts.serviceFee, 0.01),
      compareMetric('detailAttributed', detailAggregate.attributed, dbAggregate.orderFacts.attributed),
      compareMetric('detailUnattributed', detailAggregate.unattributed, dbAggregate.orderFacts.unattributed),
      compareMetric('detailProductUncovered', detailAggregate.productUncovered, dbAggregate.orderFacts.productUncovered),
      compareMetric('detailRefundClosed', detailAggregate.refundClosed, dbAggregate.orderFacts.refundClosed),
      ...comparePerformance('biz', api.summary.colonelPerformance || [], dbAggregate.byBiz, 'colonelUserName'),
      ...comparePerformance('channel', api.summary.channelPerformance || [], dbAggregate.byChannel, 'channelUserName')
    ];
  } catch (error) {
    ctx.notes.push(error?.stack || String(error));
  }
  const reconcile = buildReconcileSummary(metricResults);
  const summary = {
    name: SCRIPT_NAME,
    qaRunId: ctx.qaRunId,
    apiBaseUrl: API_BASE_URL,
    outputDir: ctx.outDir,
    startedAt,
    finishedAt: new Date().toISOString(),
    environment: ctx.environment,
    health: ctx.health,
    db: { container: DB_CONTAINER, user: DB_USER, name: DB_NAME },
    api,
    detailAggregate,
    dbAggregate,
    metricResults,
    ...reconcile,
    overallPass: ctx.environment?.isTest === true && reconcile.overallPass && ctx.notes.length === 0,
    notes: ctx.notes
  };
  writeArtifacts(ctx, summary);
  console.log(`Dashboard reconcile output: ${ctx.outDir}`);
  if (!summary.overallPass) process.exitCode = 1;
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error?.stack || String(error));
    process.exitCode = 1;
  });
}

module.exports = {
  centToYuan,
  aggregateOrders,
  compareMetric,
  parsePsqlTsv,
  buildReconcileSummary,
  selectMetricTrack,
  selectDbContainer
};
