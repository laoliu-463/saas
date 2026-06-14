const path = require('node:path');

const REPO_ROOT = path.resolve(__dirname, '..', '..');
const { chromium, request } = require(path.join(REPO_ROOT, 'node_modules', 'playwright'));
const {
  applyRealPreEnv,
  createEvidenceDir,
  redactSecretLikeKeys,
  writeJson,
  writeText,
  unwrapApiBody
} = require('./real-pre-env.cjs');

const urls = applyRealPreEnv(process.env);
const FRONTEND = urls.frontendUrl;
const BACKEND = urls.backendUrl;
const API = urls.apiBaseUrl;
const USERNAME = process.env.E2E_ADMIN_USERNAME || 'admin';
const PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'admin123';
const HEADLESS = process.env.E2E_HEADLESS ? process.env.E2E_HEADLESS === 'true' : true;
const OUT_DIR = createEvidenceDir(REPO_ROOT, 'order-detail-field-source-visible', 'ORDER_DETAIL_FIELD_SOURCE_EVIDENCE_DIR');

const report = {
  generatedAt: new Date().toISOString(),
  frontend: FRONTEND,
  backend: BACKEND,
  conclusion: 'PENDING',
  steps: [],
  screenshots: []
};

function addStep(name, status, details = {}) {
  report.steps.push({
    name,
    status,
    details: redactSecretLikeKeys(details)
  });
}

async function runStep(name, fn) {
  const startedAt = Date.now();
  try {
    const details = await fn();
    addStep(name, 'PASS', { durationMs: Date.now() - startedAt, ...details });
    return details;
  } catch (error) {
    addStep(name, 'FAIL', {
      durationMs: Date.now() - startedAt,
      error: error instanceof Error ? error.message : String(error)
    });
    report.conclusion = 'FAIL';
    throw error;
  }
}

async function apiLogin(apiContext) {
  const response = await apiContext.post(`${API}/auth/login`, {
    data: { username: USERNAME, password: PASSWORD }
  });
  const body = await response.json().catch(() => ({}));
  const data = unwrapApiBody(body) || {};
  const token = data.token || data.accessToken;
  if (!response.ok() || !token) {
    throw new Error(`admin login failed: HTTP ${response.status()}`);
  }
  return token;
}

function extractRecords(body) {
  const data = unwrapApiBody(body) || {};
  if (Array.isArray(data.records)) return data.records;
  if (Array.isArray(data.list)) return data.list;
  if (Array.isArray(data.content)) return data.content;
  if (Array.isArray(data)) return data;
  return [];
}

async function firstOrderId(apiContext, token) {
  const response = await apiContext.get(`${API}/orders`, {
    headers: { Authorization: `Bearer ${token}` },
    params: { page: 1, size: 1 }
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok()) {
    throw new Error(`GET /api/orders failed: HTTP ${response.status()}`);
  }
  const records = extractRecords(body);
  const orderId = records[0]?.orderId || records[0]?.order_id || '';
  return {
    orderId: String(orderId || ''),
    total: Number(unwrapApiBody(body)?.total || records.length || 0)
  };
}

async function loginByUi(page) {
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.getByTestId('login-username').locator('input').fill(USERNAME);
  await page.getByTestId('login-password').locator('input').fill(PASSWORD);
  const loginResponse = page.waitForResponse(
    (resp) => resp.url().includes('/api/auth/login') && resp.request().method() === 'POST',
    { timeout: 30000 }
  ).catch(() => null);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => null),
    page.getByTestId('login-submit').click()
  ]);
  const response = await loginResponse;
  const pathname = new URL(page.url()).pathname;
  if (pathname.startsWith('/login')) {
    throw new Error('login did not leave /login');
  }
  return {
    loginHttpStatus: response ? response.status() : null,
    finalUrl: page.url()
  };
}

async function screenshot(page, name) {
  const filePath = path.join(OUT_DIR, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage: true });
  report.screenshots.push(filePath);
  return filePath;
}

function assertSources(sourceTexts) {
  if (sourceTexts.length < 7) {
    throw new Error(`expected at least 7 section-source nodes, got ${sourceTexts.length}`);
  }
  const required = ['colonelsettlement_order', 'pick_source_mapping', 'sample_request'];
  for (const keyword of required) {
    if (!sourceTexts.some((text) => text.includes(keyword))) {
      throw new Error(`missing field source keyword: ${keyword}`);
    }
  }
}

function writeReport() {
  writeJson(path.join(OUT_DIR, 'summary.json'), report);
  const lines = [
    '# order-detail-field-source-visible',
    '',
    `- generatedAt: ${report.generatedAt}`,
    `- frontend: ${report.frontend}`,
    `- backend: ${report.backend}`,
    `- conclusion: ${report.conclusion}`,
    '',
    '## steps',
    ...report.steps.map((step) => `- ${step.status} ${step.name}: ${JSON.stringify(step.details)}`),
    '',
    '## screenshots',
    ...report.screenshots.map((item) => `- ${item}`)
  ];
  writeText(path.join(OUT_DIR, 'report.md'), `${lines.join('\n')}\n`);
}

(async () => {
  let browser;
  const apiContext = await request.newContext({ ignoreHTTPSErrors: true });
  try {
    const token = await runStep('api login and find first order', async () => {
      const authToken = await apiLogin(apiContext);
      const order = await firstOrderId(apiContext, authToken);
      report.orderId = order.orderId;
      if (!order.orderId) {
        report.conclusion = 'PENDING';
        return { total: order.total, pendingReason: 'real-pre order list is empty' };
      }
      return { total: order.total, orderId: order.orderId };
    });

    if (!report.orderId) {
      return;
    }

    browser = await chromium.launch({ headless: HEADLESS });
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

    await runStep('login by UI', async () => loginByUi(page));

    await runStep('open order detail modal from list', async () => {
      await page.goto(`${FRONTEND}/orders?orderId=${encodeURIComponent(report.orderId)}`, {
        waitUntil: 'domcontentloaded',
        timeout: 30000
      });
      await page.getByTestId('orders-page').waitFor({ state: 'visible', timeout: 30000 });
      await page.getByTestId('order-detail-button').first().waitFor({ state: 'visible', timeout: 30000 });
      const detailResponse = page.waitForResponse(
        (resp) => resp.url().includes(`/api/orders/${encodeURIComponent(report.orderId)}`) && resp.status() < 500,
        { timeout: 30000 }
      ).catch(() => null);
      await page.getByTestId('order-detail-button').first().click();
      const response = await detailResponse;
      await page.locator('.detail-body').waitFor({ state: 'visible', timeout: 45000 });
      await page.locator('.section-source').first().waitFor({ state: 'visible', timeout: 15000 });
      const sourceTexts = (await page.locator('.section-source').allTextContents()).map((text) => text.trim());
      assertSources(sourceTexts);
      const screenshotPath = await screenshot(page, 'order-detail-field-source');
      return {
        orderId: report.orderId,
        detailHttpStatus: response ? response.status() : null,
        sectionSourceCount: sourceTexts.length,
        sourceTexts,
        screenshot: screenshotPath
      };
    });

    report.conclusion = 'PASS';
  } catch (error) {
    if (report.conclusion !== 'FAIL') report.conclusion = 'FAIL';
    report.error = error instanceof Error ? error.message : String(error);
    throw error;
  } finally {
    await apiContext.dispose();
    if (browser) await browser.close();
    writeReport();
    console.log(JSON.stringify(redactSecretLikeKeys(report), null, 2));
  }
})().catch(() => {
  process.exitCode = report.conclusion === 'PENDING' ? 2 : 1;
});
