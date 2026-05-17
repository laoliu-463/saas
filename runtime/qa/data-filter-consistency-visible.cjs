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
const OUT_DIR = path.join(REPO_ROOT, 'out', `e2e-data-filter-consistency-visible-${stamp}`);
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
    const authHeaders = { Authorization: `Bearer ${adminToken}` };

    await apiContext.post(`${API}/test/reset`, { headers: authHeaders });
    await apiContext.post(`${API}/test/seed`, { headers: authHeaders });

    const attributedRes = await apiContext.post(`${API}/test/orders/generate-attributed`, { headers: authHeaders });
    const attributed = (await attributedRes.json()).data;
    const noPickRes = await apiContext.post(`${API}/test/orders/generate-no-pick-source`, { headers: authHeaders });
    const noPick = (await noPickRes.json()).data;
    const noMapRes = await apiContext.post(`${API}/test/orders/generate-missing-mapping`, { headers: authHeaders });
    const noMap = (await noMapRes.json()).data;

    report.setup.expected = {
      attributedOrderId: attributed.orderId,
      noPickOrderId: noPick.orderId,
      noMapOrderId: noMap.orderId
    };

    const case1 = addCase('FC-01', '/orders', '订单工作台按已归因订单号筛选后摘要收敛为 1');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1500);
      await page.getByPlaceholder('订单 ID').fill(attributed.orderId);
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1400);
      const summaryText = (await page.locator('.attribution-summary').textContent()) || '';
      const attributedMatch = summaryText.match(/已归因:\s*(\d+)\s*单/);
      const unattributedMatch = summaryText.match(/待排查:\s*(\d+)\s*单/);
      const rowText = (await page.locator('tbody tr').first().textContent()) || '';
      const rowCount = await countVisibleRows(page);
      case1.screenshots.push(await shot(page, 'FC-01_orders_attributed_filter'));

      const attributedCount = attributedMatch ? Number(attributedMatch[1]) : null;
      const unattributedCount = unattributedMatch ? Number(unattributedMatch[1]) : null;
      const ok = attributedCount === 1 && unattributedCount === 0 && rowCount === 1 && rowText.includes(attributed.orderId);
      case1.result = ok ? '✅' : '❌';
      case1.note = `筛选订单=${attributed.orderId}; 摘要 已归因=${attributedCount}, 待排查=${unattributedCount}; 列表行数=${rowCount}`;
    });

    const case2 = addCase('FC-02', '/orders', '订单工作台按待排查订单号筛选后摘要收敛为 1');
    await withVisibleBrowser('admin', async (page) => {
      await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1500);
      await page.getByPlaceholder('订单 ID').fill(noPick.orderId);
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1400);
      const summaryText = (await page.locator('.attribution-summary').textContent()) || '';
      const attributedMatch = summaryText.match(/已归因:\s*(\d+)\s*单/);
      const unattributedMatch = summaryText.match(/待排查:\s*(\d+)\s*单/);
      const rowText = (await page.locator('tbody tr').first().textContent()) || '';
      const rowCount = await countVisibleRows(page);
      case2.screenshots.push(await shot(page, 'FC-02_orders_unattributed_filter'));

      const attributedCount = attributedMatch ? Number(attributedMatch[1]) : null;
      const unattributedCount = unattributedMatch ? Number(unattributedMatch[1]) : null;
      const ok = attributedCount === 0 && unattributedCount === 1 && rowCount === 1 && rowText.includes(noPick.orderId);
      case2.result = ok ? '✅' : '❌';
      case2.note = `筛选订单=${noPick.orderId}; 摘要 已归因=${attributedCount}, 待排查=${unattributedCount}; 列表行数=${rowCount}`;
    });

    const case3 = addCase('FC-03', '/data/orders', '订单明细按订单号筛选并刷新后结果保持稳定');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1500);
      await page.getByPlaceholder('订单号筛选').fill(attributed.orderId);
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1200);
      const firstRowText = (await page.locator('tbody tr').first().textContent()) || '';
      const firstRowCount = await countVisibleRows(page);
      await page.getByRole('button', { name: '刷新订单' }).click();
      await sleep(1200);
      const refreshRowText = (await page.locator('tbody tr').first().textContent()) || '';
      const refreshRowCount = await countVisibleRows(page);
      case3.screenshots.push(await shot(page, 'FC-03_data_orders_filter_refresh'));

      const ok = firstRowCount === 1
        && refreshRowCount === 1
        && firstRowText.includes(attributed.orderId)
        && refreshRowText.includes(attributed.orderId);
      case3.result = ok ? '✅' : '❌';
      case3.note = `筛选订单=${attributed.orderId}; 查询后=${firstRowCount}行; 刷新后=${refreshRowCount}行`;
    });

    const passed = report.cases.filter((item) => item.result === '✅').length;
    const lines = [];
    lines.push(`# 数据筛选一致性可见浏览器报告 - ${report.generatedAt}`);
    lines.push('');
    lines.push(`- 前端: ${FRONTEND}`);
    lines.push(`- 后端: ${BACKEND}`);
    lines.push(`- 已归因样本: ${report.setup.expected.attributedOrderId}`);
    lines.push(`- 待排查样本: ${report.setup.expected.noPickOrderId}`);
    lines.push(`- 映射缺失样本: ${report.setup.expected.noMapOrderId}`);
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
