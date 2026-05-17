const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('@playwright/test');

const FRONTEND = process.env.QA_FRONTEND || 'http://127.0.0.1:3000';
const CASES_FILE = path.join(__dirname, 'role-page-cases.json');
const DWELL_MS = Number(process.env.QA_PAGE_ROLE_DWELL_MS || 4000);
const DEFAULT_CONSOLE_NOISE = [
  'ResizeObserver loop limit exceeded',
  'ResizeObserver loop completed with undelivered notifications',
  '/favicon.ico',
  'Failed to load resource: the server responded with a status of 404'
];

function normalizePathname(input) {
  const raw = String(input || '/').split(/[?#]/)[0].trim() || '/';
  const pathname = raw.startsWith('/') ? raw : `/${raw}`;
  return pathname === '/' ? pathname : pathname.replace(/\/+$/, '');
}

function matchesPathPrefix(pathname, prefix) {
  const finalPath = normalizePathname(pathname);
  const base = normalizePathname(prefix);
  return finalPath === base || finalPath.startsWith(`${base}/`);
}

function matchesAllowedPath(pathname, allowedList) {
  return (allowedList || []).some((candidate) => matchesPathPrefix(pathname, candidate));
}

function loadRoleCases(filePath = CASES_FILE) {
  const raw = JSON.parse(fs.readFileSync(filePath, 'utf8'));
  return raw.roles || raw;
}

function resolveTextTarget(rule, sidebarText, routeResults) {
  if (rule.scope === '@sidebar') {
    return {
      label: '@sidebar',
      text: sidebarText || ''
    };
  }

  const rulePath = normalizePathname(rule.path || '/');
  const routeResult = routeResults.find(
    (item) => normalizePathname(item.route) === rulePath || normalizePathname(item.finalPath) === rulePath
  );

  return {
    label: rulePath,
    text: routeResult?.bodyText || ''
  };
}

function evaluateTextRules({ seeRules, notSeeRules, sidebarText, routeResults }) {
  const seeResults = (seeRules || []).map((rule) => {
    const target = resolveTextTarget(rule, sidebarText, routeResults);
    const checks = (rule.texts || []).map((text) => ({
      text,
      ok: target.text.includes(text)
    }));
    return {
      type: 'mustSeeText',
      target: target.label,
      checks,
      pass: checks.every((item) => item.ok)
    };
  });

  const notSeeResults = (notSeeRules || []).map((rule) => {
    const target = resolveTextTarget(rule, sidebarText, routeResults);
    const checks = (rule.texts || []).map((text) => ({
      text,
      ok: !target.text.includes(text)
    }));
    return {
      type: 'mustNotSeeText',
      target: target.label,
      checks,
      pass: checks.every((item) => item.ok)
    };
  });

  return {
    pass: [...seeResults, ...notSeeResults].every((item) => item.pass),
    seeResults,
    notSeeResults
  };
}

function evaluateAccessRule(rule, routeResult) {
  const rulePath = rule.path || rule.goto;
  const allowedFinalPaths = rule.allowedFinalPaths?.length ? rule.allowedFinalPaths : [rulePath];
  return {
    path: rulePath,
    finalPath: routeResult.finalPath,
    allowedFinalPaths,
    pass: matchesAllowedPath(routeResult.finalPath, allowedFinalPaths)
  };
}

function evaluateRejectRule(rule, routeResult) {
  const rulePath = rule.path || rule.goto;
  const sensitivePrefix = rule.mustNotStayUnderPrefix || rulePath;
  const stayedUnderSensitivePrefix = matchesPathPrefix(routeResult.finalPath, sensitivePrefix);
  const fallbackOk = rule.allowedFinalPaths?.length
    ? matchesAllowedPath(routeResult.finalPath, rule.allowedFinalPaths)
    : true;

  return {
    path: rulePath,
    finalPath: routeResult.finalPath,
    mustNotStayUnderPrefix: sensitivePrefix,
    allowedFinalPaths: rule.allowedFinalPaths || [],
    stayedUnderSensitivePrefix,
    pass: !stayedUnderSensitivePrefix && fallbackOk
  };
}

function isConsoleNoise(text, extraAllowSubstrings = []) {
  const value = String(text || '');
  if ((value.includes('system/env') || value.includes('/api/system/env')) && value.includes('401')) {
    return true;
  }
  return [...DEFAULT_CONSOLE_NOISE, ...(extraAllowSubstrings || [])].some((item) => item && value.includes(item));
}

function hasFatalText(text) {
  return /Unexpected Application Error|500 Internal Server Error|NoResourceFoundException|Error response/i.test(text || '');
}

function excerpt(text, max = 500) {
  return String(text || '').replace(/\s+/g, ' ').trim().slice(0, max);
}

async function waitForPageSettled(page) {
  await page.waitForTimeout(DWELL_MS);
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 4000 }).catch(() => {});
}

async function hasActiveSpinner(page) {
  return page.locator('.n-spin-body').isVisible().catch(() => false);
}

async function loginAsRole(page, username, password) {
  await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.getByTestId('login-username').locator('input').fill(username);
  await page.getByTestId('login-password').locator('input').fill(password);
  await Promise.all([
    page.waitForURL(/\/(dashboard|data|product|sample|orders|ops\/shipping|system)/, {
      waitUntil: 'domcontentloaded',
      timeout: 30000
    }),
    page.getByTestId('login-submit').click()
  ]);
  await waitForPageSettled(page);
}

async function collectAuthState(page) {
  return page.evaluate(() => {
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
}

async function visitRoute(page, route, sharedState) {
  sharedState.currentRoute = route;
  const beforeConsoleRaw = sharedState.consoleErrorsRaw.length;
  const beforeConsoleFiltered = sharedState.consoleErrorsFiltered.length;
  const beforeResponses = sharedState.apiResponses.length;

  await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
  await waitForPageSettled(page);

  const finalPath = normalizePathname(new URL(page.url()).pathname);
  const bodyText = await page.locator('body').innerText().catch(() => '');
  const loadingStuck = await hasActiveSpinner(page);
  const apiResponses = sharedState.apiResponses.slice(beforeResponses).filter((item) => item.route === route);
  const consoleErrorsRaw = sharedState.consoleErrorsRaw.slice(beforeConsoleRaw).filter((item) => item.route === route);
  const consoleErrorsFiltered = sharedState.consoleErrorsFiltered
    .slice(beforeConsoleFiltered)
    .filter((item) => item.route === route);

  return {
    route,
    finalPath,
    bodyText,
    bodyExcerpt: excerpt(bodyText),
    loadingStuck,
    hasFatalText: hasFatalText(bodyText),
    apiResponses,
    consoleErrorsRaw,
    consoleErrorsFiltered,
    hasApi500: apiResponses.some((item) => item.status >= 500)
  };
}

function createSharedState(extraConsoleNoise) {
  return {
    currentRoute: 'bootstrap',
    consoleErrorsRaw: [],
    consoleErrorsFiltered: [],
    apiResponses: [],
    extraConsoleNoise
  };
}

function attachPageObservers(page, sharedState) {
  page.on('console', (msg) => {
    if (msg.type() !== 'error') {
      return;
    }
    const event = {
      route: sharedState.currentRoute,
      text: msg.text()
    };
    sharedState.consoleErrorsRaw.push(event);
    if (!isConsoleNoise(event.text, sharedState.extraConsoleNoise)) {
      sharedState.consoleErrorsFiltered.push(event);
    }
  });

  page.on('response', (resp) => {
    const url = resp.url();
    if (!url.includes('/api/')) {
      return;
    }
    sharedState.apiResponses.push({
      route: sharedState.currentRoute,
      status: resp.status(),
      url,
      method: resp.request().method()
    });
  });
}

async function runRoleSmoke({ outDir, role, frontend = FRONTEND, filePath = CASES_FILE }) {
  if (!outDir || !role) {
    throw new Error('usage: node page-role-smoke.cjs <outDir> <role>');
  }

  const roleCases = loadRoleCases(filePath);
  const caseDef = roleCases[role];
  if (!caseDef) {
    throw new Error(`Unknown role "${role}". Known: ${Object.keys(roleCases).join(', ')}`);
  }

  fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ baseURL: frontend });
  const sharedState = createSharedState(caseDef.consoleErrorAllowSubstrings || []);
  attachPageObservers(page, sharedState);

  try {
    await loginAsRole(page, caseDef.username, caseDef.password);

    const authState = await collectAuthState(page);
    const envBadge = await page.locator('[data-testid="current-env-badge"]').innerText().catch(() => '');
    const sidebarText = await page.locator('[data-testid="sidebar-menu"]').innerText().catch(() => '');

    const mustAccessResults = [];
    for (const rule of caseDef.mustAccess || []) {
      const routePath = rule.path || rule.goto;
      const routeResult = await visitRoute(page, routePath, sharedState);
      mustAccessResults.push({
        rule,
        routeResult,
        evaluation: evaluateAccessRule(rule, routeResult)
      });
    }

    const mustRejectResults = [];
    for (const rule of caseDef.mustReject || []) {
      const routePath = rule.path || rule.goto;
      const routeResult = await visitRoute(page, routePath, sharedState);
      mustRejectResults.push({
        rule,
        routeResult,
        evaluation: evaluateRejectRule(rule, routeResult)
      });
    }

    const textRouteMap = new Map();
    for (const item of [...mustAccessResults, ...mustRejectResults]) {
      textRouteMap.set(normalizePathname(item.routeResult.route), item.routeResult);
    }

    const textResults = evaluateTextRules({
      seeRules: caseDef.mustSeeText || [],
      notSeeRules: caseDef.mustNotSeeText || [],
      sidebarText,
      routeResults: [...textRouteMap.values()]
    });

    const allRouteResults = [...mustAccessResults, ...mustRejectResults].map((item) => ({
      route: item.routeResult.route,
      finalPath: item.routeResult.finalPath,
      hasApi500: item.routeResult.hasApi500,
      hasFatalText: item.routeResult.hasFatalText,
      loadingStuck: item.routeResult.loadingStuck,
      bodyExcerpt: item.routeResult.bodyExcerpt,
      consoleErrorsFiltered: item.routeResult.consoleErrorsFiltered,
      apiResponses: item.routeResult.apiResponses
    }));

    const noApi500 = sharedState.apiResponses.every((item) => item.status < 500);
    const noConsoleErrors = sharedState.consoleErrorsFiltered.length === 0;
    const accessOk = mustAccessResults.every((item) => item.evaluation.pass);
    const rejectOk = mustRejectResults.every((item) => item.evaluation.pass);
    const routeHealthOk = [...mustAccessResults, ...mustRejectResults].every(
      (item) => !item.routeResult.hasApi500 && !item.routeResult.hasFatalText
    );
    const overallPass = accessOk && rejectOk && textResults.pass && routeHealthOk && noApi500 && noConsoleErrors;

    const summary = {
      script: 'qa-page-role-smoke',
      role,
      generatedAt: new Date().toISOString(),
      outputDir: outDir,
      casesFile: filePath,
      account: {
        username: caseDef.username
      },
      envBadge,
      authState,
      sidebarText,
      mustAccessResults,
      mustRejectResults,
      textResults,
      allRouteResults,
      api500Events: sharedState.apiResponses.filter((item) => item.status >= 500),
      consoleErrorsRawCount: sharedState.consoleErrorsRaw.length,
      consoleErrorsFiltered: sharedState.consoleErrorsFiltered,
      checks: {
        accessOk,
        rejectOk,
        textOk: textResults.pass,
        routeHealthOk,
        noApi500,
        noConsoleErrors
      },
      overallPass
    };

    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));

    const lines = [];
    lines.push(`# QA Page Role Smoke - ${role}`);
    lines.push('');
    lines.push(`- Account: ${caseDef.username}`);
    lines.push(`- Env badge: ${envBadge}`);
    lines.push(`- Role codes: ${(authState.roleCodes || []).join(', ')}`);
    lines.push(`- Overall: **${overallPass ? 'PASS' : 'FAIL'}**`);
    lines.push('');
    lines.push('## mustAccess');
    for (const item of mustAccessResults) {
      const p = item.rule.path || item.rule.goto;
      lines.push(
        `- ${p} -> ${item.routeResult.finalPath} | allowed=${item.evaluation.allowedFinalPaths.join(', ')} | pass=${item.evaluation.pass}`
      );
    }
    lines.push('');
    lines.push('## mustReject');
    for (const item of mustRejectResults) {
      const p = item.rule.path || item.rule.goto;
      const fallbacks = item.evaluation.allowedFinalPaths?.length ? item.evaluation.allowedFinalPaths.join(', ') : '(any safe page)';
      lines.push(
        `- ${p} -> ${item.routeResult.finalPath} | forbid=${item.evaluation.mustNotStayUnderPrefix} | fallback=${fallbacks} | pass=${item.evaluation.pass}`
      );
    }
    lines.push('');
    lines.push('## Text checks');
    for (const item of textResults.seeResults) {
      lines.push(`- mustSee @ ${item.target}: ${item.pass ? 'PASS' : 'FAIL'}`);
      for (const check of item.checks) {
        lines.push(`  - ${check.text}: ${check.ok ? 'OK' : 'MISSING'}`);
      }
    }
    for (const item of textResults.notSeeResults) {
      lines.push(`- mustNotSee @ ${item.target}: ${item.pass ? 'PASS' : 'FAIL'}`);
      for (const check of item.checks) {
        lines.push(`  - ${check.text}: ${check.ok ? 'OK' : 'FOUND'}`);
      }
    }
    lines.push('');
    lines.push('## API 500');
    lines.push(summary.api500Events.length ? summary.api500Events.map((item) => `- ${item.status} ${item.method} ${item.url}`).join('\n') : '- (none)');
    lines.push('');
    lines.push('## Console errors after filter');
    lines.push(summary.consoleErrorsFiltered.length ? summary.consoleErrorsFiltered.map((item) => `- [${item.route}] ${item.text}`).join('\n') : '- (none)');
    lines.push('');
    lines.push('## Summary');
    lines.push('```json');
    lines.push(JSON.stringify(summary, null, 2));
    lines.push('```');
    fs.writeFileSync(path.join(outDir, 'report.md'), `${lines.join('\n')}\n`);

    return summary;
  } finally {
    await browser.close();
  }
}

async function main() {
  const argv = process.argv.slice(2);
  const outDir = argv[0];
  const role = argv[1];
  const summary = await runRoleSmoke({ outDir, role });
  if (!summary.overallPass) {
    process.exitCode = 1;
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = {
  CASES_FILE,
  loadRoleCases,
  normalizePathname,
  matchesAllowedPath,
  evaluateAccessRule,
  evaluateRejectRule,
  evaluateTextRules,
  runRoleSmoke
};
