import { defineConfig, devices } from '@playwright/test';
import * as dotenv from 'dotenv';

dotenv.config({ path: process.env.E2E_ENV_FILE || '.env.test' });
const alwaysCaptureArtifacts =
  process.env.E2E_VISUAL_CAPTURE === 'true' ||
  process.env.E2E_REAL_PRE_JOURNEY_VISUAL === 'true';
const slowMo = Number(process.env.PW_SLOWMO_MS || 0);
const requestedWorkers = Number(process.env.PW_WORKERS || 1);
const workers = Number.isFinite(requestedWorkers) && requestedWorkers > 0 ? requestedWorkers : 1;
const headless = process.env.E2E_HEADLESS === undefined
  ? true
  : process.env.E2E_HEADLESS === 'true';

export default defineConfig({
  testDir: './tests/e2e',
  testIgnore: ['**/helpers/**'],
  timeout: 60_000,
  expect: {
    timeout: 10_000,
    toHaveScreenshot: {
      maxDiffPixels: 500
    }
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers,
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list']
  ],
  outputDir: 'test-results/playwright',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:3000',
    headless,
    launchOptions: {
      slowMo
    },
    screenshot: alwaysCaptureArtifacts ? 'on' : 'only-on-failure',
    video: alwaysCaptureArtifacts ? 'on' : 'retain-on-failure',
    trace: alwaysCaptureArtifacts ? 'on' : 'off',
    viewport: { width: 1440, height: 900 },
    permissions: ['clipboard-read', 'clipboard-write']
  },
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        screenshot: 'off',
        video: 'off',
        trace: 'off'
      }
    },
    {
      name: 'chromium',
      dependencies: ['setup'],
      testIgnore: [
        /auth\.setup\.ts/,
        /08-real-pre-douyin-integration\.spec\.ts/,
        /10-real-pre-business-flow\.spec\.ts/,
        /11-real-pre-role-business-flow\.spec\.ts/,
        /12-real-pre-full-business-journey\.visual\.spec\.ts/,
        /09-full-user-journey\.spec\.ts/,
        /2[0-4]-v1-.*\.spec\.ts/,
        /3[1-6]-real-pre-.*\.spec\.ts/
      ],
      use: { ...devices['Desktop Chrome'] }
    },
    {
      // V1-P0 验收级套件：渠道链 / 招商链 / 管理配置链 / RBAC / 业绩看板
      // 需要 storageState（由 setup project 生成），独立于日常 CI smoke 运行。
      name: 'v1-p0',
      dependencies: ['setup'],
      testMatch: /2[0-4]-v1-.*\.spec\.ts/,
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'real-pre',
      testMatch: /08-real-pre-douyin-integration\.spec\.ts/,
      dependencies: [],
      use: { ...devices['Desktop Chrome'] }
    },
    {
      // real-pre P0 验收级套件：商品链 / 订单归因 / 寄样链 / 业绩看板 / RBAC / 清理计划
      // 与 v1-p0 (test/mock) 完全互不重叠；只允许在 real-pre 环境真实联调使用。
      name: 'real-pre-p0',
      testMatch: /3[1-6]-real-pre-.*\.spec\.ts/,
      dependencies: [],
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'real-pre-business',
      testMatch: /10-real-pre-business-flow\.spec\.ts/,
      dependencies: [],
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'real-pre-roles',
      testMatch: /11-real-pre-role-business-flow\.spec\.ts/,
      dependencies: [],
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'real-pre-journey-visual',
      testMatch: /12-real-pre-full-business-journey\.visual\.spec\.ts/,
      dependencies: [],
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'full-journey',
      testMatch: /09-full-user-journey\.spec\.ts/,
      dependencies: [],
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
