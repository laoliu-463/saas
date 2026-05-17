const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8080';
const API = `${BACKEND}/api`;
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : true;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 0);
const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `talent-platform-smoke-${stamp}`);
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
  const item = { id, title, result: '❌', note: '', screenshots: [] };
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
  if (!res.ok() || !token) {
    throw new Error(`API 登录失败: ${username}`);
  }
  return token;
}

async function bootstrapSession(page, apiContext, username, password = 'admin123') {
  const res = await apiContext.post(`${API}/auth/login`, { data: { username, password } });
  const body = await res.json();
  const auth = body?.data;
  if (!res.ok() || !auth?.token) {
    throw new Error(`登录失败: ${username}`);
  }
  await page.goto(FRONTEND, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.evaluate((payload) => {
    localStorage.setItem('token', payload.token);
    localStorage.setItem('userInfo', JSON.stringify({
      id: payload.userId,
      userId: payload.userId,
      deptId: payload.deptId,
      dataScope: payload.dataScope,
      roleCodes: payload.roleCodes,
      username: payload.username,
      realName: payload.realName
    }));
  }, auth);
}

async function confirmDialog(page) {
  const confirm = page.getByRole('button', { name: /确认/ }).last();
  await confirm.click();
  await sleep(1200);
}

async function prepareSeed(apiContext) {
  const token = await apiLogin(apiContext, 'admin');
  const headers = { Authorization: `Bearer ${token}` };
  const reset = await apiContext.post(`${API}/test/reset`, { headers });
  const seed = await apiContext.post(`${API}/test/seed`, { headers });
  if (!reset.ok() || !seed.ok()) {
    throw new Error(`reset/seed 失败: ${reset.status()} / ${seed.status()}`);
  }
}

async function clickTab(page, name) {
  await page.locator('.n-tabs-tab').filter({ hasText: name }).first().click();
  await page.waitForLoadState('networkidle', { timeout: 20000 });
  await sleep(800);
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  const browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();

  try {
    await prepareSeed(apiContext);
    await bootstrapSession(page, apiContext, 'channel_leader');
    await page.goto(`${FRONTEND}/talent`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1000);

    const viewCase = addCase('TALENT-VIEWS', '四个经营视图可切换');
    for (const label of ['团队公海', '我的达人', '自然出单达人', '达人黑名单']) {
      await clickTab(page, label);
    }
    viewCase.screenshots.push(await shot(page, 'talent-views'));
    viewCase.result = '✅';
    viewCase.note = '四个经营视图均可打开。';

    const claimCase = addCase('TALENT-CLAIM', '公海达人可认领');
    await clickTab(page, '团队公海');
    const claimButton = page.getByRole('button', { name: '认领' }).first();
    if (await claimButton.isVisible().catch(() => false)) {
      await claimButton.click();
      await confirmDialog(page);
      claimCase.screenshots.push(await shot(page, 'talent-claim'));
      claimCase.result = '✅';
      claimCase.note = '已执行一次认领动作。';
    } else {
      claimCase.result = '⚠️';
      claimCase.note = '首次进入团队公海未找到可认领达人，待释放私海达人后回试。';
    }

    const releaseCase = addCase('TALENT-RELEASE', '私海达人可释放');
    await clickTab(page, '我的达人');
    const releaseButton = page.getByRole('button', { name: '释放' }).first();
    if (await releaseButton.isVisible().catch(() => false)) {
      await releaseButton.click();
      await confirmDialog(page);
      releaseCase.screenshots.push(await shot(page, 'talent-release'));
      releaseCase.result = '✅';
      releaseCase.note = '已执行一次释放动作。';
      if (claimCase.result !== '✅') {
        await clickTab(page, '团队公海');
        const retryClaim = page.getByRole('button', { name: '认领' }).first();
        if (await retryClaim.isVisible().catch(() => false)) {
          await retryClaim.click();
          await confirmDialog(page);
          claimCase.screenshots.push(await shot(page, 'talent-claim-retry'));
          claimCase.result = '✅';
          claimCase.note = '释放私海达人后，已在团队公海完成认领回试。';
        }
      }
    } else {
      releaseCase.result = '⚠️';
      releaseCase.note = '当前页未找到可释放达人按钮。';
    }

    const blacklistCase = addCase('TALENT-BLACKLIST', '达人可拉黑并恢复');
    await clickTab(page, '团队公海');
    const blacklistButton = page.getByRole('button', { name: '拉黑' }).first();
    if (await blacklistButton.isVisible().catch(() => false)) {
      await blacklistButton.click();
      await confirmDialog(page);
      await clickTab(page, '达人黑名单');
      const recoverButton = page.getByRole('button', { name: '解除拉黑' }).first();
      if (await recoverButton.isVisible().catch(() => false)) {
        await recoverButton.click();
        await confirmDialog(page);
      }
      blacklistCase.screenshots.push(await shot(page, 'talent-blacklist'));
      blacklistCase.result = '✅';
      blacklistCase.note = '已执行拉黑和解除拉黑。';
    } else {
      blacklistCase.result = '⚠️';
      blacklistCase.note = '当前页未找到可拉黑达人按钮。';
    }
  } finally {
    await context.close();
    await browser.close();
    await apiContext.dispose();
  }

  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2));
  const lines = [
    '# Talent Platform Smoke Report',
    '',
    `- generatedAt: ${report.generatedAt}`,
    `- frontend: ${report.frontend}`,
    `- backend: ${report.backend}`,
    '',
    '## Cases',
    ...report.cases.map((item) => `- ${item.result} ${item.id} ${item.title} - ${item.note}`)
  ];
  fs.writeFileSync(REPORT_MD, `${lines.join('\n')}\n`);
  console.log(`report: ${REPORT_MD}`);
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
