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
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `user-crud-by-role-3000-${stamp}`);
const SHOT_DIR = path.join(OUT_DIR, 'screenshots');
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const CASES = [];
const NOTES = [];
const CREATED_USERS = [];

const ROLE_PLAN = [
  { code: 'admin', expectedHome: '/dashboard' },
  { code: 'biz_leader', expectedHome: '/dashboard' },
  { code: 'biz_staff', expectedHome: '/data' },
  { code: 'channel_leader', expectedHome: '/dashboard' },
  { code: 'channel_staff', expectedHome: '/data' },
  { code: 'ops_staff', expectedHome: '/ops/shipping' }
];

function short(text, max = 160) {
  const value = String(text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

function addCase(id, title) {
  const item = { id, title, result: '❌', note: '', screenshot: '' };
  CASES.push(item);
  return item;
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function screenshot(page, name) {
  const file = path.join(SHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function buildReport(healthStatus, healthBody) {
  const passed = CASES.filter((item) => item.result === '✅').length;
  const lines = [];
  lines.push(`# 3000 用户管理角色 CRUD 报告 - ${new Date().toLocaleString('zh-CN', { hour12: false })}`);
  lines.push('');
  lines.push(`- 前端: ${FRONTEND}`);
  lines.push(`- 后端: ${BACKEND}`);
  lines.push(`- 健康: ${healthStatus} ${short(healthBody, 120)}`);
  lines.push(`- 通过: ${passed}/${CASES.length}`);
  lines.push('');
  lines.push('| 编号 | 项目 | 结果 | 备注 |');
  lines.push('| --- | --- | --- | --- |');
  for (const item of CASES) {
    lines.push(`| ${item.id} | ${item.title} | ${item.result} | ${String(item.note || '').replace(/\|/g, '/')} |`);
  }
  lines.push('');
  lines.push('## 截图');
  for (const item of CASES.filter((entry) => entry.screenshot)) {
    lines.push(`- ${item.id}: ${item.screenshot}`);
  }
  lines.push('');
  lines.push('## 创建账号');
  for (const user of CREATED_USERS) {
    lines.push(`- ${user.username} / ${user.roleCode} / ${user.expectedHome}`);
  }
  lines.push('');
  lines.push('## Notes');
  if (!NOTES.length) {
    lines.push('- 无');
  } else {
    for (const note of NOTES) lines.push(`- ${note}`);
  }
  return lines.join('\n');
}

async function login(page, username, password) {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登/ }).click();
}

async function loginAsAdmin(page) {
  await page.goto(`${FRONTEND}/`, { waitUntil: 'networkidle', timeout: 30000 });
  const usernameInput = page.getByPlaceholder('请输入用户名');
  if (await usernameInput.count()) {
    await usernameInput.fill('admin');
    await page.getByPlaceholder('请输入密码').fill('admin123');
    await page.getByRole('button', { name: /登/ }).click();
    await page.waitForURL(/dashboard/, { timeout: 15000 });
  }
  await page.waitForLoadState('networkidle', { timeout: 15000 });
  await sleep(500);
}

async function openRoleDropdown(page) {
  const selector = page.locator('.n-modal-body .n-base-selection, [role="dialog"] .n-base-selection').last();
  await selector.click({ force: true });
  await page.locator('.n-base-select-option').first().waitFor({ state: 'visible', timeout: 5000 });
  await sleep(200);
}

async function selectRoleByName(page, roleName) {
  let lastError = null;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      await openRoleDropdown(page);
      const option = page.locator('.n-base-select-option').filter({ hasText: roleName }).first();
      await option.waitFor({ state: 'visible', timeout: 5000 });
      await option.click({ force: true });
      await sleep(300);
      await page.keyboard.press('Escape').catch(() => {});
      await sleep(200);
      return;
    } catch (error) {
      lastError = error;
      await page.keyboard.press('Escape').catch(() => {});
      await sleep(300);
    }
  }
  throw lastError || new Error(`Failed to select role: ${roleName}`);
}

async function searchUser(page, username) {
  const filter = page.getByPlaceholder('请输入用户名').first();
  await filter.fill(username);
  await page.getByRole('button', { name: '查询' }).click({ force: true });
  await sleep(900);
}

async function cleanupUsers(page, usernames) {
  await page.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle', timeout: 30000 });
  for (const username of usernames) {
    await searchUser(page, username);
    const body = await page.locator('body').innerText();
    if (!body.includes(username)) continue;
    const row = page.locator('tbody tr').filter({ hasText: username }).first();
    const deletePromise = page.waitForResponse((r) => /\/api\/users\/.+/.test(r.url()) && r.request().method() === 'DELETE', { timeout: 15000 }).catch(() => null);
    await row.getByText('删除').click({ force: true });
    await sleep(200);
    await page.getByText('确认').last().click({ force: true });
    await deletePromise;
    await sleep(700);
  }
}

async function getAuthHeaders(page) {
  const token = await page.evaluate(() => localStorage.getItem('token'));
  if (!token) {
    throw new Error('admin token missing after login');
  }
  return { Authorization: `Bearer ${token}` };
}

async function fetchRoles(apiCtx, authHeaders) {
  const response = await apiCtx.get(`${API}/roles/enabled`, { headers: authHeaders });
  const payload = await response.json();
  const data = payload?.data || payload;
  const rows = Array.isArray(data) ? data : (data?.records || []);
  return rows.map((role) => ({
    id: String(role.id),
    roleCode: String(role.roleCode),
    roleName: String(role.roleName || role.roleCode)
  }));
}

async function logout(page) {
  const body = page.locator('body');
  if (await body.getByText('退出登录').count()) {
    await body.getByText('退出登录').first().click({ force: true });
  } else {
    const triggers = [
      page.locator('.header-user, .user-dropdown, .avatar-trigger').first(),
      page.getByText(/admin|biz_|channel_|ops_/).first()
    ];
    for (const trigger of triggers) {
      try {
        if (await trigger.count()) {
          await trigger.click({ force: true, timeout: 2000 });
          await sleep(300);
          if (await page.getByText('退出登录').count()) {
            await page.getByText('退出登录').first().click({ force: true });
            break;
          }
        }
      } catch {}
    }
  }
  await page.waitForURL(/\/login/, { timeout: 15000 });
}

async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: EDGE_PATH });
  const adminContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const adminPage = await adminContext.newPage();
  const apiCtx = await request.newContext();
  let healthStatus = null;
  let healthBody = null;

  try {
    const healthResp = await apiCtx.get(`${API}/actuator/health`);
    healthStatus = healthResp.status();
    healthBody = await healthResp.text();

    const smoke = addCase('U0', '3000/8080 健康检查');
    smoke.result = healthStatus === 200 ? '✅' : '❌';
    smoke.note = `health=${healthStatus}`;

    await loginAsAdmin(adminPage);
    const authHeaders = await getAuthHeaders(adminPage);
    const roles = await fetchRoles(apiCtx, authHeaders);
    const roleMap = new Map(roles.map((role) => [role.roleCode, role]));

    const missingRoles = ROLE_PLAN.filter((role) => !roleMap.has(role.code)).map((role) => role.code);
    if (missingRoles.length) {
      throw new Error(`Missing roles from /roles/enabled: ${missingRoles.join(', ')}`);
    }

    const probeUsernames = ROLE_PLAN.map((role) => `qa_${role.code}_${stamp}`);
    await cleanupUsers(adminPage, probeUsernames);

    const createCase = addCase('U1', '用户管理创建 6 个角色账号');
    await adminPage.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle', timeout: 30000 });
    const creationResults = [];
    for (const plan of ROLE_PLAN) {
      const role = roleMap.get(plan.code);
      const username = `qa_${plan.code}_${stamp}`;
      const realName = `QA-${plan.code}-${stamp}`;
      const email = `${username}@example.com`;

      await adminPage.getByRole('button', { name: '新增用户' }).click({ force: true });
      const inputs = adminPage.locator('.n-modal-body input, [role="dialog"] input');
      await inputs.nth(0).fill(username);
      await inputs.nth(1).fill('admin123');
      await inputs.nth(2).fill(realName);
      await inputs.nth(3).fill('13900000000');
      await inputs.nth(4).fill(email);
      await selectRoleByName(adminPage, role.roleName);
      const createRespPromise = adminPage.waitForResponse((r) => r.url().endsWith('/api/users') && r.request().method() === 'POST', { timeout: 15000 });
      await adminPage.getByRole('button', { name: '确定' }).click({ force: true });
      const createResp = await createRespPromise;
      await sleep(900);

      await searchUser(adminPage, username);
      const body = await adminPage.locator('body').innerText();
      const present = body.includes(username) && body.includes(realName) && body.includes(role.roleName);
      CREATED_USERS.push({
        username,
        password: 'admin123',
        roleCode: plan.code,
        roleName: role.roleName,
        expectedHome: plan.expectedHome
      });
      creationResults.push(`${plan.code}:${createResp.status()}:${present}`);
    }
    createCase.result = creationResults.every((part) => part.includes(':200:true')) ? '✅' : '❌';
    createCase.note = creationResults.join(' ; ');
    createCase.screenshot = await screenshot(adminPage, 'U1_create_users');

    const updateCase = addCase('U2', '编辑 channel_staff 账号真实姓名');
    const userToEdit = CREATED_USERS.find((user) => user.roleCode === 'channel_staff');
    if (!userToEdit) {
      throw new Error('No channel_staff test user created');
    }
    await adminPage.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle', timeout: 30000 });
    await searchUser(adminPage, userToEdit.username);
    let row = adminPage.locator('tbody tr').filter({ hasText: userToEdit.username }).first();
    await row.getByText('编辑').click({ force: true });
    await sleep(300);
    const editInputs = adminPage.locator('.n-modal-body input, [role="dialog"] input');
    const editedRealName = `${userToEdit.roleName}-edited`;
    await editInputs.nth(1).fill(editedRealName);
    const editRespPromise = adminPage.waitForResponse((r) => /\/api\/users\/.+/.test(r.url()) && r.request().method() === 'PUT', { timeout: 15000 });
    await adminPage.getByRole('button', { name: '确定' }).click({ force: true });
    const editResp = await editRespPromise;
    await sleep(900);
    await searchUser(adminPage, userToEdit.username);
    const editBody = await adminPage.locator('body').innerText();
    const editedOk = editBody.includes(editedRealName);
    updateCase.result = editResp.status() === 200 && editedOk ? '✅' : '❌';
    updateCase.note = `edit=${editResp.status()} edited=${editedOk}`;
    updateCase.screenshot = await screenshot(adminPage, 'U2_edit_channel_staff');
    userToEdit.realName = editedRealName;

    const loginCase = addCase('U3', '6 个新账号逐个登录并校验首页');
    const loginResults = [];
    for (const user of CREATED_USERS) {
      const userContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
      const userPage = await userContext.newPage();
      try {
        await login(userPage, user.username, user.password);
        await userPage.waitForLoadState('networkidle', { timeout: 15000 });
        await sleep(500);
        const currentUrl = new URL(userPage.url());
        const actualPath = currentUrl.pathname;
        loginResults.push(`${user.roleCode}:${actualPath}`);
        if (actualPath !== user.expectedHome) {
          NOTES.push(`Landing mismatch for ${user.username}: expected ${user.expectedHome}, got ${actualPath}`);
        }
      } finally {
        await userContext.close();
      }
    }
    const loginOk = CREATED_USERS.every((user) => loginResults.includes(`${user.roleCode}:${user.expectedHome}`));
    loginCase.result = loginOk ? '✅' : '❌';
    loginCase.note = loginResults.join(' ; ');

    const deleteCase = addCase('U4', '删除 6 个测试账号');
    await loginAsAdmin(adminPage);
    const deleteResults = [];
    await adminPage.goto(`${FRONTEND}/system/users`, { waitUntil: 'networkidle', timeout: 30000 });
    for (const user of CREATED_USERS) {
      await searchUser(adminPage, user.username);
      const body = await adminPage.locator('body').innerText();
      if (!body.includes(user.username)) {
        deleteResults.push(`${user.roleCode}:missing`);
        continue;
      }
      row = adminPage.locator('tbody tr').filter({ hasText: user.username }).first();
      const deleteRespPromise = adminPage.waitForResponse((r) => /\/api\/users\/.+/.test(r.url()) && r.request().method() === 'DELETE', { timeout: 15000 });
      await row.getByText('删除').click({ force: true });
      await sleep(200);
      await adminPage.getByText('确认').last().click({ force: true });
      const deleteResp = await deleteRespPromise;
      await sleep(800);
      await searchUser(adminPage, user.username);
      const nextBody = await adminPage.locator('body').innerText();
      const deleted = !nextBody.includes(user.username);
      deleteResults.push(`${user.roleCode}:${deleteResp.status()}:${deleted}`);
    }
    deleteCase.result = deleteResults.every((part) => part.includes(':200:true')) ? '✅' : '❌';
    deleteCase.note = deleteResults.join(' ; ');
    deleteCase.screenshot = await screenshot(adminPage, 'U4_delete_users');
  } finally {
    fs.writeFileSync(REPORT_JSON, JSON.stringify({
      generatedAt: new Date().toISOString(),
      frontend: FRONTEND,
      backend: BACKEND,
      healthStatus,
      healthBody,
      users: CREATED_USERS,
      cases: CASES,
      notes: NOTES
    }, null, 2));
    fs.writeFileSync(REPORT_MD, buildReport(healthStatus, healthBody));
    await apiCtx.dispose();
    await adminContext.close();
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
