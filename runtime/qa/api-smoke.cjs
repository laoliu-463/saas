const fs = require('node:fs');
const path = require('node:path');
const { request } = require('@playwright/test');

const args = process.argv.slice(2);
const outDir = args[0];
if (!outDir) {
  throw new Error('outDir argument is required');
}

const BACKEND = process.env.QA_BACKEND || 'http://127.0.0.1:8080';
const API = `${BACKEND}/api`;

fs.mkdirSync(outDir, { recursive: true });

function pushReportLine(lines, value = '') {
  lines.push(value);
}

async function login(api, username, password) {
  const res = await api.post(`${API}/auth/login`, {
    data: { username, password }
  });
  const body = await res.json().catch(() => ({}));
  return {
    ok: res.ok(),
    status: res.status(),
    body,
    token: body?.data?.token || ''
  };
}

async function apiGet(api, token, urlPath) {
  const res = await api.get(`${API}${urlPath}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });
  const body = await res.json().catch(() => ({}));
  return {
    http: res.status(),
    businessCode: body?.code,
    okHttp: res.ok(),
    okBiz: body?.code === 200,
    body
  };
}

async function main() {
  const api = await request.newContext();
  const checks = [];
  const lines = [];
  pushReportLine(lines, '# QA API Smoke');
  pushReportLine(lines);
  pushReportLine(lines, `- Backend: ${BACKEND}`);
  pushReportLine(lines);

  try {
    const health = await apiGet(api, null, '/actuator/health');
    checks.push({
      name: 'health_up',
      ok: health.okHttp && health.body?.status === 'UP',
      detail: { http: health.http, body: health.body }
    });

    const envAnon = await apiGet(api, null, '/system/env');
    checks.push({
      name: 'system_env_anonymous',
      ok: envAnon.okBiz && envAnon.body?.data?.environmentLabel === 'TEST',
      detail: {
        http: envAnon.http,
        businessCode: envAnon.businessCode,
        body: envAnon.body
      }
    });

    const adminLogin = await login(api, 'admin', 'admin123');
    checks.push({
      name: 'admin_login',
      ok: adminLogin.ok && !!adminLogin.token,
      detail: { status: adminLogin.status, code: adminLogin.body?.code }
    });

    const bizLeaderLogin = await login(api, 'biz_leader', 'admin123');
    checks.push({
      name: 'biz_leader_login',
      ok: bizLeaderLogin.ok && !!bizLeaderLogin.token,
      detail: { status: bizLeaderLogin.status, code: bizLeaderLogin.body?.code }
    });

    if (adminLogin.token) {
      const envAuth = await apiGet(api, adminLogin.token, '/system/env');
      checks.push({
        name: 'system_env_with_bearer_test',
        ok: envAuth.okBiz && envAuth.body?.data?.environmentLabel === 'TEST',
        detail: envAuth.body
      });

      const dash = await apiGet(api, adminLogin.token, '/dashboard/summary');
      checks.push({
        name: 'admin_dashboard_summary',
        ok: dash.okBiz,
        detail: { http: dash.http, code: dash.businessCode }
      });
    }

    if (bizLeaderLogin.token) {
      const paths = [
        { path: '/dashboard/summary', expectBiz: 200 },
        { path: '/products?page=1&size=5', expectBiz: 200 },
        { path: '/orders?page=1&size=5', expectBiz: 200 },
        { path: '/talents?page=1&size=5', expectBiz: 403 },
        { path: '/configs?page=1&size=5', expectBiz: 403 },
        { path: '/users?page=1&size=5', expectBiz: 403 }
      ];
      for (const item of paths) {
        const r = await apiGet(api, bizLeaderLogin.token, item.path);
        checks.push({
          name: `biz_leader_${item.path.split('?')[0].replace(/\//g, '_')}`,
          ok: r.businessCode === item.expectBiz,
          detail: { path: item.path, http: r.http, businessCode: r.businessCode }
        });
      }
    }
  } finally {
    await api.dispose();
  }

  const overallPass = checks.every((check) => check.ok);
  const summary = {
    script: 'qa-api-smoke',
    generatedAt: new Date().toISOString(),
    outputDir: outDir,
    overallPass,
    checks
  };

  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2));

  pushReportLine(lines, '## Checks');
  for (const check of checks) {
    pushReportLine(lines, `- ${check.name}: ${check.ok ? 'PASS' : 'FAIL'}`);
  }
  pushReportLine(lines);
  pushReportLine(lines, '## Details');
  pushReportLine(lines, '```json');
  pushReportLine(lines, JSON.stringify(summary, null, 2));
  pushReportLine(lines, '```');
  fs.writeFileSync(path.join(outDir, 'report.md'), `${lines.join('\n')}\n`);

  if (!overallPass) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
