const fs = require('node:fs');
const path = require('node:path');
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));

const FRONTEND = process.env.QA_FRONTEND || 'http://localhost:3000';
const HEADLESS = process.env.QA_HEADLESS ? process.env.QA_HEADLESS !== 'false' : false;
const SLOW_MO = Number(process.env.QA_SLOW_MO || 1500);
const EDGE_PATH = fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
  ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
  : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe';

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const OUT_DIR = path.join(__dirname, 'out', `permission-boundary-visible-${stamp}`);
const SHOT_DIR = path.join(OUT_DIR, 'screenshots');
const REPORT_MD = path.join(OUT_DIR, 'report.md');
const REPORT_JSON = path.join(OUT_DIR, 'report.json');
fs.mkdirSync(SHOT_DIR, { recursive: true });

const USERS = [
  {
    id: 'PB-01',
    username: 'admin',
    password: 'admin123',
    expectedHome: '/dashboard',
    expectMenus: ['系统管理', '数据平台', '商品库', '达人 CRM', '寄样台'],
    forbidMenus: []
  },
  {
    id: 'PB-02',
    username: 'biz_leader',
    password: 'admin123',
    expectedHome: '/dashboard',
    expectMenus: ['数据看板', '商品库', '商品管理', '寄样审核'],
    forbidMenus: ['系统管理', '达人 CRM']
  },
  {
    id: 'PB-03',
    username: 'biz_staff',
    password: 'admin123',
    expectedHome: '/data',
    expectMenus: ['我的业绩', '商品库', '商品管理', '寄样台'],
    forbidMenus: ['系统管理', '订单工作台', '达人 CRM']
  },
  {
    id: 'PB-04',
    username: 'channel_leader',
    password: 'admin123',
    expectedHome: '/dashboard',
    expectMenus: ['数据平台', '商品库', '达人 CRM', '寄样台', '订单工作台'],
    forbidMenus: ['系统管理']
  },
  {
    id: 'PB-05',
    username: 'channel_staff',
    password: 'admin123',
    expectedHome: '/data',
    expectMenus: ['我的业绩', '商品库', '我的达人', '寄样台'],
    forbidMenus: ['系统管理', '订单工作台']
  },
  {
    id: 'PB-06',
    username: 'ops_staff',
    password: 'admin123',
    expectedHome: '/ops/shipping',
    expectMenus: ['寄样发货台', '物流发货'],
    forbidMenus: ['系统管理', '订单工作台', '达人 CRM', '商品库', '寄样审核', '独家状态']
  }
];

const ROUTE_CASES = [
  { id: 'PB-07', username: 'channel_staff', route: '/system/users', expected: '/data', note: '渠道专员不可进入用户管理' },
  { id: 'PB-08', username: 'biz_staff', route: '/system/roles', expected: '/data', note: '招商专员不可进入角色管理' },
  { id: 'PB-09', username: 'ops_staff', route: '/system/config', expected: '/ops/shipping', note: '运营不可进入系统配置' },
  { id: 'PB-10', username: 'channel_leader', route: '/system/operation-logs', expected: '/dashboard', note: '渠道组长不可进入操作日志中心' }
];

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  slowMo: SLOW_MO,
  cases: []
};

function short(text, max = 160) {
  const value = String(text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForIdle(page) {
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 4000 }).catch(() => {});
  await sleep(500);
}

async function shot(page, name) {
  const file = path.join(SHOT_DIR, `${name}.png`);
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

async function login(page, username, password) {
  await resetSession(page);
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'networkidle', timeout: 30000 });
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: /登/ }).click();
  await sleep(1200);
  await waitForIdle(page);
  return page.url().replace(FRONTEND, '');
}

function pushCase(item) {
  report.cases.push(item);
  fs.writeFileSync(REPORT_JSON, JSON.stringify(report, null, 2));
}

function writeMarkdown() {
  const passed = report.cases.filter((item) => item.result === '✅').length;
  const lines = [
    `# 权限边界可见浏览器回归 - ${new Date().toLocaleString('zh-CN', { hour12: false })}`,
    '',
    `- 前端: ${FRONTEND}`,
    `- 模式: ${HEADLESS ? 'headless' : 'visible'} / slowMo=${SLOW_MO}ms`,
    `- 通过: ${passed}/${report.cases.length}`,
    '',
    '| 编号 | 测试点 | 结果 | 备注 |',
    '| --- | --- | --- | --- |',
    ...report.cases.map((item) => `| ${item.id} | ${item.title} | ${item.result} | ${String(item.note || '').replace(/\|/g, '/')} |`),
    '',
    '## 截图',
    ...report.cases.filter((item) => item.screenshot).map((item) => `- ${item.id}: ${item.screenshot}`)
  ];
  fs.writeFileSync(REPORT_MD, `${lines.join('\n')}\n`);
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
    for (const user of USERS) {
      const item = { id: user.id, title: `${user.username} 登录与菜单`, result: '❌', note: '', screenshot: '' };
      try {
        const current = await login(page, user.username, user.password);
        const body = await page.locator('body').innerText().catch(() => '');
        const missing = user.expectMenus.filter((label) => !body.includes(label));
        const unexpected = user.forbidMenus.filter((label) => body.includes(label));
        const ok = current === user.expectedHome && missing.length === 0 && unexpected.length === 0;
        item.result = ok ? '✅' : '❌';
        item.note = `落地=${current}; 缺失=${missing.join(',') || '无'}; 越权菜单=${unexpected.join(',') || '无'}`;
        item.screenshot = await shot(page, `${user.id}_${user.username}_home`);
      } catch (error) {
        item.result = '❌';
        item.note = short(error.message || error);
        item.screenshot = await shot(page, `${user.id}_${user.username}_fail`).catch(() => '');
      }
      pushCase(item);
      writeMarkdown();
    }

    for (const routeCase of ROUTE_CASES) {
      const user = USERS.find((item) => item.username === routeCase.username);
      const item = { id: routeCase.id, title: routeCase.note, result: '❌', note: '', screenshot: '' };
      try {
        await login(page, user.username, user.password);
        await page.goto(`${FRONTEND}${routeCase.route}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
        await waitForIdle(page);
        const current = page.url().replace(FRONTEND, '');
        const body = await page.locator('body').innerText().catch(() => '');
        const ok = current === routeCase.expected || body.includes('权限') || body.includes('无权');
        item.result = ok ? '✅' : '❌';
        item.note = `访问=${routeCase.route}; 实际=${current}; 页面摘要=${short(body)}`;
        item.screenshot = await shot(page, `${routeCase.id}_${routeCase.username}_route_guard`);
      } catch (error) {
        item.result = '❌';
        item.note = short(error.message || error);
        item.screenshot = await shot(page, `${routeCase.id}_${routeCase.username}_fail`).catch(() => '');
      }
      pushCase(item);
      writeMarkdown();
    }
  } finally {
    await context.close();
    await browser.close();
    writeMarkdown();
  }

  console.log(`report: ${REPORT_MD}`);
}

main().catch((error) => {
  console.error(error);
  writeMarkdown();
  process.exitCode = 1;
});
