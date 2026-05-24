const test = require('node:test');
const assert = require('node:assert/strict');

const {
  DEFAULT_REAL_PRE_BACKEND_URL,
  DEFAULT_REAL_PRE_FRONTEND_URL,
  applyRealPreEnv,
  isRealPreRuntime,
  normalizeSystemEnv,
  redactSecretLikeKeys,
  resolveRealPreUrls
} = require('./real-pre-env.cjs');

test('resolveRealPreUrls defaults to the real-pre 3001/8081 pair', () => {
  const urls = resolveRealPreUrls({});
  assert.equal(urls.frontendUrl, DEFAULT_REAL_PRE_FRONTEND_URL);
  assert.equal(urls.backendUrl, DEFAULT_REAL_PRE_BACKEND_URL);
  assert.equal(urls.apiBaseUrl, `${DEFAULT_REAL_PRE_BACKEND_URL}/api`);
});

test('applyRealPreEnv preserves explicit overrides and marks the run as real-pre', () => {
  const env = {
    FRONTEND_URL: 'http://localhost:3101/',
    BACKEND_URL: 'http://localhost:8181/'
  };
  const urls = applyRealPreEnv(env);
  assert.equal(urls.frontendUrl, 'http://localhost:3101');
  assert.equal(urls.backendUrl, 'http://localhost:8181');
  assert.equal(env.E2E_REAL_PRE, 'true');
  assert.equal(env.E2E_BASE_URL, 'http://localhost:3101');
  assert.equal(env.E2E_BACKEND_URL, 'http://localhost:8181');
});

test('normalizeSystemEnv accepts REAL-PRE on real or real-pre profile only when test switches are off', () => {
  const realProfile = normalizeSystemEnv({
    code: 200,
    data: {
      environmentLabel: 'REAL-PRE',
      activeProfiles: ['real'],
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
