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
const OUT_DIR = path.join(REPO_ROOT, 'out', `e2e-data-orders-paging-export-visible-${stamp}`);
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
  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    acceptDownloads: true
  });
  const page = await context.newPage();
  try {
    await login(page, username);
    await fn(page, context);
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

async function readFirstColumnValues(page, limit = 20) {
  const rows = page.locator('tbody tr');
  const rowCount = Math.min(await rows.count(), limit);
  const values = [];
  for (let i = 0; i < rowCount; i += 1) {
    const text = (await rows.nth(i).locator('td').nth(1).textContent().catch(() => '')) || '';
    const cleaned = text.trim();
    if (cleaned) {
      values.push(cleaned);
    }
  }
  return values;
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const adminToken = await apiLogin(apiContext, 'admin');
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

    const loginRes = await apiContext.post(`${API}/auth/login`, { data: { username: 'channel_leader', password: 'admin123' } });
    const channelToken = (await loginRes.json()).data.token;
    const today = new Date().toISOString().split('T')[0];
    const apiPage1 = await apiContext.get(`${API}/data/orders?page=1&size=10&startDate=${today}&endDate=${today}`, {
      headers: { Authorization: `Bearer ${channelToken}` }
    });
    const page1Body = (await apiPage1.json()).data;
    const apiPage2 = await apiContext.get(`${API}/data/orders?page=2&size=10&startDate=${today}&endDate=${today}`, {
      headers: { Authorization: `Bearer ${channelToken}` }
    });
    const page2Body = (await apiPage2.json()).data;
    const sampleOrderId = page1Body.records[0]?.id;

    report.setup.expected = {
      total: page1Body.total,
      page1Rows: page1Body.records.length,
      page2Rows: page2Body.records.length,
      sampleOrderId
    };

    const case1 = addCase('DO-01', '/data/orders', '分页切到第 2 页后，列表条数与样本变化正确');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      const page1Values = await readFirstColumnValues(page, 10);
      const page1Rows = await countVisibleRows(page);
      const pageTwoButton = page.locator('.n-pagination-item').filter({ hasText: /^2$/ }).first();
      await pageTwoButton.click();
      await sleep(1400);
      const page2Values = await readFirstColumnValues(page, 10);
      const page2Rows = await countVisibleRows(page);
      case1.screenshots.push(await shot(page, 'DO-01_page2'));

      const changed = page1Values[0] && page2Values[0] && page1Values[0] !== page2Values[0];
      const ok = page1Rows === report.setup.expected.page1Rows
        && page2Rows === report.setup.expected.page2Rows
        && changed;
      case1.result = ok ? '✅' : '❌';
      case1.note = `页1=${page1Rows}行, 页2=${page2Rows}行, 首行变化=${changed}`;
    });

    const case2 = addCase('DO-02', '/data/orders', '页大小切到 20 后，当前页条数扩展为 20');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      await page.locator('.n-data-table').evaluate((el) => {
        el.scrollTop = el.scrollHeight;
      }).catch(() => {});
      await page.mouse.wheel(0, 4000);
      await sleep(600);
      await page.locator('.n-base-selection-input__content').filter({ hasText: /10\s*\/\s*页/ }).last().click();
      await sleep(400);
      await page.locator('.n-base-select-option__content').filter({ hasText: /20\s*\/\s*页/ }).first().click();
      await sleep(1600);
      const rows = await countVisibleRows(page);
      case2.screenshots.push(await shot(page, 'DO-02_page_size_20'));

      const ok = rows === Math.min(20, report.setup.expected.total);
      case2.result = ok ? '✅' : '❌';
      case2.note = `切到20/页后显示=${rows}行; 期望=${Math.min(20, report.setup.expected.total)}`;
    });

    const case3 = addCase('DO-03', '/data/orders', '筛选态下导出 CSV 成功且文件存在');
    await withVisibleBrowser('channel_leader', async (page) => {
      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      await page.getByPlaceholder('订单号筛选').fill(sampleOrderId);
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1400);
      const rowCount = await countVisibleRows(page);
      const firstRow = (await page.locator('tbody tr').first().textContent()) || '';
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        page.getByRole('button', { name: '导出 CSV' }).click()
      ]);
      const savePath = path.join(OUT_DIR, `DO-03-${download.suggestedFilename()}`);
      await download.saveAs(savePath);
      await sleep(600);
      case3.screenshots.push(await shot(page, 'DO-03_export_filtered'));

      const exists = fs.existsSync(savePath);
      const ok = rowCount === 1 && firstRow.includes(sampleOrderId) && exists;
      case3.result = ok ? '✅' : '❌';
      case3.note = `筛选订单=${sampleOrderId}; 行数=${rowCount}; 下载文件存在=${exists}`;
    });

    const passed = report.cases.filter((item) => item.result === '✅').length;
    const lines = [];
    lines.push(`# 订单明细分页与导出回归报告 - ${report.generatedAt}`);
    lines.push('');
    lines.push(`- 前端: ${FRONTEND}`);
    lines.push(`- 后端: ${BACKEND}`);
    lines.push(`- 总订单: ${report.setup.expected.total}`);
    lines.push(`- 页1条数: ${report.setup.expected.page1Rows}`);
    lines.push(`- 页2条数: ${report.setup.expected.page2Rows}`);
    lines.push(`- 导出筛选样本: ${report.setup.expected.sampleOrderId}`);
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
