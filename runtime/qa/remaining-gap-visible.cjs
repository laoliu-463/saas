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
const OUT_DIR = path.join(REPO_ROOT, 'out', `e2e-gap-followup-${stamp}`);
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'results.json');
fs.mkdirSync(OUT_DIR, { recursive: true });

const report = {
  frontend: FRONTEND,
  backend: BACKEND,
  generatedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
  cases: [],
  notes: [],
  setup: {}
};

function addCase(id, title, pathName) {
  const item = { id, title, path: pathName, result: '❌', note: '', screenshots: [] };
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

async function login(page, username, password = 'admin123') {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登/ }).click();
  await page.waitForLoadState('networkidle', { timeout: 20000 });
  await sleep(800);
}

async function apiLogin(apiContext, username, password = 'admin123') {
  const res = await apiContext.post(`${API}/auth/login`, {
    data: { username, password }
  });
  const body = await res.json();
  const token = body?.data?.token || body?.data?.accessToken;
  if (!res.ok() || !token) {
    throw new Error(`登录失败: ${username}`);
  }
  return token;
}

async function setupSeed(apiContext) {
  const token = await apiLogin(apiContext, 'admin');
  const headers = { Authorization: `Bearer ${token}` };
  const resetRes = await apiContext.post(`${API}/test/reset`, { headers });
  const resetBody = await resetRes.json();
  const seedRes = await apiContext.post(`${API}/test/seed`, { headers });
  const seedBody = await seedRes.json();
  report.setup.resetStatus = resetRes.status();
  report.setup.seedStatus = seedRes.status();
  report.setup.seed = seedBody?.data || {};
  if (!resetRes.ok() || !seedRes.ok()) {
    throw new Error(`Reset/Seed 失败: ${resetRes.status()} / ${seedRes.status()}`);
  }
  return { token, resetBody, seedBody };
}

async function fetchSamplesByStatus(apiContext, token, status) {
  const res = await apiContext.get(`${API}/samples`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { page: 1, size: 50, status }
  });
  const body = await res.json();
  return body?.data?.records || [];
}

async function withVisibleBrowser(caseItem, username, fn) {
  const browser = await chromium.launch({
    headless: false,
    executablePath: EDGE_PATH,
    slowMo: 250
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();
  try {
    await login(page, username);
    await fn({ browser, context, page });
  } finally {
    await context.close();
    await browser.close();
  }
}

function summarizeShots(caseItem) {
  return caseItem.screenshots.map((p) => path.basename(p)).join(', ');
}

async function run() {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  try {
    const { token } = await setupSeed(apiContext);

    const seedCase = addCase('GF-SETUP', 'Reset + Seed 数据初始化', '/dashboard');
    await withVisibleBrowser(seedCase, 'admin', async ({ page }) => {
      await page.goto(`${FRONTEND}/dashboard`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1000);
      seedCase.screenshots.push(await shot(page, 'GF-SETUP_dashboard_seeded'));
      seedCase.result = '✅';
      seedCase.note = `Reset=${report.setup.resetStatus}, Seed=${report.setup.seedStatus}`;
    });

    const talentCase = addCase('GF-01', '达人认领与释放可见回归', '/talent');
    await withVisibleBrowser(talentCase, 'channel_leader', async ({ page }) => {
      await page.goto(`${FRONTEND}/talent`, { waitUntil: 'networkidle', timeout: 30000 });
      await page.getByRole('button', { name: '重置' }).click();
      await sleep(800);

      const targetRow = page.locator('tr', { hasText: '达人E-保护期到期回公海' }).first();
      await targetRow.scrollIntoViewIfNeeded();
      await targetRow.getByRole('button', { name: '认领' }).click();
      await page.getByRole('button', { name: '确认认领' }).click();
      await sleep(1200);
      talentCase.screenshots.push(await shot(page, 'GF-01_talent_claimed'));

      const claimedRow = page.locator('tr', { hasText: '达人E-保护期到期回公海' }).first();
      const claimedText = await claimedRow.textContent();
      const claimOk = claimedText && claimedText.includes('私海');
      const leaderReleaseVisible = claimedText && claimedText.includes('释放');

      let selfReleaseOk = false;
      await withVisibleBrowser(talentCase, 'channel_staff', async ({ page: staffPage }) => {
        await staffPage.goto(`${FRONTEND}/talent`, { waitUntil: 'networkidle', timeout: 30000 });
        await staffPage.getByRole('button', { name: '重置' }).click();
        await sleep(800);
        const ownRow = staffPage.locator('tr', { hasText: '达人C-他人已认领' }).first();
        await ownRow.scrollIntoViewIfNeeded();
        await ownRow.getByRole('button', { name: '释放' }).click();
        await staffPage.getByRole('button', { name: '确认释放' }).click();
        await sleep(1200);
        talentCase.screenshots.push(await shot(staffPage, 'GF-01_talent_self_released'));
        const releasedLocator = staffPage.locator('tr', { hasText: '达人C-他人已认领' }).first();
        const releasedCount = await releasedLocator.count();
        if (!releasedCount) {
          selfReleaseOk = true;
        } else {
          const releasedText = await releasedLocator.textContent();
          selfReleaseOk = Boolean(releasedText && releasedText.includes('公海') && releasedText.includes('认领'));
        }
      });

      if (claimOk && !leaderReleaseVisible) {
        report.notes.push('GF-01: channel_leader 认领成功后，前端未显示“释放”按钮；后端规则允许同组组长释放，前台能力未完全暴露。');
      }
      talentCase.result = claimOk && selfReleaseOk ? '✅' : '❌';
      talentCase.note = `组长认领=${claimOk ? '成功' : '失败'}；组长释放按钮=${leaderReleaseVisible ? '可见' : '未暴露'}；本人释放=${selfReleaseOk ? '成功' : '失败'}`;
    });

    const productCase = addCase('GF-02', '商品审核、分配与日志回归', '/product/activity/TEST_ACTIVITY_A');
    await withVisibleBrowser(productCase, 'biz_leader', async ({ page }) => {
      await page.goto(`${FRONTEND}/product/activity/TEST_ACTIVITY_A`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1800);

      const auditCard = page.locator('.product-card-shell', { hasText: '排查演示商品-推广映射缺失' }).first();
      await auditCard.scrollIntoViewIfNeeded();
      await auditCard.getByRole('button', { name: '审核' }).click();
      await page.getByRole('button', { name: '提交' }).click();
      await sleep(1500);
      productCase.screenshots.push(await shot(page, 'GF-02_product_audited'));

      const assignCard = page.locator('.product-card-shell', { hasText: '排查演示商品-推广映射缺失' }).first();
      await assignCard.hover();
      await sleep(500);
      await assignCard.getByRole('button', { name: '分配' }).click();
      const select = page.locator('.n-base-selection').last();
      await select.click();
      await page.locator('.n-base-select-option').first().click();
      await page.getByRole('button', { name: '确认' }).click();
      await sleep(1500);
      productCase.screenshots.push(await shot(page, 'GF-02_product_assigned'));

      await assignCard.hover();
      await sleep(500);
      await assignCard.getByRole('button', { name: '查看操作日志' }).click();
      await sleep(1500);
      const drawer = page.locator('.n-drawer');
      const drawerText = await drawer.textContent();
      const hasAuditLog = drawerText && drawerText.includes('商品审核');
      const hasAssignLog = drawerText && drawerText.includes('分配招商');
      productCase.screenshots.push(await shot(page, 'GF-02_product_logs'));

      const cardText = await assignCard.textContent();
      const auditOk = cardText && !cardText.includes('待审核');
      const assignOk = cardText && /已分配招商|负责人|未分配负责人/.test(cardText);
      productCase.result = auditOk && hasAuditLog && hasAssignLog ? '✅' : '❌';
      productCase.note = `审核后状态=${auditOk ? '已变化' : '未识别'}；日志=审核:${hasAuditLog ? 'Y' : 'N'}/分配:${hasAssignLog ? 'Y' : 'N'}；卡片摘要=${assignOk ? '可见' : '异常'}`;
    });

    const sampleCase = addCase('GF-03', '寄样关闭样例详情可见', '/sample/:id');
    const closedSamples = await fetchSamplesByStatus(apiContext, token, 'CLOSED');
    const closedSample = closedSamples.find((item) => item.requestNo === 'TEST-SAMPLE-CLOSED-001') || closedSamples[0];
    if (!closedSample?.id) {
      sampleCase.result = '❌';
      sampleCase.note = '未找到 CLOSED 状态寄样单';
    } else {
      await withVisibleBrowser(sampleCase, 'biz_leader', async ({ page }) => {
        await page.goto(`${FRONTEND}/sample/${closedSample.id}`, { waitUntil: 'networkidle', timeout: 30000 });
        await sleep(1200);
        sampleCase.screenshots.push(await shot(page, 'GF-03_sample_closed_detail'));
        const bodyText = await page.locator('body').textContent();
        const hasClosedReason = bodyText
          && bodyText.includes('关闭原因')
          && (bodyText.includes('自动关闭') || bodyText.includes('30天未出单'));
        sampleCase.result = hasClosedReason ? '✅' : '❌';
        sampleCase.note = `requestNo=${closedSample.requestNo || 'N/A'}；关闭原因${hasClosedReason ? '已显示' : '未显示'}`;
      });
    }

    const sampleAutoCase = addCase('GF-04', '寄样待交作业自动完成回归', '/sample/:id');
    const pendingSamples = await fetchSamplesByStatus(apiContext, token, 'PENDING_TASK');
    const pendingSample = pendingSamples.find((item) => item.requestNo === 'TEST-SAMPLE-001') || pendingSamples[0];
    if (!pendingSample?.id) {
      sampleAutoCase.result = '❌';
      sampleAutoCase.note = '未找到 PENDING_TASK 状态寄样单';
    } else {
      await withVisibleBrowser(sampleAutoCase, 'biz_leader', async ({ page }) => {
        await page.goto(`${FRONTEND}/sample/${pendingSample.id}`, { waitUntil: 'networkidle', timeout: 30000 });
        await sleep(1200);
        sampleAutoCase.screenshots.push(await shot(page, 'GF-04_sample_before_order'));

        const adminToken = await apiLogin(apiContext, 'admin');
        const genRes = await apiContext.post(`${API}/test/orders/generate-attributed`, {
          headers: { Authorization: `Bearer ${adminToken}` }
        });
        const genBody = await genRes.json();
        await page.reload({ waitUntil: 'networkidle', timeout: 30000 });
        await sleep(1200);
        sampleAutoCase.screenshots.push(await shot(page, 'GF-04_sample_after_order'));
        const bodyText = await page.locator('body').textContent();
        const completed = bodyText && bodyText.includes('该寄样单已完成');
        sampleAutoCase.result = genRes.ok() && completed ? '✅' : '❌';
        sampleAutoCase.note = `订单生成=${genRes.status()}；orderNo=${genBody?.data?.orderNo || 'N/A'}；完成态=${completed ? '已显示' : '未显示'}`;
      });
    }

    const talentRefreshCase = addCase('GF-05', '达人刷新能力前台暴露情况核对', '/talent');
    await withVisibleBrowser(talentRefreshCase, 'channel_leader', async ({ page }) => {
      await page.goto(`${FRONTEND}/talent`, { waitUntil: 'networkidle', timeout: 30000 });
      await sleep(1200);
      talentRefreshCase.screenshots.push(await shot(page, 'GF-05_talent_page_refresh_button'));
      const pageText = await page.locator('body').textContent();
      const hasListRefresh = pageText && pageText.includes('刷新达人');
      const hasSingleRefresh = pageText && pageText.includes('刷新单个达人');
      talentRefreshCase.result = hasListRefresh ? '✅' : '❌';
      talentRefreshCase.note = hasSingleRefresh
        ? '页面存在刷新能力'
        : '当前前台仅暴露列表刷新按钮，未发现单达人 enrich 刷新入口；后端 /talents/{id}/refresh 仍属接口级能力';
    });

    const passed = report.cases.filter((item) => item.result === '✅').length;
    const lines = [];
    lines.push(`# 剩余规则型功能补测报告 - ${report.generatedAt}`);
    lines.push('');
    lines.push(`- 前端: ${FRONTEND}`);
    lines.push(`- 后端: ${BACKEND}`);
    lines.push(`- Reset/Seed: ${report.setup.resetStatus}/${report.setup.seedStatus}`);
    lines.push(`- 通过: ${passed}/${report.cases.length}`);
    lines.push('');
    lines.push('| 编号 | 路径 | 结果 | 备注 |');
    lines.push('| --- | --- | --- | --- |');
    for (const item of report.cases) {
      lines.push(`| ${item.id} | ${item.path} | ${item.result} | ${String(item.note || '').replace(/\|/g, '/')} |`);
    }
    lines.push('');
    lines.push('## 截图');
    for (const item of report.cases) {
      if (item.screenshots.length) {
        lines.push(`- ${item.id}: ${item.screenshots.join(' ; ')}`);
      }
    }
    lines.push('');
    lines.push('## 备注');
    lines.push(`- GF-05: ${report.cases.find((item) => item.id === 'GF-05')?.note || ''}`);
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
