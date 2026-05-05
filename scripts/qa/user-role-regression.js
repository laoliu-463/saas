const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const BASE_URL = 'http://localhost:3000';
const PASSWORD = 'admin123';
const timestamp = new Date()
  .toISOString()
  .replace(/[-:TZ.]/g, '')
  .slice(0, 14);
const outputDir = path.join('D:', 'Projects', 'SAAS', 'out', `e2e-user-role-${timestamp}`);

const roles = [
  { code: 'admin', name: '超级管理员', expectedPath: '/dashboard' },
  { code: 'biz_leader', name: '招商组长', expectedPath: '/dashboard' },
  { code: 'biz_staff', name: '招商专员', expectedPath: '/data' },
  { code: 'channel_leader', name: '渠道组长', expectedPath: '/dashboard' },
  { code: 'channel_staff', name: '渠道专员', expectedPath: '/data' },
  { code: 'ops_staff', name: '运营', expectedPath: '/ops/shipping' }
];

function ensureOutputDir() {
  fs.mkdirSync(outputDir, { recursive: true });
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function waitForToast(page, text) {
  const toast = page.locator('.n-message').filter({ hasText: text }).last();
  await toast.waitFor({ state: 'visible', timeout: 10000 });
  return (await toast.textContent()) || '';
}

async function login(page, username, password) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: '登 录' }).click();
}

async function createUser(adminPage, user) {
  await adminPage.goto(`${BASE_URL}/system/users`, { waitUntil: 'networkidle' });
  await adminPage.getByRole('button', { name: '新增用户' }).click();
  const modal = adminPage.locator('.n-modal').filter({ hasText: '新增用户' }).last();
  await modal.waitFor({ state: 'visible', timeout: 10000 });

  const inputs = modal.locator('input');
  await inputs.nth(0).fill(user.username);
  await inputs.nth(1).fill(PASSWORD);
  await inputs.nth(2).fill(user.realName);
  await inputs.nth(3).fill(user.phone);
  await inputs.nth(4).fill(user.email);

  await modal.locator('.n-base-selection').first().click();
  await adminPage.locator('.n-base-select-option').filter({ hasText: user.roleName }).last().click();
  await modal.getByText(`已选身份：${user.roleName}`).waitFor({ state: 'visible', timeout: 5000 });

  await modal.getByRole('button', { name: '确定' }).click();
  const toastText = await waitForToast(adminPage, `用户 ${user.username} 已创建`);

  await adminPage.getByPlaceholder('请输入用户名').waitFor({ state: 'visible' });
  const searchInput = adminPage.getByPlaceholder('请输入用户名');
  assert((await searchInput.inputValue()) === user.username, `搜索框未回填用户名 ${user.username}`);
  await adminPage.getByRole('cell', { name: user.username }).first().waitFor({ state: 'visible', timeout: 10000 });
  await adminPage.screenshot({ path: path.join(outputDir, `${user.code}-created.png`), fullPage: true });
  return toastText;
}

async function verifyLogin(browser, user) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const page = await context.newPage();
  const consoleErrors = [];

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  await login(page, user.username, PASSWORD);
  await page.waitForURL(`**${user.expectedPath}`, { timeout: 15000 });
  assert(page.url().endsWith(user.expectedPath), `${user.username} 实际落地 ${page.url()}，期望 ${user.expectedPath}`);
  await page.locator('.user-name').filter({ hasText: user.realName }).waitFor({ state: 'visible', timeout: 10000 });
  await page.screenshot({ path: path.join(outputDir, `${user.code}-home.png`), fullPage: true });
  await context.close();
  return consoleErrors;
}

async function deleteUser(adminPage, user) {
  await adminPage.goto(`${BASE_URL}/system/users`, { waitUntil: 'networkidle' });
  const searchInput = adminPage.getByPlaceholder('请输入用户名');
  await searchInput.fill(user.username);
  await adminPage.getByRole('button', { name: '查询' }).click();
  const row = adminPage.locator('tr', { hasText: user.username }).first();
  await row.waitFor({ state: 'visible', timeout: 10000 });
  await row.getByRole('button', { name: '删除' }).click();
  const popconfirm = adminPage.locator('.n-popover').filter({ hasText: '确认删除该用户吗？' }).last();
  await popconfirm.waitFor({ state: 'visible', timeout: 5000 });
  await popconfirm.getByRole('button', { name: '确认' }).click();
  return waitForToast(adminPage, '删除成功');
}

async function main() {
  ensureOutputDir();
  const browser = await chromium.launch({ headless: true });
  const adminContext = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const adminPage = await adminContext.newPage();
  const adminConsoleErrors = [];

  adminPage.on('console', (msg) => {
    if (msg.type() === 'error') {
      adminConsoleErrors.push(msg.text());
    }
  });

  const results = [];

  try {
    await login(adminPage, 'admin', PASSWORD);
    await adminPage.waitForURL('**/dashboard', { timeout: 15000 });

    for (const role of roles) {
      const user = {
        ...role,
        username: `trace_${role.code}_${timestamp}`,
        realName: `回归-${role.name}`,
        phone: `139${Math.floor(Math.random() * 90000000 + 10000000)}`,
        email: `${role.code}.${timestamp}@example.com`,
        roleName: role.name
      };

      const createToast = await createUser(adminPage, user);
      const consoleErrors = await verifyLogin(browser, user);
      const deleteToast = await deleteUser(adminPage, user);

      results.push({
        role: role.code,
        username: user.username,
        expectedPath: role.expectedPath,
        createToast,
        deleteToast,
        consoleErrors
      });
    }
  } finally {
    fs.writeFileSync(
      path.join(outputDir, 'results.json'),
      JSON.stringify({ results, adminConsoleErrors }, null, 2)
    );
    await adminContext.close();
    await browser.close();
  }

  console.log(JSON.stringify({ outputDir, results, adminConsoleErrors }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
