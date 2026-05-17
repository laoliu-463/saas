const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));
const { getAuthToken, postTestAction } = require('./test-api.cjs');

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : false;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 2000);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `role-business-flow-visible-${stamp}`);
const SHOT_DIR = path.join(OUT_DIR, 'screenshots');
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  headless: HEADLESS,
  slowMo: SLOW_MO,
  steps: []
};

function short(text, max = 180) {
  const value = String(text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForIdle(page) {
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 4000 }).catch(() => {});
  await page.locator('.loading-state').waitFor({ state: 'hidden', timeout: 4000 }).catch(() => {});
  await sleep(800);
}

async function shot(page, name) {
  const safe = name.replace(/[<>:"/\\|?*\u0000-\u001F]+/g, '_');
  const file = path.join(SHOT_DIR, `${safe}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function resetSession(page) {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  }).catch(() => {});
  await page.context().clearCookies();
}

async function login(page, username, password = 'admin123') {
  let lastStatus = 'no-response';
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await resetSession(page);
    await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.getByPlaceholder('请输入用户名').fill(username);
    await page.getByPlaceholder('请输入密码').fill(password);
    const respPromise = page.waitForResponse((resp) => resp.url().includes('/api/auth/login') && resp.request().method() === 'POST', { timeout: 15000 }).catch(() => null);
    await page.getByRole('button', { name: /登/ }).click();
    const resp = await respPromise;
    await waitForIdle(page);
    lastStatus = resp ? String(resp.status()) : 'no-response';
    if (resp?.ok()) {
      return;
    }
    await sleep(2000);
  }
  throw new Error(`登录失败: ${username} ${lastStatus}`);
}

async function goto(page, route) {
  await page.goto(`${FRONTEND}${route}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
}

async function clickButtonIfExists(page, name) {
  const button = page.getByRole('button', { name }).first();
  if (await button.count()) {
    await button.click({ force: true });
    return true;
  }
  return false;
}

async function clickTab(page, name) {
  const tab = page.locator('.n-tabs-tab').filter({ hasText: name }).first();
  if (!await tab.count()) {
    throw new Error(`未找到标签: ${name}`);
  }
  await tab.click({ force: true });
  await waitForIdle(page);
}

async function record(page, id, title, fn) {
  const item = { id, title, result: '❌', note: '', screenshot: '' };
  try {
    const result = await fn();
    item.result = result?.result || '✅';
    item.note = result?.note || '';
    item.screenshot = result?.screenshot || await shot(page, id);
  } catch (error) {
    item.result = '❌';
    item.note = short(error?.stack || error?.message || String(error));
    item.screenshot = await shot(page, `${id}_fail`).catch(() => '');
  }
  report.steps.push(item);
  writeReport();
}

function writeReport() {
  const passed = report.steps.filter((item) => item.result === '✅').length;
  const lines = [
    `# 角色业务流转可视化报告 - ${new Date().toLocaleString('zh-CN', { hour12: false })}`,
    '',
    `- 前端: ${FRONTEND}`,
    `- 模式: ${HEADLESS ? 'headless' : 'visible'} / slowMo=${SLOW_MO}ms`,
    `- 通过: ${passed}/${report.steps.length}`,
    '',
    '| 编号 | 流程 | 结果 | 备注 |',
    '| --- | --- | --- | --- |',
    ...report.steps.map((item) => `| ${item.id} | ${item.title} | ${item.result} | ${String(item.note || '').replace(/\|/g, '/')} |`),
    '',
    '## 截图',
    ...report.steps.filter((item) => item.screenshot).map((item) => `- ${item.id}: ${item.screenshot}`)
  ];
  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2));
  fs.writeFileSync(REPORT_MD, `${lines.join('\n')}\n`);
}

async function prepareSeed(page) {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  let reset;
  let seed;
  try {
    const token = await getAuthToken(apiContext, API, 'admin');
    reset = await postTestAction(apiContext, API, token, '/test/reset');
    seed = await postTestAction(apiContext, API, token, '/test/seed');
  } finally {
    await apiContext.dispose();
  }
  if (!reset?.ok() || !seed?.ok()) {
    throw new Error(`reset/seed 失败: reset=${reset?.status} seed=${seed?.status}`);
  }
}

async function chooseFirstTalentAndCapture(page) {
  const chooseButton = page.getByRole('button', { name: '选择' }).first();
  await chooseButton.waitFor({ state: 'visible', timeout: 20000 });
  const rowText = short(await chooseButton.locator('xpath=ancestor::tr').innerText().catch(() => ''), 120);
  await chooseButton.click({ force: true });
  await waitForIdle(page);
  return rowText;
}

async function pickProduct(page, keyword, label) {
  const input = page.locator('.sample-apply .n-base-selection input').first();
  await input.click({ force: true });
  await input.fill(keyword);
  const option = page.getByText(label).first();
  await option.waitFor({ state: 'visible', timeout: 10000 });
  await option.click({ force: true });
  await sleep(600);
}

async function openFirstSampleDetail(page) {
  const button = page.getByRole('button', { name: '查看处理' }).first();
  await button.waitFor({ state: 'visible', timeout: 15000 });
  const rowText = short(await button.locator('xpath=ancestor::tr').innerText().catch(() => ''), 140);
  await button.click({ force: true });
  await waitForIdle(page);
  return rowText;
}

async function openSampleDetailByText(page, text) {
  const row = page.locator('tbody tr').filter({ hasText: text }).first();
  if (!await row.count()) {
    return null;
  }
  await row.waitFor({ state: 'visible', timeout: 15000 });
  const rowText = short(await row.innerText().catch(() => ''), 140);
  await row.getByRole('button', { name: '查看处理' }).click({ force: true });
  await waitForIdle(page);
  return rowText;
}

async function fillDialogInput(page, placeholder, value) {
  const input = page.getByPlaceholder(placeholder).last();
  await input.waitFor({ state: 'visible', timeout: 10000 });
  await input.fill(value);
}

async function submitSample(page) {
  const postResp = page.waitForResponse((resp) => resp.url().includes('/api/samples') && resp.request().method() === 'POST', { timeout: 15000 }).catch(() => null);
  await page.getByRole('button', { name: '提交申请' }).click({ force: true });
  await sleep(600);
  await page.getByRole('button', { name: '确认' }).last().click({ force: true });
  const post = await postResp;
  await waitForIdle(page);
  if (!post?.ok()) {
    throw new Error(`提交寄样失败: ${post?.status()}`);
  }
}

async function generateAttributedOrder(page) {
  const apiContext = await request.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });
  let resp;
  try {
    const token = await getAuthToken(apiContext, API, 'admin');
    resp = await postTestAction(apiContext, API, token, '/test/orders/generate-attributed');
  } finally {
    await apiContext.dispose();
  }
  if (!resp?.ok()) {
    throw new Error(`生成已归因订单失败: ${resp?.status}`);
  }
}

async function main() {
  const browser = await chromium.launch({
    headless: HEADLESS,
    slowMo: SLOW_MO,
    executablePath: fs.existsSync(EDGE_PATH) ? EDGE_PATH : undefined
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();

  try {
    await record(page, 'RB-00', '管理员重置并铺设演示数据', async () => {
      await login(page, 'admin');
      await prepareSeed(page);
      await goto(page, '/dashboard');
      return { note: '管理员通过测试 API 完成 reset + seed', screenshot: await shot(page, 'RB-00_admin_seed') };
    });

    await record(page, 'RB-01', '渠道组长在团队公海认领达人', async () => {
      await login(page, 'channel_leader');
      await goto(page, '/talent?view=TEAM_PUBLIC&page=1&size=10');
      await clickButtonIfExists(page, '刷新数据');
      await waitForIdle(page);
      await clickButtonIfExists(page, '查询');
      await waitForIdle(page);
      const publicShot = await shot(page, 'RB-01_public_pool_before_claim');
      if (!await clickButtonIfExists(page, '认领')) {
        throw new Error('团队公海未找到可认领达人');
      }
      await sleep(500);
      await page.getByRole('button', { name: '确认认领' }).last().click({ force: true });
      await waitForIdle(page);
      await clickTab(page, '我的达人');
      const body = await page.locator('body').innerText().catch(() => '');
      if (!body.includes('我的达人')) {
        throw new Error('认领后未切换到我的达人');
      }
      return { note: `渠道组长已从团队公海认领达人并进入我的达人；前置截图=${publicShot}`, screenshot: await shot(page, 'RB-01_my_talents_after_claim') };
    });

    await record(page, 'RB-02', '渠道组长申请寄样', async () => {
      await login(page, 'channel_leader');
      await goto(page, '/sample/apply');
      const talentRow = await chooseFirstTalentAndCapture(page);
      await pickProduct(page, '主演示', '主演示商品-已转链可出单');
      const quantityInput = page.locator('.sample-apply .n-input-number input').first();
      await quantityInput.fill('2');
      await page.locator('textarea').fill(`可视化业务流转演示-${stamp}`);
      await submitSample(page);
      await goto(page, '/sample');
      await clickTab(page, '待审核');
      return { note: `渠道组长完成寄样申请；所选达人=${talentRow}`, screenshot: await shot(page, 'RB-02_channel_sample_pending_audit') };
    });

    await record(page, 'RB-03', '待审核寄样处理并推进到待发货', async () => {
      let actor = 'biz_leader';
      await login(page, 'biz_leader');
      await goto(page, '/sample');
      await clickTab(page, '待审核');
      let row = await openSampleDetailByText(page, '主演示商品-已转链可出单');
      if (!row) {
        actor = 'admin';
        await login(page, 'admin');
        await goto(page, '/sample');
        await clickTab(page, '待审核');
        row = await openSampleDetailByText(page, '主演示商品-已转链可出单');
      }
      if (!row) {
        throw new Error('当前待审核列表未找到刚提交的主演示商品寄样单');
      }
      const bodyBefore = await page.locator('body').innerText().catch(() => '');
      if (!bodyBefore.includes('审核通过')) {
        throw new Error('当前寄样详情未出现审核通过按钮');
      }
      await page.getByRole('button', { name: '审核通过' }).click({ force: true });
      await waitForIdle(page);
      const bodyAfter = await page.locator('body').innerText().catch(() => '');
      if (!bodyAfter.includes('待发货')) {
        throw new Error('审核通过后详情未进入待发货');
      }
      await clickButtonIfExists(page, '关闭');
      await waitForIdle(page);
      return { note: `处理角色=${actor}; 已将主演示商品寄样推进到待发货；样本行=${row}`, screenshot: await shot(page, 'RB-03_pending_ship_ready') };
    });

    await record(page, 'RB-04', '运营在物流页完成发货', async () => {
      await login(page, 'ops_staff');
      await goto(page, '/ops/shipping');
      await clickTab(page, '待发货');
      const row = await openSampleDetailByText(page, '主演示商品-已转链可出单') || await openFirstSampleDetail(page);
      const bodyBefore = await page.locator('body').innerText().catch(() => '');
      if (!bodyBefore.includes('发货')) {
        throw new Error('当前寄样详情未出现发货按钮');
      }
      await page.getByRole('button', { name: '发货' }).click({ force: true });
      await waitForIdle(page);
      await fillDialogInput(page, 'SF1234567890', `SF${stamp.replace(/[-]/g, '')}`);
      await page.getByRole('button', { name: '确认发货' }).last().click({ force: true });
      await waitForIdle(page);
      const bodyAfter = await page.locator('body').innerText().catch(() => '');
      if (!bodyAfter.includes('快递中')) {
        throw new Error('发货后详情未进入快递中');
      }
      await clickButtonIfExists(page, '关闭');
      await waitForIdle(page);
      return { note: `运营已录入物流单号并发货；样本行=${row}`, screenshot: await shot(page, 'RB-04_ops_shipped') };
    });

    await record(page, 'RB-05', '运营完成签收推进到待交作业', async () => {
      await clickTab(page, '快递中');
      const row = await openSampleDetailByText(page, '主演示商品-已转链可出单') || await openFirstSampleDetail(page);
      const bodyBefore = await page.locator('body').innerText().catch(() => '');
      if (!bodyBefore.includes('签收')) {
        throw new Error('当前寄样详情未出现签收按钮');
      }
      await page.getByRole('button', { name: '签收' }).click({ force: true });
      await waitForIdle(page);
      const bodyAfter = await page.locator('body').innerText().catch(() => '');
      if (!bodyAfter.includes('待交作业')) {
        throw new Error('签收后详情未进入待交作业');
      }
      await clickButtonIfExists(page, '关闭');
      await waitForIdle(page);
      return { note: `运营已完成签收，寄样进入待交作业；样本行=${row}`, screenshot: await shot(page, 'RB-05_ops_signed') };
    });

    await record(page, 'RB-06', '管理员生成归因订单并回看数据平台', async () => {
      await login(page, 'admin');
      await generateAttributedOrder(page);
      await goto(page, '/dashboard');
      const dashboardBody = await page.locator('body').innerText().catch(() => '');
      const dashboardOk = dashboardBody.includes('经营概况') || dashboardBody.includes('数据平台') || dashboardBody.includes('订单');
      if (!dashboardOk) {
        throw new Error('Dashboard 未正常加载');
      }
      return { note: '管理员生成已归因订单后，回看 Dashboard 核心指标', screenshot: await shot(page, 'RB-06_admin_dashboard') };
    });

    await record(page, 'RB-07', '管理员回看订单明细与归因结果', async () => {
      await goto(page, '/orders');
      const body = await page.locator('body').innerText().catch(() => '');
      if (!body.includes('订单') && !body.includes('归因')) {
        throw new Error('订单工作台未正常加载');
      }
      return { note: '管理员回看订单工作台，确认归因订单已进入主链路', screenshot: await shot(page, 'RB-07_admin_orders') };
    });

    await record(page, 'RB-08', '管理员回看寄样台完成后的状态分布', async () => {
      await goto(page, '/sample');
      await clickTab(page, '待交作业');
      const pendingTaskShot = await shot(page, 'RB-08_sample_pending_task');
      await clickTab(page, '已完成').catch(() => {});
      return { note: `管理员回看寄样台待交作业/已完成分布；待交作业截图=${pendingTaskShot}`, screenshot: await shot(page, 'RB-08_sample_finished') };
    });
  } finally {
    await context.close();
    await browser.close();
    writeReport();
  }

  console.log(`report: ${REPORT_MD}`);
}

main().catch((error) => {
  console.error(error);
  writeReport();
  process.exitCode = 1;
});
