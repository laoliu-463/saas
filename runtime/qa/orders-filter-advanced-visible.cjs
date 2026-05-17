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
const OUT_DIR = path.join(REPO_ROOT, 'out', `e2e-orders-filter-advanced-visible-${stamp}`);
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
  const count = await rows.count();
  let visible = 0;
  for (let i = 0; i < count; i += 1) {
    const text = (await rows.nth(i).textContent()) || '';
    if (text.trim()) {
      visible += 1;
    }
  }
  return visible;
}

async function readSummary(page) {
  const text = (await page.locator('.attribution-summary').textContent()) || '';
  const attributed = text.match(/已归因:\s*(\d+)\s*单/);
  const unattributed = text.match(/待排查:\s*(\d+)\s*单/);
  const partial = text.match(/部分归因:\s*(\d+)\s*单/);
  return {
    raw: text,
    attributed: attributed ? Number(attributed[1]) : null,
    unattributed: unattributed ? Number(unattributed[1]) : null,
    partial: partial ? Number(partial[1]) : null
  };
}

async function selectAttributionStatus(page, label) {
  await page.locator('.toolbar .n-base-selection').nth(0).click();
  await sleep(400);
  await page.locator('.n-base-select-menu .n-base-select-option').filter({ hasText: label }).first().click();
  await sleep(400);
}

async function pickTodayRange(page) {
  const picker = page.locator('.toolbar .n-date-picker').first();
  await picker.click();
  await sleep(500);
  const confirmButtons = page.getByRole('button', { name: '确认' });
  const confirmCount = await confirmButtons.count();
  if (confirmCount > 0) {
    await confirmButtons.last().click();
  } else {
    await page.keyboard.press('Escape');
  }
  await sleep(500);
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const adminToken = await apiLogin(apiContext, 'admin');
    const authHeaders = { Authorization: `Bearer ${adminToken}` };

    await apiContext.post(`${API}/test/reset`, { headers: authHeaders });
    await apiContext.post(`${API}/test/seed`, { headers: authHeaders });
    const attrRes = await apiContext.post(`${API}/test/orders/generate-attributed`, { headers: authHeaders });
    const attrData = (await attrRes.json()).data;
    const noPickRes = await apiContext.post(`${API}/test/orders/generate-no-pick-source`, { headers: authHeaders });
    const noPickData = (await noPickRes.json()).data;
    const noMapRes = await apiContext.post(`${API}/test/orders/generate-missing-mapping`, { headers: authHeaders });
    const noMapData = (await noMapRes.json()).data;

    const readStats = async (suffix = '') => {
      const res = await apiContext.get(`${API}/orders/stats${suffix}`, { headers: authHeaders });
      return (await res.json()).data;
    };

    let adminStats;
    let unattributedStats;
    let attributedStats;
    for (let i = 0; i < 5; i += 1) {
      await sleep(500);
      adminStats = await readStats();
      unattributedStats = await readStats('?attributionStatus=UNATTRIBUTED');
      attributedStats = await readStats('?attributionStatus=ATTRIBUTED');
      if ((adminStats?.totalOrders || 0) >= 7 && (unattributedStats?.totalOrders || 0) >= 4 && (attributedStats?.totalOrders || 0) >= 3) {
        break;
      }
    }

    report.setup.expected = {
      adminTotal: adminStats.totalOrders,
      attributedTotal: attributedStats.totalOrders,
      unattributedTotal: unattributedStats.totalOrders,
      samples: {
        attributed: attrData.orderId,
        noPick: noPickData.orderId,
        noMap: noMapData.orderId
      }
    };

    const case1 = addCase('OF-01', '/orders', '按归因状态筛选后摘要与列表同步收敛');
    await withVisibleBrowser('admin', async (page) => {
      await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      await selectAttributionStatus(page, '待排查');
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1400);
      const summary = await readSummary(page);
      const rowCount = await countVisibleRows(page);
      const firstRow = (await page.locator('tbody tr').first().textContent()) || '';
      case1.screenshots.push(await shot(page, 'OF-01_orders_status_filter'));

      const ok = summary.attributed === 0
        && summary.unattributed === report.setup.expected.unattributedTotal
        && rowCount > 0
        && (firstRow.includes('待排查') || firstRow.includes(noPickData.orderId) || firstRow.includes(noMapData.orderId));
      case1.result = ok ? '✅' : '❌';
      case1.note = `待排查摘要=${summary.unattributed}; 期望=${report.setup.expected.unattributedTotal}; 列表行数=${rowCount}`;
    });

    const case2 = addCase('OF-02', '/orders', '筛选后点击重置，摘要与列表恢复全量');
    await withVisibleBrowser('admin', async (page) => {
      await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      await page.getByPlaceholder('订单 ID').fill(attrData.orderId);
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1200);
      await page.getByRole('button', { name: '重置' }).click();
      await sleep(1400);
      const summary = await readSummary(page);
      const rowCount = await countVisibleRows(page);
      const resetStats = await readStats();
      case2.screenshots.push(await shot(page, 'OF-02_orders_reset'));

      const ok = summary.attributed === resetStats.attributedOrders
        && summary.unattributed === resetStats.unattributedOrders
        && rowCount > 1;
      case2.result = ok ? '✅' : '❌';
      case2.note = `重置后 已归因=${summary.attributed}, 待排查=${summary.unattributed}; 实时接口 已归因=${resetStats.attributedOrders}, 待排查=${resetStats.unattributedOrders}`;
    });

    const case3 = addCase('OF-03', '/orders', '日期筛选与状态筛选叠加后页面稳定无报错');
    await withVisibleBrowser('admin', async (page) => {
      await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1600);
      await pickTodayRange(page);
      await selectAttributionStatus(page, '已归因');
      await page.getByRole('button', { name: '查询' }).click();
      await sleep(1500);
      const summary = await readSummary(page);
      const rowCount = await countVisibleRows(page);
      const hasErrorToast = await page.locator('.n-message--error').count();
      case3.screenshots.push(await shot(page, 'OF-03_orders_date_status_filter'));

      const ok = hasErrorToast === 0
        && summary.attributed !== null
        && summary.unattributed !== null
        && rowCount > 0;
      case3.result = ok ? '✅' : '❌';
      case3.note = `日期+已归因筛选后 已归因=${summary.attributed}, 待排查=${summary.unattributed}, 行数=${rowCount}, 错误Toast=${hasErrorToast}`;
    });

    const passed = report.cases.filter((item) => item.result === '✅').length;
    const lines = [];
    lines.push(`# 订单筛选高级回归报告 - ${report.generatedAt}`);
    lines.push('');
    lines.push(`- 前端: ${FRONTEND}`);
    lines.push(`- 后端: ${BACKEND}`);
    lines.push(`- 全量订单: ${report.setup.expected.adminTotal}`);
    lines.push(`- 已归因订单: ${report.setup.expected.attributedTotal}`);
    lines.push(`- 待排查订单: ${report.setup.expected.unattributedTotal}`);
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
