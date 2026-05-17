const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8080';
const API = `${BACKEND}/api`;
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : false;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 1200);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `sample-default-standard-visible-${stamp}`);
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(OUT_DIR, { recursive: true });

const report = { generatedAt: new Date().toISOString(), steps: [] };

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
  return body?.data?.token || body?.data?.accessToken;
}

async function login(page, username, password = 'admin123') {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登/ }).click();
  await page.waitForLoadState('networkidle', { timeout: 30000 });
  await sleep(1500);
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

  try {
    const adminToken = await apiLogin(apiContext, 'admin');
    await apiContext.post(`${API}/test/reset`, { headers: { Authorization: `Bearer ${adminToken}` } });
    await apiContext.post(`${API}/test/seed`, { headers: { Authorization: `Bearer ${adminToken}` } });

    await login(page, 'channel_staff');
    await page.goto(`${FRONTEND}/sample/apply`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1800);

    await page.getByPlaceholder('昵称/达人号').fill('达人F-寄样已拒绝');
    await page.getByRole('button', { name: '搜索达人' }).click();
    await sleep(1500);
    await page.locator('tr', { hasText: '达人F-寄样已拒绝' }).first().getByRole('button', { name: /选择|已选择/ }).click();
    await sleep(800);

    const productSelect = page.locator('.sample-apply .n-base-selection').first();
    await productSelect.click();
    await page.keyboard.type('排查演示商品-未带推广参数');
    await sleep(1200);
    await page.locator('.n-base-select-option', { hasText: '排查演示商品-未带推广参数' }).first().click();
    await sleep(800);

    await page.getByRole('button', { name: '提交申请' }).click();
    await sleep(1200);
    const warningVisible = await page.getByText('达人暂未满足默认寄样标准').count();
    const warningShot = await shot(page, 'step-01-warning-required');
    addStep('不达标达人首次提交触发提醒', warningVisible ? 'PASS' : 'FAIL', warningVisible ? '已弹出提醒并要求填写申请原因' : '未出现预期提醒', warningShot);

    await page.goto(`${FRONTEND}/sample/apply`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1500);
    await page.getByPlaceholder('昵称/达人号').fill('达人F-寄样已拒绝');
    await page.getByRole('button', { name: '搜索达人' }).click();
    await sleep(1500);
    await page.locator('tr', { hasText: '达人F-寄样已拒绝' }).first().getByRole('button', { name: /选择|已选择/ }).click();
    await sleep(800);
    const productSelectRetry = page.locator('.sample-apply .n-base-selection').first();
    await productSelectRetry.click();
    await page.keyboard.type('排查演示商品-未带推广参数');
    await sleep(1200);
    await page.locator('.n-base-select-option', { hasText: '排查演示商品-未带推广参数' }).first().click();
    await sleep(800);
    await page.getByPlaceholder('补充说明').fill('达人近期潜力高，申请特批寄样');
    await sleep(600);
    await page.getByRole('button', { name: '提交申请' }).click();
    await sleep(600);
    await page.getByRole('button', { name: '确认' }).click();
    await page.waitForURL(/\/sample/, { timeout: 30000 });
    await sleep(1500);
    const successShot = await shot(page, 'step-02-submit-with-reason');
    const bodyText = await page.locator('body').innerText();
    addStep('填写申请原因后允许提交', bodyText.includes('寄样申请提交成功') || bodyText.includes('寄样台') ? 'PASS' : 'FAIL', '填写备注后成功进入寄样列表', successShot);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    await apiContext.dispose().catch(() => {});
  }

  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2), 'utf8');
  const lines = [
    '# 寄样默认标准可见验收',
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
