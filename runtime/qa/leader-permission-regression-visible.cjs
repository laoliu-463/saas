const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : false;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 1200);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `leader-permission-regression-visible-${stamp}`);
const SHOT_DIR = path.join(OUT_DIR, 'screenshots');
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const USERS = {
  bizLeader: { username: 'biz_leader', password: 'admin123' },
  channelLeader: { username: 'channel_leader', password: 'admin123' }
};

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  headless: HEADLESS,
  slowMo: SLOW_MO,
  checks: []
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
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 3000 }).catch(() => {});
  await sleep(500);
}

async function shot(page, name) {
  const file = path.join(SHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function pushCheck(item) {
  report.checks.push(item);
  writeReport();
}

function writeReport() {
  const passed = report.checks.filter((item) => item.result === 'PASS').length;
  const lines = [
    `# 组长权限回归报告 - ${new Date().toLocaleString('zh-CN', { hour12: false })}`,
    '',
    `- 前端: ${FRONTEND}`,
    `- 模式: ${HEADLESS ? 'headless' : 'visible'} / slowMo=${SLOW_MO}ms`,
    `- 通过: ${passed}/${report.checks.length}`,
    '',
    '| 编号 | 检查项 | 结果 | 备注 |',
    '| --- | --- | --- | --- |',
    ...report.checks.map((item) => `| ${item.id} | ${item.title} | ${item.result} | ${String(item.note || '').replace(/\|/g, '/')} |`),
    '',
    '## 截图',
    ...report.checks.filter((item) => item.screenshot).map((item) => `- ${item.id}: ${item.screenshot}`)
  ];
  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2));
  fs.writeFileSync(REPORT_MD, `${lines.join('\n')}\n`);
}

async function resetSession(page) {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  }).catch(() => {});
  await page.context().clearCookies();
}

async function login(page, username, password) {
  await resetSession(page);
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登/ }).click();
  await waitForIdle(page);
}

async function getToken(page) {
  return page.evaluate(() => localStorage.getItem('token') || '');
}

async function checkRouteGuard(page, route, forbiddenText) {
  await page.goto(`${FRONTEND}${route}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitForIdle(page);
  const current = page.url().replace(FRONTEND, '');
  const body = await page.locator('body').innerText().catch(() => '');
  const blocked = current !== route || (forbiddenText && body.includes(forbiddenText));
  return { blocked, current, body: short(body) };
}

async function checkApiForbidden(context, token, endpoint) {
  const resp = await context.request.post(`${FRONTEND}${endpoint}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { requestNos: ['SAMPLE-NOT-EXIST'], remark: 'regression-check' }
  });
  const body = await resp.json().catch(() => ({}));
  return {
    httpStatus: resp.status(),
    businessCode: body?.code,
    businessMsg: body?.msg || ''
  };
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
    // 招商组长：能进商品库/商品管理/寄样/数据，不能进达人/system，且无审核动作
    await login(page, USERS.bizLeader.username, USERS.bizLeader.password);
    let body = await page.locator('body').innerText();
    pushCheck({
      id: 'BL-01',
      title: '招商组长菜单可见性',
      result: body.includes('商品库') && body.includes('商品管理') && body.includes('寄样审核') && body.includes('数据看板') && !body.includes('达人 CRM') && !body.includes('系统管理') ? 'PASS' : 'FAIL',
      note: '应可见商品库/商品管理/寄样审核/数据看板',
      screenshot: await shot(page, 'BL-01-menu')
    });

    const blBlockProduct = await checkRouteGuard(page, '/product', '无权');
    pushCheck({
      id: 'BL-02',
      title: '招商组长可访问商品库路由',
      result: !blBlockProduct.blocked ? 'PASS' : 'FAIL',
      note: `访问=/product 实际=${blBlockProduct.current}`,
      screenshot: await shot(page, 'BL-02-product-guard')
    });

    const blBlockTalent = await checkRouteGuard(page, '/talent', '无权');
    pushCheck({
      id: 'BL-03',
      title: '招商组长不可访问达人CRM',
      result: blBlockTalent.blocked ? 'PASS' : 'FAIL',
      note: `访问=/talent 实际=${blBlockTalent.current}`,
      screenshot: await shot(page, 'BL-03-talent-guard')
    });

    await page.goto(`${FRONTEND}/sample`, { waitUntil: 'domcontentloaded' });
    await waitForIdle(page);
    await page.getByRole('button', { name: '查看处理' }).first().click().catch(() => {});
    await waitForIdle(page);
    body = await page.locator('body').innerText().catch(() => '');
    pushCheck({
      id: 'BL-04',
      title: '招商组长寄样详情无审核按钮',
      result: !body.includes('审核通过') && !body.includes('审核拒绝') ? 'PASS' : 'FAIL',
      note: '招商组长不能执行寄样审核',
      screenshot: await shot(page, 'BL-04-sample-detail-no-audit')
    });

    const blToken = await getToken(page);
    const blBatchApproveResult = await checkApiForbidden(context, blToken, '/api/samples/batch-approve');
    pushCheck({
      id: 'BL-05',
      title: '招商组长调用寄样审核接口应被拒绝',
      result: blBatchApproveResult.businessCode === 403 ? 'PASS' : 'FAIL',
      note: `POST /api/samples/batch-approve http=${blBatchApproveResult.httpStatus} code=${blBatchApproveResult.businessCode} msg=${blBatchApproveResult.businessMsg}`,
      screenshot: await shot(page, 'BL-05-api-forbidden')
    });

    // 渠道组长：可见商品库/达人/寄样/数据，不可系统；无审核；导出可用；接口审核拒绝
    await login(page, USERS.channelLeader.username, USERS.channelLeader.password);
    body = await page.locator('body').innerText();
    pushCheck({
      id: 'CL-01',
      title: '渠道组长菜单可见性',
      result: body.includes('商品库') && body.includes('达人 CRM') && body.includes('寄样审核') && body.includes('数据看板') && !body.includes('系统管理') ? 'PASS' : 'FAIL',
      note: '应可见商品库/达人CRM/寄样审核/数据看板',
      screenshot: await shot(page, 'CL-01-menu')
    });

    const clBlockSystem = await checkRouteGuard(page, '/system/users', '无权');
    pushCheck({
      id: 'CL-02',
      title: '渠道组长不可访问系统管理',
      result: clBlockSystem.blocked ? 'PASS' : 'FAIL',
      note: `访问=/system/users 实际=${clBlockSystem.current}`,
      screenshot: await shot(page, 'CL-02-system-guard')
    });

    await page.goto(`${FRONTEND}/sample`, { waitUntil: 'domcontentloaded' });
    await waitForIdle(page);
    await page.getByRole('button', { name: '查看处理' }).first().click().catch(() => {});
    await waitForIdle(page);
    body = await page.locator('body').innerText().catch(() => '');
    pushCheck({
      id: 'CL-03',
      title: '渠道组长寄样详情无审核按钮',
      result: !body.includes('审核通过') && !body.includes('审核拒绝') ? 'PASS' : 'FAIL',
      note: '渠道组长不能执行寄样审核',
      screenshot: await shot(page, 'CL-03-sample-detail-no-audit')
    });

    const exportResp = await context.request.get(`${FRONTEND}/api/samples/exports`, {
      headers: { Authorization: `Bearer ${await getToken(page)}` }
    });
    pushCheck({
      id: 'CL-04',
      title: '渠道组长可导出寄样数据',
      result: exportResp.status() === 200 ? 'PASS' : 'FAIL',
      note: `GET /api/samples/exports status=${exportResp.status()}`,
      screenshot: await shot(page, 'CL-04-export-allowed')
    });

    const clBatchRejectResult = await checkApiForbidden(context, await getToken(page), '/api/samples/batch-reject');
    pushCheck({
      id: 'CL-05',
      title: '渠道组长调用寄样审核接口应被拒绝',
      result: clBatchRejectResult.businessCode === 403 ? 'PASS' : 'FAIL',
      note: `POST /api/samples/batch-reject http=${clBatchRejectResult.httpStatus} code=${clBatchRejectResult.businessCode} msg=${clBatchRejectResult.businessMsg}`,
      screenshot: await shot(page, 'CL-05-api-forbidden')
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
