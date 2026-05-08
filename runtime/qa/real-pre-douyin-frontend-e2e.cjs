const fs = require('node:fs');
const path = require('node:path');

function loadPlaywright() {
  const candidates = [
    'playwright',
    path.join(
      process.env.USERPROFILE || process.env.HOME || '',
      '.cache',
      'codex-runtimes',
      'codex-primary-runtime',
      'dependencies',
      'node',
      'node_modules',
      'playwright'
    )
  ];

  for (const candidate of candidates) {
    try {
      return require(candidate);
    } catch (_error) {
      // Try the next candidate.
    }
  }

  throw new Error('未找到 playwright，请先安装依赖或提供 Codex bundled runtime。');
}

const { chromium, request } = loadPlaywright();

const FRONTEND = process.env.FRONTEND_URL || 'http://localhost:3001';
const BACKEND = process.env.BACKEND_URL || 'http://localhost:8081';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(process.cwd(), 'runtime', 'qa', 'out', `real-pre-douyin-frontend-${stamp}`);
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'results.json');

fs.mkdirSync(OUT_DIR, { recursive: true });

const report = {
  generatedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
  frontend: FRONTEND,
  backend: BACKEND,
  cases: []
};

function addCase(id, title) {
  const item = { id, title, result: 'PENDING', note: '', screenshots: [] };
  report.cases.push(item);
  return item;
}

async function shot(page, name) {
  const file = path.join(OUT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function apiLogin() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const res = await apiContext.post('/api/auth/login', {
      data: { username: 'admin', password: 'admin123' }
    });
    const body = await res.json();
    const data = body?.data || {};
    if (!res.ok() || !data?.token) {
      throw new Error(`管理员登录失败: HTTP ${res.status()} ${body?.msg || ''}`);
    }
    return data;
  } finally {
    await apiContext.dispose();
  }
}

async function assertVisibleText(page, text) {
  await page.getByText(text, { exact: true }).first().waitFor({ state: 'visible', timeout: 60000 });
}

function writeReport() {
  const passed = report.cases.filter((item) => item.result === 'PASS').length;
  const lines = [];
  lines.push(`# Real-Pre 抖店前端 E2E 报告 - ${report.generatedAt}`);
  lines.push('');
  lines.push(`- 前端: ${report.frontend}`);
  lines.push(`- 后端: ${report.backend}`);
  lines.push(`- 通过: ${passed}/${report.cases.length}`);
  lines.push('');
  lines.push('| 编号 | 结果 | 说明 |');
  lines.push('| --- | --- | --- |');
  for (const item of report.cases) {
    lines.push(`| ${item.id} | ${item.result} | ${String(item.note).replace(/\|/g, '/')} |`);
  }
  lines.push('');
  lines.push('## 截图');
  for (const item of report.cases) {
    lines.push(`- ${item.id}: ${item.screenshots.join(' ; ') || '-'}`);
  }
  fs.writeFileSync(REPORT_MD, lines.join('\n'), 'utf8');
  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2), 'utf8');
}

async function run() {
  const browser = await chromium.launch({ headless: true });
  const auth = await apiLogin();
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  await context.addInitScript((payload) => {
    localStorage.setItem('token', payload.token);
    localStorage.setItem('refreshToken', payload.refreshToken || '');
    localStorage.setItem('refreshExpiresIn', String(payload.refreshExpiresIn || ''));
    localStorage.setItem('accessTokenExpiresIn', String(payload.accessTokenExpiresIn || payload.expiresIn || ''));
    localStorage.setItem('userInfo', JSON.stringify(payload));
  }, auth);
  const page = await context.newPage();
  const flow = addCase('DY-FE-01', '管理员在 real-pre 前端完成抖店联调状态汇总');

  try {
    await page.goto(`${FRONTEND}/system/douyin`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.getByPlaceholder('活动ID').fill('');
    await page.getByRole('button', { name: '一键刷新联调状态' }).click();

    await assertVisibleText(page, 'Token 正常');
    await assertVisibleText(page, '授权主体正常');
    await assertVisibleText(page, '活动商品已刷新');
    await assertVisibleText(page, '订单同步成功');
    await assertVisibleText(page, 'Dashboard 已读取真实订单');
    await assertVisibleText(page, '店铺侧订单权限待补齐');

    flow.screenshots.push(await shot(page, 'DY-FE-01-douyin-integration'));
    flow.result = 'PASS';
    flow.note = 'Token、授权主体、活动商品、订单同步、Dashboard 与店铺侧权限阻塞均已在前端可见。';
  } catch (error) {
    flow.result = 'FAIL';
    flow.note = error?.message || String(error);
    try {
      flow.screenshots.push(await shot(page, 'DY-FE-01-failure'));
    } catch (_shotError) {
      // Ignore screenshot failures so the original test failure remains visible.
    }
    throw error;
  } finally {
    writeReport();
    await context.close();
    await browser.close();
  }
}

run()
  .then(() => {
    console.log(REPORT_MD);
  })
  .catch((error) => {
    console.error(error);
    console.error(`Report: ${REPORT_MD}`);
    process.exitCode = 1;
  });
