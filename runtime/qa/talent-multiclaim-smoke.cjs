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
const OUT_DIR = path.join(__dirname, 'out', `talent-multiclaim-smoke-${stamp}`);
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

async function waitForIdle(page) {
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await sleep(800);
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
  return body.data;
}

async function bootstrapSession(page, apiContext, username, password = 'admin123') {
  const auth = await apiLogin(apiContext, username, password);
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
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
  await sleep(300);
}

async function resetAndSeed(apiContext) {
  const adminAuth = await apiLogin(apiContext, 'admin');
  const adminApi = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${adminAuth.token}` }
  });
  try {
    const reset = await adminApi.post(`${API}/test/reset`);
    if (!reset.ok()) {
      throw new Error(`reset failed: ${reset.status()}`);
    }
    await sleep(1000);
    const seed = await adminApi.post(`${API}/test/seed`);
    if (!seed.ok()) {
      throw new Error(`seed failed: ${seed.status()}`);
    }
    await sleep(1500);
  } finally {
    await adminApi.dispose();
  }
}

async function findSharedPublicTalent(apiContext, username) {
  const auth = await apiLogin(apiContext, username);
  const scopedApi = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${auth.token}` }
  });
  try {
    const resp = await scopedApi.get(`${API}/talents?page=1&size=20&view=TEAM_PUBLIC`);
    const body = await resp.json().catch(() => ({}));
    const payload = body?.data || body || {};
    const records = Array.isArray(payload.records) ? payload.records : [];
    const available = records.find((item) => item && item.poolStatus !== 'PRIVATE' && !item.blacklisted && Number(item.activeClaimCount || 0) > 0);
    if (!available) {
      throw new Error('TEAM_PUBLIC 视图未找到“他人已认领但仍可继续认领”的达人');
    }
    return {
      id: String(available.id),
      name: String(available.nickname || available.douyinNo || available.uid || available.id),
      activeClaimCount: Number(available.activeClaimCount || 0),
      ownerName: String(available.ownerName || '')
    };
  } finally {
    await scopedApi.dispose();
  }
}

async function openTalentPage(page) {
  await page.goto(`${FRONTEND}/talent?view=TEAM_PUBLIC&page=1&size=10`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
}

async function searchTalent(page, keyword) {
  const input = page.getByPlaceholder('搜索达人昵称 / 抖音号 / UID');
  await input.fill(keyword);
  await sleep(300);
  await page.getByRole('button', { name: '查询' }).click();
  await waitForIdle(page);
}

async function confirmDialog(page, buttonName = '确认认领') {
  await page.getByRole('button', { name: buttonName }).click();
  await sleep(1200);
}

async function firstRowText(page) {
  const row = page.locator('tbody tr').first();
  await row.waitFor({ state: 'visible', timeout: 20000 });
  return row.innerText();
}

async function claimFirstTalent(page, expectedTalentName) {
  if (expectedTalentName) {
    await searchTalent(page, expectedTalentName);
  }
  const rowText = await firstRowText(page);
  const talentName = rowText.split('\n')[0].trim();
  const claimPromise = page.waitForResponse((r) => /\/api\/talents\/.+\/claims$/.test(r.url()) && r.request().method() === 'POST', { timeout: 20000 });
  await page.getByRole('button', { name: '认领' }).first().click();
  await sleep(500);
  await confirmDialog(page);
  const claimResp = await claimPromise;
  await waitForIdle(page);
  return { talentName, status: claimResp.status() };
}

async function talentExistsInMyPool(page, talentName) {
  await page.goto(`${FRONTEND}/talent?view=MY_TALENTS`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
  await searchTalent(page, talentName);
  return (await page.locator('body').innerText()).includes(talentName);
}

async function openDetail(page, talentName) {
  await page.goto(`${FRONTEND}/talent?view=MY_TALENTS`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
  await searchTalent(page, talentName);
  const row = page.locator('tbody tr').filter({ hasText: talentName }).first();
  await row.waitFor({ state: 'visible', timeout: 20000 });
  const rowDetailButton = row.getByRole('button', { name: '查看详情' }).first();
  if (await rowDetailButton.count()) {
    await rowDetailButton.click();
  } else {
    await page.getByRole('button', { name: '查看详情' }).first().click();
  }
  await sleep(1200);
  return page.locator('body').innerText();
}

async function fetchTalentDetail(page, talentId) {
  return page.evaluate(async (id) => {
    const token = localStorage.getItem('token');
    const resp = await fetch(`/api/talents/${id}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    const body = await resp.json().catch(() => ({}));
    return body.data || body || {};
  }, talentId);
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  const browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });

  try {
    await resetAndSeed(apiContext);
    const targetTalent = await findSharedPublicTalent(apiContext, 'channel_leader');

    const smoke = addCase('TALENT-MULTI-CLAIM', '同一达人在已有认领人的情况下仍可被再次认领，并同时出现在两位渠道账号私海');

    const leaderContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
    const leaderPage = await leaderContext.newPage();
    await bootstrapSession(leaderPage, apiContext, 'channel_leader');
    await openTalentPage(leaderPage);
    const leaderClaim = await claimFirstTalent(leaderPage, targetTalent.name);
    await leaderContext.close();

    const staffContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
    const staffPage = await staffContext.newPage();
    await bootstrapSession(staffPage, apiContext, 'channel_staff');
    const staffOwns = await talentExistsInMyPool(staffPage, leaderClaim.talentName);
    await staffContext.close();

    const verifyContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
    const verifyPage = await verifyContext.newPage();
    await bootstrapSession(verifyPage, apiContext, 'channel_leader');
    const leaderOwns = await talentExistsInMyPool(verifyPage, leaderClaim.talentName);
    const detail = await fetchTalentDetail(verifyPage, targetTalent.id);
    const detailOk = Number(detail?.claim?.activeClaimCount || 0) === 2
      && Array.isArray(detail?.claim?.activeClaimOwners)
      && detail.claim.activeClaimOwners.length === 2;

    smoke.screenshots.push(await shot(verifyPage, 'multiclaim-my-talents'));
    smoke.result = targetTalent.activeClaimCount >= 1 && leaderClaim.status === 200 && leaderOwns && staffOwns && detailOk ? '✅' : '❌';
    smoke.note = `seedActiveClaims=${targetTalent.activeClaimCount}; previousOwner=${targetTalent.ownerName}; leader=${leaderClaim.status}; leaderOwns=${leaderOwns}; staffOwns=${staffOwns}; detail=${detailOk}; talent=${leaderClaim.talentName}`;
    await verifyContext.close();
  } finally {
    await browser.close();
    await apiContext.dispose();
  }

  fs.writeFileSync(path.join(OUT_DIR, 'report.json'), JSON.stringify(report, null, 2));
  fs.writeFileSync(
    path.join(OUT_DIR, 'report.md'),
    [
      '# Talent Multi-Claim Smoke Report',
      '',
      `- generatedAt: ${report.generatedAt}`,
      `- frontend: ${report.frontend}`,
      `- backend: ${report.backend}`,
      '',
      '## Cases',
      ...report.cases.map((item) => `- ${item.result} ${item.id} ${item.title} - ${item.note}`)
    ].join('\n') + '\n'
  );
  console.log(`report: ${path.join(OUT_DIR, 'report.md')}`);
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
