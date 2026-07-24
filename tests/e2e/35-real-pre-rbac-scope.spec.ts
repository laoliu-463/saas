/**
 * real-pre P0 / 35 / RBAC
 *
 * 校验 6 类账号在 real-pre 下：
 *   - 登录可用
 *   - 允许页面可达 & 无运行时错误
 *   - 禁止页面进不去（或显示无权限）
 *   - 普通账号 403/401 关键接口（用户管理 / 配置 / 导出）
 *   - 数据范围：admin all / 组长 group / 普通成员 self；样本不足输出 PENDING_SCOPE_SAMPLE
 *
 * 不删除任何真实业务数据。
 */
import { test, expect, request as playwrightRequest, type APIRequestContext, type Browser } from '@playwright/test';
import { accounts } from './helpers/test-data';
import { installAuth } from './helpers/auth';
import { gotoApp } from './helpers/page-ready';
import { apiLogin } from './helpers/real-pre-api';
import {
  createRealPreP0Step,
  ensureRealPreP0Env,
  markBlocked,
  markFail,
  markPending,
  persistStepSummary,
  setDetail,
  shouldRunRealPreP0,
  safeUnwrap
} from './helpers/real-pre-p0-step';

type JsonMap = Record<string, unknown>;
const FATAL_TEXT = /Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i;

interface RoleCase {
  label: string;
  username: string;
  password: string;
  allowedPages: string[];
  forbiddenPages: string[];
  forbiddenApis: Array<{ method: 'GET' | 'POST' | 'PUT'; path: string; data?: JsonMap }>;
}

const PASSWORD = process.env.E2E_DEFAULT_PASSWORD || 'admin123';

const ROLE_CASES: RoleCase[] = [
  {
    label: 'admin',
    username: accounts.admin.username,
    password: accounts.admin.password,
    allowedPages: ['/dashboard', '/system/users', '/system/roles', '/system/config', '/system/douyin'],
    forbiddenPages: [],
    forbiddenApis: []
  },
  {
    label: 'biz_leader',
    username: accounts.bizLeader.username,
    password: accounts.bizLeader.password,
    allowedPages: ['/product', '/sample', '/orders', '/data'],
    forbiddenPages: ['/system/users', '/system/douyin'],
    // 注：/api/configs/grouped 在 SysConfigController 上方法级 @RequireRoles 已显式
    // 对 6 类角色全部开放（业务模块要读分组配置），不能放进 forbidden 列表。
    forbiddenApis: [
      { method: 'GET', path: '/api/users?page=1&size=5' },
      { method: 'GET', path: '/api/configs?page=1&size=5' }
    ]
  },
  {
    label: 'biz_staff',
    username: 'biz_staff',
    password: PASSWORD,
    allowedPages: ['/product', '/sample', '/orders'],
    forbiddenPages: ['/system/users'],
    forbiddenApis: [
      { method: 'GET', path: '/api/users?page=1&size=5' },
      { method: 'GET', path: '/api/configs?page=1&size=5' }
    ]
  },
  {
    label: 'channel_leader',
    username: 'channel_leader',
    password: PASSWORD,
    allowedPages: ['/product', '/talent', '/sample', '/orders', '/data'],
    forbiddenPages: ['/system/users', '/system/douyin'],
    forbiddenApis: [
      { method: 'GET', path: '/api/users?page=1&size=5' },
      { method: 'GET', path: '/api/samples/exports?page=1&size=1' }
    ]
  },
  {
    label: 'channel_staff',
    username: accounts.channelStaff.username,
    password: accounts.channelStaff.password,
    allowedPages: ['/product', '/talent', '/sample', '/orders'],
    forbiddenPages: ['/system/users', '/system/douyin'],
    forbiddenApis: [
      { method: 'GET', path: '/api/users?page=1&size=5' },
      { method: 'GET', path: '/api/samples/exports?page=1&size=1' }
    ]
  },
  {
    label: 'ops_staff',
    username: accounts.ops.username,
    password: accounts.ops.password,
    allowedPages: ['/sample', '/orders'],
    forbiddenPages: ['/system/users', '/product/manage'],
    forbiddenApis: [
      { method: 'GET', path: '/api/users?page=1&size=5' },
      { method: 'GET', path: '/api/products?page=1&size=5' }
    ]
  }
];

test('real-pre P0 / 35 / RBAC', async ({ browser }, testInfo) => {
  test.skip(!shouldRunRealPreP0('35-real-pre-rbac-scope'), 'Run via npm run e2e:real-pre:p0 or set E2E_REAL_PRE_P0=true');
  test.setTimeout(20 * 60_000);
  ensureRealPreP0Env();

  const ctx = createRealPreP0Step('35-real-pre-rbac-scope', 'real-pre-p0/35/rbac');
  const backend = (process.env.E2E_BACKEND_URL || 'http://localhost:8081').replace(/\/$/, '');
  const frontend = (process.env.E2E_BASE_URL || 'http://localhost:3001').replace(/\/$/, '');
  const api = await playwrightRequest.newContext({ baseURL: backend, ignoreHTTPSErrors: true });

  const roleResults: Array<JsonMap> = [];

  try {
    for (const roleCase of ROLE_CASES) {
      const roleResult: JsonMap = { role: roleCase.label, username: roleCase.username };
      try {
        const auth = await apiLogin(`${backend}/api`, roleCase.username, roleCase.password);
        roleResult.loginResult = { ok: true, dataScope: auth.dataScope, roleCodes: auth.roleCodes };

        const allowed: JsonMap[] = [];
        for (const route of roleCase.allowedPages) {
          allowed.push(await checkPage(browser, frontend, auth, route, true));
        }
        roleResult.allowedPages = allowed;
        const allowedRuntimeError = allowed.find((item) => item.runtimeError);
        if (allowedRuntimeError) {
          markFail(ctx, `${roleCase.label} ${allowedRuntimeError.route} 出现运行时错误`);
        }

        const denied: JsonMap[] = [];
        for (const route of roleCase.forbiddenPages) {
          denied.push(await checkPage(browser, frontend, auth, route, false));
        }
        roleResult.deniedPages = denied;
        const leakage = denied.find((item) => item.finalPath === item.route);
        if (leakage) {
          markFail(ctx, `${roleCase.label} 未拦截禁止页面：${leakage.route}`);
        }

        const apiChecks: JsonMap[] = [];
        for (const probe of roleCase.forbiddenApis) {
          const result = await rawApi(api, probe.method, probe.path, String(auth.token || ''), { data: probe.data });
          const code = Number((result.body as JsonMap | undefined)?.code);
          const forbidden = [401, 403].includes(result.status) || [401, 403].includes(code);
          apiChecks.push({ method: probe.method, path: probe.path, status: result.status, code, forbidden });
          if (!forbidden) {
            markFail(ctx, `${roleCase.label} ${probe.method} ${probe.path} 越权未拦截 (HTTP ${result.status}, code=${code})`);
          }
        }
        roleResult.api403Checks = apiChecks;

        roleResult.dataScopeChecks = await sampleDataScope(api, auth, roleCase.label);
      } catch (error) {
        markFail(ctx, `${roleCase.label} RBAC 检查异常：${error instanceof Error ? error.message : String(error)}`);
        roleResult.loginResult = { ok: false, error: error instanceof Error ? error.message : String(error) };
      }
      roleResults.push(roleResult);
    }

    setDetail(ctx, 'roles', roleResults);

    const scopeSamplesMissing = roleResults.every((row) => {
      const checks = row.dataScopeChecks as JsonMap | undefined;
      return !checks || !checks.hasSample;
    });
    if (scopeSamplesMissing && ctx.summary.conclusion === 'PASS') {
      markPending(ctx, 'PENDING_SCOPE_SAMPLE: 当前数据不足以精确比对数据范围（admin/group/self）；权限负向已验证。');
    }

    const allLoginsOk = roleResults.every((row) => (row.loginResult as JsonMap | undefined)?.ok === true);
    if (!allLoginsOk && ctx.summary.conclusion !== 'FAIL') {
      markBlocked(ctx, 'BLOCKED_AUTH: 部分角色账号无法登录，可能账号未在 real-pre 库初始化');
    }
  } catch (error) {
    markFail(ctx, error instanceof Error ? error.message : String(error));
  } finally {
    persistStepSummary(ctx);
    await api.dispose();
    await testInfo.attach('step-summary.json', {
      body: JSON.stringify(ctx.summary, null, 2),
      contentType: 'application/json'
    });
  }

  expect(
    ctx.summary.conclusion === 'FAIL' ? ctx.summary.failures.join('; ') : 'OK',
    `real-pre 35 RBAC conclusion=${ctx.summary.conclusion}`
  ).toBe('OK');
});

async function checkPage(
  browser: Browser,
  frontend: string,
  auth: Record<string, unknown>,
  route: string,
  shouldAllow: boolean
): Promise<JsonMap> {
  const context = await browser.newContext({ baseURL: frontend, viewport: { width: 1440, height: 900 } });
  await installAuth(context, auth);
  const page = await context.newPage();
  try {
    await gotoApp(page, route, { timeout: 120_000 });
    const bodyText = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
    return {
      route,
      shouldAllow,
      finalPath: new URL(page.url()).pathname,
      runtimeError: FATAL_TEXT.test(bodyText)
    };
  } finally {
    await context.close().catch(() => undefined);
  }
}

async function sampleDataScope(api: APIRequestContext, auth: Record<string, unknown>, role: string): Promise<JsonMap> {
  // 渠道相关角色取 /api/samples?page=1&size=5；招商相关角色取 /api/products?page=1&size=5。
  // 这里只做样本统计，不做绝对值断言。
  const probe = role.startsWith('channel') ? '/api/samples?page=1&size=5'
    : role.startsWith('biz') ? '/api/products?page=1&size=5'
      : role === 'ops_staff' ? '/api/samples?page=1&size=5&status=PENDING_SHIP'
        : '/api/users?page=1&size=5';
  const result = await rawApi(api, 'GET', probe, String(auth.token || ''));
  const data = safeUnwrap<JsonMap>(result.body) || {};
  const total = Number(data.total ?? 0);
  return {
    probe,
    status: result.status,
    total,
    hasSample: total > 0
  };
}

async function rawApi(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  token: string,
  options: { data?: JsonMap } = {}
): Promise<{ status: number; ok: boolean; body: unknown }> {
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const requestOptions = { headers, data: options.data };
  const response = method === 'GET'
    ? await api.get(path, requestOptions)
    : method === 'POST'
      ? await api.post(path, requestOptions)
      : await api.put(path, requestOptions);
  const body = await response.json().catch(async () => ({ rawText: await response.text().catch(() => '') }));
  return { status: response.status(), ok: response.ok(), body };
}
