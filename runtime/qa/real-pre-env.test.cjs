const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  DEFAULT_REAL_PRE_BACKEND_URL,
  DEFAULT_REAL_PRE_DB_CONTAINER,
  DEFAULT_REAL_PRE_FRONTEND_URL,
  applyRealPreEnv,
  applyQaAdminCredentialToE2eEnv,
  isRealPreRuntime,
  normalizeSystemEnv,
  redactSecretLikeKeys,
  resolveQaAdminCredential,
  resolveRealPreDbContainer,
  resolveRealPreUrls
} = require('./real-pre-env.cjs');

test('resolveRealPreUrls defaults to the real-pre 3001/8081 pair', () => {
  const urls = resolveRealPreUrls({});
  assert.equal(urls.frontendUrl, DEFAULT_REAL_PRE_FRONTEND_URL);
  assert.equal(urls.backendUrl, DEFAULT_REAL_PRE_BACKEND_URL);
  assert.equal(urls.apiBaseUrl, `${DEFAULT_REAL_PRE_BACKEND_URL}/api`);
});

test('default database container matches the active real-pre compose project', () => {
  assert.equal(DEFAULT_REAL_PRE_DB_CONTAINER, 'saas-active-postgres-real-pre-1');
  assert.equal(resolveRealPreDbContainer({}), 'saas-active-postgres-real-pre-1');
});

test('applyRealPreEnv preserves explicit overrides and marks the run as real-pre', () => {
  const env = {
    FRONTEND_URL: 'http://localhost:3101/',
    BACKEND_URL: 'http://localhost:8181/',
    E2E_DB_CONTAINER: 'custom-postgres-real-pre-1'
  };
  const urls = applyRealPreEnv(env);
  assert.equal(urls.frontendUrl, 'http://localhost:3101');
  assert.equal(urls.backendUrl, 'http://localhost:8181');
  assert.equal(env.E2E_REAL_PRE, 'true');
  assert.equal(env.E2E_BASE_URL, 'http://localhost:3101');
  assert.equal(env.E2E_BACKEND_URL, 'http://localhost:8181');
  assert.equal(env.E2E_DB_CONTAINER, 'custom-postgres-real-pre-1');
});

test('applyRealPreEnv replaces the retired real-pre database container default', () => {
  const env = {
    E2E_DB_CONTAINER: 'saas-postgres-real-pre-1',
    QA_POSTGRES_CONTAINER: 'saas-postgres-real-pre-1'
  };
  applyRealPreEnv(env);
  assert.equal(env.E2E_DB_CONTAINER, 'saas-active-postgres-real-pre-1');
  assert.equal(env.QA_POSTGRES_CONTAINER, 'saas-active-postgres-real-pre-1');
});

test('resolveQaAdminCredential prefers an explicit QA credential', () => {
  const envFile = path.join(os.tmpdir(), `saas-real-pre-env-${Date.now()}-explicit`);
  fs.writeFileSync(envFile, 'ADMIN_PASSWORD=file-pwd\n', 'utf8');
  try {
    assert.equal(
      resolveQaAdminCredential(
        { QA_ADMIN_PASSWORD: 'x' },
        { envFile }
      ),
      'x'
    );
  } finally {
    fs.rmSync(envFile, { force: true });
  }
});

test('resolveQaAdminCredential reads ADMIN_PASSWORD from the supplied local env file', () => {
  const envFile = path.join(os.tmpdir(), `saas-real-pre-env-${Date.now()}-file`);
  fs.writeFileSync(envFile, '# local only\nQA_ADMIN_PASSWORD=ignored\nADMIN_PASSWORD=file-pwd\n', 'utf8');
  try {
    assert.equal(resolveQaAdminCredential({}, { envFile }), 'file-pwd');
  } finally {
    fs.rmSync(envFile, { force: true });
  }
});

test('resolveQaAdminCredential has no insecure hardcoded fallback', () => {
  const envFile = path.join(os.tmpdir(), `saas-real-pre-env-${Date.now()}-missing`);
  try {
    assert.equal(resolveQaAdminCredential({}, { envFile }), '');
  } finally {
    fs.rmSync(envFile, { force: true });
  }
});

test('applyQaAdminCredentialToE2eEnv only sets the admin E2E password', () => {
  const env = { E2E_ADMIN_PASSWORD: 'stale', E2E_DEFAULT_PASSWORD: 'default' };
  applyQaAdminCredentialToE2eEnv(env, 'x');
  assert.equal(env.E2E_ADMIN_PASSWORD, 'x');
  assert.equal(env.E2E_DEFAULT_PASSWORD, 'default');
});

test('normalizeSystemEnv accepts REAL-PRE only on real-pre profile when test switches are off', () => {
  const realProfile = normalizeSystemEnv({
    code: 200,
    data: {
      environmentLabel: 'REAL-PRE',
      activeProfiles: ['real-pre'],
      appTestEnabled: false,
      douyinTestEnabled: false,
      database: 'saas_real_pre'
    }
  });
  assert.equal(isRealPreRuntime(realProfile), true);

  const testProfile = normalizeSystemEnv({
    data: {
      environmentLabel: 'TEST',
      activeProfiles: ['test'],
      appTestEnabled: true,
      douyinTestEnabled: true,
      database: 'saas_test'
    }
  });
  assert.equal(isRealPreRuntime(testProfile), false);
});

test('redactSecretLikeKeys removes token and secret-like values from reports', () => {
  assert.deepEqual(
    redactSecretLikeKeys({
      token: 'abc',
      nested: { refreshToken: 'def', safe: 'value' },
      list: [{ clientSecret: 'ghi' }]
    }),
    {
      token: '[redacted]',
      nested: { refreshToken: '[redacted]', safe: 'value' },
      list: [{ clientSecret: '[redacted]' }]
    }
  );
});
