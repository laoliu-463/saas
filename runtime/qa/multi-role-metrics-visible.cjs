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
const OUT_DIR = path.join(REPO_ROOT, 'out', `e2e-multi-role-metrics-visible-${stamp}`);
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'results.json');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ROLES = [
  { username: 'admin', label: 'admin' },
  { username: 'biz_leader', label: 'biz_leader' },
  { username: 'channel_leader', label: 'channel_leader' }
];

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

function parseNumber(text) {
  if (!text) return null;
  const match = String(text).replace(/,/g, '').match(/(\d+(?:\.\d+)?)/);
  return match ? Number(match[1]) : null;
}

function summarizeRoleFacts(roleFacts) {
  return ROLES.map((role) => {
    const item = roleFacts[role.username];
    const ordersSummary = item.ordersSummary
      ? `${item.ordersSummary.attributed}/${item.ordersSummary.unattributed}/${item.ordersSummary.partial}`
      : 'N/A';
    return `${role.label}: dashboardGMV=${item.dashboardGmv}, dataGMV=${item.dataGmv}, dataOrders=${item.dataOrders}, ordersSummary=${ordersSummary}`;
  }).join(' ; ');
}

async function readOrdersSummary(page) {
  const summaryLocator = page.locator('.attribution-summary');
  const exists = await summaryLocator.count().catch(() => 0);
  if (!exists) {
    return {
      present: false,
      attributed: 0,
      unattributed: 0,
      partial: 0
    };
  }
  const text = (await summaryLocator.textContent()) || '';
  const attributed = text.match(/已归因:\s*(\d+)\s*单/);
  const unattributed = text.match(/待排查:\s*(\d+)\s*单/);
  const partial = text.match(/部分归因:\s*(\d+)\s*单/);
  return {
    present: true,
    attributed: attributed ? Number(attributed[1]) : null,
    unattributed: unattributed ? Number(unattributed[1]) : null,
    partial: partial ? Number(partial[1]) : null
  };
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const adminToken = await apiLogin(apiContext, 'admin');
    const authHeaders = { Authorization: `Bearer ${adminToken}` };

    await apiContext.post(`${API}/test/reset`, { headers: authHeaders });
    await apiContext.post(`${API}/test/seed`, { headers: authHeaders });
    for (let i = 0; i < 8; i += 1) {
      await apiContext.post(`${API}/test/orders/generate-attributed`, { headers: authHeaders });
    }
    for (let i = 0; i < 2; i += 1) {
      await apiContext.post(`${API}/test/orders/generate-no-pick-source`, { headers: authHeaders });
    }
    for (let i = 0; i < 2; i += 1) {
      await apiContext.post(`${API}/test/orders/generate-missing-mapping`, { headers: authHeaders });
    }

    const apiFacts = {};
    for (const role of ROLES) {
      const token = await apiLogin(apiContext, role.username);
      const headers = { Authorization: `Bearer ${token}` };
      const summaryRes = await apiContext.get(`${API}/dashboard/summary`, { headers });
      const summary = (await summaryRes.json()).data;
      const metricsRes = await apiContext.get(`${API}/dashboard/metrics`, { headers });
      const metrics = (await metricsRes.json()).data;
      const statsRes = await apiContext.get(`${API}/orders/stats`, { headers });
      const stats = (await statsRes.json()).data;
      apiFacts[role.username] = {
        dashboardGmv: Number((summary.orderAmount / 100).toFixed(2)),
        dataGmv: Number(metrics.totalAmount),
        dataOrders: Number(metrics.totalOrders),
        ordersSummary: {
          attributed: Number(stats.attributedOrders),
          unattributed: Number(stats.unattributedOrders),
          partial: Number(stats.partialOrders || 0)
        }
      };
    }
    report.setup.expected = apiFacts;

    const roleFacts = {};
    const case1 = addCase('MR-01', '/dashboard + /data', '三角色在看板与数据页上的口径分别自洽');
    for (const role of ROLES) {
      await withVisibleBrowser(role.username, async (page) => {
        await page.goto(`${FRONTEND}/dashboard`, { waitUntil: 'networkidle', timeout: 30000 });
        await sleep(1500);
        const dashboardCard = page.locator('.stat-card').filter({ hasText: 'GMV' }).first();
        const dashboardText = await dashboardCard.textContent();
        const dashboardGmv = parseNumber(dashboardText);
        case1.screenshots.push(await shot(page, `MR-01-${role.label}-dashboard`));

        await page.goto(`${FRONTEND}/data`, { waitUntil: 'networkidle', timeout: 30000 });
        await sleep(1600);
        const amountCard = page.locator('.metric-card').filter({ hasText: '今日订单总额' }).first();
        const ordersCard = page.locator('.metric-card').filter({ hasText: '今日订单数' }).first();
        const dataGmv = parseNumber(await amountCard.textContent());
        const dataOrders = parseNumber(await ordersCard.textContent());
        case1.screenshots.push(await shot(page, `MR-01-${role.label}-data`));

        roleFacts[role.username] = {
          ...(roleFacts[role.username] || {}),
          dashboardGmv,
          dataGmv,
          dataOrders
        };
      });
    }
    const case1Ok = ROLES.every((role) => {
      const actual = roleFacts[role.username];
      const expected = apiFacts[role.username];
      return actual.dashboardGmv === expected.dashboardGmv
        && actual.dataGmv === expected.dataGmv
        && actual.dataOrders === expected.dataOrders;
    });
    case1.result = case1Ok ? '✅' : '❌';
    case1.note = summarizeRoleFacts(roleFacts);

    const case2 = addCase('MR-02', '/orders', '三角色订单工作台摘要与各自权限范围一致');
    for (const role of ROLES) {
      await withVisibleBrowser(role.username, async (page) => {
        await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle', timeout: 30000 });
        await sleep(1600);
        const summary = await readOrdersSummary(page);
        const rowCount = await page.locator('tbody tr').count().catch(() => 0);
        case2.screenshots.push(await shot(page, `MR-02-${role.label}-orders`));
        roleFacts[role.username] = {
          ...(roleFacts[role.username] || {}),
          ordersSummary: summary,
          ordersRowCount: rowCount
        };
      });
    }
    const case2Ok = ROLES.every((role) => {
      const actual = roleFacts[role.username].ordersSummary;
      const expected = apiFacts[role.username].ordersSummary;
      const expectedTotal = apiFacts[role.username].dataOrders;
      if (expectedTotal === 0) {
        return actual.present === false;
      }
      return actual.present === true
        && actual.attributed === expected.attributed
        && actual.unattributed === expected.unattributed
        && actual.partial === expected.partial;
    });
    case2.result = case2Ok ? '✅' : '❌';
    case2.note = summarizeRoleFacts(roleFacts);

    const case3 = addCase('MR-03', 'cross-role', '角色之间呈现出可解释的收敛关系');
    const admin = roleFacts.admin;
    const biz = roleFacts.biz_leader;
    const channel = roleFacts.channel_leader;
    const expectedAdmin = apiFacts.admin;
    const expectedBiz = apiFacts.biz_leader;
    const expectedChannel = apiFacts.channel_leader;
    const monotonic = admin.dataGmv >= biz.dataGmv
      && admin.dataGmv >= channel.dataGmv
      && admin.dataOrders >= biz.dataOrders
      && admin.dataOrders >= channel.dataOrders;
    const exact = JSON.stringify(expectedAdmin) === JSON.stringify(apiFacts.admin)
      && JSON.stringify(expectedBiz) === JSON.stringify(apiFacts.biz_leader)
      && JSON.stringify(expectedChannel) === JSON.stringify(apiFacts.channel_leader);
    case3.result = monotonic && exact ? '✅' : '❌';
    case3.note = `admin(data=${admin.dataGmv}/${admin.dataOrders}) >= biz(${biz.dataGmv}/${biz.dataOrders}), channel(${channel.dataGmv}/${channel.dataOrders})`;

    const passed = report.cases.filter((item) => item.result === '✅').length;
    const lines = [];
    lines.push(`# 多角色指标对比回归报告 - ${report.generatedAt}`);
    lines.push('');
    lines.push(`- 前端: ${FRONTEND}`);
    lines.push(`- 后端: ${BACKEND}`);
    lines.push(`- 角色: ${ROLES.map((role) => role.label).join(', ')}`);
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
    fs.writeFileSync(REPORT_JSON, JSON.stringify({ ...report, actual: roleFacts }, null, 2), 'utf8');
    console.log(REPORT_MD);
  } finally {
    await apiContext.dispose();
  }
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
