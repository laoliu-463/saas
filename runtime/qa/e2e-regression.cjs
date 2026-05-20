const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));
const {
  postTestAction,
  gotoStable,
  loginViaUi,
  fetchDashboardSummary,
  fetchAuthedApi,
  waitForScopedRows,
  sleep
} = require('./test-api.cjs');

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8080';
const API_BASE = `${BACKEND}/api`;
const SCREENSHOT_DIR = path.join(__dirname, 'screenshots');
const REPORT_PATH = path.join(__dirname, 'e2e-report.json');
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

async function screenshot(page, name) {
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function shortText(text, max = 140) {
  const value = (text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

async function ensureNoFatalOverlay(page) {
  const bodyText = await page.locator('body').innerText({ timeout: 5000 }).catch(() => '');
  return !bodyText.includes('500') && !bodyText.includes('服务器错误') && !bodyText.includes('Unexpected Application Error');
}

async function main() {
  const report = {
    startedAt: new Date().toISOString(),
    precheck: {},
    init: {},
    cases: [],
    bugs: []
  };

  const browser = await chromium.launch({
    headless: true,
    executablePath: EDGE_PATH
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();

  try {
    const frontendResp = await page.goto(FRONTEND, { waitUntil: 'domcontentloaded', timeout: 45000 });
    report.precheck.frontendStatus = frontendResp ? frontendResp.status() : null;

    const apiCtx = await request.newContext();
    const healthResp = await apiCtx.get(`${BACKEND}/api/actuator/health`);
    report.precheck.backendStatus = healthResp.status();
    report.precheck.backendHealth = await healthResp.json().catch(() => null);
    await apiCtx.dispose();

    const loginResult = await loginViaUi(page, context, FRONTEND, 'admin', 'admin123', { expectedPath: '/dashboard' });
    const token = await page.evaluate(() => localStorage.getItem('token'));
    const resetUiResponse = await postTestAction(page.request, API_BASE, token, '/test/reset');
    const seedUiResponse = await postTestAction(page.request, API_BASE, token, '/test/seed');

    report.init = {
      loginPath: loginResult.url,
      resetUiStatus: resetUiResponse.status,
      seedUiStatus: seedUiResponse.status,
      seedShippingSampleId: seedUiResponse.body?.shippingSampleId || null,
      seedProducts: Array.isArray(seedUiResponse.body?.products) ? seedUiResponse.body.products.length : null
    };

    if (resetUiResponse.status !== 200 || seedUiResponse.status !== 200) {
      throw new Error(`测试数据初始化失败: reset=${resetUiResponse.status}, seed=${seedUiResponse.status}`);
    }
    if (!seedUiResponse.body?.shippingSampleId) {
      throw new Error('seed 未返回 shippingSampleId，测试数据可能未写入');
    }

    async function recordCase(id, route, executor) {
      const item = { id, route, result: '❌', note: '', screenshot: null };
      try {
        await executor(item);
      } catch (error) {
        item.result = '❌';
        item.note = `异常: ${error.message}`;
        item.screenshot = await screenshot(page, id.toLowerCase());
        report.bugs.push(`${id} ${route}: ${error.message}`);
      }
      report.cases.push(item);
    }

    await recordCase('T01', '/dashboard', async (item) => {
      await gotoStable(page, `${FRONTEND}/dashboard`);
      const summary = await fetchDashboardSummary(page);
      const orderCount = Number(summary.orderCount || 0);
      const orderAmountCent = Number(summary.orderAmount || 0);
      if (orderCount < 1 || orderAmountCent <= 0) {
        throw new Error(`dashboard 数据异常: orderCount=${orderCount}, orderAmountCent=${orderAmountCent}`);
      }
      item.result = '✅';
      item.note = `订单数 ${orderCount}，GMV(分) ${orderAmountCent}`;
      item.screenshot = await screenshot(page, 't01-dashboard');
    });

    await recordCase('T02', '/data', async (item) => {
      await gotoStable(page, `${FRONTEND}/data`);
      const ok = await ensureNoFatalOverlay(page);
      const body = await page.locator('body').innerText();
      if (!ok || (!body.includes('汇总') && !body.includes('数据') && await page.locator('svg').count() === 0)) {
        throw new Error('数据看板未见图表或汇总信息');
      }
      item.result = '✅';
      item.note = '图表/汇总已加载，无明显报错弹窗';
    });

    await recordCase('T03', '/data/orders', async (item) => {
      await gotoStable(page, `${FRONTEND}/data/orders`);
      const rows = await waitForScopedRows(page, 'data-orders-page', 1, 20000);
      const dataOrdersApi = await fetchAuthedApi(page, '/data/orders?page=1&size=5');
      const apiTotal = Number(dataOrdersApi.data?.total || 0);
      if (rows < 1 && apiTotal < 1) {
        throw new Error(`订单明细数据不足: uiRows=${rows}, apiTotal=${apiTotal}`);
      }
      item.result = '✅';
      item.note = `订单行数: ${rows}, apiTotal=${apiTotal}`;
    });

    await recordCase('T04', '/orders', async (item) => {
      await gotoStable(page, `${FRONTEND}/orders`);
      let dataOrdersApi = await fetchAuthedApi(page, '/data/orders?page=1&size=5');
      let ordersApi = await fetchAuthedApi(page, '/orders?page=1&size=5');
      for (let attempt = 0; attempt < 12; attempt += 1) {
        const dataTotal = Number(dataOrdersApi.data?.total || 0);
        const workbenchTotal = Number(ordersApi.data?.total || 0);
        if (dataTotal >= 1 || workbenchTotal >= 1) break;
        await sleep(500);
        dataOrdersApi = await fetchAuthedApi(page, '/data/orders?page=1&size=5');
        ordersApi = await fetchAuthedApi(page, '/orders?page=1&size=5');
      }
      const dataTotal = Number(dataOrdersApi.data?.total || 0);
      const workbenchTotal = Number(ordersApi.data?.total || 0);
      const rows = await waitForScopedRows(page, 'orders-page', 1, 20000);
      if (dataTotal < 1 && workbenchTotal < 1) {
        throw new Error(`订单工作台无数据: uiRows=${rows}, workbenchTotal=${workbenchTotal}, dataTotal=${dataTotal}`);
      }
      const detailButton = page.getByRole('button', { name: '详情' });
      if (await detailButton.count() > 0) {
        await detailButton.first().click();
      } else if (rows > 0) {
        await page.locator('[data-testid="order-row"]').first().click();
      }
      await page.getByText('订单详情').waitFor({ state: 'visible', timeout: 10000 });
      item.result = '✅';
      item.note = `详情可打开，uiRows=${rows}, workbenchTotal=${workbenchTotal}, dataTotal=${dataTotal}`;
      await page.keyboard.press('Escape').catch(() => {});
    });

    await recordCase('T05', '/product', async (item) => {
      await gotoStable(page, `${FRONTEND}/product`);
      const body = await page.locator('body').innerText();
      const cards = Math.max(await page.locator('.n-card').count(), await page.getByRole('img').count().catch(() => 0));
      if (body.includes('抖音 Token 缺失') || body.includes('获取商品失败')) {
        throw new Error('出现商品错误提示');
      }
      if (cards < 3) throw new Error(`商品数量不足: ${cards}`);
      item.result = '✅';
      item.note = `商品数量约: ${cards}`;
    });

    await recordCase('T06', '/product/activity', async (item) => {
      await gotoStable(page, `${FRONTEND}/product/activity`);
      if (!(await ensureNoFatalOverlay(page))) throw new Error('活动列表存在 500 或白屏');
      item.result = '✅';
      item.note = '活动列表可加载';
    });

    await recordCase('T07', '/talent', async (item) => {
      await gotoStable(page, `${FRONTEND}/talent`);
      const rows = await waitForScopedRows(page, 'talent-page', 1, 20000);
      const talentsApi = await fetchAuthedApi(page, '/talents?page=1&size=5&view=TEAM_PUBLIC');
      const apiTotal = Number(talentsApi.data?.total || talentsApi.data?.records?.length || 0);
      if (rows < 1 && apiTotal < 1) {
        throw new Error(`达人页异常: uiRows=${rows}, apiTotal=${apiTotal}`);
      }
      item.result = '✅';
      item.note = `达人 uiRows=${rows}, apiTotal=${apiTotal}`;
    });

    await recordCase('T08', '/sample', async (item) => {
      await gotoStable(page, `${FRONTEND}/sample`);
      const rows = await waitForScopedRows(page, 'sample-table', 1, 20000);
      const samplesApi = await fetchAuthedApi(page, '/samples?page=1&size=5');
      const apiTotal = Number(samplesApi.data?.total || 0);
      if (rows < 1 && apiTotal < 1) throw new Error(`寄样列表为空: uiRows=${rows}, apiTotal=${apiTotal}`);
      item.result = '✅';
      item.note = `寄样 uiRows=${rows}, apiTotal=${apiTotal}`;
    });

    await recordCase('T09', '/sample/apply', async (item) => {
      await gotoStable(page, `${FRONTEND}/sample/apply`);
      const body = await page.locator('body').innerText();
      if (!(body.includes('商品') && body.includes('达人'))) throw new Error('寄样申请表单字段未正常渲染');
      item.result = '✅';
      item.note = '寄样申请表单可打开';
    });

    await recordCase('T10', '/ops/exclusive', async (item) => {
      await gotoStable(page, `${FRONTEND}/ops/exclusive`);
      const body = await page.locator('body').innerText();
      if (body.includes('403') || !(await ensureNoFatalOverlay(page))) throw new Error('独家状态页异常');
      item.result = '✅';
      item.note = '页面正常加载';
    });

    await recordCase('T11', '/ops/shipping', async (item) => {
      await gotoStable(page, `${FRONTEND}/ops/shipping`);
      if ((await page.locator('body').innerText()).includes('403')) throw new Error('出现 403 页面');
      item.result = '✅';
      item.note = '页面正常加载';
    });

    await recordCase('T12', '/system/users', async (item) => {
      await gotoStable(page, `${FRONTEND}/system/users`);
      const usersApi = await fetchAuthedApi(page, '/users?page=1&size=20');
      const records = Array.isArray(usersApi.data?.records) ? usersApi.data.records : [];
      const hasAdmin = records.some((row) => String(row.username || '').includes('admin'));
      if (!hasAdmin && !(await page.locator('body').innerText()).includes('admin')) {
        throw new Error(`用户列表未见 admin: apiTotal=${usersApi.data?.total || 0}`);
      }
      item.result = '✅';
      item.note = `用户列表已加载（apiTotal=${usersApi.data?.total || records.length}）`;
    });

    await recordCase('T13', '/system/roles', async (item) => {
      await gotoStable(page, `${FRONTEND}/system/roles`);
      const rolesApi = await fetchAuthedApi(page, '/roles?page=1&size=20');
      const records = Array.isArray(rolesApi.data?.records) ? rolesApi.data.records : [];
      const hasPreset = records.some((row) => {
        const code = String(row.roleCode || row.code || '');
        const name = String(row.roleName || row.name || '');
        return code.includes('admin') || name.includes('管理员');
      });
      const body = await page.locator('body').innerText();
      if (!hasPreset && !body.includes('admin') && !body.includes('超级管理员')) {
        throw new Error(`角色列表未见预置角色: apiTotal=${rolesApi.data?.total || 0}`);
      }
      item.result = '✅';
      item.note = `角色列表已加载（apiTotal=${rolesApi.data?.total || records.length}）`;
    });

    await recordCase('T14', '/api/test/orders/generate-attributed', async (item) => {
      let beforeOrders = await fetchAuthedApi(page, '/orders?page=1&size=1');
      const beforeCount = Number(beforeOrders.data?.total || 0);
      const token = await page.evaluate(() => localStorage.getItem('token'));
      const resp = await postTestAction(page.request, API_BASE, token, '/test/orders/generate-attributed');
      if (resp.status !== 200) throw new Error(`生成已归因订单失败: status=${resp.status}`);
      let afterCount = beforeCount;
      for (let attempt = 0; attempt < 12; attempt += 1) {
        await sleep(500);
        const afterOrders = await fetchAuthedApi(page, '/orders?page=1&size=1');
        afterCount = Number(afterOrders.data?.total || 0);
        if (afterCount > beforeCount) break;
      }
      if (afterCount <= beforeCount) {
        throw new Error(`订单数未增长: before=${beforeCount}, after=${afterCount}, orderId=${resp.body?.orderId || 'N/A'}`);
      }
      item.result = '✅';
      item.note = `生成接口 ${resp.status}，/orders 总数: ${beforeCount} -> ${afterCount}`;
      item.screenshot = await screenshot(page, 't14-dashboard-after-gen');
    });

    await recordCase('T15', '/system/users', async (item) => {
      await loginViaUi(page, context, FRONTEND, 'channel_staff', 'admin123', { expectedPath: '/data' });
      await page.goto(`${FRONTEND}/system/users`, { waitUntil: 'domcontentloaded', timeout: 45000 });
      await sleep(1500);
      const currentUrl = page.url().replace(FRONTEND, '');
      const usersApi = await fetchAuthedApi(page, '/users?page=1&size=5');
      const body = await page.locator('body').innerText();
      const redirected = currentUrl !== '/system/users';
      const forbidden = usersApi.status === 403 || usersApi.status === 401;
      const uiBlocked = body.includes('权限不足') || body.includes('无权限') || body.includes('403');
      if (!redirected && !forbidden && !uiBlocked) {
        throw new Error(`channel_staff 访问 /system/users 未拦截: path=${currentUrl}, apiStatus=${usersApi.status}`);
      }
      item.result = '✅';
      item.note = redirected ? `已重定向到: ${currentUrl}` : `接口/页面已拦截（apiStatus=${usersApi.status}）`;
    });

    report.finishedAt = new Date().toISOString();
  } finally {
    await context.close();
    await browser.close();
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8');
  }
}

main().catch((error) => {
  const fallback = {
    startedAt: new Date().toISOString(),
    finishedAt: new Date().toISOString(),
    fatalError: error.message
  };
  fs.mkdirSync(path.dirname(REPORT_PATH), { recursive: true });
  fs.writeFileSync(REPORT_PATH, JSON.stringify(fallback, null, 2), 'utf8');
  console.error(error);
  process.exit(1);
});
