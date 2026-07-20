const path = require('node:path');
const {
  applyRealPreEnv,
  createEvidenceDir,
  resolveQaAdminCredential,
  unwrapApiBody,
  writeJson,
  writeText
} = require('./real-pre-env.cjs');

const root = path.join(__dirname, '..', '..');
const envFile = path.join(root, '.env.real-pre');
const urls = applyRealPreEnv(process.env);
const username = String(process.env.QA_ADMIN_USER || 'admin').trim();
const password = resolveQaAdminCredential(process.env, { envFile });
const evidenceDir = createEvidenceDir(root, 'rbac-permission-real-pre-probe');

run().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  writeEvidence({ status: 'FAIL', error: message });
  console.error(`RBAC real-pre probe failed: ${message}`);
  process.exitCode = 1;
});

async function run() {
  if (!password) throw new Error('QA admin credential is missing');

  const login = await request('/api/auth/login', {
    method: 'POST',
    body: { username, password }
  });
  const loginData = unwrapApiBody(login.body) || {};
  const token = loginData.token || loginData.accessToken;
  if (!token) throw new Error('admin login response did not contain an access token');

  const projectedPermissions = normalizeCodes(loginData.permissionCodes);
  assertIncludes(projectedPermissions, 'sys-role:access', 'login permission projection');

  const unauthorized = await request('/api/roles/permissions', {}, false);
  if (unauthorized.response.ok) {
    throw new Error('permission catalog accepted an unauthenticated request');
  }

  const headers = { Authorization: `Bearer ${token}` };
  const catalogResponse = await request('/api/roles/permissions', { headers });
  const catalog = unwrapApiBody(catalogResponse.body);
  if (!Array.isArray(catalog) || catalog.length < 129) {
    throw new Error(`permission catalog is incomplete: count=${Array.isArray(catalog) ? catalog.length : 'invalid'}`);
  }
  assertIncludes(catalog.map((item) => item.permissionCode), 'sys-role:access', 'permission catalog');

  const rolesResponse = await request('/api/roles/enabled', { headers });
  const roles = unwrapApiBody(rolesResponse.body);
  const adminRole = Array.isArray(roles) ? roles.find((role) => role.roleCode === 'admin') : null;
  if (!adminRole?.id) throw new Error('enabled admin role was not found');

  const grantsResponse = await request(`/api/roles/${adminRole.id}/permissions`, { headers });
  const before = normalizeCodes(unwrapApiBody(grantsResponse.body));
  assertIncludes(before, 'sys-role:access', 'admin role grants');

  await request(`/api/roles/${adminRole.id}/permissions`, {
    method: 'PUT',
    headers,
    body: before
  });
  const afterResponse = await request(`/api/roles/${adminRole.id}/permissions`, { headers });
  const after = normalizeCodes(unwrapApiBody(afterResponse.body));
  if (JSON.stringify(before) !== JSON.stringify(after)) {
    throw new Error('no-op permission assignment changed the admin grant set');
  }

  const summary = {
    status: 'PASS',
    backendUrl: urls.backendUrl,
    unauthenticatedStatus: unauthorized.response.status,
    projectedPermissionCount: projectedPermissions.length,
    catalogPermissionCount: catalog.length,
    adminGrantCount: after.length,
    noOpAssignmentPreservedGrants: true
  };
  writeEvidence(summary);
  console.log(`RBAC real-pre probe PASS; evidence=${evidenceDir}`);
}

async function request(pathname, options = {}, requireSuccess = true) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const response = await fetch(`${urls.backendUrl}${pathname}`, {
    method: options.method || 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: AbortSignal.timeout(15_000)
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  if (requireSuccess && (!response.ok || isBusinessFailure(body))) {
    throw new Error(`${options.method || 'GET'} ${pathname} failed: HTTP ${response.status}`);
  }
  return { response, body };
}

function isBusinessFailure(body) {
  if (!body || typeof body !== 'object' || body.code === undefined) return false;
  return ![0, 200, '0', '200'].includes(body.code);
}

function normalizeCodes(value) {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map(String).map((item) => item.trim()).filter(Boolean))].sort();
}

function assertIncludes(values, expected, source) {
  if (!values.includes(expected)) throw new Error(`${source} is missing ${expected}`);
}

function writeEvidence(summary) {
  writeJson(path.join(evidenceDir, 'summary.json'), summary);
  const lines = [
    '# RBAC permission real-pre probe',
    '',
    `- status: ${summary.status}`,
    `- backendUrl: ${summary.backendUrl || urls.backendUrl}`,
    `- unauthenticatedStatus: ${summary.unauthenticatedStatus ?? 'not collected'}`,
    `- projectedPermissionCount: ${summary.projectedPermissionCount ?? 'not collected'}`,
    `- catalogPermissionCount: ${summary.catalogPermissionCount ?? 'not collected'}`,
    `- adminGrantCount: ${summary.adminGrantCount ?? 'not collected'}`,
    `- noOpAssignmentPreservedGrants: ${summary.noOpAssignmentPreservedGrants ?? false}`,
    `- error: ${summary.error || 'none'}`,
    ''
  ];
  writeText(path.join(evidenceDir, 'report.md'), lines.join('\n'));
}
