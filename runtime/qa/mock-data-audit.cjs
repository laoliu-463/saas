const fs = require('node:fs');
const path = require('node:path');

const SCRIPT_NAME = 'qa-mock-data-audit';
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const MATRIX_FILE = path.join(__dirname, 'mock-scenario-matrix.json');
const OUT_ROOT = path.join(__dirname, 'out');
const API_BASE_URL = normalizeBaseUrl(process.env.API_BASE_URL || 'http://127.0.0.1:8080');
const ADMIN_USER = process.env.ADMIN_USER || process.env.QA_ADMIN_USER || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || process.env.QA_ADMIN_PASSWORD || 'admin123';
const REQUEST_TIMEOUT_MS = Number(process.env.QA_REQUEST_TIMEOUT_MS || 20000);

const WARNING_ONLY_SCENARIOS = new Set([
  'EXCLUSIVE_TALENT',
  'EXCLUSIVE_MERCHANT',
  'AMBIGUOUS_MAPPING'
]);

function timestamp(date = new Date()) {
  const pad = (n, len = 2) => String(n).padStart(len, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}-${pad(date.getMilliseconds(), 3)}`;
}

function normalizeBaseUrl(value) {
  return String(value || '').replace(/\/+$/, '');
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function readJson(filePath, fallback = null) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch {
    return fallback;
  }
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
  const candidates = [
    data.records,
    data.list,
    data.rows,
    data.items,
    data.content,
    data.activityList,
    data.productList,
    data.talentList,
    data.orderList,
    data.orders,
    data.samples,
    data.users,
    data.roles
  ];
  for (const candidate of candidates) {
    if (Array.isArray(candidate)) return candidate;
  }
  return [];
}

function extractTotal(body) {
  const data = unwrapApiBody(body);
  if (Array.isArray(data)) return data.length;
  if (!data || typeof data !== 'object') return 0;
  for (const key of ['total', 'count', 'totalCount', 'size']) {
    const value = Number(data[key]);
    if (Number.isFinite(value)) return value;
  }
  return extractRecords(body).length;
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
  return {
    environmentLabel: environmentLabel || null,
    profile: profile || null,
    activeProfiles,
    appTestEnabled,
    douyinTestEnabled,
    database: data.database || data.databaseName || null,
    raw,
    isRealPre,
    isTest
  };
}

function deepValues(input, acc = []) {
  if (input == null) return acc;
  if (Array.isArray(input)) {
    for (const item of input) deepValues(item, acc);
    return acc;
  }
  if (typeof input === 'object') {
    for (const value of Object.values(input)) deepValues(value, acc);
    return acc;
  }
  acc.push(input);
  return acc;
}

function recordText(record) {
  return deepValues(record)
    .map((value) => String(value))
    .join(' ')
    .toLowerCase();
}

function getField(record, names) {
  if (!record || typeof record !== 'object') return undefined;
  const wanted = new Set([].concat(names).map((name) => String(name).toLowerCase()));
  const stack = [record];
  while (stack.length) {
    const current = stack.pop();
    if (!current || typeof current !== 'object') continue;
    for (const [key, value] of Object.entries(current)) {
      if (wanted.has(key.toLowerCase())) return value;
      if (value && typeof value === 'object') stack.push(value);
    }
  }
  return undefined;
}

function asArray(value) {
  return Array.isArray(value) ? value : value == null ? [] : [value];
}

function hasAnyText(record, needles) {
  const text = recordText(record);
  return [].concat(needles).some((needle) => text.includes(String(needle).toLowerCase()));
}

function toNumber(value) {
  if (value == null || value === '') return null;
  const num = Number(String(value).replace(/[^\d.-]/g, ''));
  return Number.isFinite(num) ? num : null;
}

function parseDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function inc(counter, key, by = 1) {
  counter[key] = (counter[key] || 0) + by;
}

function makeCounter(keys = []) {
  return Object.fromEntries(keys.map((key) => [key, 0]));
}

function scenarioGroups(matrixGroup) {
  return Object.entries(matrixGroup)
    .filter(([key, value]) => Array.isArray(value) && !['specialCases'].includes(key))
    .map(([key, values]) => ({ key, values }));
}

function isWarningScenario(group, scenario) {
  return group === 'specialCases' || WARNING_ONLY_SCENARIOS.has(scenario);
}

function isHardFailureScenario(item) {
  return item?.severity === 'fail';
}

function evaluateScenarioGroup(matrixGroup, counters = {}, options = {}) {
  const coveredScenarios = [];
  const missingRequired = [];
  const missingSpecial = [];
  let hardRequiredCount = 0;
  let hardActualCount = 0;
  let allScenarioCount = 0;
  let allCoveredCount = 0;

  for (const { key, values } of scenarioGroups(matrixGroup)) {
    const groupCounter = counters[key] || {};
    for (const scenario of values) {
      const actual = Number(groupCounter[scenario] || 0);
      const warning = isWarningScenario(key, scenario);
      allScenarioCount += 1;
      if (!warning) hardRequiredCount += 1;
      if (actual > 0) {
        allCoveredCount += 1;
        if (!warning) hardActualCount += 1;
        coveredScenarios.push({
          moduleId: matrixGroup.id,
          moduleLabel: matrixGroup.label,
          group: key,
          scenario,
          actualCount: actual
        });
      } else {
        const item = {
          moduleId: matrixGroup.id,
          moduleLabel: matrixGroup.label,
          group: key,
          scenario,
          severity: warning ? 'warning' : 'fail',
          reason: warning ? 'special scenario has no mock sample' : 'required scenario has no mock sample'
        };
        if (warning) missingSpecial.push(item);
        else missingRequired.push(item);
      }
    }
  }

  if (Array.isArray(matrixGroup.specialCases)) {
    const groupCounter = counters.specialCases || {};
    for (const scenario of matrixGroup.specialCases) {
      const actual = Number(groupCounter[scenario] || 0);
      allScenarioCount += 1;
      if (actual > 0) {
        allCoveredCount += 1;
        coveredScenarios.push({
          moduleId: matrixGroup.id,
          moduleLabel: matrixGroup.label,
          group: 'specialCases',
          scenario,
          actualCount: actual
        });
      } else {
        missingSpecial.push({
          moduleId: matrixGroup.id,
          moduleLabel: matrixGroup.label,
          group: 'specialCases',
          scenario,
          severity: 'warning',
          reason: 'special scenario has no mock sample'
        });
      }
    }
  }

  return {
    moduleId: matrixGroup.id,
    moduleLabel: matrixGroup.label,
    requiredCount: hardRequiredCount,
    actualCount: hardActualCount,
    scenarioCount: allScenarioCount,
    scenarioCoveredCount: allCoveredCount,
    coverage: allScenarioCount ? round(allCoveredCount / allScenarioCount) : 1,
    dataCovered: hardRequiredCount === hardActualCount,
    apiCovered: options.apiCovered === true,
    pageCovered: options.pageCovered ?? 'unknown',
    coveredScenarios,
    missingRequired,
    missingSpecial
  };
}

function round(value) {
  return Math.round(Number(value || 0) * 10000) / 10000;
}

function buildSeedSuggestions(missingScenarios) {
  return missingScenarios.map((item) => ({
    moduleId: item.moduleId,
    moduleLabel: item.moduleLabel,
    group: item.group,
    scenario: item.scenario,
    severity: item.severity,
    seedKey: `${item.moduleId}_${item.group}_${item.scenario}`,
    suggestion: seedSuggestionText(item)
  }));
}

function seedSuggestionText(item) {
  const label = `${item.moduleLabel}/${item.scenario}`;
  const byModule = {
    roles: `补充一个覆盖 ${item.scenario} 的测试角色/账号或权限负向样本，并保证菜单、路由、接口均可验证。`,
    products: `补充一个商品或活动商品 mock 样本，使其命中 ${item.scenario}，并带上活动、负责人、入库/转链等必要字段。`,
    talents: `补充一个达人 mock 样本，使其命中 ${item.scenario}，并带上认领、保护期、联系方式或爬虫兜底字段。`,
    samples: `补充一条寄样 mock 单，使其命中 ${item.scenario}，并保留商品、达人、地址、物流和状态时间字段。`,
    orders: `补充一条订单 mock 样本，使其命中 ${item.scenario}，并保留归因状态、未归因原因、金额、结算/创建时间和 pick_source。`,
    dashboard: `补充数据平台查询样本或只读 QA 证据，使 ${item.scenario} 可由 dashboard/data/orders/filter-options 覆盖。`,
    system: `补充系统管理 mock 样本或只读 QA 证据，使 ${item.scenario} 可由用户、角色、配置、操作日志接口覆盖。`
  };
  return byModule[item.moduleId] || `补充 ${label} 对应 mock seed 数据。`;
}

function inferPageCoverageFromReports(reports) {
  const modules = {
    roles: 'unknown',
    products: 'unknown',
    talents: 'unknown',
    samples: 'unknown',
    orders: 'unknown',
    dashboard: 'unknown',
    system: 'unknown'
  };

  const setCoverage = (moduleId, value) => {
    const current = modules[moduleId];
    if (value === 'partial') {
      modules[moduleId] = 'partial';
      return;
    }
    if (value === true && current === 'unknown') modules[moduleId] = true;
  };

  for (const report of reports || []) {
    for (const step of report.steps || []) {
      const name = String(step.name || '').toLowerCase();
      const pass = step.pass === true;
      const skipped = step.skipped === true;
      if (!pass) continue;
      const value = skipped ? 'partial' : true;
      if (name.includes('商品')) setCoverage('products', value);
      if (name.includes('达人')) setCoverage('talents', value);
      if (name.includes('寄样')) setCoverage('samples', value);
      if (name.includes('订单')) setCoverage('orders', value);
      if (name.includes('看板') || name.includes('数据平台') || name.includes('dashboard')) setCoverage('dashboard', value);
      if (name.includes('系统管理') || name.includes('用户')) setCoverage('system', value);
      if (name.includes('权限')) setCoverage('roles', value);
    }

    for (const module of report.modules || []) {
      const name = String(module.name || '').toLowerCase();
      if (module.pass !== true) continue;
      if (name.includes('product')) setCoverage('products', true);
      if (name.includes('talent')) setCoverage('talents', true);
      if (name.includes('sample')) setCoverage('samples', true);
      if (name.includes('order')) setCoverage('orders', true);
      if (name.includes('dashboard') || name.includes('data')) setCoverage('dashboard', true);
      if (name.includes('system') || name.includes('permission')) {
        setCoverage('system', true);
        setCoverage('roles', true);
      }
    }

    for (const route of report.allRouteResults || []) {
      if (route.hasApi500 || route.hasFatalText || route.loadingStuck) continue;
      const routePath = String(route.route || route.finalPath || '');
      if (routePath.includes('/product')) setCoverage('products', true);
      if (routePath.includes('/talent')) setCoverage('talents', true);
      if (routePath.includes('/sample')) setCoverage('samples', true);
      if (routePath.includes('/orders')) setCoverage('orders', true);
      if (routePath.includes('/data') || routePath.includes('/dashboard')) setCoverage('dashboard', true);
      if (routePath.includes('/system')) setCoverage('system', true);
    }

    for (const result of report.mustRejectResults || []) {
      if (result.evaluation?.pass) {
        setCoverage('roles', true);
        if (String(result.rule?.path || '').includes('/system')) setCoverage('system', true);
      }
    }
  }

  return modules;
}

async function fetchJson(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const started = Date.now();
  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {})
      }
    });
    const text = await response.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = { rawText: text.slice(0, 2000) };
    }
    return {
      url,
      status: response.status,
      ok: response.ok,
      durationMs: Date.now() - started,
      body
    };
  } finally {
    clearTimeout(timeout);
  }
}

function apiUrl(apiPath) {
  return `${API_BASE_URL}/api${apiPath.startsWith('/') ? apiPath : `/${apiPath}`}`;
}

async function apiRequest(ctx, method, apiPath, { token, body, core = false, missingOk = true, parseJson = true } = {}) {
  const url = apiUrl(apiPath);
  let result;
  try {
    result = parseJson
      ? await fetchJson(url, {
          method,
          body: body == null ? undefined : JSON.stringify(body),
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        })
      : await fetchText(url, {
          method,
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        });
  } catch (error) {
    result = { url, status: 0, ok: false, durationMs: 0, body: null, error: error?.message || String(error) };
  }

  const businessCode = result.body?.code;
  const missingApi = result.status === 404;
  const serverError = result.status >= 500 || Number(businessCode) >= 500;
  const bad = !result.ok || (businessCode != null && Number(businessCode) !== 200);
  if (missingApi || serverError || bad) {
    ctx.apiErrors.push({
      name: apiPath,
      method,
      url,
      httpStatus: result.status,
      businessCode,
      missingApi,
      core,
      fatal: serverError || (core && !missingApi && !missingOk),
      body: result.body,
      error: result.error || null
    });
  }
  return { ...result, businessCode, missingApi, serverError };
}

async function fetchText(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const started = Date.now();
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text();
    return {
      url,
      status: response.status,
      ok: response.ok,
      durationMs: Date.now() - started,
      body: { rawText: text.slice(0, 2000) }
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function fetchFirstAvailable(ctx, name, paths, token, options = {}) {
  const attempts = [];
  for (const apiPath of paths) {
    const result = await apiRequest(ctx, 'GET', apiPath, {
      token,
      core: options.core,
      missingOk: true
    });
    attempts.push({
      path: apiPath,
      httpStatus: result.status,
      businessCode: result.businessCode,
      missingApi: result.missingApi
    });
    if (!result.missingApi && result.ok && (result.businessCode == null || result.businessCode === 200)) {
      ctx.apiAvailability[name] = true;
      return { result, attempts };
    }
  }
  ctx.apiAvailability[name] = false;
  return { result: attempts.length ? { body: null, status: attempts.at(-1).httpStatus } : null, attempts };
}

async function loginAdmin(ctx) {
  const result = await apiRequest(ctx, 'POST', '/auth/login', {
    core: true,
    missingOk: false,
    body: { username: ADMIN_USER, password: ADMIN_PASSWORD }
  });
  const data = unwrapApiBody(result.body);
  const token = data?.token || data?.accessToken || '';
  if (!result.ok || !token) {
    throw new Error(`admin login failed: HTTP ${result.status}, code=${result.businessCode}`);
  }
  return token;
}

async function ensureTestEnvironment(ctx) {
  const envResult = await apiRequest(ctx, 'GET', '/system/env', { core: true, missingOk: false });
  if (!envResult.ok) {
    throw new Error(`/api/system/env failed with HTTP ${envResult.status}`);
  }
  ctx.environment = normalizeEnv(envResult.body);
  if (!ctx.environment.isTest) {
    throw new Error(`Refusing to run outside TEST/mock environment: ${JSON.stringify(ctx.environment)}`);
  }

  const health = await apiRequest(ctx, 'GET', '/system/health', { core: true, missingOk: false });
  ctx.health = health.body;
  if (!health.ok || health.body?.status !== 'UP') {
    throw new Error(`/api/system/health is not UP: ${JSON.stringify(health.body)}`);
  }
}

function tagRecords(records, source) {
  return (records || []).map((record) => ({ ...record, __source: source }));
}

async function collectMockData(ctx, token) {
  const data = {
    users: [],
    roles: [],
    configs: null,
    operationLogs: [],
    products: [],
    productPicks: [],
    activities: [],
    colonelActivities: [],
    activityProducts: [],
    activityProductGroups: {},
    talents: [],
    talentPublic: [],
    talentPrivate: [],
    samples: [],
    sampleByStatus: {},
    orders: [],
    unattributedOrders: [],
    orderStats: null,
    filterOptions: null,
    dashboardSummary: null,
    dashboardMetrics: null,
    dataOrders: [],
    emptyDateRangeOrders: [],
    exportAllowedProbe: null
  };

  const users = await fetchFirstAvailable(ctx, 'users', ['/users?page=1&size=100', '/system/users?page=1&size=100'], token, { core: true });
  data.users = tagRecords(extractRecords(users.result?.body), 'users');

  const roles = await fetchFirstAvailable(ctx, 'roles', ['/roles?page=1&size=100', '/roles/enabled'], token);
  data.roles = tagRecords(extractRecords(roles.result?.body), 'roles');

  const configs = await fetchFirstAvailable(ctx, 'configs', ['/configs/grouped', '/configs?page=1&size=100'], token);
  data.configs = unwrapApiBody(configs.result?.body);

  const logs = await fetchFirstAvailable(ctx, 'operationLogs', ['/operation-logs?page=1&size=100'], token);
  data.operationLogs = tagRecords(extractRecords(logs.result?.body), 'operationLogs');

  const products = await fetchFirstAvailable(ctx, 'products', ['/products?page=1&size=100'], token, { core: true });
  data.products = tagRecords(extractRecords(products.result?.body), 'products');

  const picks = await fetchFirstAvailable(ctx, 'productPicks', ['/products/picks?page=1&size=100', '/products/picks?page=1&pageSize=100'], token);
  data.productPicks = tagRecords(extractRecords(picks.result?.body), 'productPicks');

  const activities = await fetchFirstAvailable(ctx, 'activities', ['/activities?page=1&size=100'], token, { core: true });
  data.activities = tagRecords(extractRecords(activities.result?.body), 'activities');

  const colonelActivities = await fetchFirstAvailable(ctx, 'colonelActivities', ['/colonel/activities?page=1&pageSize=20'], token);
  data.colonelActivities = tagRecords(extractRecords(colonelActivities.result?.body), 'colonelActivities');

  const activityIds = [...data.activities, ...data.colonelActivities]
    .map((item) => getField(item, ['activityId', 'id']))
    .filter(Boolean)
    .slice(0, 8);
  for (const activityId of activityIds) {
    const result = await apiRequest(ctx, 'GET', `/colonel/activities/${encodeURIComponent(activityId)}/products?page=1&pageSize=100`, { token });
    const records = tagRecords(extractRecords(result.body), `activityProducts:${activityId}`);
    data.activityProductGroups[String(activityId)] = records;
    data.activityProducts.push(...records);
  }
  ctx.apiAvailability.activityProducts = ctx.apiErrors.every((item) => !String(item.name).startsWith('/colonel/activities/') || !item.missingApi);

  const talents = await fetchFirstAvailable(ctx, 'talents', ['/talents?page=1&size=100', '/talents?view=TEAM_PUBLIC&page=1&size=100'], token, { core: true });
  data.talents = tagRecords(extractRecords(talents.result?.body), 'talents');

  const talentPublic = await fetchFirstAvailable(ctx, 'talentPublic', ['/talents/pools/public?page=1&size=100', '/talents?view=TEAM_PUBLIC&page=1&size=100'], token);
  data.talentPublic = tagRecords(extractRecords(talentPublic.result?.body), 'talentPublic');

  const talentPrivate = await fetchFirstAvailable(ctx, 'talentPrivate', ['/talents/pools/private?page=1&size=100', '/talents?view=MY_TALENTS&page=1&size=100'], token);
  data.talentPrivate = tagRecords(extractRecords(talentPrivate.result?.body), 'talentPrivate');
  const teamPrivate = await apiRequest(ctx, 'GET', '/talents?view=TEAM_PRIVATE&page=1&size=100', { token });
  data.talentPrivate.push(...tagRecords(extractRecords(teamPrivate.body), 'talentPrivate:TEAM_PRIVATE'));

  const sampleStatuses = ['PENDING_AUDIT', 'PENDING_SHIP', 'SHIPPING', 'PENDING_HOMEWORK', 'COMPLETED', 'REJECTED', 'CLOSED'];
  const seenSamples = new Map();
  for (const status of sampleStatuses) {
    const result = await apiRequest(ctx, 'GET', `/samples?page=1&size=100&status=${encodeURIComponent(status)}`, { token, core: status === 'PENDING_AUDIT' });
    const records = tagRecords(extractRecords(result.body), `samples:${status}`);
    data.sampleByStatus[status] = records;
    for (const record of records) {
      const key = String(getField(record, ['id', 'requestNo', 'sampleRequestNo']) || JSON.stringify(record));
      if (!seenSamples.has(key)) seenSamples.set(key, record);
    }
  }
  data.samples = [...seenSamples.values()];
  ctx.apiAvailability.samples = data.samples.length > 0 || !ctx.apiErrors.some((item) => item.name.startsWith('/samples') && item.missingApi);

  const orders = await fetchFirstAvailable(ctx, 'orders', ['/orders?page=1&size=100&timeField=createTime'], token, { core: true });
  data.orders = tagRecords(extractRecords(orders.result?.body), 'orders');
  const attributed = await apiRequest(ctx, 'GET', '/orders?page=1&size=100&attributionStatus=ATTRIBUTED&timeField=createTime', { token });
  data.orders.push(...tagRecords(extractRecords(attributed.body), 'orders:ATTRIBUTED'));
  const unattributed = await apiRequest(ctx, 'GET', '/orders?page=1&size=100&attributionStatus=UNATTRIBUTED&timeField=createTime', { token });
  data.unattributedOrders = tagRecords(extractRecords(unattributed.body), 'orders:UNATTRIBUTED');
  data.orders.push(...data.unattributedOrders);
  const unattributedAlt = await fetchFirstAvailable(ctx, 'ordersUnattributed', ['/orders/unattributed?page=1&size=100', '/orders/order-attribution-unattributed?page=1&size=100'], token);
  data.unattributedOrders.push(...tagRecords(extractRecords(unattributedAlt.result?.body), 'orders:unattributedAlt'));

  const stats = await fetchFirstAvailable(ctx, 'ordersStats', ['/orders/stats?timeField=createTime'], token);
  data.orderStats = unwrapApiBody(stats.result?.body);
  const filters = await fetchFirstAvailable(ctx, 'orderFilterOptions', ['/orders/filter-options'], token, { core: true });
  data.filterOptions = unwrapApiBody(filters.result?.body);

  const dash = await fetchFirstAvailable(ctx, 'dashboardSummary', ['/dashboard/summary'], token, { core: true });
  data.dashboardSummary = unwrapApiBody(dash.result?.body);
  const metrics = await fetchFirstAvailable(ctx, 'dashboardMetrics', ['/dashboard/metrics?timeField=createTime'], token);
  data.dashboardMetrics = unwrapApiBody(metrics.result?.body);
  const dataOrders = await fetchFirstAvailable(ctx, 'dataOrders', ['/data/orders?page=1&size=100&timeField=createTime'], token);
  data.dataOrders = tagRecords(extractRecords(dataOrders.result?.body), 'dataOrders');
  const futureStart = '2099-01-01';
  const futureEnd = '2099-01-02';
  const emptyRange = await apiRequest(ctx, 'GET', `/data/orders?page=1&size=20&startDate=${futureStart}&endDate=${futureEnd}&timeField=createTime`, { token });
  data.emptyDateRangeOrders = extractRecords(emptyRange.body);
  const exportProbe = await apiRequest(ctx, 'GET', `/orders/exports?startTime=${futureStart}&endTime=${futureEnd}&timeField=createTime`, { token, parseJson: false });
  data.exportAllowedProbe = exportProbe;

  return data;
}

function roleStrings(user) {
  const values = [];
  for (const key of ['roleCode', 'roleCodes', 'roleName', 'roles', 'username']) {
    values.push(...asArray(getField(user, key)));
  }
  for (const value of deepValues(getField(user, 'roles') || [])) values.push(value);
  return values.map((value) => String(value).toUpperCase());
}

function hasRole(user, aliases) {
  const values = roleStrings(user).join(' ');
  return aliases.some((alias) => values.includes(alias.toUpperCase()));
}

function auditRoles(matrixGroup, data, pageCoverage) {
  const counters = {
    required: makeCounter(matrixGroup.required),
    specialCases: makeCounter(matrixGroup.specialCases)
  };
  const aliases = {
    ADMIN: ['ADMIN', 'SUPER_ADMIN'],
    BIZ_LEADER: ['BIZ_LEADER', 'COLONEL_LEADER'],
    BIZ_USER: ['BIZ_USER', 'BIZ_STAFF', 'SELLER'],
    CHANNEL_LEADER: ['CHANNEL_LEADER'],
    CHANNEL_USER: ['CHANNEL_USER', 'CHANNEL_STAFF'],
    OPS: ['OPS', 'OPS_STAFF']
  };
  for (const user of data.users) {
    for (const [required, roleAliases] of Object.entries(aliases)) {
      if (hasRole(user, roleAliases)) inc(counters.required, required);
    }
  }
  const deptIds = new Set(data.users.map((user) => getField(user, ['deptId', 'departmentId', 'teamId'])).filter(Boolean));
  if (deptIds.size > 1) inc(counters.specialCases, 'cross_group_data');
  if (data.users.some((user) => String(getField(user, 'dataScope') || '').includes('1') || hasRole(user, ['BIZ_STAFF', 'CHANNEL_STAFF']))) inc(counters.specialCases, 'own_data_only');
  if (data.users.some((user) => String(getField(user, 'dataScope') || '').includes('2') || hasRole(user, ['BIZ_LEADER', 'CHANNEL_LEADER']))) inc(counters.specialCases, 'team_data');
  if (pageCoverage.roles === true) {
    inc(counters.specialCases, 'forbidden_system_access');
    inc(counters.specialCases, 'menu_visible');
    inc(counters.specialCases, 'route_rejected');
    inc(counters.specialCases, 'api_forbidden');
  }
  return counters;
}

function productStatusBuckets(record) {
  const buckets = new Set();
  const text = recordText(record).toUpperCase();
  if (/PENDING_AUDIT|PENDING_REVIEW|待审核/.test(text)) buckets.add('PENDING_REVIEW');
  if (/APPROVED|审核通过/.test(text)) buckets.add('APPROVED');
  if (/REJECTED|审核拒绝|已拒绝/.test(text)) buckets.add('REJECTED');
  const auditStatus = toNumber(getField(record, ['auditStatus', 'audit_status']));
  if (auditStatus === 2) buckets.add('APPROVED');
  if (auditStatus === 3) buckets.add('REJECTED');
  if (/ASSIGNED|已分配/.test(text)) buckets.add('ASSIGNED');
  if (/LINKED|FOLLOWING|LISTED|已入库|商品库|已转链/.test(text) || record.__source === 'products') buckets.add('LISTED');
  if (/UNLISTED|未上架|未入库|PENDING_AUDIT|REJECTED/.test(text) || record.__source !== 'products') buckets.add('UNLISTED');
  const assignee = getField(record, ['assigneeId', 'assigneeName', 'ownerId', 'ownerName', 'auditAssigneeId']);
  if (assignee) buckets.add('ASSIGNED');
  else buckets.add('UNASSIGNED');
  return buckets;
}

function auditProducts(matrixGroup, data) {
  const counters = {
    requiredStatuses: makeCounter(matrixGroup.requiredStatuses),
    specialCases: makeCounter(matrixGroup.specialCases)
  };
  const products = [...data.products, ...data.productPicks, ...data.activityProducts];
  for (const product of products) {
    for (const bucket of productStatusBuckets(product)) {
      if (bucket in counters.requiredStatuses) inc(counters.requiredStatuses, bucket);
    }
    const activityId = getField(product, ['activityId', 'activity_id']);
    if (activityId) inc(counters.specialCases, 'bound_activity');
    else inc(counters.specialCases, 'unbound_activity');
    if (truthyField(product, ['supportTraffic', 'trafficSupported', 'isTrafficSupported'])) inc(counters.specialCases, 'support_traffic');
    if (falseyField(product, ['supportTraffic', 'trafficSupported', 'isTrafficSupported'])) inc(counters.specialCases, 'not_support_traffic');
    if (truthyField(product, ['sampleRequired', 'needSample', 'sampleNeeded'])) inc(counters.specialCases, 'sample_required');
    if (falseyField(product, ['sampleRequired', 'needSample', 'sampleNeeded'])) inc(counters.specialCases, 'sample_not_required');
    if (missingSupplementInfo(product)) inc(counters.specialCases, 'missing_supplement_info');
    if (hasAnyText(product, ['pick_source', 'promotionLink', 'product_url', 'convert_success', '转链成功'])) inc(counters.specialCases, 'convert_success');
    if (hasAnyText(product, ['convert_failed', '转链失败', 'promotion failed'])) inc(counters.specialCases, 'convert_failed');
  }
  const groupCounts = Object.values(data.activityProductGroups).map((records) => records.length);
  if (groupCounts.some((count) => count === 0) || (data.activities.length + data.colonelActivities.length > 0 && products.length === 0)) inc(counters.specialCases, 'activity_without_products');
  if (groupCounts.some((count) => count > 1)) inc(counters.specialCases, 'activity_with_multiple_products');
  return counters;
}

function truthyField(record, names) {
  const value = getField(record, names);
  return value === true || value === 1 || String(value).toLowerCase() === 'true' || String(value).toLowerCase() === 'yes';
}

function falseyField(record, names) {
  const value = getField(record, names);
  return value === false || value === 0 || String(value).toLowerCase() === 'false' || String(value).toLowerCase() === 'no';
}

function missingSupplementInfo(product) {
  const fields = ['sellingPoint', 'promotionText', 'shippingInfo', 'auditPayload', 'productHighlights'];
  return fields.every((field) => {
    const value = getField(product, field);
    return value == null || value === '' || (typeof value === 'object' && Object.keys(value).length === 0);
  });
}

function auditTalents(matrixGroup, data) {
  const counters = {
    requiredStatuses: makeCounter(matrixGroup.requiredStatuses),
    specialCases: makeCounter(matrixGroup.specialCases)
  };
  const talents = [...data.talents, ...data.talentPublic, ...data.talentPrivate];
  if (data.talentPublic.length > 0) inc(counters.requiredStatuses, 'PUBLIC', data.talentPublic.length);
  if (data.talentPrivate.length > 0) inc(counters.requiredStatuses, 'PRIVATE', data.talentPrivate.length);
  for (const talent of talents) {
    const text = recordText(talent).toUpperCase();
    if (/PUBLIC|TEAM_PUBLIC|公海|未认领/.test(text)) inc(counters.requiredStatuses, 'PUBLIC');
    if (/PRIVATE|MY|私海|我的达人/.test(text)) inc(counters.requiredStatuses, 'PRIVATE');
    if (/CLAIMED|已认领|认领人/.test(text) || getField(talent, ['ownerId', 'assigneeId', 'claimUserId'])) inc(counters.requiredStatuses, 'CLAIMED');
    const protectionEnd = parseDate(getField(talent, ['protectionEndTime', 'claimExpireTime', 'expireTime', 'protectionExpireAt', 'protectedUntil']));
    if (protectionEnd && protectionEnd.getTime() > Date.now()) inc(counters.requiredStatuses, 'PROTECTION_ACTIVE');
    if (protectionEnd && protectionEnd.getTime() <= Date.now()) inc(counters.requiredStatuses, 'PROTECTION_EXPIRED');
    if (hasAnyText(talent, ['crawler_success', 'enrichSuccess', '刷新成功'])) inc(counters.specialCases, 'crawler_success');
    if (hasAnyText(talent, ['manual_fallback', 'manualFill', '爬虫失败', '兜底录入'])) inc(counters.specialCases, 'crawler_failed_manual_fallback');
    if (truthyField(talent, ['qualifiedForSample', 'sampleQualified'])) inc(counters.specialCases, 'qualified_for_sample');
    if (falseyField(talent, ['qualifiedForSample', 'sampleQualified'])) inc(counters.specialCases, 'not_qualified_for_sample');
    if (toNumber(getField(talent, ['claimCount', 'ownerCount'])) > 1 || hasAnyText(talent, ['多人认领', 'multi_claim'])) inc(counters.specialCases, 'multi_claim');
    if (!getField(talent, ['contactPhone', 'phone', 'wechat', 'contact'])) inc(counters.specialCases, 'missing_contact');
    if (hasProfileCompleteness(talent)) inc(counters.specialCases, 'complete_profile');
    else inc(counters.specialCases, 'incomplete_profile');
  }
  return counters;
}

function hasProfileCompleteness(talent) {
  return ['nickname', 'douyinNo', 'douyinUid', 'fansCount'].every((field) => getField(talent, field) != null);
}

function sampleStatusBuckets(sample) {
  const buckets = new Set();
  const status = getField(sample, ['status', 'apiStatus', 'statusCode', 'state']);
  const text = `${status ?? ''} ${recordText(sample)}`.toUpperCase();
  const numeric = toNumber(status);
  const numericMap = {
    1: 'PENDING_REVIEW',
    2: 'PENDING_SHIPMENT',
    3: 'SHIPPING',
    5: 'WAITING_TASK',
    6: 'COMPLETED',
    7: 'REJECTED',
    8: 'CLOSED'
  };
  if (numericMap[numeric]) buckets.add(numericMap[numeric]);
  if (/PENDING_AUDIT|PENDING_REVIEW|待审核/.test(text)) buckets.add('PENDING_REVIEW');
  if (/PENDING_SHIP|PENDING_SHIPMENT|待发货/.test(text)) buckets.add('PENDING_SHIPMENT');
  if (/SHIPPING|SHIPPED|快递中|已发货/.test(text)) buckets.add('SHIPPING');
  if (/PENDING_HOMEWORK|PENDING_TASK|WAITING_TASK|待交作业/.test(text)) buckets.add('WAITING_TASK');
  if (/COMPLETED|FINISHED|已完成/.test(text)) buckets.add('COMPLETED');
  if (/REJECTED|已拒绝/.test(text)) buckets.add('REJECTED');
  if (/CLOSED|已关闭/.test(text)) buckets.add('CLOSED');
  return buckets;
}

function auditSamples(matrixGroup, data) {
  const counters = {
    requiredStatuses: makeCounter(matrixGroup.requiredStatuses),
    specialCases: makeCounter(matrixGroup.specialCases)
  };
  for (const sample of data.samples) {
    for (const bucket of sampleStatusBuckets(sample)) {
      if (bucket in counters.requiredStatuses) inc(counters.requiredStatuses, bucket);
    }
    if (!getField(sample, ['receiverAddress', 'address', 'receiverDetailAddress'])) inc(counters.specialCases, 'missing_address');
    if (!getField(sample, ['trackingNo', 'trackingNumber'])) inc(counters.specialCases, 'missing_tracking_no');
    if (hasAnyText(sample, ['completedByOrder', '命中订单', 'auto complete'])) inc(counters.specialCases, 'completed_by_order');
    if (hasAnyText(sample, ['timeout', '超时', '自动关闭'])) inc(counters.specialCases, 'auto_closed_timeout');
    if (hasAnyText(sample, ['not qualified', '不符合', '资格不足']) && getField(sample, ['reason', 'remark', 'applyReason'])) inc(counters.specialCases, 'talent_not_qualified_but_reason_filled');
  }
  const keys = new Map();
  for (const sample of data.samples) {
    const productId = getField(sample, ['productId', 'product_id']);
    const talentId = getField(sample, ['talentId', 'talent_id', 'douyinUid']);
    const created = parseDate(getField(sample, ['createTime', 'createdAt', 'applyTime']));
    if (!productId || !talentId || !created) continue;
    const key = `${productId}:${talentId}`;
    const list = keys.get(key) || [];
    if (list.some((date) => Math.abs(created.getTime() - date.getTime()) <= 7 * 24 * 3600 * 1000)) inc(counters.specialCases, 'duplicate_within_7_days');
    list.push(created);
    keys.set(key, list);
  }
  if (data.samples.some((sample) => sampleStatusBuckets(sample).has('REJECTED')) && data.samples.length > 1) inc(counters.specialCases, 'reapply_after_rejected');
  if (data.users?.some((user) => hasRole(user, ['ADMIN']))) inc(counters.specialCases, 'admin_bypass_limit');
  if (data.users?.some((user) => hasRole(user, ['BIZ_LEADER', 'CHANNEL_LEADER']))) inc(counters.specialCases, 'leader_bypass_limit');
  return counters;
}

function auditOrders(matrixGroup, data) {
  const counters = {
    requiredAttributionCases: makeCounter(matrixGroup.requiredAttributionCases),
    specialCases: makeCounter(matrixGroup.specialCases)
  };
  const orders = uniqueBy([...data.orders, ...data.unattributedOrders, ...data.dataOrders], ['orderId', 'id']);
  for (const order of orders) {
    const text = recordText(order).toUpperCase();
    const attributionStatus = String(getField(order, ['attributionStatus', 'attribution_status']) || '').toUpperCase();
    const reason = String(getField(order, ['unattributedReason', 'reason', 'attributionReason', 'attributionRemark', 'remark']) || '').toUpperCase();
    if (attributionStatus === 'ATTRIBUTED' || /已归因|已确认业绩/.test(text)) {
      inc(counters.requiredAttributionCases, 'ATTRIBUTED');
      inc(counters.specialCases, 'order_with_valid_mapping');
    }
    if (reason.includes('NO_PICK_SOURCE') || /未携带|无推广参数|NO PICK/.test(text)) {
      inc(counters.requiredAttributionCases, 'NO_PICK_SOURCE');
      inc(counters.specialCases, 'order_without_mapping');
    }
    if (reason.includes('MAPPING_NOT_FOUND') || reason.includes('COLONEL_MAPPING_NOT_FOUND') || /未找到对应推广链接|映射缺失/.test(text)) {
      inc(counters.requiredAttributionCases, 'MAPPING_NOT_FOUND');
      inc(counters.specialCases, 'order_without_mapping');
    }
    if (reason.includes('PRODUCT_UNCOVERED') || reason.includes('PRODUCT_NOT_FOUND') || reason.includes('ACTIVITY_NOT_FOUND')) inc(counters.requiredAttributionCases, 'PRODUCT_UNCOVERED');
    if (reason.includes('AMBIGUOUS') || /多重映射|歧义/.test(text)) inc(counters.requiredAttributionCases, 'AMBIGUOUS_MAPPING');
    if (hasAnyText(order, ['EXCLUSIVE_TALENT', '独家达人'])) inc(counters.requiredAttributionCases, 'EXCLUSIVE_TALENT');
    if (hasAnyText(order, ['EXCLUSIVE_MERCHANT', '独家商家'])) inc(counters.requiredAttributionCases, 'EXCLUSIVE_MERCHANT');
    const settleTime = getField(order, ['settleTime', 'settlementTime', 'settledAt']);
    if (settleTime || hasAnyText(order, ['SETTLED', '已结算'])) inc(counters.requiredAttributionCases, 'SETTLED');
    if (!settleTime || hasAnyText(order, ['UNSETTLED', '未结算'])) inc(counters.requiredAttributionCases, 'UNSETTLED');
    if (/REFUND|CLOSED|CANCEL|退款|关闭|取消/.test(text)) inc(counters.requiredAttributionCases, 'REFUNDED_OR_CLOSED');
    const fee = toNumber(getField(order, [
      'serviceFee',
      'realCommission',
      'commission',
      'serviceFeeAmount',
      'settleColonelCommission',
      'settleSecondColonelCommission',
      'serviceFeeIncome'
    ]));
    if (fee === 0) inc(counters.specialCases, 'zero_service_fee');
    const amount = toNumber(getField(order, ['orderAmount', 'totalAmount', 'payAmount', 'goodsAmount', 'gmv']));
    if (amount != null && amount >= 10000) inc(counters.specialCases, 'large_amount');
    const orderDate = parseDate(getField(order, ['orderDate', 'createTime', 'payTime']));
    const mappingDate = parseDate(getField(order, ['mappingCreateTime', 'promotionCreateTime', 'linkCreateTime']));
    if (orderDate && mappingDate && orderDate.getTime() < mappingDate.getTime()) inc(counters.specialCases, 'order_created_before_mapping');
    if (orderDate && mappingDate && orderDate.getTime() >= mappingDate.getTime()) inc(counters.specialCases, 'order_created_after_mapping');
    const settleDate = parseDate(settleTime);
    if (orderDate && settleDate && (orderDate.getMonth() !== settleDate.getMonth() || orderDate.getFullYear() !== settleDate.getFullYear())) inc(counters.specialCases, 'cross_month');
    if (!orderDate) inc(counters.specialCases, 'no_order_date');
  }
  return counters;
}

function uniqueBy(records, keys) {
  const result = [];
  const seen = new Set();
  for (const record of records || []) {
    const key = keys.map((name) => getField(record, name)).find(Boolean) || JSON.stringify(record);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(record);
  }
  return result;
}

function auditDashboard(matrixGroup, data) {
  const counters = { requiredCases: makeCounter(matrixGroup.requiredCases) };
  const metricText = recordText(data.dashboardMetrics || {}) + ' ' + recordText(data.dashboardSummary || {});
  const metricCandidates = dashboardMetricCandidates(data.dashboardMetrics);
  if (metricCandidates.some((item) => hasPositiveMetric(item, ['todayOrderCount', 'todayGmv', 'todayServiceFee'])) || /今日/.test(metricText)) inc(counters.requiredCases, 'today_data');
  if (hasAnyText(data.dashboardMetrics, ['trend7d', '7 日', 'seven']) || data.orders.some((order) => withinDays(parseDate(getField(order, ['createTime', 'orderDate', 'payTime'])), 7))) inc(counters.requiredCases, 'seven_days_data');
  if (data.orders.some((order) => withinDays(parseDate(getField(order, ['createTime', 'orderDate', 'payTime'])), 30))) inc(counters.requiredCases, 'thirty_days_data');
  if (data.orders.some((order) => {
    const create = parseDate(getField(order, ['createTime', 'orderDate', 'payTime']));
    const settle = parseDate(getField(order, ['settleTime', 'settlementTime']));
    return create && settle && (create.getMonth() !== settle.getMonth() || create.getFullYear() !== settle.getFullYear());
  })) inc(counters.requiredCases, 'cross_month_data');
  if (Array.isArray(data.emptyDateRangeOrders) && data.emptyDateRangeOrders.length === 0) inc(counters.requiredCases, 'empty_date_range');
  if (optionHasData(data.filterOptions, ['product', 'goods'])) inc(counters.requiredCases, 'filter_by_product');
  if (optionHasData(data.filterOptions, ['talent']) || recordsHaveData([...data.orders, ...data.dataOrders], ['talentId', 'talentName'])) inc(counters.requiredCases, 'filter_by_talent');
  if (optionHasData(data.filterOptions, ['biz', 'business', 'colonel'])) inc(counters.requiredCases, 'filter_by_biz_user');
  if (optionHasData(data.filterOptions, ['channel'])) inc(counters.requiredCases, 'filter_by_channel_user');
  if (optionHasData(data.filterOptions, ['activity']) || recordsHaveData([...data.orders, ...data.dataOrders], ['activityId', 'colonelActivityId'])) inc(counters.requiredCases, 'filter_by_activity');
  if (data.exportAllowedProbe?.ok) inc(counters.requiredCases, 'export_allowed_role');
  if (data.pageCoverage?.roles === true) inc(counters.requiredCases, 'export_forbidden_role');
  return counters;
}

function dashboardMetricCandidates(metrics) {
  if (!metrics || typeof metrics !== 'object') return [];
  return [metrics, metrics.settle, metrics.estimate].filter(Boolean);
}

function hasPositiveMetric(object, names) {
  return names.some((name) => toNumber(getField(object, name)) > 0);
}

function withinDays(date, days) {
  if (!date) return false;
  return Date.now() - date.getTime() <= days * 24 * 3600 * 1000;
}

function optionHasData(object, keywords) {
  if (!object) return false;
  const stack = [object];
  while (stack.length) {
    const current = stack.pop();
    if (!current || typeof current !== 'object') continue;
    for (const [key, value] of Object.entries(current)) {
      const keyMatches = keywords.some((keyword) => key.toLowerCase().includes(keyword));
      if (keyMatches) {
        if (Array.isArray(value) && value.length > 0) return true;
        if (value && typeof value === 'object' && Object.keys(value).length > 0) return true;
        if (typeof value === 'string' && value) return true;
      }
      if (value && typeof value === 'object') stack.push(value);
    }
  }
  return false;
}

function recordsHaveData(records, fields) {
  return (records || []).some((record) => fields.some((field) => {
    const value = getField(record, field);
    return value != null && value !== '';
  }));
}

function auditSystem(matrixGroup, data) {
  const counters = { requiredCases: makeCounter(matrixGroup.requiredCases) };
  if (data.users.length > 0) inc(counters.requiredCases, 'user_create_case');
  if (data.users.some((user) => {
    const rawStatus = getField(user, ['status', 'enabled', 'disabled']);
    const status = String(rawStatus ?? '').toLowerCase();
    return rawStatus === 0
      || rawStatus === false
      || status.includes('disabled')
      || status === 'false'
      || status === '0'
      || status.includes('停用');
  })) inc(counters.requiredCases, 'user_disabled_case');
  if (data.roles.length > 0) inc(counters.requiredCases, 'role_permission_case');
  if (hasAnyText(data.configs || {}, ['sample.default_standard', 'sample.max', 'sample.limit', 'sample.timeout'])) inc(counters.requiredCases, 'config_sample_limit_case');
  if (hasAnyText(data.configs || {}, ['commission', '提成'])) inc(counters.requiredCases, 'config_commission_case');
  if (data.operationLogs.length > 0) inc(counters.requiredCases, 'operation_log_case');
  return counters;
}

function readRecentQaReports(outRoot = OUT_ROOT) {
  if (!fs.existsSync(outRoot)) return [];
  const prefixes = ['qa-page-role', 'qa-business-browser-flow', 'qa-business-crud-flow'];
  return fs.readdirSync(outRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && prefixes.some((prefix) => entry.name.startsWith(prefix)))
    .map((entry) => {
      const dir = path.join(outRoot, entry.name);
      const stat = fs.statSync(dir);
      return { dir, name: entry.name, mtimeMs: stat.mtimeMs };
    })
    .sort((a, b) => b.mtimeMs - a.mtimeMs)
    .slice(0, 12)
    .map((entry) => {
      const summary = readJson(path.join(entry.dir, 'summary.json'), null);
      return summary ? { ...summary, sourceDir: path.relative(REPO_ROOT, entry.dir).replace(/\\/g, '/') } : null;
    })
    .filter(Boolean);
}

function buildRawCounts(data, moduleResults, countersByModule) {
  return {
    recordCounts: {
      users: data.users.length,
      roles: data.roles.length,
      products: data.products.length,
      productPicks: data.productPicks.length,
      activities: data.activities.length,
      colonelActivities: data.colonelActivities.length,
      activityProducts: data.activityProducts.length,
      talents: data.talents.length,
      talentPublic: data.talentPublic.length,
      talentPrivate: data.talentPrivate.length,
      samples: data.samples.length,
      orders: uniqueBy(data.orders, ['orderId', 'id']).length,
      unattributedOrders: data.unattributedOrders.length,
      dataOrders: data.dataOrders.length,
      operationLogs: data.operationLogs.length
    },
    countersByModule,
    moduleResults
  };
}

function evaluateAll(matrix, data, pageCoverage, apiAvailability) {
  data.pageCoverage = pageCoverage;
  const countersByModule = {};
  const results = [];
  for (const group of matrix.modules) {
    let counters;
    if (group.id === 'roles') counters = auditRoles(group, data, pageCoverage);
    else if (group.id === 'products') counters = auditProducts(group, data);
    else if (group.id === 'talents') counters = auditTalents(group, data);
    else if (group.id === 'samples') counters = auditSamples(group, data);
    else if (group.id === 'orders') counters = auditOrders(group, data);
    else if (group.id === 'dashboard') counters = auditDashboard(group, data);
    else if (group.id === 'system') counters = auditSystem(group, data);
    else counters = {};
    countersByModule[group.id] = counters;
    results.push(evaluateScenarioGroup(group, counters, {
      apiCovered: moduleApiCovered(group.id, apiAvailability),
      pageCovered: pageCoverage[group.id] ?? 'unknown'
    }));
  }
  return { results, countersByModule };
}

function moduleApiCovered(moduleId, apiAvailability) {
  const required = {
    roles: ['users', 'roles'],
    products: ['products', 'activities'],
    talents: ['talents'],
    samples: ['samples'],
    orders: ['orders', 'orderFilterOptions'],
    dashboard: ['dashboardSummary'],
    system: ['users', 'roles', 'configs']
  }[moduleId] || [];
  return required.every((key) => apiAvailability[key] === true);
}

function coverageSummary(moduleResults) {
  const result = {
    total: 0,
    roles: 0,
    products: 0,
    talents: 0,
    samples: 0,
    orders: 0,
    dashboard: 0,
    system: 0
  };
  for (const item of moduleResults) {
    result[item.moduleId] = item.coverage;
  }
  result.total = round(moduleResults.reduce((sum, item) => sum + item.coverage, 0) / Math.max(moduleResults.length, 1));
  return result;
}

function pageCoverageNotes(pageCoverage) {
  const notes = [];
  for (const [moduleId, value] of Object.entries(pageCoverage)) {
    if (value === 'unknown') notes.push(`${moduleId}: pageCovered unknown，需要先跑 qa-page-role / qa-business-browser-flow / qa-business-crud-flow。`);
    if (value === 'partial') notes.push(`${moduleId}: 页面 QA 仅部分覆盖，存在 skipped 或可选入口未命中。`);
  }
  return notes;
}

function writeArtifacts(ctx, artifacts) {
  fs.writeFileSync(path.join(ctx.outDir, 'summary.json'), JSON.stringify(artifacts.summary, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'missing-scenarios.json'), JSON.stringify(artifacts.missingScenarios, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'seed-suggestions.json'), JSON.stringify(artifacts.seedSuggestions, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'raw-counts.json'), JSON.stringify(artifacts.rawCounts, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'api-errors.json'), JSON.stringify(artifacts.apiErrors, null, 2));
  fs.writeFileSync(path.join(ctx.outDir, 'report.md'), buildReport(artifacts));
}

function buildReport({ summary, moduleResults, missingScenarios, seedSuggestions, rawCounts, pageCoverage, reportsUsed }) {
  const lines = [];
  lines.push('# TEST/mock 数据业务场景覆盖审计报告');
  lines.push('');
  lines.push(`- 脚本：${SCRIPT_NAME}`);
  lines.push(`- 后端：${summary.apiBaseUrl}`);
  lines.push(`- 环境：${summary.environment?.environmentLabel || '(unknown)'}`);
  lines.push(`- 总体结论：**${summary.overallPass ? '通过' : '失败'}**`);
  lines.push(`- 覆盖率：${Math.round(summary.coverage.total * 100)}%`);
  lines.push('');
  lines.push('## 已覆盖场景');
  for (const module of moduleResults) {
    lines.push(`### ${module.moduleLabel}`);
    const covered = module.coveredScenarios;
    if (!covered.length) {
      lines.push('- 无。');
    } else {
      for (const item of covered) {
        lines.push(`- ${item.group} / ${item.scenario}：${item.actualCount}`);
      }
    }
  }
  lines.push('');
  lines.push('## 缺失场景');
  const hardMissing = missingScenarios.filter((item) => item.severity === 'fail');
  if (!hardMissing.length) {
    lines.push('- 无主流程硬缺口。');
  } else {
    for (const item of hardMissing) {
      lines.push(`- [FAIL] ${item.moduleLabel} / ${item.group} / ${item.scenario}：${item.reason}`);
    }
  }
  lines.push('');
  lines.push('## 特殊情况缺口');
  const warnings = missingScenarios.filter((item) => item.severity === 'warning');
  if (!warnings.length) {
    lines.push('- 无 warning 缺口。');
  } else {
    for (const item of warnings) {
      lines.push(`- [WARNING] ${item.moduleLabel} / ${item.group} / ${item.scenario}：${item.reason}`);
    }
  }
  lines.push('');
  lines.push('## Seed 补充建议');
  if (!seedSuggestions.length) {
    lines.push('- 暂无。');
  } else {
    for (const item of seedSuggestions) {
      lines.push(`- ${item.seedKey}（${item.severity}）：${item.suggestion}`);
    }
  }
  lines.push('');
  lines.push('## 页面 QA 覆盖');
  for (const [moduleId, value] of Object.entries(pageCoverage)) {
    lines.push(`- ${moduleId}：${value}`);
  }
  lines.push('');
  lines.push('## 已读取页面 QA 报告');
  if (!reportsUsed.length) {
    lines.push('- 无。');
  } else {
    for (const report of reportsUsed) {
      lines.push(`- ${report.sourceDir || report.name || report.script || '(unknown)'}`);
    }
  }
  lines.push('');
  lines.push('## 原始数据计数');
  lines.push('```json');
  lines.push(JSON.stringify(rawCounts.recordCounts, null, 2));
  lines.push('```');
  lines.push('');
  lines.push('## API 错误');
  if (!summary.apiErrors.length) {
    lines.push('- 无。');
  } else {
    for (const error of summary.apiErrors) {
      const tag = error.missingApi ? 'missingApi' : error.fatal ? 'fatal' : 'apiError';
      lines.push(`- [${tag}] ${error.httpStatus || 'NETWORK'} ${error.name} code=${error.businessCode ?? '-'}`);
    }
  }
  lines.push('');
  lines.push('## Notes');
  if (!summary.notes.length) lines.push('- 无。');
  for (const note of summary.notes) lines.push(`- ${note}`);
  return `${lines.join('\n')}\n`;
}

function createContext(outDir) {
  return {
    outDir,
    apiBaseUrl: API_BASE_URL,
    environment: null,
    health: null,
    apiErrors: [],
    apiAvailability: {},
    notes: []
  };
}

async function main() {
  const outDir = process.argv[2] || path.join(OUT_ROOT, `${SCRIPT_NAME}-${timestamp()}`);
  const ctx = createContext(outDir);
  ensureDir(outDir);
  const startedAt = new Date().toISOString();
  const matrix = readJson(MATRIX_FILE, { modules: [] });
  let moduleResults = [];
  let missingScenarios = [];
  let seedSuggestions = [];
  let rawCounts = { recordCounts: {}, countersByModule: {}, moduleResults: [] };
  let pageCoverage = {
    roles: 'unknown',
    products: 'unknown',
    talents: 'unknown',
    samples: 'unknown',
    orders: 'unknown',
    dashboard: 'unknown',
    system: 'unknown'
  };
  let reportsUsed = [];

  try {
    await ensureTestEnvironment(ctx);
    const token = await loginAdmin(ctx);
    reportsUsed = readRecentQaReports();
    pageCoverage = inferPageCoverageFromReports(reportsUsed);
    const data = await collectMockData(ctx, token);
    const evaluated = evaluateAll(matrix, data, pageCoverage, ctx.apiAvailability);
    moduleResults = evaluated.results;
    missingScenarios = moduleResults.flatMap((item) => [...item.missingRequired, ...item.missingSpecial]);
    seedSuggestions = buildSeedSuggestions(missingScenarios);
    rawCounts = buildRawCounts(data, moduleResults, evaluated.countersByModule);
    ctx.notes.push(...pageCoverageNotes(pageCoverage));
  } catch (error) {
    ctx.notes.push(`Fatal: ${error?.stack || String(error)}`);
  }

  const fatalApiErrors = ctx.apiErrors.filter((item) => item.fatal || item.httpStatus >= 500 || Number(item.businessCode) >= 500);
  const hardMissing = missingScenarios.filter(isHardFailureScenario);
  const summary = {
    name: SCRIPT_NAME,
    startedAt,
    finishedAt: new Date().toISOString(),
    apiBaseUrl: API_BASE_URL,
    environment: ctx.environment,
    health: ctx.health,
    overallPass: ctx.environment?.isTest === true && fatalApiErrors.length === 0 && hardMissing.length === 0,
    coverage: coverageSummary(moduleResults),
    moduleResults,
    missingScenarios,
    seedSuggestions,
    apiErrors: ctx.apiErrors,
    notes: ctx.notes
  };

  writeArtifacts(ctx, {
    summary,
    moduleResults,
    missingScenarios,
    seedSuggestions,
    rawCounts,
    apiErrors: ctx.apiErrors,
    pageCoverage,
    reportsUsed
  });
  console.log(`QA mock data audit output: ${outDir}`);
  if (!summary.overallPass) process.exitCode = 1;
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error?.stack || String(error));
    process.exitCode = 1;
  });
}

module.exports = {
  normalizeEnv,
  unwrapApiBody,
  extractRecords,
  evaluateScenarioGroup,
  buildSeedSuggestions,
  inferPageCoverageFromReports,
  isHardFailureScenario,
  readRecentQaReports,
  collectMockData,
  evaluateAll,
  coverageSummary
};
