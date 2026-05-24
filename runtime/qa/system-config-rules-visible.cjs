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
const OUT_DIR = path.join(__dirname, 'out', `system-config-rules-visible-${stamp}`);
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(OUT_DIR, { recursive: true });

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  backend: BACKEND,
  steps: [],
  sampleRule: {},
  talentRule: {}
};

function pushStep(name, status, note, screenshot) {
  report.steps.push({ name, status, note, screenshot: screenshot ? path.basename(screenshot) : null });
}

function daysBetween(from, to) {
  return Math.round((to.getTime() - from.getTime()) / (24 * 60 * 60 * 1000));
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
  await sleep(1500);
  return shot(page, screenshotName);
}

async function login(page, username, password = 'admin123') {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch((error) => {
    if (!String(error?.message || error).includes('interrupted by another navigation')) {
      throw error;
    }
  });
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  const respPromise = page.waitForResponse((resp) =>
    resp.url().includes('/api/auth/login') && resp.request().method() === 'POST',
    { timeout: 15000 }
  ).catch(() => null);
  await page.getByRole('button', { name: /登/ }).click();
  const resp = await respPromise;
  if (!resp || !resp.ok()) {
    throw new Error(`登录失败: ${username} ${resp ? resp.status() : 'no-response'}`);
  }
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await sleep(1500);
}

async function clearSession(page) {
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  }).catch(() => {});
  await page.context().clearCookies();
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
  let channelToken;
  let channelLeaderToken;
  let sampleConfigOriginal;
  let talentConfigOriginal;
  let commissionConfigOriginal;

  try {
    adminToken = await apiLogin(apiContext, 'admin');
    channelToken = await apiLogin(apiContext, 'channel_staff');
    channelLeaderToken = await apiLogin(apiContext, 'channel_leader');

    await apiJson(apiContext, 'POST', `${API}/test/reset`, adminToken);
    await apiJson(apiContext, 'POST', `${API}/test/seed`, adminToken);

    sampleConfigOriginal = await getConfigRecord(apiContext, adminToken, 'sample.restrict_days');
    talentConfigOriginal = await getConfigRecord(apiContext, adminToken, 'talent.protection_days');
    commissionConfigOriginal = await getConfigRecord(apiContext, adminToken, 'commission.business_default_ratio');

    await login(page, 'admin');
    await page.goto(`${FRONTEND}/system/config`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1500);

    const sampleConfigShot = await updateConfigViaUi(page, 'sample.restrict_days', '1', 'step-01-sample-restrict-days');
    pushStep('管理员调整寄样限制天数', 'PASS', '将 sample.restrict_days 从默认值改为 1', sampleConfigShot);

    const sampleConfigSecondShot = await updateConfigViaUi(page, 'sample.restrict_days', '2', 'step-01b-sample-restrict-days-second-update');
    pushStep('管理员再次调整寄样限制天数', 'PASS', '将 sample.restrict_days 从 1 改为 2，用于验证规则读取即时失效', sampleConfigSecondShot);

    const talentConfigShot = await updateConfigViaUi(page, 'talent.protection_days', '15', 'step-02-talent-protection-days');
    pushStep('管理员调整达人保护期', 'PASS', '将 talent.protection_days 从默认值改为 15', talentConfigShot);

    const commissionConfigShot = await updateConfigViaUi(page, 'commission.business_default_ratio', '0.16', 'step-02b-commission-business-ratio');
    pushStep('管理员调整招商默认提成比例', 'PASS', '将 commission.business_default_ratio 调整为 0.16', commissionConfigShot);

    const talentCandidates = await apiJson(apiContext, 'GET', `${API}/samples/talent-candidates?keyword=&page=1&size=20`, channelToken);
    const productCandidates = await apiJson(apiContext, 'GET', `${API}/samples/product-candidates?page=1&size=20`, channelToken);
    const targetTalent = (talentCandidates.records || [])[0];
    const targetProduct = (productCandidates.records || [])[0];
    if (!targetTalent || !targetProduct) {
      throw new Error('缺少寄样候选达人或商品');
    }

    await apiJson(apiContext, 'POST', `${API}/samples`, channelToken, {
      talentId: targetTalent.talentId,
      talentNickname: targetTalent.nickname,
      talentFansCount: targetTalent.fansCount,
      talentCreditScore: targetTalent.creditScore,
      talentMainCategory: targetTalent.mainCategory,
      productId: targetProduct.id,
      quantity: 1,
      remark: 'system-config-visible-test'
    });

    await clearSession(page);
    await login(page, 'channel_staff');
    await page.goto(`${FRONTEND}/sample/apply`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1500);

    await page.getByPlaceholder('昵称/达人号').fill(targetTalent.nickname);
    await page.getByRole('button', { name: /搜索我的达人|搜索达人/ }).click();
    await sleep(1500);
    await page.locator('tr', { hasText: targetTalent.nickname }).first().getByRole('button', { name: /选择|已选择/ }).click();
    await sleep(800);

    const productSelect = page.locator('.sample-apply .n-base-selection').first();
    await productSelect.click();
    const targetProductLabel = targetProduct.productName || targetProduct.productId || '';
    await page.keyboard.type(targetProductLabel);
    await sleep(1200);
    const exactOption = page.locator('.n-base-select-option', { hasText: targetProductLabel }).first();
    if (await exactOption.count()) {
      await exactOption.click();
    } else {
      await page.locator('.n-base-select-option').first().click();
    }
    await sleep(800);

    await page.getByPlaceholder('例如：短视频测品').fill('规则验证：重复寄样限制');
    await page.getByPlaceholder('请输入收货人').fill('规则验证收货人');
    await page.getByPlaceholder('请输入手机号').fill('13900000002');
    await page.getByPlaceholder('请输入完整收货地址').fill('规则验证地址');
    await page.getByPlaceholder('可填写发货偏好、沟通备注等').fill('规则验证：重复寄样限制');
    await sleep(300);

    const submitRespPromise = page.waitForResponse(
      (resp) => {
        if (resp.request().method() !== 'POST') return false;
        const url = resp.url();
        return url.endsWith('/api/samples') || url.includes('/api/samples?');
      },
      { timeout: 15000 }
    ).catch(() => null);
    await page.getByRole('button', { name: '提交申请' }).click();
    await sleep(1000);
    const confirmButton = page.getByRole('button', { name: '确认' }).last();
    if (await confirmButton.count()) {
      await confirmButton.click().catch(() => {});
      await sleep(1800);
    }
    const submitResp = await submitRespPromise;
    const submitBody = submitResp ? await submitResp.json().catch(() => ({})) : {};
    const sampleErrorText = await page.locator('body').innerText();
    const sampleBlocked = submitBody?.code === 460
      || sampleErrorText.includes('within 2 days')
      || sampleErrorText.includes('Duplicate sample request is blocked within 2 days');
    const sampleShot = await shot(page, 'step-03-sample-duplicate-blocked');
    report.sampleRule = {
      talent: targetTalent.nickname,
      product: targetProduct.productName || targetProduct.productId,
      blocked: sampleBlocked,
      responseCode: submitBody?.code ?? null,
      responseMsg: submitBody?.msg ?? null
    };
    pushStep(
      '渠道重复申请寄样触发新规则',
      sampleBlocked ? 'PASS' : 'FAIL',
      sampleBlocked
        ? `重复申请被 2 天限制拦截（code=${submitBody?.code ?? 'N/A'}）`
        : '页面与接口均未出现预期限制提示',
      sampleShot
    );

    const publicPage = await apiJson(apiContext, 'GET', `${API}/talents?page=1&size=10&view=TEAM_PUBLIC`, channelLeaderToken);
    const claimTarget = (publicPage.records || [])[0];
    if (!claimTarget) {
      throw new Error('当前没有可认领的公海达人');
    }

    await clearSession(page);
    await login(page, 'channel_leader');
    await page.goto(`${FRONTEND}/talent?view=TEAM_PUBLIC&page=1&size=10`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1800);
    const talentRow = page.locator('tr', { hasText: claimTarget.nickname }).first();
    await talentRow.scrollIntoViewIfNeeded();
    await talentRow.getByRole('button', { name: '认领' }).click();
    await sleep(600);
    await page.getByRole('button', { name: '确认认领' }).click();
    await sleep(1800);
    await page.goto(`${FRONTEND}/talent?view=MY_TALENTS&page=1&size=10`, { waitUntil: 'networkidle', timeout: 30000 });
    await sleep(1800);
    const claimedRow = page.locator('tr', { hasText: claimTarget.nickname }).first();
    if (await claimedRow.count()) {
      await claimedRow.scrollIntoViewIfNeeded().catch(() => {});
      const detailButton = claimedRow.getByRole('button', { name: '查看详情' }).first();
      if (await detailButton.count()) {
        await detailButton.click().catch(() => {});
        await sleep(1800);
      }
    }
    const talentShot = await shot(page, 'step-04-talent-claim-protection');

    const detail = await apiJson(apiContext, 'GET', `${API}/talents/${claimTarget.id}`, channelLeaderToken);
    const protectedUntil = detail?.claim?.protectedUntil ? new Date(detail.claim.protectedUntil) : null;
    const protectionDays = protectedUntil ? daysBetween(new Date(), protectedUntil) : null;
    const protectionMatched = protectionDays !== null && protectionDays >= 14 && protectionDays <= 16;
    report.talentRule = {
      talent: claimTarget.nickname,
      protectedUntil: detail?.claim?.protectedUntil || null,
      protectionDays
    };
    pushStep(
      '达人认领后保护期按新配置生效',
      protectionMatched ? 'PASS' : 'FAIL',
      protectionMatched ? `保护期约 ${protectionDays} 天` : `保护期天数异常: ${protectionDays}`,
      talentShot
    );
  } finally {
    if (adminToken && sampleConfigOriginal && talentConfigOriginal) {
      try {
        await apiJson(apiContext, 'PUT', `${API}/configs/${sampleConfigOriginal.id}`, adminToken, {
          configValue: sampleConfigOriginal.configValue
        });
        await apiJson(apiContext, 'PUT', `${API}/configs/${talentConfigOriginal.id}`, adminToken, {
          configValue: talentConfigOriginal.configValue
        });
        if (commissionConfigOriginal?.id) {
          await apiJson(apiContext, 'PUT', `${API}/configs/${commissionConfigOriginal.id}`, adminToken, {
            configValue: commissionConfigOriginal.configValue
          });
        }
      } catch (error) {
        report.restoreError = String(error);
      }
    }
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    await apiContext.dispose().catch(() => {});
  }

  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2), 'utf8');
  const lines = [
    '# 本地规则中心可见验收',
    '',
    `- 生成时间：${report.generatedAt}`,
    `- 前端：${FRONTEND}`,
    `- 后端：${BACKEND}`,
    '',
    '| 步骤 | 结果 | 说明 | 截图 |',
    '| --- | --- | --- | --- |',
    ...report.steps.map((item) => `| ${item.name} | ${item.status === 'PASS' ? '✅ PASS' : '❌ FAIL'} | ${item.note} | ${item.screenshot || '-'} |`)
  ];
  fs.writeFileSync(REPORT_MD, lines.join('\n'), 'utf8');
  console.log(JSON.stringify({
    ok: report.steps.every((item) => item.status === 'PASS'),
    outDir: OUT_DIR,
    report: REPORT_MD
  }, null, 2));
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
