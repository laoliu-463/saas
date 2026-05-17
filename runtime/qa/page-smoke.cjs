const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('@playwright/test');

const argv = process.argv.slice(2);
const outDir = argv[0];
const username = argv[1];
const password = argv[2];
const label = argv[3];
const routesArg = argv[4];

if (!outDir || !username || !password || !label || !routesArg) {
  throw new Error('usage: node page-smoke.cjs <outDir> <username> <password> <label> <route1,route2,...>');
}

const FRONTEND = process.env.QA_FRONTEND || 'http://127.0.0.1:3000';
const routes = routesArg
  .split(/[|,]/)
  .map((item) => item.trim())
  .filter(Boolean);
const allowedRedirects = {
  '/system': ['/system/users'],
  '/product/activity': ['/product/manage'],
  '/product/activity/': ['/product/manage/']
};

const routeExpectations = {
  '/system': {
    mustInclude: ['系统管理', '用户管理']
  },
  '/system/users': {
    mustInclude: ['系统管理', '用户管理']
  }
};

fs.mkdirSync(outDir, { recursive: true });

function excerpt(text, max = 500) {
  return String(text || '').replace(/\s+/g, ' ').trim().slice(0, max);
}

function hasFatalText(text) {
  return /Unexpected Application Error|500 Internal Server Error|NoResourceFoundException|Error response/i.test(text || '');
}

function isAllowedPath(route, finalPath) {
  if (finalPath === route) return true;
  for (const [from, targets] of Object.entries(allowedRedirects)) {
    if (route === from || route.startsWith(from)) {
      return targets.some((target) => finalPath === target || finalPath.startsWith(target));
    }
  }
  return false;
}

function matchesExpectation(route, bodyText) {
  const expectation = routeExpectations[route];
  if (!expectation) return true;
  return expectation.mustInclude.every((text) => bodyText.includes(text));
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ baseURL: FRONTEND });
  const pageConsoleErrors = [];
  const responseEvents = [];
  let currentRoute = 'bootstrap';

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      pageConsoleErrors.push({
        route: currentRoute,
        text: msg.text()
      });
    }
  });

  page.on('response', async (resp) => {
    const status = resp.status();
    if (status < 400) return;
    responseEvents.push({
      route: currentRoute,
      status,
      url: resp.url(),
      method: resp.request().method()
    });
  });

  try {
    await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.getByTestId('login-username').locator('input').fill(username);
    await page.getByTestId('login-password').locator('input').fill(password);
    await page.getByTestId('login-submit').click();
    await page.waitForURL(/\/(dashboard|data|product|sample|orders|ops\/shipping)/, { timeout: 30000 });
    await page.waitForTimeout(3000);

    const authState = await page.evaluate(() => {
      try {
        const raw = JSON.parse(localStorage.getItem('userInfo') || 'null');
        return {
          tokenExists: !!localStorage.getItem('token'),
          username: raw?.username || raw?.name || null,
          roleCodes: raw?.roleCodes || [],
          teamId: raw?.teamId ?? null,
          groupId: raw?.groupId ?? null
        };
      } catch (error) {
        return { parseError: String(error) };
      }
    });

    const envBadge = await page.locator('[data-testid="current-env-badge"]').innerText().catch(() => '');
    const sidebarText = await page.locator('[data-testid="sidebar-menu"]').innerText().catch(() => '');

    const routeResults = [];
    for (const route of routes) {
      currentRoute = route;
      const beforeConsoleCount = pageConsoleErrors.length;
      const beforeResponseCount = responseEvents.length;
      await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
      await page.waitForTimeout(4000);
      await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 4000 }).catch(() => {});
      const bodyText = await page.locator('body').innerText().catch(() => '');
      const finalUrl = page.url();
      const finalPath = new URL(finalUrl).pathname;
      const routeResponses = responseEvents.slice(beforeResponseCount).filter((event) => event.route === route);
      const routeConsole = pageConsoleErrors.slice(beforeConsoleCount).filter((event) => event.route === route);
      const loadingVisible = await page.locator('.n-spin-body').isVisible().catch(() => false);
      const has403 = routeResponses.some((event) => event.status === 403);
      const has500 = routeResponses.some((event) => event.status >= 500);
      const hasEnv401 = routeResponses.some((event) => event.status === 401 && event.url.includes('/api/system/env'));
      const opened = isAllowedPath(route, finalPath);
      const matchesContent = matchesExpectation(route, bodyText);
      routeResults.push({
        route,
        finalPath,
        opened,
        matchesContent,
        hasEnv401,
        hasFatalText: hasFatalText(bodyText),
        has500,
        has403,
        loadingStuck: loadingVisible,
        consoleErrors: routeConsole,
        networkErrors: routeResponses,
        bodyExcerpt: excerpt(bodyText)
      });
    }

    const summary = {
      script: 'qa-page-smoke',
      generatedAt: new Date().toISOString(),
      outputDir: outDir,
      account: { username, label },
      envBadge,
      authState,
      sidebarText,
      routeResults,
      overallPass: routeResults.every(
        (item) => item.opened && item.matchesContent && !item.hasEnv401 && !item.hasFatalText && !item.has500 && !item.loadingStuck
      )
    };

    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));

    const lines = [];
    lines.push(`# QA Page Smoke - ${label}`);
    lines.push('');
    lines.push(`- Username: ${username}`);
    lines.push(`- Env badge: ${envBadge}`);
    lines.push(`- Role codes: ${(authState.roleCodes || []).join(', ')}`);
    lines.push('');
    lines.push('## Sidebar');
    lines.push('```text');
    lines.push(sidebarText || '(empty)');
    lines.push('```');
    lines.push('');
    lines.push('## Routes');
    for (const result of routeResults) {
      lines.push(`- ${result.route} -> ${result.finalPath} | opened=${result.opened} | content=${result.matchesContent} | env401=${result.hasEnv401} | 403=${result.has403} | 500=${result.has500} | loadingStuck=${result.loadingStuck} | fatal=${result.hasFatalText}`);
    }
    lines.push('');
    lines.push('## Details');
    lines.push('```json');
    lines.push(JSON.stringify(summary, null, 2));
    lines.push('```');
    fs.writeFileSync(path.join(outDir, 'report.md'), `${lines.join('\n')}\n`);

    if (!summary.overallPass) {
      process.exitCode = 1;
    }
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
