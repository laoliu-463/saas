const fs = require('node:fs');
const path = require('node:path');

const DEFAULT_REAL_PRE_FRONTEND_URL = 'http://localhost:3001';
const DEFAULT_REAL_PRE_BACKEND_URL = 'http://localhost:8081';
const DEFAULT_REAL_PRE_API_BASE_URL = `${DEFAULT_REAL_PRE_BACKEND_URL}/api`;
const DEFAULT_REAL_PRE_DB_NAME = 'saas_real_pre';
const DEFAULT_REAL_PRE_COMPOSE_PROJECT = 'saas-active';
const LEGACY_REAL_PRE_DB_CONTAINER = 'saas-postgres-real-pre-1';
const DEFAULT_REAL_PRE_DB_CONTAINER = `${DEFAULT_REAL_PRE_COMPOSE_PROJECT}-postgres-real-pre-1`;
const DEFAULT_REAL_PRE_DB_USER = 'saas';

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function readEnvFile(filePath) {
  if (!filePath) return {};
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const values = {};
    for (const line of content.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
      if (!match) continue;
      let value = match[2].trim();
      if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
        value = value.slice(1, -1);
      }
      values[match[1]] = value;
    }
    return values;
  } catch {
    return {};
  }
}

function resolveQaAdminCredential(env = {}, options = {}) {
  const explicit = String(env.QA_ADMIN_PASSWORD || '').trim();
  if (explicit) return explicit;
  const fileValues = readEnvFile(options.envFile);
  return String(fileValues.QA_ADMIN_PASSWORD || fileValues.ADMIN_PASSWORD || '').trim();
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

function resolveRealPreDbContainer(env = process.env) {
  const explicit = String(env.E2E_DB_CONTAINER || env.QA_POSTGRES_CONTAINER || '').trim();
  if (explicit && explicit !== LEGACY_REAL_PRE_DB_CONTAINER) {
    return explicit;
  }
  const project = String(env.REAL_PRE_COMPOSE_PROJECT || env.COMPOSE_PROJECT_NAME || DEFAULT_REAL_PRE_COMPOSE_PROJECT).trim() || DEFAULT_REAL_PRE_COMPOSE_PROJECT;
  return `${project}-postgres-real-pre-1`;
}

function applyRealPreEnv(env = process.env) {
  const urls = resolveRealPreUrls(env);
  const dbContainer = resolveRealPreDbContainer(env);
  env.E2E_REAL_PRE = 'true';
  env.E2E_BASE_URL = urls.frontendUrl;
  env.E2E_BACKEND_URL = urls.backendUrl;
  env.API_BASE_URL = env.API_BASE_URL || urls.backendUrl;
  if (!env.E2E_DB_CONTAINER || env.E2E_DB_CONTAINER === LEGACY_REAL_PRE_DB_CONTAINER) {
    env.E2E_DB_CONTAINER = dbContainer;
  }
  if (env.QA_POSTGRES_CONTAINER === LEGACY_REAL_PRE_DB_CONTAINER) {
    env.QA_POSTGRES_CONTAINER = dbContainer;
  }
  env.E2E_DB_NAME = env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME;
  env.E2E_DB_USER = env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER;
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
    profiles.includes('real-pre') &&
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
  DEFAULT_REAL_PRE_COMPOSE_PROJECT,
  DEFAULT_REAL_PRE_DB_CONTAINER,
  DEFAULT_REAL_PRE_DB_USER,
  LEGACY_REAL_PRE_DB_CONTAINER,
  stripTrailingSlash,
  resolveQaAdminCredential,
  resolveRealPreUrls,
  resolveRealPreDbContainer,
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
