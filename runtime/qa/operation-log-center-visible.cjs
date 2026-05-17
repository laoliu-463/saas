const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3001';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8081';
const API = `${BACKEND}/api`;
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : false;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 1200);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `operation-log-center-visible-${stamp}`);
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(OUT_DIR, { recursive: true });

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  backend: BACKEND,
  steps: []
};

function addStep(name, status, note, screenshot) {
  report.steps.push({ name, status, note, screenshot: screenshot ? path.basename(screenshot) : null });
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
  if (!res.ok() || !token) {
    throw new Error(`API 登录失败: ${username}`);
  }
  return token;
}

async function apiJson(apiContext, method, url, token, data) {
  const res = await apiContext.fetch(url, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok()) {
    throw new Error(`${method} ${url} 失败: ${res.status()} ${JSON.stringify(body)}`);
  }
  return body?.data ?? body;
}

async function getConfigRecord(apiContext, adminToken, key) {
  const res = await apiContext.get(`${API}/configs`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    params: { page: 1, size: 20, keyword: key }
  });
  const body = await res.json();
  const records = body?.data?.records || [];
  const record = records.find((item) => item.configKey === key);
  if (!record) {
    throw new Error(`未找到配置项: ${key}`);
  }
  return record;
}

async function findOperationLog(apiContext, token, params) {
  const res = await apiContext.get(`${API}/operation-logs`, {
    headers: { Authorization: `Bearer ${token}` },
    params
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok()) {
    throw new Error(`查询操作日志失败: ${res.status()} ${JSON.stringify(body)}`);
  }
  const page = body?.data ?? {};
  return page.records || [];
}

async function login(page, username, password = 'admin123') {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登/ }).click();
  await page.waitForLoadState('networkidle', { timeout: 30000 });
  await sleep(1500);
}

async function updateConfigViaUi(page, key, value, screenshotName) {
  const searchInput = page.getByPlaceholder('搜索配置键或名称');
  await searchInput.fill(key);
  await page.getByRole('button', { name: '查询' }).click();
  await sleep(1500);
  const row = page.locator('tr', { hasText: key }).first();
  await row.scrollIntoViewIfNeeded();
  await row.getByRole('button', { name: '编辑' }).click();
  await sleep(800);
  const modal = page.locator('.n-modal');
  await modal.getByPlaceholder('配置值').fill(String(value));
  await modal.getByRole('button', { name: '确定' }).click();
  await sleep(1800);
  return shot(page, screenshotName);
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  const browser = await chromium.launch({
    headless: HEADLESS,
    slowMo: SLOW_MO,
    executablePath: fs.existsSync(EDGE_PATH) ? EDGE_PATH : undefined
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();

  let adminToken;
  let originalConfig;

  try {
    adminToken = await apiLogin(apiContext, 'admin');
    await apiJson(apiContext, 'POST', `${API}/test/reset`, adminToken);
    await apiJson(apiContext, 'POST', `${API}/test/seed`, adminToken);
    originalConfig = await getConfigRecord(apiContext, adminToken, 'sample.restrict_days');

    await login(page, 'admin');
    await page.goto(`${FRONTEND}/system/config`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1500);

    const configShot = await updateConfigViaUi(page, 'sample.restrict_days', '3', 'step-01-config-updated');
    addStep('管理员修改系统配置', 'PASS', '将 sample.restrict_days 更新为 3，触发关键变更日志', configShot);

    await page.goto(`${FRONTEND}/system/operation-logs`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1800);
    await page.getByPlaceholder('操作人').fill('admin');
    await page.getByPlaceholder('模块').fill('系统配置');
    await page.getByRole('button', { name: '查询' }).click();
    await sleep(1800);

    const bodyText = await page.locator('body').innerText();
    const foundLog = bodyText.includes('系统配置') && bodyText.includes('更新配置') && bodyText.includes('PUT');
    const logShot = await shot(page, 'step-03-log-center-config-visible');
    addStep(
      '日志中心显示配置变更记录',
      foundLog ? 'PASS' : 'FAIL',
      foundLog ? '操作日志中心成功显示 sample.restrict_days 的更新记录' : '未在日志中心看到预期配置更新记录',
      logShot
    );
  } finally {
    if (adminToken && originalConfig) {
      await apiJson(apiContext, 'PUT', `${API}/configs/${originalConfig.id}`, adminToken, {
        configValue: originalConfig.configValue
      }).catch(() => {});
    }
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    await apiContext.dispose().catch(() => {});
  }

  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2), 'utf8');
  const lines = [
    '# 操作日志中心可见验收',
    '',
    `- 生成时间：${report.generatedAt}`,
    '',
    '| 步骤 | 结果 | 说明 | 截图 |',
    '| --- | --- | --- | --- |',
    ...report.steps.map((item) => `| ${item.name} | ${item.status === 'PASS' ? '✅ PASS' : '❌ FAIL'} | ${item.note} | ${item.screenshot || '-'} |`)
  ];
  fs.writeFileSync(REPORT_MD, lines.join('\n'), 'utf8');
  console.log(JSON.stringify({ ok: report.steps.every((item) => item.status === 'PASS'), outDir: OUT_DIR, report: REPORT_MD }, null, 2));
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
