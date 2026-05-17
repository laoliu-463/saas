const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));
const { postTestAction } = require('./test-api.cjs');

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8080';
const API_BASE = `${BACKEND}/api`;
const SCREENSHOT_DIR = path.join(__dirname, 'screenshots');
const REPORT_PATH = path.join(__dirname, 'e2e-report.json');
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function screenshot(page, name) {
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function textOrNull(locator) {
  if (await locator.count() === 0) return null;
  const text = await locator.first().innerText().catch(() => null);
  return text ? text.trim() : null;
}

async function countVisible(locator) {
  const count = await locator.count();
  let visible = 0;
  for (let i = 0; i < count; i += 1) {
    if (await locator.nth(i).isVisible().catch(() => false)) {
      visible += 1;
    }
  }
  return visible;
}

function parseFirstMoney(text) {
  if (!text) return null;
  const cleaned = text.replace(/,/g, '');
  const match = cleaned.match(/([0-9]+(?:\.[0-9]+)?)/);
  return match ? Number(match[1]) : null;
}

function parseFirstInteger(text) {
  if (!text) return null;
  const match = text.replace(/,/g, '').match(/([0-9]+)/);
  return match ? Number(match[1]) : null;
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
    const frontendResp = await page.goto(FRONTEND, { waitUntil: 'networkidle', timeout: 30000 });
    report.precheck.frontendStatus = frontendResp ? frontendResp.status() : null;

    const apiCtx = await request.newContext();
    const healthResp = await apiCtx.get(`${BACKEND}/api/actuator/health`);
    report.precheck.backendStatus = healthResp.status();
    report.precheck.backendHealth = await healthResp.json().catch(() => null);
    await apiCtx.dispose();

    await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle' });
    await page.getByPlaceholder('请输入用户名').fill('admin');
    await page.getByPlaceholder('请输入密码').fill('admin123');
    await Promise.all([
      page.waitForURL(/\/dashboard$/, { timeout: 15000 }),
      page.getByRole('button', { name: '登 录' }).click()
    ]);
    report.init.loginUrl = page.url();
    const token = await page.evaluate(() => localStorage.getItem('token'));
    const resetUiResponse = await postTestAction(page.request, API_BASE, token, '/test/reset');
    const seedUiResponse = await postTestAction(page.request, API_BASE, token, '/test/seed');

    report.init = {
      ...report.init,
      resetUiStatus: resetUiResponse.status,
      seedUiStatus: seedUiResponse.status
    };

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
      await page.goto(`${FRONTEND}/dashboard`, { waitUntil: 'networkidle' });
      const body = await page.locator('body').innerText();
      const cardsOk = body.includes('GMV') && body.includes('归因成功率');
      const tableRows = await page.locator('table tbody tr').count();
      const gmvText = await textOrNull(page.getByText(/GMV/i));
      const bodyMoney = parseFirstMoney(body);
      if (!cardsOk || !bodyMoney || bodyMoney <= 0 || tableRows < 1) {
        throw new Error(`dashboard 数据异常: cardsOk=${cardsOk}, bodyMoney=${bodyMoney}, rows=${tableRows}`);
      }
      item.result = '✅';
      item.note = `GMV: ¥${bodyMoney}`;
      item.screenshot = await screenshot(page, 't01-dashboard');
    });

    await recordCase('T02', '/data', async (item) => {
      await page.goto(`${FRONTEND}/data`, { waitUntil: 'networkidle' });
      const ok = await ensureNoFatalOverlay(page);
      const hasSvg = await page.locator('svg').count();
      const body = await page.locator('body').innerText();
      if (!ok || (!hasSvg && !body.includes('汇总') && !body.includes('数据'))) {
        throw new Error('数据看板未见图表或汇总信息');
      }
      item.result = '✅';
      item.note = '图表/汇总已加载，无明显报错弹窗';
    });

    await recordCase('T03', '/data/orders', async (item) => {
      await page.goto(`${FRONTEND}/data/orders`, { waitUntil: 'networkidle' });
      const rows = await page.locator('table tbody tr').count();
      const paginationVisible = await page.getByText('第').count().catch(() => 0);
      if (rows < 1 || paginationVisible < 1) {
        throw new Error(`订单明细数据不足: rows=${rows}, pagination=${paginationVisible}`);
      }
      item.result = '✅';
      item.note = `订单行数: ${rows}`;
    });

    await recordCase('T04', '/orders', async (item) => {
      await page.goto(`${FRONTEND}/orders`, { waitUntil: 'networkidle' });
      const rows = await page.locator('table tbody tr').count();
      if (rows < 1) throw new Error('订单工作台无数据');
      const detailButton = page.getByRole('button', { name: '详情' });
      if (await detailButton.count() > 0) {
        await detailButton.first().click();
      } else {
        await page.locator('table tbody tr').first().click();
      }
      await page.getByText('订单详情').waitFor({ state: 'visible', timeout: 10000 });
      item.result = '✅';
      item.note = `订单列表正常，详情可打开，行数 ${rows}`;
      await page.keyboard.press('Escape').catch(() => {});
    });

    await recordCase('T05', '/product', async (item) => {
      await page.goto(`${FRONTEND}/product`, { waitUntil: 'networkidle' });
      await sleep(1000);
      const body = await page.locator('body').innerText();
      const cards = Math.max(
        await page.locator('.n-card').count(),
        await page.getByRole('img').count().catch(() => 0)
      );
      if (body.includes('抖音 Token 缺失') || body.includes('获取商品失败')) {
        throw new Error('出现商品错误提示');
      }
      if (cards < 3) {
        throw new Error(`商品数量不足: ${cards}`);
      }
      item.result = '✅';
      item.note = `商品数量约: ${cards}`;
    });

    await recordCase('T06', '/product/activity', async (item) => {
      await page.goto(`${FRONTEND}/product/activity`, { waitUntil: 'networkidle' });
      const ok = await ensureNoFatalOverlay(page);
      if (!ok) throw new Error('活动列表存在 500 或白屏');
      item.result = '✅';
      item.note = '活动列表可加载';
    });

    await recordCase('T07', '/talent', async (item) => {
      await page.goto(`${FRONTEND}/talent`, { waitUntil: 'networkidle' });
      const body = await page.locator('body').innerText();
      const rows = await page.locator('table tbody tr').count();
      if (!body.includes('达人') || rows < 1) {
        throw new Error(`达人页异常: rows=${rows}`);
      }
      item.result = '✅';
      item.note = `达人数量(表格行): ${rows}`;
    });

    await recordCase('T08', '/sample', async (item) => {
      await page.goto(`${FRONTEND}/sample`, { waitUntil: 'networkidle' });
      const rows = await page.locator('table tbody tr').count();
      if (rows < 1) throw new Error('寄样列表为空');
      item.result = '✅';
      item.note = `寄样记录数: ${rows}`;
    });

    await recordCase('T09', '/sample/apply', async (item) => {
      await page.goto(`${FRONTEND}/sample/apply`, { waitUntil: 'networkidle' });
      const body = await page.locator('body').innerText();
      const formVisible = body.includes('商品') && body.includes('达人');
      if (!formVisible) throw new Error('寄样申请表单字段未正常渲染');
      item.result = '✅';
      item.note = '寄样申请表单可打开';
    });

    await recordCase('T10', '/ops/exclusive', async (item) => {
      await page.goto(`${FRONTEND}/ops/exclusive`, { waitUntil: 'networkidle' });
      const body = await page.locator('body').innerText();
      if (body.includes('403') || body.includes('无权限') || !(await ensureNoFatalOverlay(page))) {
        throw new Error('独家状态页出现 403/白屏');
      }
      item.result = '✅';
      item.note = '页面正常加载';
    });

    await recordCase('T11', '/ops/shipping', async (item) => {
      await page.goto(`${FRONTEND}/ops/shipping`, { waitUntil: 'networkidle' });
      const currentUrl = page.url();
      const body = await page.locator('body').innerText();
      if (body.includes('403')) throw new Error('出现 403 页面');
      item.result = '✅';
      item.note = currentUrl.endsWith('/ops/shipping') ? '页面正常加载' : `实际跳转到: ${currentUrl.replace(FRONTEND, '')}`;
    });

    await recordCase('T12', '/system/users', async (item) => {
      await page.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle' });
      const body = await page.locator('body').innerText();
      if (!body.includes('admin')) throw new Error('未见 admin 用户');
      item.result = '✅';
      item.note = '用户列表已加载，admin 可见';
    });

    await recordCase('T13', '/system/roles', async (item) => {
      await page.goto(`${FRONTEND}/system/roles`, { waitUntil: 'networkidle' });
      const body = await page.locator('body').innerText();
      if (!body.includes('admin') && !body.includes('超级管理员')) {
        throw new Error('角色列表未见预置角色');
      }
      item.result = '✅';
      item.note = '角色列表已加载';
    });

    let dashboardBefore = null;
    await recordCase('T14', '/api/test/orders/generate-attributed', async (item) => {
      await page.goto(`${FRONTEND}/dashboard`, { waitUntil: 'networkidle' });
      dashboardBefore = parseFirstMoney(await page.locator('body').innerText());
      const token = await page.evaluate(() => localStorage.getItem('token'));
      const resp = await postTestAction(page.request, API_BASE, token, '/test/orders/generate-attributed');
      await page.goto(`${FRONTEND}/dashboard`, { waitUntil: 'networkidle' });
      const dashboardAfter = parseFirstMoney(await page.locator('body').innerText());
      if (!dashboardBefore || !dashboardAfter || dashboardAfter <= dashboardBefore) {
        throw new Error(`GMV 未增长: before=${dashboardBefore}, after=${dashboardAfter}`);
      }
      item.result = '✅';
      item.note = `生成接口 ${resp.status}，GMV: ¥${dashboardBefore} -> ¥${dashboardAfter}`;
      item.screenshot = await screenshot(page, 't14-dashboard-after-gen');
    });

    await recordCase('T15', '/system/users', async (item) => {
      await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle' });
      await page.getByPlaceholder('请输入用户名').fill('channel_staff');
      await page.getByPlaceholder('请输入密码').fill('admin123');
      await Promise.all([
        page.waitForURL(/\/dashboard$/, { timeout: 15000 }),
        page.getByRole('button', { name: '登 录' }).click()
      ]);
      await page.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle' });
      const currentUrl = page.url();
      const body = await page.locator('body').innerText();
      const redirected = !currentUrl.endsWith('/system/users');
      if (!redirected && !body.includes('权限不足')) {
        throw new Error(`channel_staff 访问 /system/users 未见重定向，当前 ${currentUrl}`);
      }
      item.result = '✅';
      item.note = redirected ? `已重定向到: ${currentUrl.replace(FRONTEND, '')}` : '未进入目标页，展示权限限制';
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
