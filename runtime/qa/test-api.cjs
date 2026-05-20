async function getAuthToken(apiContext, apiBase, username = 'admin', password = 'admin123') {
  const res = await apiContext.post(`${apiBase}/auth/login`, {
    data: { username, password }
  });
  const body = await res.json().catch(() => ({}));
  const token = body?.data?.token || body?.data?.accessToken;
  if (!res.ok() || !token) {
    throw new Error(`QA API 登录失败: ${username} (${res.status()})`);
  }
  return token;
}

async function postTestAction(apiContext, apiBase, token, path, data) {
  const normalized = path.startsWith('/') ? path : `/${path}`;
  const res = await apiContext.post(`${apiBase}${normalized}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data
  });
  const body = await res.json().catch(() => ({}));
  return {
    ok: res.ok(),
    status: res.status(),
    body: body?.data || body
  };
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function gotoStable(page, url, { timeout = 45000 } = {}) {
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout });
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {});
  await page.locator('.loading-state').waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {});
  await page.locator('#boot-loading').waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {});
  await sleep(400);
}

async function resetBrowserSession(page, context, frontendBase) {
  await context.clearCookies();
  await page.evaluate(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    sessionStorage.clear();
  }).catch(() => {});
  await page.goto(`${frontendBase}/login`, { waitUntil: 'domcontentloaded', timeout: 45000 }).catch(() => {});
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  await sleep(300);
}

async function loginViaUi(page, context, frontendBase, username, password, { expectedPath = null } = {}) {
  await resetBrowserSession(page, context, frontendBase);
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码').fill(password);
  const loginRespPromise = page.waitForResponse(
    (resp) => resp.url().includes('/api/auth/login') && resp.request().method() === 'POST',
    { timeout: 15000 }
  ).catch(() => null);
  await page.getByRole('button', { name: /登/ }).click();
  const loginResp = await loginRespPromise;
  if (expectedPath) {
    await page.waitForURL((url) => url.pathname === expectedPath, { timeout: 15000 }).catch(() => {});
  } else {
    await sleep(1200);
  }
  await gotoStable(page, page.url());
  return {
    url: page.url().replace(frontendBase, ''),
    status: loginResp ? loginResp.status() : null
  };
}

async function countScopedRows(page, scopeTestId) {
  const scoped = page.locator(`[data-testid="${scopeTestId}"]`);
  const orderRows = await scoped.locator('[data-testid="order-row"]').count();
  if (orderRows > 0) {
    return orderRows;
  }
  const tableRows = await scoped.locator('.n-data-table-tbody .n-data-table-tr').count();
  if (tableRows > 0) {
    return tableRows;
  }
  return scoped.locator('.n-data-table-tr').count();
}

async function fetchAuthedApi(page, apiPath) {
  return page.evaluate(async (path) => {
    const token = localStorage.getItem('token');
    if (!token) {
      return { ok: false, status: 0, data: null, error: 'missing-token' };
    }
    const resp = await fetch(`/api${path}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    const json = await resp.json().catch(() => ({}));
    return {
      ok: resp.ok,
      status: resp.status,
      data: json?.data ?? json,
      error: resp.ok ? null : json?.msg || json?.message || `http-${resp.status}`
    };
  }, apiPath);
}

async function fetchDashboardSummary(page) {
  const result = await fetchAuthedApi(page, '/dashboard/summary');
  if (!result.ok || !result.data) {
    throw new Error(`dashboard summary 请求失败: status=${result.status}, error=${result.error || 'unknown'}`);
  }
  return result.data;
}

async function waitForScopedRows(page, scopeTestId, minRows = 1, timeout = 20000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const rows = await countScopedRows(page, scopeTestId);
    if (rows >= minRows) {
      return rows;
    }
    await sleep(500);
  }
  return countScopedRows(page, scopeTestId);
}

module.exports = {
  getAuthToken,
  postTestAction,
  sleep,
  gotoStable,
  resetBrowserSession,
  loginViaUi,
  countScopedRows,
  fetchDashboardSummary,
  fetchAuthedApi,
  waitForScopedRows
};
