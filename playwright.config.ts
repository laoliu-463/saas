import { defineConfig, devices } from '@playwright/test';
import * as dotenv from 'dotenv';

dotenv.config({ path: '.env.e2e' });

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
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list']
  ],
  outputDir: 'test-results/playwright',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    headless: process.env.E2E_HEADLESS === 'true',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
    viewport: { width: 1440, height: 960 },
    permissions: ['clipboard-read', 'clipboard-write']
  },
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'chromium',
      dependencies: ['setup'],
      testIgnore: [/auth\.setup\.ts/, /08-real-pre-douyin-integration\.spec\.ts/],
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'real-pre',
      testMatch: /08-real-pre-douyin-integration\.spec\.ts/,
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
