const fs = require('node:fs');
const path = require('node:path');

const DEFAULT_REAL_PRE_FRONTEND_URL = 'http://localhost:3001';
const DEFAULT_REAL_PRE_BACKEND_URL = 'http://localhost:8081';
const DEFAULT_REAL_PRE_API_BASE_URL = `${DEFAULT_REAL_PRE_BACKEND_URL}/api`;
const DEFAULT_REAL_PRE_DB_NAME = 'saas_real_pre';
const DEFAULT_REAL_PRE_DB_CONTAINER = 'saas-active-postgres-real-pre-1';
const DEFAULT_REAL_PRE_DB_USER = 'saas';

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function resolveRealPreUrls(env = process.env) {
  const frontendUrl = stripTrailingSlash(env.E2E_BASE_URL || env.FRONTEND_URL || DEFAULT_REAL_PRE_FRONTEND_URL);
  const backendUrl = stripTrailingSlash(env.E2E_BACKEND_URL || env.BACKEND_URL || DEFAULT_REAL_PRE_BACKEND_URL);
  return {
    frontendUrl,
    backendUrl,
    apiBaseUrl: `${backendUrl}/api`
  };
}

function applyRealPreEnv(env = process.env) {
  const urls = resolveRealPreUrls(env);
  env.E2E_REAL_PRE = 'true';
  env.E2E_BASE_URL = urls.frontendUrl;
  env.E2E_BACKEND_URL = urls.backendUrl;
  env.API_BASE_URL = env.API_BASE_URL || urls.backendUrl;
  return urls;
}

function formatLocalTimestamp(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0');
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join('') + '-' + [
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds())
  ].join('');
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function createEvidenceDir(root, name, envName) {
  const override = envName ? process.env[envName] : '';
  if (override) return ensureDir(path.resolve(override));
  return ensureDir(path.join(root, 'runtime', 'qa', 'out', `${name}-${formatLocalTimestamp()}`));
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function writeText(filePath, value) {
  fs.writeFileSync(filePath, value, 'utf8');
}

function unwrapApiBody(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data;
  }
  return body;
}

function normalizeSystemEnv(body) {
  const data = unwrapApiBody(body) || {};
  const rawProfiles = []
    .concat(data.activeProfiles || [])
    .concat(data.activeProfile || [])
    .concat(data.profiles || [])
    .filter(Boolean);
  const activeProfiles = rawProfiles.map((item) => String(item).trim().toLowerCase()).filter(Boolean);
  return {
    activeProfiles,
    environmentLabel: String(data.environmentLabel || '').trim().toUpperCase(),
    appTestEnabled: data.appTestEnabled === true || String(data.appTestEnabled).toLowerCase() === 'true',
    douyinTestEnabled: data.douyinTestEnabled === true || String(data.douyinTestEnabled).toLowerCase() === 'true',
    database: String(data.database || '').trim()
  };
}

function isRealPreRuntime(env) {
  const profiles = Array.isArray(env?.activeProfiles) ? env.activeProfiles : [];
  return (
    env?.environmentLabel === 'REAL-PRE' &&
    (profiles.includes('real-pre') || profiles.includes('real')) &&
    env?.appTestEnabled === false &&
    env?.douyinTestEnabled === false &&
    env?.database === DEFAULT_REAL_PRE_DB_NAME
  );
}

function redactSecretLikeKeys(value) {
  if (Array.isArray(value)) return value.map(redactSecretLikeKeys);
  if (!value || typeof value !== 'object') return value;
  const out = {};
  for (const [key, raw] of Object.entries(value)) {
    if (/token|secret|password|authorization|sign/i.test(key)) {
      out[key] = '[redacted]';
    } else {
      out[key] = redactSecretLikeKeys(raw);
    }
  }
  return out;
}

module.exports = {
  DEFAULT_REAL_PRE_FRONTEND_URL,
  DEFAULT_REAL_PRE_BACKEND_URL,
  DEFAULT_REAL_PRE_API_BASE_URL,
  DEFAULT_REAL_PRE_DB_NAME,
  DEFAULT_REAL_PRE_DB_CONTAINER,
  DEFAULT_REAL_PRE_DB_USER,
  stripTrailingSlash,
  resolveRealPreUrls,
  applyRealPreEnv,
  formatLocalTimestamp,
  ensureDir,
  createEvidenceDir,
  writeJson,
  writeText,
  unwrapApiBody,
  normalizeSystemEnv,
  isRealPreRuntime,
  redactSecretLikeKeys
};
