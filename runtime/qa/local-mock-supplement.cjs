const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));
const { postTestAction } = require('./test-api.cjs');

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const BACKEND = process.env.QA_BACKEND || 'http://localhost:8080';
const API = `${BACKEND}/api`;
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : true;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 0);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`;
const OUT_DIR = path.join(__dirname, 'out', `local-mock-supplement-${stamp}`);
const SHOT_DIR = path.join(OUT_DIR, 'screenshots');
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  backend: BACKEND,
  healthStatus: null,
  healthBody: null,
  cases: [],
  notes: []
};

let browser;
let context;
let page;
let authHeaders = null;

function short(text, max = 180) {
  const value = (text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function shot(name) {
  const file = path.join(SHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function login() {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill('admin');
  await page.getByPlaceholder('请输入密码').fill('admin123');
  await page.getByRole('button', { name: /登/ }).click();
  await page.waitForURL(/dashboard/, { timeout: 15000 });
}

async function initAuthHeaders(apiCtx) {
  const res = await apiCtx.post(`${API}/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  });
  const body = await res.json();
  const token = body?.data?.token || body?.data?.accessToken;
  if (!res.ok() || !token) {
    throw new Error('初始化 API 登录失败');
  }
  authHeaders = {
    Authorization: `Bearer ${token}`
  };
}

async function addCase(id, title, executor) {
  const item = { id, title, result: '❌', note: '', screenshot: null };
  try {
    await executor(item);
  } catch (error) {
    item.result = '❌';
    item.note = `异常: ${error.message}`;
  }
  report.cases.push(item);
}

async function selectUserRoleByIndex(index = 1) {
  const selector = page.locator('.n-modal-body .n-base-selection, [role="dialog"] .n-base-selection').last();
  await page.waitForFunction(() => {
    const placeholder = document.querySelector('[role="dialog"] .n-base-selection-placeholder');
    return !!placeholder && !document.querySelector('[role="dialog"] [aria-label="loading"]');
  }, { timeout: 15000 }).catch(() => {});
  await selector.click({ force: true });
  await page.waitForFunction(() => document.querySelectorAll('.n-base-select-option').length > 0, { timeout: 15000 });
  await page.locator('.n-base-select-option').nth(index).click({ force: true });
  await page.keyboard.press('Escape').catch(() => {});
  await sleep(300);
}

async function confirmDialog(confirmText = '确认') {
  await page.getByText(confirmText).last().click({ force: true });
  await sleep(300);
}

async function clickDialogPrimaryButton(label = '确定') {
  const dialog = page.locator('[role="dialog"]').last();
  const button = dialog.getByRole('button', { name: label }).last();
  await button.waitFor({ state: 'visible', timeout: 15000 });
  await button.click({ force: true });
}

async function clickNamedDialogPrimaryButton(title, label = '确定') {
  const dialog = page.locator('[role="dialog"]').filter({ hasText: title }).last();
  const button = dialog.getByRole('button', { name: label }).last();
  await button.waitFor({ state: 'visible', timeout: 20000 });
  await button.click({ force: true });
}

async function waitForTableRow(text, timeout = 15000) {
  const row = page.locator('tbody tr').filter({ hasText: text }).first();
  await row.waitFor({ state: 'visible', timeout });
  return row;
}

async function cleanupArtifacts() {
  const knownUsers = ['probe_user_z', 'lm_user_probe2_1777788917972'];
  await page.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle' });
  const userFilter = page.getByPlaceholder('请输入用户名').first();
  for (const username of knownUsers) {
    await userFilter.fill(username);
    await page.getByRole('button', { name: '查询' }).click({ force: true });
    await sleep(800);
    const body = await page.locator('body').innerText();
    if (!body.includes(username)) continue;
    const row = page.locator('tbody tr').filter({ hasText: username }).first();
    await row.getByText('删除').click();
    await sleep(300);
    const respPromise = page.waitForResponse((r) => r.url().match(/\/api\/users\//) && r.request().method() === 'DELETE', { timeout: 15000 }).catch(() => null);
    await confirmDialog('确认');
    await respPromise;
    await sleep(800);
  }

  const knownRoles = ['probe_role_1777788581156', 'qa_role_1777788322284'];
  await page.goto(`${FRONTEND}/system/roles`, { waitUntil: 'networkidle' });
  for (const roleCode of knownRoles) {
    const body = await page.locator('body').innerText();
    if (!body.includes(roleCode)) continue;
    const row = page.locator('tbody tr').filter({ hasText: roleCode }).first();
    await row.getByText('删除').click();
    await sleep(300);
    const respPromise = page.waitForResponse((r) => r.url().match(/\/api\/roles\//) && r.request().method() === 'DELETE', { timeout: 15000 }).catch(() => null);
    await confirmDialog('确认');
    await respPromise;
    await sleep(800);
  }
}

function buildMarkdown() {
  const passed = report.cases.filter((item) => item.result === '✅').length;
  const lines = [];
  lines.push(`# local-mock 补充验收报告 - ${new Date().toLocaleString('zh-CN', { hour12: false })}`);
  lines.push('');
  lines.push(`- 前端: ${FRONTEND}`);
  lines.push(`- 后端: ${BACKEND}`);
  lines.push(`- 健康: ${report.healthStatus} ${short(report.healthBody, 80)}`);
  lines.push(`- 通过: ${passed}/${report.cases.length}`);
  lines.push('');
  lines.push('| 编号 | 项目 | 结果 | 备注 |');
  lines.push('| --- | --- | --- | --- |');
  for (const item of report.cases) {
    lines.push(`| ${item.id} | ${item.title} | ${item.result} | ${item.note.replace(/\|/g, '/')} |`);
  }
  lines.push('');
  lines.push('## 截图');
  for (const item of report.cases.filter((entry) => entry.screenshot)) {
    lines.push(`- ${item.id}: ${item.screenshot}`);
  }
  lines.push('');
  lines.push('## Notes');
  if (!report.notes.length) {
    lines.push('- 无');
  } else {
    for (const note of report.notes) lines.push(`- ${note}`);
  }
  return lines.join('\n');
}

async function main() {
  browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO, executablePath: EDGE_PATH });
  context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  page = await context.newPage();
  const apiCtx = await request.newContext();

  try {
    const health = await apiCtx.get(`${API}/actuator/health`);
    report.healthStatus = health.status();
    report.healthBody = await health.text();

    await initAuthHeaders(apiCtx);
    await login();
    await cleanupArtifacts();

    await addCase('LM-0', 'local-mock 健康与 reset/seed', async (item) => {
      const token = authHeaders.Authorization.replace('Bearer ', '');
      const resetResp = await postTestAction(apiCtx, API, token, '/test/reset');
      await sleep(300);
      const seedResp = await postTestAction(apiCtx, API, token, '/test/seed');
      await page.goto(`${FRONTEND}/dashboard`, { waitUntil: 'networkidle' });
      item.result = report.healthStatus === 200 && resetResp.status === 200 && seedResp.status === 200 ? '✅' : '❌';
      item.note = `health=${report.healthStatus}; reset=${resetResp.status}; seed=${seedResp.status}`;
      item.screenshot = await shot('LM-0_seeded_dashboard');
    });

    await addCase('LM-1', '商品详情抽屉可打开并展示业务信息', async (item) => {
      await page.goto(`${FRONTEND}/product/activity/TEST_ACTIVITY_A`, { waitUntil: 'networkidle' });
      const card = page.locator('.product-card').first();
      await card.waitFor({ state: 'visible', timeout: 15000 });
      const detailButton = card.getByRole('button', { name: '详情' }).first();
      if (await detailButton.count()) {
        await detailButton.click();
      } else {
        await card.click();
      }
      await sleep(800);
      const body = await page.locator('body').innerText();
      const ok = body.includes('商品业务全貌') && body.includes('业务推进') && body.includes('操作日志');
      item.result = ok ? '✅' : '❌';
      item.note = `商品业务全貌=${body.includes('商品业务全貌')} 业务推进=${body.includes('业务推进')} 操作日志=${body.includes('操作日志')}`;
      item.screenshot = await shot('LM-1_product-detail');
    });

    await addCase('LM-2', '寄样申请可搜索达人、选择商品并成功提交', async (item) => {
      const marker = `LM-${stamp}`;
      await page.goto(`${FRONTEND}/sample/apply`, { waitUntil: 'networkidle' });
      await page.getByRole('button', { name: '搜索达人' }).click();
      const chooseTalentButton = page.getByRole('button', { name: '选择' }).first();
      await chooseTalentButton.waitFor({ state: 'visible', timeout: 20000 });
      const firstTalent = short(await chooseTalentButton.locator('xpath=ancestor::tr').innerText(), 80);
      await chooseTalentButton.click();
      await sleep(600);
      const productInput = page.locator('.sample-apply .n-select input').first();
      await productInput.waitFor({ state: 'visible', timeout: 10000 });
      await productInput.click();
      await productInput.fill('主演示');
      const productOption = page.getByText('主演示商品-已转链可出单').first();
      await productOption.waitFor({ state: 'visible', timeout: 10000 });
      await productOption.click();
      await sleep(400);
      const quantityInput = page.locator('.sample-apply .n-input-number input').first();
      await quantityInput.fill('2');
      await page.locator('textarea').fill(marker);
      const submitPromise = page.waitForResponse((r) => r.url().includes('/api/samples') && r.request().method() === 'POST');
      await page.getByRole('button', { name: '提交申请' }).click();
      await clickNamedDialogPrimaryButton('确认提交', '确认');
      const submitResp = await submitPromise;
      await page.waitForURL(/\/sample$/, { timeout: 10000 }).catch(() => {});
      await sleep(600);
      const body = await page.locator('body').innerText();
      const ok = submitResp.status() === 200;
      item.result = ok ? '✅' : '❌';
      item.note = `submit=${submitResp.status()}; redirect=${page.url().replace(FRONTEND, '')}; firstTalent=${firstTalent}`;
      item.screenshot = await shot('LM-2_sample-submit');
    });

    const roleCode = `lm_role_${Date.now()}`;
    const roleName = `补充角色${stamp}`;
    const roleNameEdited = `${roleName}-已编辑`;
    await addCase('LM-3', '角色新增/编辑/删除', async (item) => {
      await page.goto(`${FRONTEND}/system/roles`, { waitUntil: 'networkidle' });
      const createResp = await apiCtx.post(`${API}/roles`, {
        headers: authHeaders,
        data: { roleCode, roleName, dataScope: 1, status: 1, remark: 'local-mock supplement role' }
      });
      const createdBody = await createResp.json().catch(() => ({}));
      const roleId = createdBody?.data?.id || createdBody?.data?.roleId || createdBody?.id || null;
      const editResp = await apiCtx.put(`${API}/roles/${roleId}`, {
        headers: authHeaders,
        data: { roleCode, roleName: roleNameEdited, dataScope: 1, status: 1, remark: 'local-mock supplement role edited' }
      });
      const deleteResp = await apiCtx.delete(`${API}/roles/${roleId}`, {
        headers: authHeaders
      });
      const body = await page.locator('body').innerText();
      const created = createResp.status() === 200;
      const edited = editResp.status() === 200;
      const deleted = deleteResp.status() === 200;
      item.result = created && edited && deleted && body.includes('角色管理') ? '✅' : '❌';
      item.note = `create=${createResp.status()} edit=${editResp.status()} delete=${deleteResp.status()} roleId=${roleId || 'N/A'}`;
      item.screenshot = await shot('LM-3_role-crud');
    });

    const username = `lm_user_${Date.now()}`;
    const realName = `补充用户${stamp}`;
    const realNameEdited = `${realName}-已编辑`;
    const email = `${username}@example.com`;
    const emailEdited = `${username}_edit@example.com`;
    await addCase('LM-4', '用户新增/编辑/重置密码/删除', async (item) => {
      await page.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle' });
      const filter = page.getByPlaceholder('请输入用户名').first();
      const createResp = await apiCtx.post(`${API}/users`, {
        headers: authHeaders,
        data: {
          username,
          password: 'admin123',
          realName,
          phone: '13900001234',
          email,
          roleIds: ['e5fc8a5c-2a8d-466c-b196-6709b7eeeaf5'],
          status: 1
        }
      });
      await sleep(1000);

      await filter.fill(username);
      await page.getByRole('button', { name: '查询' }).click({ force: true });
      await sleep(800);
      let body = await page.locator('body').innerText();
      const created = body.includes(username) && body.includes(realName);

      let row = await waitForTableRow(username);
      await row.getByText('编辑').click();
      await sleep(400);
      const editInputs = page.locator('.n-modal-body input, [role="dialog"] input');
      await editInputs.nth(1).fill(realNameEdited);
      await editInputs.nth(3).fill(emailEdited);
      const editPromise = page.waitForResponse((r) => r.url().match(/\/api\/users\//) && r.request().method() === 'PUT');
      await clickNamedDialogPrimaryButton('编辑用户', '确定');
      const editResp = await editPromise;
      await sleep(1000);

      await filter.fill(username);
      await page.getByRole('button', { name: '查询' }).click({ force: true });
      await sleep(800);
      body = await page.locator('body').innerText();
      const edited = body.includes(realNameEdited) && body.includes(emailEdited);

      row = await waitForTableRow(username);
      await row.getByText('重置密码').click();
      await sleep(300);
      await page.locator('.n-modal-body input, [role="dialog"] input').last().fill('admin1234');
      const resetPromise = page.waitForResponse((r) => r.url().match(/\/api\/users\/.+\/password/) && r.request().method() === 'PUT');
      await clickNamedDialogPrimaryButton('重置密码', '确定');
      const resetResp = await resetPromise;
      await sleep(800);

      row = await waitForTableRow(username);
      await row.getByText('删除').click();
      await sleep(300);
      const deletePromise = page.waitForResponse((r) => r.url().match(/\/api\/users\//) && r.request().method() === 'DELETE');
      await confirmDialog('确认');
      const deleteResp = await deletePromise;
      await sleep(1000);

      await filter.fill(username);
      await page.getByRole('button', { name: '查询' }).click({ force: true });
      await sleep(800);
      body = await page.locator('body').innerText();
      const deleted = !body.includes(username);

      item.result = [createResp.status(), editResp.status(), resetResp.status(), deleteResp.status()].every((s) => s === 200) && created && edited && deleted ? '✅' : '❌';
      item.note = `create=${createResp.status()} edit=${editResp.status()} reset=${resetResp.status()} delete=${deleteResp.status()} created=${created} edited=${edited} deleted=${deleted}`;
      item.screenshot = await shot('LM-4_user-crud');
    });
  } finally {
    await apiCtx.dispose();
    fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2));
    fs.writeFileSync(REPORT_MD, buildMarkdown());
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
