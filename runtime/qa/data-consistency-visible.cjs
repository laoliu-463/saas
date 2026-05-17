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
const OUT_DIR = path.join(REPO_ROOT, 'out', `e2e-data-consistency-visible-${stamp}`);
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
    slowMo: 250
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

function parseYuanFromText(text) {
  if (!text) return null;
  const match = String(text).replace(/,/g, '').match(/(\d+(?:\.\d+)?)/);
  return match ? Number(match[1]) : null;
}

function parseCountFromText(text) {
  if (!text) return null;
  const match = String(text).match(/(\d+)/);
  return match ? Number(match[1]) : null;
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

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const adminToken = await apiLogin(apiContext, 'admin');
    const channelLeaderToken = await apiLogin(apiContext, 'channel_leader');

    const authHeaders = { Authorization: `Bearer ${adminToken}` };
    await apiContext.post(`${API}/test/reset`, { headers: authHeaders });
    await apiContext.post(`${API}/test/seed`, { headers: authHeaders });
    for (let i = 0; i < 18; i += 1) {
      await apiContext.post(`${API}/test/orders/generate-attributed`, { headers: authHeaders });
    }
    for (let i = 0; i < 4; i += 1) {
      await apiContext.post(`${API}/test/orders/generate-no-pick-source`, { headers: authHeaders });
    }
    for (let i = 0; i < 3; i += 1) {
      await apiContext.post(`${API}/test/orders/generate-missing-mapping`, { headers: authHeaders });
    }

    const summaryRes = await apiContext.get(`${API}/dashboard/summary`, {
      headers: { Authorization: `Bearer ${channelLeaderToken}` }
    });
    const summary = (await summaryRes.json()).data;
    const metricsRes = await apiContext.get(`${API}/dashboard/metrics`, {
      headers: { Authorization: `Bearer ${channelLeaderToken}` }
    });
    const metrics = (await metricsRes.json()).data;
    const statsRes = await apiContext.get(`${API}/orders/stats`, {
      headers: { Authorization: `Bearer ${channelLeaderToken}` }
    });
    const stats = (await statsRes.json()).data;

    report.setup.expected = {
      dashboardGmv: Number((summary.orderAmount / 100).toFixed(2)),
      dataOrders: metrics.totalOrders,
      dataAmount: Number(metrics.totalAmount),
      ordersStats: stats
    };

    const case1 = addCase('DC-01', '/dashboard + /data', '看板与数据页 GMV 对齐');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/dashboard`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1200);
      const dashboardCard = page.locator('.stat-card').filter({ hasText: '今日 GMV' }).first();
      const dashboardText = await dashboardCard.textContent();
      const dashboardGmv = parseYuanFromText(dashboardText);
      case1.screenshots.push(await shot(page, 'DC-01_dashboard'));

      await page.goto(`${FRONTEND}/data`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      const dataCard = page.locator('.metric-card').filter({ hasText: '今日订单总额' }).first();
      const dataText = await dataCard.textContent();
      const dataGmv = parseYuanFromText(dataText);
      case1.screenshots.push(await shot(page, 'DC-01_data'));

      const ok = dashboardGmv === report.setup.expected.dashboardGmv && dataGmv === report.setup.expected.dataAmount;
      case1.result = ok ? '✅' : '❌';
      case1.note = `/dashboard ¥${dashboardGmv}; /data ¥${dataGmv}; 期望 ¥${report.setup.expected.dataAmount}`;
    });

    const case2 = addCase('DC-02', '/data + /data/orders', '数据页订单数与明细总数对齐');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      const ordersCard = page.locator('.metric-card').filter({ hasText: '今日订单数' }).first();
      const ordersText = await ordersCard.textContent();
      const dataOrders = parseCountFromText(ordersText);
      case2.screenshots.push(await shot(page, 'DC-02_data'));

      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1500);
      const firstPageRows = await countVisibleRows(page);
      let secondPageRows = 0;
      const pageTwoButton = page.locator('.n-pagination-item').filter({ hasText: /^2$/ }).first();
      if (await pageTwoButton.isVisible().catch(() => false)) {
        await pageTwoButton.click();
        await sleep(1200);
        secondPageRows = await countVisibleRows(page);
      }
      const dataOrdersTotal = firstPageRows + secondPageRows;
      case2.screenshots.push(await shot(page, 'DC-02_data_orders'));

      const ok = dataOrders === report.setup.expected.dataOrders && dataOrdersTotal === report.setup.expected.dataOrders;
      case2.result = ok ? '✅' : '❌';
      case2.note = `/data ${dataOrders}; /data/orders 页1=${firstPageRows}, 页2=${secondPageRows}, 合计=${dataOrdersTotal}; 期望 ${report.setup.expected.dataOrders}`;
    });

    const case3 = addCase('DC-03', '/orders', '订单工作台摘要与全量统计对齐');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1500);
      const summaryText = await page.locator('.attribution-summary').textContent();
      const attributedMatch = summaryText.match(/已归因:\s*(\d+)\s*单/);
      const unattributedMatch = summaryText.match(/待排查:\s*(\d+)\s*单/);
      const partialMatch = summaryText.match(/部分归因:\s*(\d+)\s*单/);
      const pageRows = await page.locator('tbody tr').count();
      case3.screenshots.push(await shot(page, 'DC-03_orders'));

      const attributed = attributedMatch ? Number(attributedMatch[1]) : null;
      const unattributed = unattributedMatch ? Number(unattributedMatch[1]) : null;
      const partial = partialMatch ? Number(partialMatch[1]) : null;
      const ok = attributed === report.setup.expected.ordersStats.attributedOrders
        && unattributed === report.setup.expected.ordersStats.unattributedOrders
        && partial === 0;
      case3.result = ok ? '✅' : '❌';
      case3.note = `摘要 已归因=${attributed}, 待排查=${unattributed}, 部分归因=${partial}, 当前页=${pageRows}; 期望 已归因=${report.setup.expected.ordersStats.attributedOrders}, 待排查=${report.setup.expected.ordersStats.unattributedOrders}`;
    });

    const passed = report.cases.filter((item) => item.result === '✅').length;
    const lines = [];
    lines.push(`# 数据一致性可见浏览器报告 - ${report.generatedAt}`);
    lines.push('');
    lines.push(`- 前端: ${FRONTEND}`);
    lines.push(`- 后端: ${BACKEND}`);
    lines.push(`- 期望总订单: ${report.setup.expected.dataOrders}`);
    lines.push(`- 期望 GMV: ¥${report.setup.expected.dataAmount}`);
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
