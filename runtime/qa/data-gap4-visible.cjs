const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8080';
const API = `${BACKEND}/api`;
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(REPO_ROOT, 'out', `e2e-data-gap4-visible-${stamp}`);
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'results.json');
fs.mkdirSync(OUT_DIR, { recursive: true });

const report = {
  generatedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
  frontend: FRONTEND,
  backend: BACKEND,
  setup: {},
  cases: []
};

function addCase(id, route, title) {
  const item = { id, route, title, result: '❌', note: '', screenshots: [] };
  report.cases.push(item);
  return item;
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function shot(page, name) {
  const file = path.join(OUT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function apiLogin(apiContext, username, password = 'admin123') {
  const res = await apiContext.post(`${API}/auth/login`, { data: { username, password } });
  const body = await res.json();
  const token = body?.data?.token || body?.data?.accessToken;
  if (!res.ok() || !token) throw new Error(`登录失败: ${username}`);
  return token;
}

async function login(page, username, password = 'admin123') {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登/ }).click();
  await page.waitForLoadState('networkidle', { timeout: 30000 });
  await sleep(800);
}

async function withVisibleBrowser(username, fn) {
  const browser = await chromium.launch({
    headless: false,
    executablePath: EDGE_PATH,
    slowMo: 200
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();
  try {
    await login(page, username);
    await fn(page);
  } finally {
    await context.close();
    await browser.close();
  }
}

function extractMetricNumber(text) {
  if (!text) return null;
  const match = String(text).replace(/,/g, '').match(/(\d+(?:\.\d+)?)/);
  return match ? Number(match[1]) : null;
}

async function readDashboardMetrics(page) {
  const ordersCard = page.locator('.metric-card').filter({ hasText: '今日订单数' }).first();
  const amountCard = page.locator('.metric-card').filter({ hasText: '今日订单总额' }).first();
  return {
    totalOrders: extractMetricNumber(await ordersCard.textContent()),
    totalAmount: extractMetricNumber(await amountCard.textContent())
  };
}

async function countVisibleRows(page) {
  const rows = page.locator('tbody tr');
  const rowCount = await rows.count();
  let visible = 0;
  for (let i = 0; i < rowCount; i += 1) {
    const text = (await rows.nth(i).textContent()) || '';
    if (text.trim()) {
      visible += 1;
    }
  }
  return visible;
}

async function resolveTalentId(apiContext, headers) {
  const talentRes = await apiContext.get(`${API}/talents?page=1&size=50&keyword=talent_test_a`, { headers });
  const talentBody = (await talentRes.json()).data;
  const talentRecord = Array.isArray(talentBody?.records)
    ? talentBody.records.find((item) => item?.douyinUid === 'talent_test_a' || item?.uid === 'talent_test_a')
    : null;
  if (talentRecord?.id) {
    return talentRecord.id;
  }

  const sampleRes = await apiContext.get(`${API}/samples?page=1&size=20&keyword=TEST-SAMPLE-001`, { headers });
  const sampleBody = (await sampleRes.json()).data;
  const sampleRecord = Array.isArray(sampleBody?.records)
    ? sampleBody.records.find((item) => item?.requestNo === 'TEST-SAMPLE-001')
    : null;
  if (sampleRecord?.talentId) {
    return sampleRecord.talentId;
  }

  throw new Error('未找到 talent_test_a 的 UUID');
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const adminToken = await apiLogin(apiContext, 'admin');
    const authHeaders = { Authorization: `Bearer ${adminToken}` };

    await apiContext.post(`${API}/test/reset`, { headers: authHeaders });
    await apiContext.post(`${API}/test/seed`, { headers: authHeaders });
    const attributedRes = await apiContext.post(`${API}/test/orders/generate-attributed`, { headers: authHeaders });
    const attributed = (await attributedRes.json()).data;
    await apiContext.post(`${API}/test/orders/generate-no-pick-source`, { headers: authHeaders });
    await apiContext.post(`${API}/test/orders/generate-missing-mapping`, { headers: authHeaders });

    const channelToken = await apiLogin(apiContext, 'channel_leader');
    const channelHeaders = { Authorization: `Bearer ${channelToken}` };

    const settleMetricsRes = await apiContext.get(`${API}/dashboard/metrics?timeField=settleTime`, { headers: channelHeaders });
    const settleMetrics = (await settleMetricsRes.json()).data;
    const createMetricsRes = await apiContext.get(`${API}/dashboard/metrics?timeField=createTime`, { headers: channelHeaders });
    const createMetrics = (await createMetricsRes.json()).data;
    const talentId = await resolveTalentId(apiContext, channelHeaders);

    report.setup.expected = {
      merchantId: 'M_ATTR',
      talentId,
      orderId: attributed.orderId,
      settleMetrics: {
        totalOrders: Number(settleMetrics.totalOrders || 0),
        totalAmount: Number(settleMetrics.totalAmount || 0)
      },
      createMetrics: {
        totalOrders: Number(createMetrics.totalOrders || 0),
        totalAmount: Number(createMetrics.totalAmount || 0)
      }
    };

    const case1 = addCase('DG4-01', '/data', '数据看板时间字段切换与后端口径一致');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1800);
      const settleUi = await readDashboardMetrics(page);
      await page.getByText('按创建时间', { exact: true }).click();
      await sleep(1600);
      const createUi = await readDashboardMetrics(page);
      case1.screenshots.push(await shot(page, 'DG4-01-dashboard-time-toggle'));

      const ok = settleUi.totalOrders === report.setup.expected.settleMetrics.totalOrders
        && settleUi.totalAmount === report.setup.expected.settleMetrics.totalAmount
        && createUi.totalOrders === report.setup.expected.createMetrics.totalOrders
        && createUi.totalAmount === report.setup.expected.createMetrics.totalAmount;
      case1.result = ok ? '✅' : '❌';
      case1.note = `settle(UI/API)=${settleUi.totalOrders}/${settleUi.totalAmount} vs ${report.setup.expected.settleMetrics.totalOrders}/${report.setup.expected.settleMetrics.totalAmount}; create(UI/API)=${createUi.totalOrders}/${createUi.totalAmount} vs ${report.setup.expected.createMetrics.totalOrders}/${report.setup.expected.createMetrics.totalAmount}`;
    });

    const case2 = addCase('DG4-02', '/data/orders', '订单明细按商家ID筛选可收敛到指定样本');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1800);
      await page.getByPlaceholder('商家ID筛选').fill(report.setup.expected.merchantId);
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1500);
      const rows = await countVisibleRows(page);
      const firstRow = (await page.locator('tbody tr').first().textContent()) || '';
      case2.screenshots.push(await shot(page, 'DG4-02-orders-merchant-filter'));

      const ok = rows >= 1 && firstRow.includes(report.setup.expected.orderId);
      case2.result = ok ? '✅' : '❌';
      case2.note = `merchantId=${report.setup.expected.merchantId}; 行数=${rows}; 首行命中样本=${firstRow.includes(report.setup.expected.orderId)}`;
    });

    const case3 = addCase('DG4-03', '/data/orders', '订单明细按达人ID筛选可收敛到指定样本');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1800);
      await page.getByPlaceholder('达人ID筛选').fill(report.setup.expected.talentId);
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1500);
      const rows = await countVisibleRows(page);
      const firstRow = (await page.locator('tbody tr').first().textContent()) || '';
      case3.screenshots.push(await shot(page, 'DG4-03-orders-talent-filter'));

      const ok = rows >= 1 && firstRow.includes(report.setup.expected.orderId);
      case3.result = ok ? '✅' : '❌';
      case3.note = `talentId=${report.setup.expected.talentId}; 行数=${rows}; 首行命中样本=${firstRow.includes(report.setup.expected.orderId)}`;
    });

    const passed = report.cases.filter((item) => item.result === '✅').length;
    const lines = [];
    lines.push(`# Gap4 数据平台定向回归报告 - ${report.generatedAt}`);
    lines.push('');
    lines.push(`- 前端: ${FRONTEND}`);
    lines.push(`- 后端: ${BACKEND}`);
    lines.push(`- 商家样本: ${report.setup.expected.merchantId}`);
    lines.push(`- 达人样本: ${report.setup.expected.talentId}`);
    lines.push(`- 订单样本: ${report.setup.expected.orderId}`);
    lines.push(`- 通过: ${passed}/${report.cases.length}`);
    lines.push('');
    lines.push('| 编号 | 路径 | 结果 | 备注 |');
    lines.push('| --- | --- | --- | --- |');
    for (const item of report.cases) {
      lines.push(`| ${item.id} | ${item.route} | ${item.result} | ${String(item.note).replace(/\|/g, '/')} |`);
    }
    lines.push('');
    lines.push('## 截图');
    for (const item of report.cases) {
      lines.push(`- ${item.id}: ${item.screenshots.join(' ; ')}`);
    }
    fs.writeFileSync(REPORT_MD, lines.join('\n'), 'utf8');
    fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2), 'utf8');
    console.log(REPORT_MD);
  } finally {
    await apiContext.dispose();
  }
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
